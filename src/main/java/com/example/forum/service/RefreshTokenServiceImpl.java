package com.example.forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.forum.config.JwtProperties;
import com.example.forum.utils.JwtUtils;
import com.example.forum.entity.RefreshToken;
import com.example.forum.repo.RefreshTokenRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl extends ServiceImpl<RefreshTokenRepo, RefreshToken>
        implements RefreshTokenService {
    private final JwtProperties jwtProperties;
    private final RedisTemplate redisTemplate;
    private String tokenKey(String token) { return "auth:refresh:" + token; }


    @Override
    public RefreshToken createRefreshToken(Integer userId, String username) {
        long refreshTtl = jwtProperties.getRefreshTtl();
        if (refreshTtl <= 0) {
            throw new IllegalStateException("Refresh token TTL must be positive");
        }
        String refreshToken = JwtUtils.createJwt(
                jwtProperties.getRefreshSecretKey(),
                refreshTtl,
                Map.of(
                        "type", "refresh",
                        "userId", userId,
                        "username", username
                )
        );

        LocalDateTime now = LocalDateTime.now();
        RefreshToken entity = new RefreshToken()
                .setUserId(userId)
                .setToken(refreshToken)
                .setCreatedAt(now)
                .setExpiresAt(now.plus(Duration.ofMillis(refreshTtl)))
                .setRevoked(Boolean.FALSE);
        save(entity);

        // 缓存到Redis，使用与JWT相同的TTL
        try {
            redisTemplate.opsForValue().set(
                    tokenKey(refreshToken),
                    entity,
                    Duration.ofMillis(refreshTtl)
            );
            log.debug("将RefreshToken缓存到Redis: userId={}", userId);
        } catch (Exception e) {
            log.warn("RefreshToken缓存到Redis失败: userId={}, error={}", userId, e.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("Created refresh token for user {} (id={})", username, userId);
        }
        return entity;
    }

    @Override
    public Optional<RefreshToken> findValidToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Optional.empty();
        }

        // 1. 先从Redis缓存中查询
        RefreshToken stored = (RefreshToken) redisTemplate.opsForValue().get(tokenKey(token));

        // 2. 缓存命中，验证有效性
        if (stored != null) {
            if (stored.getExpiresAt().isBefore(LocalDateTime.now()) || Boolean.TRUE.equals(stored.getRevoked())) {
                return Optional.empty();
            }
            return Optional.of(stored);
        }

        // 3. 缓存未命中，回退到数据库查询
        log.debug("Redis缓存未命中，查询数据库: token={}", token.substring(0, Math.min(20, token.length())));
        LambdaQueryWrapper<RefreshToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefreshToken::getToken, token)
                .eq(RefreshToken::getRevoked, Boolean.FALSE);
        RefreshToken dbToken = getOne(wrapper);

        if (dbToken == null || dbToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        // 4. 回写缓存（Cache-Aside模式）
        try {
            long ttl = Duration.between(LocalDateTime.now(), dbToken.getExpiresAt()).toMillis();
            if (ttl > 0) {
                redisTemplate.opsForValue().set(tokenKey(token), dbToken, Duration.ofMillis(ttl));
                log.debug("将数据库查询结果回写Redis缓存");
            }
        } catch (Exception e) {
            log.warn("回写Redis缓存失败: error={}", e.getMessage());
        }

        return Optional.of(dbToken);
    }

    @Override
    public void revokeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        // 1. 更新数据库标记为已撤销
        LambdaUpdateWrapper<RefreshToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RefreshToken::getToken, token)
                .eq(RefreshToken::getRevoked, Boolean.FALSE)
                .set(RefreshToken::getRevoked, Boolean.TRUE)
                .set(RefreshToken::getRevokedAt, LocalDateTime.now());
        boolean dbUpdated = update(wrapper);

        // 2. 删除Redis缓存
        try {
            Boolean deleted = redisTemplate.delete(tokenKey(token));
            log.debug("撤销RefreshToken: token={}, dbUpdated={}, redisDeleted={}",
                    token.substring(0, Math.min(20, token.length())), dbUpdated, deleted);
        } catch (Exception e) {
            log.warn("删除Redis缓存失败: error={}", e.getMessage());
        }
    }

    @Override
    public void revokeTokensByUser(Integer userId) {
        if (userId == null) {
            return;
        }

        // 1. 查询该用户所有有效的RefreshToken
        LambdaQueryWrapper<RefreshToken> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getRevoked, Boolean.FALSE);
        List<RefreshToken> tokens = list(queryWrapper);

        if (tokens == null || tokens.isEmpty()) {
            log.debug("用户没有有效的RefreshToken: userId={}", userId);
            return;
        }

        // 2. 更新数据库标记为已撤销
        LambdaUpdateWrapper<RefreshToken> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getRevoked, Boolean.FALSE)
                .set(RefreshToken::getRevoked, Boolean.TRUE)
                .set(RefreshToken::getRevokedAt, LocalDateTime.now());
        boolean dbUpdated = update(updateWrapper);

        // 3. 循环删除Redis中的缓存（记录 userId -> tokens 的 SET，循环删除）
        int deletedCount = 0;
        for (RefreshToken token : tokens) {
            if (token.getToken() != null) {
                try {
                    Boolean deleted = redisTemplate.delete(tokenKey(token.getToken()));
                    if (Boolean.TRUE.equals(deleted)) {
                        deletedCount++;
                    }
                } catch (Exception e) {
                    log.warn("或许因为缓存在Redis中不存在，删除用户RefreshToken的Redis缓存失败: userId={}, tokenId={}, error={}",
                            userId, token.getId(), e.getMessage());
                }
            }
        }

        log.info("撤销用户所有RefreshToken: userId={}, total={}, dbUpdated={}, redisCacheDeleted={}",
                userId, tokens.size(), dbUpdated, deletedCount);
    }

    /**
     * 清理过期的RefreshToken（数据库 + Redis）
     * 建议通过定时任务调用此方法
     */
    private void cleanupExpiredTokens() {
        // 1. 查询所有过期但未标记的token
        LambdaQueryWrapper<RefreshToken> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.le(RefreshToken::getExpiresAt, LocalDateTime.now())
                .eq(RefreshToken::getRevoked, Boolean.FALSE);
        List<RefreshToken> expiredTokens = list(queryWrapper);

        if (expiredTokens == null || expiredTokens.isEmpty()) {
            return;
        }

        // 2. 更新数据库标记为已撤销
        LambdaUpdateWrapper<RefreshToken> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.le(RefreshToken::getExpiresAt, LocalDateTime.now())
                .eq(RefreshToken::getRevoked, Boolean.FALSE)
                .set(RefreshToken::getRevoked, Boolean.TRUE)
                .set(RefreshToken::getRevokedAt, LocalDateTime.now());
        boolean dbUpdated = update(updateWrapper);

        // 3. 删除Redis缓存
        int deletedCount = 0;
        for (RefreshToken token : expiredTokens) {
            if (token.getToken() != null) {
                try {
                    Boolean deleted = redisTemplate.delete(tokenKey(token.getToken()));
                    if (Boolean.TRUE.equals(deleted)) {
                        deletedCount++;
                    }
                } catch (Exception e) {
                    log.warn("删除过期RefreshToken的Redis缓存失败: tokenId={}, error={}",
                            token.getId(), e.getMessage());
                }
            }
        }

        log.info("清理过期RefreshToken: total={}, dbUpdated={}, redisCacheDeleted={}",
                expiredTokens.size(), dbUpdated, deletedCount);
    }
}