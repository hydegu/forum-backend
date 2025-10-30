package com.example.forum.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.forum.dto.UpdateUserProfileRequest;
import com.example.forum.entity.AppUser;
import com.example.forum.vo.UserProfileResponse;
import com.example.forum.vo.AdminUserSummary;
import com.example.forum.vo.PageResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface UserService extends IService<AppUser> {
    AppUser findByUserName(String name);

    int regUser(AppUser appUser);

    Optional<AppUser> findByIdentifier(String identifier);

    void updatePassword(AppUser user, String rawPassword);

    UserProfileResponse updateCurrentUserProfile(Integer userId, UpdateUserProfileRequest request);

    PageResponse<AdminUserSummary> pageAdminUsers(int page, int size, String keyword, String status);

    AdminUserSummary banUser(Integer userId);
}
