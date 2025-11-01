package com.example.forum.controller;

import com.example.forum.entity.AppUser;
import com.example.forum.service.UserService;
import com.example.forum.utils.SecurityUtils;
import com.example.forum.vo.AdminUserSummary;
import com.example.forum.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/users")
    public PageResponse<AdminUserSummary> pageUsers(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "20") Integer size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status) {
        AppUser current = resolveCurrentUser();
        ensureAdmin(current);
        PageResponse<AdminUserSummary> result = userService.pageAdminUsers(page, size, keyword, status);
        log.info("管理员 {} 查询用户列表 page={}, size={}, keyword={}, status={}", current.getId(), page, size, keyword, status);
        return result;
    }

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<AdminUserSummary> banUser(@PathVariable Integer userId) {
        AppUser current = resolveCurrentUser();
        ensureAdmin(current);
        AdminUserSummary summary = userService.banUser(userId);
        log.info("管理员 {} 封禁用户 {}", current.getUserName(), userId);
        return ResponseEntity.ok(summary);
    }

    private AppUser resolveCurrentUser() {
        return SecurityUtils.getCurrentUsername()
                .map(userService::findByUserName)
                .filter(user -> user != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private void ensureAdmin(AppUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String role = user.getRole();
        boolean isAdmin = role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
