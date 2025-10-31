package com.example.forum.user.controller;

import com.example.forum.user.config.JwtProperties;
import com.example.forum.common.enums.Code;
import com.example.forum.user.utils.JwtUtils;
import com.example.forum.user.dto.*;
import com.example.forum.user.entity.AppUser;
import com.example.forum.user.entity.RefreshToken;
import com.example.forum.user.service.RefreshTokenService;
import com.example.forum.user.service.UserService;
import com.example.forum.user.service.VerificationCodeService;
import com.example.forum.user.utils.CodeUtils;
import com.example.forum.user.utils.IpUtils;
import com.example.forum.user.vo.LoginResponse;
import com.example.forum.user.vo.UserProfileResponse;
import com.example.forum.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.security.Principal;
import java.time.Duration;
import java.util.Optional;

@RestController
@Slf4j
public class UserController {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JavaMailSender sender;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest){
        log.info("收到登录请求: {}", loginRequest);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
        }catch(BadCredentialsException e){
            return ResponseEntity.status(401).body(Result.error(Code.UNAUTHORIZED, "用户名或密码错误"));
        }

        AppUser user = userService.findByUserName(loginRequest.getUsername());

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("username", user.getUserName());
        claims.put("userId", user.getId());
        claims.put("roles", user.getRole());

        refreshTokenService.revokeTokensByUser(user.getId());
        String token = JwtUtils.createJwt(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUserName());

        long maxAgeSeconds = Duration.ofMillis(jwtProperties.getUserTtl()).getSeconds();
        long refreshMaxAgeSeconds = Duration.ofMillis(jwtProperties.getRefreshTtl()).getSeconds();

        ResponseCookie accessCookie = ResponseCookie.from(jwtProperties.getUserTokenName(), token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(maxAgeSeconds > 0 ? maxAgeSeconds : -1)
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(jwtProperties.getRefreshTokenName(), refreshToken.getToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(refreshMaxAgeSeconds > 0 ? refreshMaxAgeSeconds : -1)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(
                        token,
                        refreshToken.getToken(),
                        user.getRole(),
                        Math.max(maxAgeSeconds, 0),
                        Math.max(refreshMaxAgeSeconds, 0)
                ));
    }

    @PostMapping("/api/register")
    public ResponseEntity<?> register(@RequestBody RegRequest request){
        boolean codeAccess = getResponseEntity(request.getEmail(), request.getVerificationCode());
        if(codeAccess) {
            log.info("验证成功");
        } else {
            log.info("验证失败, Redis中的验证码: {}, 用户提交的验证码: {}",
                    verificationCodeService.get(request.getEmail()).orElse("无验证码"), request.getVerificationCode());
        }
        verificationCodeService.clear(request.getEmail());
        if(!codeAccess) {
            return ResponseEntity.status(400).body(Result.error(Code.BAD_REQUEST, "Invalid verification code"));
        }
        try{
            AppUser byUserName = userService.findByUserName(request.getUsername());
            if(byUserName != null) {
                return ResponseEntity.status(400).body(Result.error(Code.BAD_REQUEST,"User already exists"));
            } else {
                AppUser user = new AppUser()
                        .setUserName(request.getUsername())
                        .setEmail(request.getEmail())
                        .setPassword(passwordEncoder.encode(request.getPassword()))
                        .setPhone(request.getMobileNumber());
                int lines = userService.regUser(user);
                if(lines > 0) {
                    return ResponseEntity.ok(Result.success(null));
                } else {
                    return ResponseEntity.status(400).body(Result.error(Code.BAD_REQUEST,"Registration failed"));
                }
            }
        } catch (RuntimeException e) {
            log.error("注册失败, 请求体: {}", request);
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/api/send-verification-email")
    public ResponseEntity<?> sendEmailCode(@RequestBody EmailRequest request, HttpServletRequest httpRequest){
        String requestIp = IpUtils.getClientIp(httpRequest);
        try {
            String email = request.getEmail();
            if (!verificationCodeService.acquireSendQuota(email, requestIp)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Result.error(Code.TOO_MANY_REQUESTS,"验证码发送过于频繁"));
            }
            log.info("准备发送验证码到 {}", email);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setSubject("Registration Verification Code");
            verificationCodeService.store(email, CodeUtils.generateCode());
            message.setText("您的验证码是: " + verificationCodeService.get(email).orElse("无验证码"));
            message.setTo(email);
            message.setFrom("2241761576@qq.com");
            sender.send(message);
            return ResponseEntity.ok(Result.success(null));
        }catch (Exception e) {
            log.error("发送验证码失败, 请求体: {}", request);
            throw new RuntimeException("发送验证码失败", e);
        }
    }

    @PostMapping("/api/validate-verification-code")
    public ResponseEntity<?> validateCode(@RequestBody ValidCodeRequest validCodeRequest) {
        log.info("收到验证码验证请求: {}", validCodeRequest);
        String email = validCodeRequest.getEmail();
        String usercode = validCodeRequest.getVerificationCode();
        boolean codeAccess = getResponseEntity(email, usercode);
        if(codeAccess) {
            log.info("验证码验证成功");
            return ResponseEntity.ok(Result.success(null));
        } else {
            log.info("验证码验证失败, 预期验证码: {}, 提供的验证码: {}",
                    verificationCodeService.get(email).orElse("无验证码"), usercode);
            return ResponseEntity.status(400).body(Result.error(Code.BAD_REQUEST, "错误的验证码"));
        }
    }

    @PutMapping("/api/users/me")
    public ResponseEntity<Result<UserProfileResponse>> updateCurrentUserProfile(
            Principal principal,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        if (principal == null) {
            log.warn("未登录用户尝试更新个人资料");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(Code.UNAUTHORIZED, "未登录"));
        }
        AppUser currentUser = userService.findByUserName(principal.getName());
        if (currentUser == null) {
            log.warn("根据 principal={} 未找到用户", principal.getName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(Code.UNAUTHORIZED, "未登录"));
        }

        log.info("收到资料更新请求, userId={},尝试更新用户，请求体为:{}", currentUser.getId(), request);
        UserProfileResponse response = userService.updateCurrentUserProfile(currentUser.getId(), request);
        return ResponseEntity.ok(Result.success(response));
    }

    private boolean getResponseEntity(String email, String usercode) {
        try {
            Optional<String> codeOptional = verificationCodeService.get(email);
            return codeOptional.filter(code -> code.equals(usercode)).isPresent();
        }catch (Exception e){
            log.error("验证邮箱验证码时发生错误");
            throw new RuntimeException();
        }
    }
}
