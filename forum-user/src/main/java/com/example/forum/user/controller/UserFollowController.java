package com.example.forum.user.controller;

import com.example.forum.common.vo.PageResponse;
import com.example.forum.user.entity.AppUser;
import com.example.forum.user.entity.UserFollow;
import com.example.forum.user.repo.UserFollowRepo;
import com.example.forum.user.service.UserFollowService;
import com.example.forum.user.service.UserService;
import com.example.forum.user.utils.SecurityUtils;
import com.example.forum.user.vo.FollowingView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class UserFollowController {

    private final UserFollowService userFollowService;
    private final UserService userService;
    private final UserFollowRepo userFollowRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/users/{userId}/follow")
    public ResponseEntity<Map<String, Object>> followUser(@PathVariable Integer userId) {
        AppUser currentUser = requireCurrentUser();
        userFollowService.follow(currentUser.getId(), userId);
        return ResponseEntity.ok(Map.of("following", true));
    }

    @DeleteMapping("/users/{userId}/follow")
    public ResponseEntity<Map<String, Object>> unfollowUser(@PathVariable Integer userId) {
        AppUser currentUser = requireCurrentUser();
        userFollowService.unfollow(currentUser.getId(), userId);
        return ResponseEntity.ok(Map.of("following", false));
    }

    @GetMapping("/users/me/followings")
    public PageResponse<FollowingView> pageFollowings(@RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer size,
                                                      @RequestParam(required = false, name = "q") String keyword) {
        AppUser currentUser = requireCurrentUser();
        log.info("用户 {} 分页查询关注用户，page={}, size={}, keyword={}", currentUser.getUserName(), page, size, keyword);
        return userFollowService.pageFollowings(currentUser.getId(), page, size, keyword);
    }

    /**
     * 数据同步修复接口 - 从MySQL重建Redis缓存
     * 用于修复Redis和MySQL数据不一致的问题
     */
    @PostMapping("/admin/sync/follows")
    public ResponseEntity<Map<String, Object>> syncFollowData() {
        log.info("开始执行关注数据同步修复...");
        
        try {
            // 1. 清空所有Redis关注缓存
            Set<String> followsKeys = redisTemplate.keys("user:follows:*");
            Set<String> followersKeys = redisTemplate.keys("user:followers:*");
            
            int deletedFollows = 0;
            int deletedFollowers = 0;
            
            if (followsKeys != null && !followsKeys.isEmpty()) {
                deletedFollows = followsKeys.size();
                redisTemplate.delete(followsKeys);
                log.info("已清除 {} 个 user:follows:* keys", deletedFollows);
            }
            
            if (followersKeys != null && !followersKeys.isEmpty()) {
                deletedFollowers = followersKeys.size();
                redisTemplate.delete(followersKeys);
                log.info("已清除 {} 个 user:followers:* keys", deletedFollowers);
            }
            
            // 2. 从MySQL读取所有关注关系
            List<UserFollow> allFollows = userFollowRepo.selectList(null);
            log.info("从MySQL读取到 {} 条关注关系", allFollows.size());
            
            // 3. 重建Redis缓存
            int syncedCount = 0;
            for (UserFollow follow : allFollows) {
                if (follow.getFollowerId() != null && follow.getFolloweeId() != null) {
                    String followsKey = "user:follows:" + follow.getFollowerId();
                    String followersKey = "user:followers:" + follow.getFolloweeId();
                    
                    redisTemplate.opsForSet().add(followsKey, follow.getFolloweeId().toString());
                    redisTemplate.opsForSet().add(followersKey, follow.getFollowerId().toString());
                    syncedCount++;
                }
            }
            
            log.info("数据同步完成，已同步 {} 条关注关系到Redis", syncedCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "数据同步成功");
            result.put("deletedFollowsKeys", deletedFollows);
            result.put("deletedFollowersKeys", deletedFollowers);
            result.put("totalFollows", allFollows.size());
            result.put("syncedCount", syncedCount);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("数据同步失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "数据同步失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 查看当前数据同步状态
     */
    @GetMapping("/admin/sync/follows/status")
    public ResponseEntity<Map<String, Object>> getFollowDataStatus() {
        try {
            // MySQL数据统计
            List<UserFollow> allFollows = userFollowRepo.selectList(null);
            int mysqlCount = allFollows.size();
            
            // Redis缓存统计
            Set<String> followsKeys = redisTemplate.keys("user:follows:*");
            Set<String> followersKeys = redisTemplate.keys("user:followers:*");
            
            int redisFollowsKeys = followsKeys != null ? followsKeys.size() : 0;
            int redisFollowersKeys = followersKeys != null ? followersKeys.size() : 0;
            
            // 统计Redis中的总关注数
            int redisTotalFollows = 0;
            if (followsKeys != null) {
                for (String key : followsKeys) {
                    Long size = redisTemplate.opsForSet().size(key);
                    redisTotalFollows += (size != null ? size.intValue() : 0);
                }
            }
            
            // 判断数据一致性
            boolean isConsistent = mysqlCount == redisTotalFollows;
            
            Map<String, Object> status = new HashMap<>();
            status.put("mysqlTotalFollows", mysqlCount);
            status.put("redisFollowsKeys", redisFollowsKeys);
            status.put("redisFollowersKeys", redisFollowersKeys);
            status.put("redisTotalFollows", redisTotalFollows);
            status.put("consistency", isConsistent ? "一致" : "不一致");
            status.put("suggestion", mysqlCount > 0 && redisFollowsKeys == 0 
                ? "检测到Redis缓存为空，建议执行数据同步" 
                : isConsistent ? "数据状态正常" : "数据不一致，建议执行同步");
            
            log.info("关注数据状态: MySQL={}, Redis缓存键={}, Redis总数={}, 一致性={}", 
                    mysqlCount, redisFollowsKeys, redisTotalFollows, isConsistent);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("获取数据状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    private AppUser requireCurrentUser() {
        return SecurityUtils.getCurrentUsername()
                .map(userService::findByUserName)
                .filter(user -> user != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
    }
}
