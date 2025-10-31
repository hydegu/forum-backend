package com.example.forum.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.user.dto.UpdateUserProfileRequest;
import com.example.forum.user.entity.AppUser;
import com.example.forum.user.vo.UserProfileResponse;
import com.example.forum.user.vo.AdminUserSummary;
import com.example.forum.common.vo.PageResponse;

import java.util.Optional;

public interface UserService extends IService<AppUser> {
    AppUser findByUserName(String name);

    int regUser(AppUser appUser);

    Optional<AppUser> findByIdentifier(String identifier);

    void updatePassword(AppUser user, String rawPassword);

    UserProfileResponse updateCurrentUserProfile(Integer userId, UpdateUserProfileRequest request);

    PageResponse<AdminUserSummary> pageAdminUsers(int page, int size, String keyword, String status);

    AdminUserSummary banUser(Integer userId);
}
