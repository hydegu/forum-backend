package com.example.forum.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.forum.user.entity.AppUser;
import com.example.forum.user.entity.UserFollow;
import com.example.forum.user.repo.UserFollowRepo;
import com.example.forum.user.repo.UserRepo;
import com.example.forum.user.vo.FollowingView;
import com.example.forum.common.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserFollowServiceImpl extends ServiceImpl<UserFollowRepo, UserFollow> implements UserFollowService {

    private final UserFollowRepo userFollowRepo;
    private final UserRepo userRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "posts:list", allEntries = true)
    public boolean follow(Integer followerId, Integer followeeId) {
        if (followerId == null || followeeId == null) {
            throw new IllegalArgumentException("关注双方信息不完整");
        }
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("不可关注自己");
        }
        ensureUsersExist(followerId, followeeId);

        String followsKey = "user:follows:" + followerId;
        String followersKey = "user:followers:" + followeeId;

        // 先检查MySQL是否已存在关注关系
        UserFollow existing = userFollowRepo.findRelation(followerId, followeeId);
        if (existing != null) {
            // MySQL中已存在，同步到Redis
            redisTemplate.opsForSet().add(followsKey, followeeId.toString());
            redisTemplate.opsForSet().add(followersKey, followerId.toString());
            log.debug("关注关系已存在于MySQL，已同步到Redis: followerId={}, followeeId={}", followerId, followeeId);
            return true;
        }

        // MySQL中不存在，尝试添加到Redis
        Long added = redisTemplate.opsForSet().add(followsKey, followeeId.toString());

        if (Boolean.TRUE.equals(added != null && added > 0)) {
            try {
                redisTemplate.opsForSet().add(followersKey, followerId.toString());

                UserFollow relation = new UserFollow()
                        .setFollowerId(followerId)
                        .setFolloweeId(followeeId)
                        .setCreatedAt(LocalDateTime.now());
                boolean dbSuccess = userFollowRepo.insert(relation) > 0;

                if (!dbSuccess) {
                    throw new RuntimeException("数据库插入失败");
                }

                log.debug("关注成功: followerId={}, followeeId={}", followerId, followeeId);
                return true;
            } catch (Exception e) {
                redisTemplate.opsForSet().remove(followsKey, followeeId.toString());
                redisTemplate.opsForSet().remove(followersKey, followerId.toString());
                log.error("数据库写入关注失败，已回滚Redis: followerId={}, followeeId={}", followerId, followeeId, e);
                throw e;
            }
        } else {
            // Redis中已存在但MySQL不存在（理论上不应该发生），重新检查MySQL并同步
            existing = userFollowRepo.findRelation(followerId, followeeId);
            if (existing == null) {
                log.warn("Redis中存在但MySQL中不存在，重新插入MySQL: followerId={}, followeeId={}", followerId, followeeId);
                UserFollow relation = new UserFollow()
                        .setFollowerId(followerId)
                        .setFolloweeId(followeeId)
                        .setCreatedAt(LocalDateTime.now());
                userFollowRepo.insert(relation);
            }
            log.debug("关注关系已存在: followerId={}, followeeId={}", followerId, followeeId);
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "posts:list", allEntries = true)
    public boolean unfollow(Integer followerId, Integer followeeId) {
        if (followerId == null || followeeId == null) {
            return false;
        }

        String followsKey = "user:follows:" + followerId;
        String followersKey = "user:followers:" + followeeId;

        Long removed = redisTemplate.opsForSet().remove(followsKey, followeeId.toString());

        if (Boolean.TRUE.equals(removed != null && removed > 0)) {
            try {
                redisTemplate.opsForSet().remove(followersKey, followerId.toString());

                UserFollow existing = userFollowRepo.findRelation(followerId, followeeId);
                if (existing != null) {
                    boolean dbSuccess = userFollowRepo.deleteById(existing.getId()) > 0;
                    if (!dbSuccess) {
                        throw new RuntimeException("数据库删除失败");
                    }
                    log.debug("取消关注成功: followerId={}, followeeId={}", followerId, followeeId);
                }

                return true;
            } catch (Exception e) {
                redisTemplate.opsForSet().add(followsKey, followeeId.toString());
                redisTemplate.opsForSet().add(followersKey, followerId.toString());
                log.error("数据库删除关注失败，已回滚Redis: followerId={}, followeeId={}", followerId, followeeId, e);
                throw e;
            }
        } else {
            log.debug("用户未关注: followerId={}, followeeId={}", followerId, followeeId);
            return false;
        }
    }

    @Override
    public boolean isFollowing(Integer followerId, Integer followeeId) {
        if (followerId == null || followeeId == null) {
            return false;
        }

        String followsKey = "user:follows:" + followerId;
        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(followsKey, followeeId.toString());
            if (Boolean.TRUE.equals(isMember)) {
                return true;
            }

            UserFollow dbRelation = userFollowRepo.findRelation(followerId, followeeId);
            if (dbRelation != null) {
                redisTemplate.opsForSet().add(followsKey, followeeId.toString());
                redisTemplate.opsForSet().add("user:followers:" + followeeId, followerId.toString());
                log.debug("同步关注关系到Redis: followerId={}, followeeId={}", followerId, followeeId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("Redis查询失败，回退到数据库: followerId={}, followeeId={}", followerId, followeeId, e);
            return userFollowRepo.findRelation(followerId, followeeId) != null;
        }
    }

    @Override
    public PageResponse<FollowingView> pageFollowings(Integer followerId, int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 50), 1);
        Page<FollowingView> pageReq = Page.of(safePage, safeSize);
        Page<FollowingView> pageResult = userFollowRepo.pageFollowings(pageReq, followerId, keyword);
        return PageResponse.of(
                pageResult.getRecords(),
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getPages(),
                Map.of("total", pageResult.getTotal())
        );
    }

    private void ensureUsersExist(Integer followerId, Integer followeeId) {
        AppUser follower = userRepo.selectById(followerId);
        AppUser followee = userRepo.selectById(followeeId);
        if (follower == null || followee == null) {
            throw new NoSuchElementException("关注用户不存在");
        }
    }
}
