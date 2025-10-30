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
            log.info("Loaded user by username: {}", appUser);
        } catch (Exception ex) {
            log.error("Failed to query user {}, authentication aborted", username, ex);
            throw new InternalAuthenticationServiceException("Failed to query user information", ex);
        }

        if (appUser == null) {
            log.warn("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        if (!StringUtils.hasText(appUser.getPassword())) {
            log.error("User {} has empty password and cannot be authenticated", username);
            throw new InternalAuthenticationServiceException("User password is not configured");
        }

        String role = appUser.getRole();
        if (!StringUtils.hasText(role)) {
            log.error("User {} has no role configured", username);
            throw new InternalAuthenticationServiceException("User role is not configured");
        }

        String grantedRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(grantedRole));
        log.info("User {} loaded with granted role {}", username, grantedRole);
        return new User(appUser.getUserName(), appUser.getPassword(), authorities);
    }
}
