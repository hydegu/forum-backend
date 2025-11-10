package com.example.forum.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT工具类（网关专用）
 * 用于验证JWT token的有效性
 */
@Slf4j
public class JwtUtils {

    /**
     * 解析JWT token
     *
     * @param secretKey JWT密钥
     * @param token     JWT token
     * @return Claims 解析后的声明
     * @throws JwtException 如果token无效、过期或被篡改
     */
    public static Claims parseJWT(String secretKey, String token) throws JwtException {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            
            JwtParser jwtParser = Jwts.parser()
                    .verifyWith(key)
                    .build();
            
            Jws<Claims> jws = jwtParser.parseSignedClaims(token);
            return jws.getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token已过期: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.debug("JWT token格式错误: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.debug("JWT token签名验证失败: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.debug("JWT token验证失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 验证JWT token是否有效
     *
     * @param secretKey JWT密钥
     * @param token     JWT token
     * @return true=有效, false=无效
     */
    public static boolean validateToken(String secretKey, String token) {
        try {
            parseJWT(secretKey, token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
