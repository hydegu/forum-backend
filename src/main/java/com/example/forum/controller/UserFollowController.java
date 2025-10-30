package com.example.forum.controller;

import com.example.forum.entity.AppUser;
import com.example.forum.service.UserFollowService;
import com.example.forum.service.UserService;
import com.example.forum.utils.SecurityUtils;
import com.example.forum.vo.FollowingView;
import com.example.forum.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class UserFollowController {

    private final UserFollowService userFollowService;
    private final UserService userService;

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

    private AppUser requireCurrentUser() {
        return SecurityUtils.getCurrentUsername()
                .map(userService::findByUserName)
                .filter(user -> user != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
    }
}
