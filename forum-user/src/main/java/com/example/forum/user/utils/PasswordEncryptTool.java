package com.example.forum.user.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具
 * 用于生成BCrypt加密后的密码字符串
 * 
 * 使用方法：
 * 1. 在IDE中直接运行此类的main方法
 * 2. 或使用Maven命令: mvn exec:java -Dexec.mainClass="com.example.forum.user.utils.PasswordEncryptTool"
 */
public class PasswordEncryptTool {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 要加密的密码
        String rawPassword = "123321qq";
        
        // 生成加密字符串
        String encodedPassword = encoder.encode(rawPassword);
        
        System.out.println("========================================");
        System.out.println("  密码加密工具");
        System.out.println("========================================");
        System.out.println();
        System.out.println("原始密码: " + rawPassword);
        System.out.println();
        System.out.println("加密后的字符串:");
        System.out.println("----------------------------------------");
        System.out.println(encodedPassword);
        System.out.println("----------------------------------------");
        System.out.println();
        System.out.println("SQL更新语句:");
        System.out.println("----------------------------------------");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE username = 'admin';");
        System.out.println("----------------------------------------");
        System.out.println();
        System.out.println("验证加密是否正确:");
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("验证结果: " + (matches ? "✓ 正确" : "✗ 错误"));
        System.out.println();
        System.out.println("========================================");
    }
}

