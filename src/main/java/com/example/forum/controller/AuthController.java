package com.example.forum.controller;

import com.example.forum.enums.Code;
import com.example.forum.config.JwtProperties;
import com.example.forum.utils.JwtUtils;
import com.example.forum.dto.PasswordResetConfirmRequest;
import com.example.forum.dto.PasswordResetRequest;
import com.example.forum.dto.Result;
import com.example.forum.dto.TokenRefreshRequest;
import com.example.forum.entity.AppUser;
import com.example.forum.entity.RefreshToken;
import com.example.forum.service.RefreshTokenService;
import com.example.forum.service.UserService;
import com.example.forum.service.VerificationCodeService;
import com.example.forum.utils.CodeUtils;
import com.example.forum.utils.IpUtils;
import com.example.forum.vo.LoginResponse;
import com.example.forum.vo.UserProfileResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JavaMailSender sender;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @GetMapping("/auth/me")
    public ResponseEntity<UserProfileResponse> currentUser(Principal principal) {
        log.info("开始获取用户信息 principal={}", principal);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = userService.findByUserName(principal.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserProfileResponse response = UserProfileResponse.builder()
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
        log.info("获取用户信息成功 user={}", response);
        return ResponseEntity.ok(response);
    }

    //刷新令牌
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(Code.BAD_REQUEST, "缺少刷新令牌"));
        }

        String refreshTokenValue = request.getRefreshToken();
        try {
            Claims claims = JwtUtils.parseJWT(jwtProperties.getRefreshSecretKey(), refreshTokenValue);
            if (!"refresh".equals(claims.get("type", String.class))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Result.error(Code.UNAUTHORIZED, "令牌类型不匹配"));
            }
            Number userIdNumber = claims.get("userId", Number.class);
            String username = claims.get("username", String.class);
            if (userIdNumber == null || !StringUtils.hasText(username)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Result.error(Code.UNAUTHORIZED, "刷新令牌载荷不完整"));
            }
            Integer userId = userIdNumber.intValue();

            Optional<RefreshToken> storedTokenOpt = refreshTokenService.findValidToken(refreshTokenValue);
            if (storedTokenOpt.isEmpty() || !storedTokenOpt.get().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Result.error(Code.UNAUTHORIZED, "刷新令牌无效或已过期"));
            }

            AppUser user = userService.findByUserName(username);
            if (user == null || !userId.equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Result.error(Code.UNAUTHORIZED, "用户信息不存在或已变更"));
            }

            refreshTokenService.revokeToken(refreshTokenValue);

            Map<String, Object> accessClaims = new HashMap<>();
            accessClaims.put("username", user.getUserName());
            accessClaims.put("userId", user.getId());
            accessClaims.put("roles", user.getRole());

            String newAccessToken = JwtUtils.createJwt(
                    jwtProperties.getUserSecretKey(),
                    jwtProperties.getUserTtl(),
                    accessClaims
            );
            RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUserName());

            long accessMaxAge = Duration.ofMillis(jwtProperties.getUserTtl()).getSeconds();
            long refreshMaxAge = Duration.ofMillis(jwtProperties.getRefreshTtl()).getSeconds();

            ResponseCookie accessCookie = ResponseCookie.from(jwtProperties.getUserTokenName(), newAccessToken)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(accessMaxAge > 0 ? accessMaxAge : -1)
                    .sameSite("Lax")
                    .build();

            ResponseCookie refreshCookie = ResponseCookie.from(jwtProperties.getRefreshTokenName(), newRefreshToken.getToken())
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(refreshMaxAge > 0 ? refreshMaxAge : -1)
                    .sameSite("Lax")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(new LoginResponse(
                            newAccessToken,
                            newRefreshToken.getToken(),
                            user.getRole(),
                            Math.max(accessMaxAge, 0),
                            Math.max(refreshMaxAge, 0)
                    ));
        } catch (JwtException ex) {
            log.warn("刷新令牌解析失败: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(Code.UNAUTHORIZED, "刷新令牌无效或已过期"));
        }
    }

    @PostMapping("/auth/password-reset/request")
    public ResponseEntity<Result<Map<String, String>>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest requestBody, HttpServletRequest request) {
        String requestIp = IpUtils.getClientIp(request);
        String identifier = StringUtils.trimAllWhitespace(requestBody.getIdentifier());
        Optional<AppUser> userOpt = userService.findByIdentifier(identifier);
        if (userOpt.isEmpty()) {
            log.warn("密码重置请求未找到用户 identifier={}", identifier);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(Code.NOT_FOUND, "未找到匹配账号"));
        }
        AppUser user = userOpt.get();
        try {
            String email = user.getEmail();
            if (!verificationCodeService.acquireSendQuota(email, requestIp)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Result.error(Code.TOO_MANY_REQUESTS,"验证码发送过于频繁"));
            }
            log.info("准备发送验证码到 {}", email);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setSubject("密码重置验证码:");
            verificationCodeService.store(email, CodeUtils.generateCode());
            message.setText("您的验证码是: " + verificationCodeService.get(email).orElse("无验证码"));
            message.setTo(email);
            message.setFrom("2241761576@qq.com");
            sender.send(message);
            return ResponseEntity.ok(Result.success(Map.of("username", user.getUserName(), "email", email)));
        } catch (Exception e) {
            log.error("发送验证码失败, 请求体: {}", requestBody);
            throw new RuntimeException("发送验证码失败", e);
        }
    }


    @PostMapping("/auth/password-reset/confirm")
    public ResponseEntity<Result<Map<String, String>>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest requestBody) {
        AppUser user = userService.findByUserName(requestBody.getUsername());
        if (user == null || !user.getEmail().equals(requestBody.getEmail())) {
            log.warn("密码重置失败，用户不存在 username={}", requestBody.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(Code.NOT_FOUND, "未找到匹配账号"));
        }
        userService.updatePassword(user, requestBody.getNewPassword());
        log.info("密码重置成功 userName={}", requestBody.getUsername());
        return ResponseEntity.ok(Result.success(Map.of("message", "密码更改成功")));
    }

    @PostMapping({"/auth/logout", "/logout"})
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    @RequestBody(required = false) TokenRefreshRequest body) {
        String refreshToken = body != null ? body.getRefreshToken() : null;
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revokeToken(refreshToken);
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7);
            try {
                Claims claims = JwtUtils.parseJWT(jwtProperties.getUserSecretKey(), token);
                Number userIdNumber = claims.get("userId", Number.class);
                if (userIdNumber != null) {
                    refreshTokenService.revokeTokensByUser(userIdNumber.intValue());
                }
            } catch (JwtException ex) {
                log.debug("访问令牌解析失败，忽略注销刷新: {}", ex.getMessage());
            }
        }

        ResponseCookie expiredAccess = ResponseCookie.from(jwtProperties.getUserTokenName(), "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        ResponseCookie expiredRefresh = ResponseCookie.from(jwtProperties.getRefreshTokenName(), "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAccess.toString())
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString())
                .body(Result.success(null));
    }
}
