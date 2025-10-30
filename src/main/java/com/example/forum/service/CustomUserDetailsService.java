package com.example.forum.service;

import com.example.forum.entity.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser;
        try {
            appUser = userService.findByUserName(username);
            log.info("根据用户名加载用户: {}", appUser);
        } catch (Exception ex) {
            log.error("查询用户 {} 失败，认证中止", username, ex);
            throw new InternalAuthenticationServiceException("Failed to query user information", ex);
        }

        if (appUser == null) {
            log.warn("用户不存在: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        if (!StringUtils.hasText(appUser.getPassword())) {
            log.error("用户 {} 密码为空，无法认证", username);
            throw new InternalAuthenticationServiceException("User password is not configured");
        }

        String role = appUser.getRole();
        if (!StringUtils.hasText(role)) {
            log.error("用户 {} 未配置角色", username);
            throw new InternalAuthenticationServiceException("User role is not configured");
        }

        String grantedRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(grantedRole));
        log.info("用户 {} 加载完成，授予角色 {}", username, grantedRole);
        return new User(appUser.getUserName(), appUser.getPassword(), authorities);
    }
}
