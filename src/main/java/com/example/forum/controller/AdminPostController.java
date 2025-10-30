package com.example.forum.controller;

import com.example.forum.entity.AppUser;
import com.example.forum.service.PostService;
import com.example.forum.service.UserService;
import com.example.forum.utils.SecurityUtils;
import com.example.forum.vo.AdminPostDetailView;
import com.example.forum.vo.AdminPostSummary;
import com.example.forum.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminPostController {

    private final PostService postService;
    private final UserService userService;

    @GetMapping("/posts")
    public PageResponse<AdminPostSummary> pagePosts(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "50") Integer size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String categoryId) {
        AppUser currentUser = resolveCurrentUser();
        ensureAdmin(currentUser);
        if (!StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        Integer parsedCategoryId = parseCategoryId(categoryId);
        PageResponse<AdminPostSummary> result = postService.pageAdminPosts(
                status.trim(),
                page,
                size,
                keyword,
                parsedCategoryId
        );
        log.info("Admin {} queried posts status={}, page={}, size={}, keyword={}, categoryId={}",
                currentUser.getId(), status, page, size, keyword, categoryId);
        return result;
    }

    @PostMapping("/posts/{postId}/approve")
    public ResponseEntity<AdminPostSummary> approvePost(@PathVariable Integer postId) {
        AppUser currentUser = resolveCurrentUser();
        ensureAdmin(currentUser);
        AdminPostSummary summary = postService.approvePost(postId);
        log.info("Admin {} approved post {}", currentUser.getId(), postId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/posts/{postId}/reject")
    public ResponseEntity<AdminPostSummary> rejectPost(@PathVariable Integer postId) {
        AppUser currentUser = resolveCurrentUser();
        ensureAdmin(currentUser);
        AdminPostSummary summary = postService.rejectPost(postId);
        log.info("Admin {} rejected post {}", currentUser.getId(), postId);
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Integer postId) {
        AppUser currentUser = resolveCurrentUser();
        ensureAdmin(currentUser);
        postService.deletePostAsAdmin(postId);
        log.info("Admin {} deleted post {}", currentUser.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/{postId}")
    public AdminPostDetailView getPost(@PathVariable Integer postId,
                                       @RequestParam(name = "includeComments", required = false) Boolean includeComments) {
        AppUser currentUser = resolveCurrentUser();
        ensureAdmin(currentUser);
        boolean withComments = Boolean.TRUE.equals(includeComments);
        AdminPostDetailView detail = postService.getAdminPost(postId, withComments);
        log.info("Admin {} fetched post {} includeComments={}", currentUser.getId(), postId, withComments);
        return detail;
    }

    private Integer parseCategoryId(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return null;
        }
        try {
            return Integer.valueOf(categoryId.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid categoryId");
        }
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
