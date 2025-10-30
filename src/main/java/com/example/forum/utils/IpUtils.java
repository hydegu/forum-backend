package com.example.forum.utils;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtils {

    /**
     * 获取客户端ip
     *
     * @param request 客户端请求对象
     * @return 客户请求的ip,String类型
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // X-Forwarded-For 可能是 "realClientIp, proxy1, proxy2..."
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}