package com.example.forum.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.forum.user.dto.UpdateUserProfileRequest;
import com.example.forum.user.entity.AppUser;
import com.example.forum.common.exception.ApiException;
import com.example.forum.common.exception.ConflictException;
import com.example.forum.user.repo.UserRepo;
import com.example.forum.user.vo.AdminUserSummary;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.user.vo.UserProfileResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserRepo, AppUser> implements UserService {

    @Resource
    private UserRepo userRepo;

    @Resource
    @Lazy
    private PasswordEncoder passwordEncoder;

    @Resource
    private CacheManager cacheManager;

    @Override
    @Cacheable(cacheNames = "users:profile", key = "#name", unless = "#result == null")
    public AppUser findByUserName(String name) {
        return userRepo.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUserName, name));
    }

    @Override
    public int regUser(AppUser appUser) {
        return userRepo.insert(appUser);
    }

    @Override
    public Optional<AppUser> findByIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            log.warn("未找到用户");
            return Optional.empty();
        }
        String value = identifier.trim();
        LambdaQueryWrapper<AppUser> query = new LambdaQueryWrapper<>();
        query.eq(AppUser::getUserName, value)
                .or()
                .eq(AppUser::getEmail, value);
        AppUser user = userRepo.selectOne(query);
        return Optional.ofNullable(user);
    }

    @Override
    @CacheEvict(cacheNames = "users:profile", key = "#user.userName()")
    public void updatePassword(AppUser user, String rawPassword) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户信息不完整");
        }
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);
        LocalDateTime now = LocalDateTime.now();
        user.setUpdatedAt(now);
        int affected = userRepo.updateById(user);
        if (affected <= 0) {
            log.error("加载密码失败, userId={}", user.getId());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "加载密码失败");
        }
        log.info("密码更新成功, userId={}", user.getId());
    }

    @Override
    public UserProfileResponse updateCurrentUserProfile(Integer userId, UpdateUserProfileRequest request) {
        AppUser user = userRepo.selectById(userId);
        if (user == null) {
            log.warn("未找到用户, userId={}", userId);
            throw new ApiException(HttpStatus.NOT_FOUND, "未找到用户");
        }
        String oldUserName = user.getUserName();  // 保存旧用户名
        String normalizedUsername = normalizeRequired(request.getUsername(), "需要用户名");
        if (!Objects.equals(normalizedUsername, user.getUserName())) {
            ensureUnique(AppUser::getUserName, normalizedUsername, userId, "用户名已存在");
        }
        user.setUserName(normalizedUsername);

        if (request.getEmail() != null) {
            String normalizedEmail = normalizeOptional(request.getEmail());
            if (normalizedEmail != null && !Objects.equals(normalizedEmail, user.getEmail())) {
                ensureUnique(AppUser::getEmail, normalizedEmail, userId, "邮箱已被使用");
            }
            user.setEmail(normalizedEmail);
        }

        if (request.getPhone() != null) {
            String normalizedPhone = normalizeOptional(request.getPhone());
            if (normalizedPhone != null && !Objects.equals(normalizedPhone, user.getPhone())) {
                ensureUnique(AppUser::getPhone, normalizedPhone, userId, "手机号已被使用");
            }
            user.setPhone(normalizedPhone);
        }

        if (request.getBio() != null) {
            user.setBio(normalizeOptional(request.getBio()));
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(normalizeOptional(request.getAvatarUrl()));
        }

        LocalDateTime now = LocalDateTime.now();
        user.setUpdatedAt(now);
        int affected = userRepo.updateById(user);
        if (affected <= 0) {
            log.error("更改用户信息失败, userId={}", userId);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "更改用户信息失败");
        }
        // 清除缓存
        evictUserCache(oldUserName);
        if (!Objects.equals(oldUserName, normalizedUsername)) {
            evictUserCache(normalizedUsername);  // 用户名变了，也清除新名字
        }

        log.info("成功变更用户信息:, userId={}", userId);
        return buildUserProfileResponse(user);
    }

    @Override
    public PageResponse<AdminUserSummary> pageAdminUsers(int page, int size, String keyword, String status) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(AppUser::getUserName, trimmed)
                    .or()
                    .like(AppUser::getEmail, trimmed));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AppUser::getStatus, status.trim());
        }
        wrapper.orderByDesc(AppUser::getCreatedAt);

        Page<AppUser> pageResult = this.page(Page.of(current, pageSize), wrapper);
        List<AdminUserSummary> records = pageResult.getRecords()
                .stream()
                .map(this::toAdminUserSummary)
                .toList();
        return PageResponse.of(
                records,
                pageResult.getTotal(),
                current,
                pageSize,
                pageResult.getPages(),
                Collections.emptyMap()
        );
    }

    @Override
    public AdminUserSummary banUser(Integer userId) {
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "需要用户名");
        }
        AppUser user = userRepo.selectById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "未找到该用户");
        }
        String userName = user.getUserName();  // 保存用户名
        user.setStatus("Banned");
        int affected = userRepo.updateById(user);
        if (affected <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "更新用户失败");
        }
        // 清除缓存
        evictUserCache(userName);
        AppUser refreshed = userRepo.selectById(userId);
        log.info("用户被管理员封禁 {}", userId);
        return toAdminUserSummary(refreshed != null ? refreshed : user);
    }

    private <T> void ensureUnique(SFunction<AppUser, T> column, T value, Integer currentUserId, String message) {
        if (value == null) {
            return;
        }
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .ne(AppUser::getId, currentUserId)
                .eq(column, value);
        Long count = userRepo.selectCount(wrapper);
        if (count != null && count > 0) {
            log.warn("Field conflict, userId={}, columnValue={}, message={}", currentUserId, value, message);
            throw new ConflictException(message);
        }
    }

    private String normalizeRequired(String value, String emptyMessage) {
        String normalized = normalizeOptional(value);
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, emptyMessage);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserProfileResponse buildUserProfileResponse(AppUser user) {
        return UserProfileResponse.builder()
                .id(String.valueOf(user.getId()))
                .userName(user.getUserName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .email(user.getEmail())
                .bio(user.getBio())
                .phone(user.getPhone())
                .updatedAt(user.getUpdatedAt() != null
                        ? user.getUpdatedAt().atOffset(ZoneOffset.UTC).toString()
                        : null)
                .build();
    }

    private AdminUserSummary toAdminUserSummary(AppUser user) {
        String registeredAt = Optional.ofNullable(user.getCreatedAt())
                .map(time -> time.atOffset(ZoneOffset.UTC).toString())
                .orElse(null);
        return new AdminUserSummary(
                user.getId() != null ? String.valueOf(user.getId()) : null,
                user.getUserName(),
                user.getEmail(),
                user.getStatus(),
                registeredAt,
                user.getBio(),
                user.getAvatarUrl()
        );
    }

    // 辅助方法：清除用户缓存
    private void evictUserCache(String userName) {
        if (userName == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache("users:profile");
            if (cache != null) {
                cache.evict(userName);
                log.debug("已清除用户缓存：userName={}", userName);
            }
        } catch (Exception e) {
            log.warn("清除用户缓存失败：userName={}, error={}", userName, e.getMessage());
        }
    }

}
