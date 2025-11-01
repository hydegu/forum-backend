package com.example.forum.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码生成测试
 * 
 * 运行方法：
 * mvn test -Dtest=PasswordGeneratorTest
 */
@SpringBootTest
public class PasswordGeneratorTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void generatePassword() {
        String rawPassword = "123321qq";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        
        System.out.println("\n========================================");
        System.out.println("  密码加密结果");
        System.out.println("========================================");
        System.out.println();
        System.out.println("原始密码: " + rawPassword);
        System.out.println();
        System.out.println("加密后的字符串:");
        System.out.println("----------------------------------------");
        System.out.println(encodedPassword);
        System.out.println("----------------------------------------");
        System.out.println();
        System.out.println("SQL更新语句（复制使用）:");
        System.out.println("----------------------------------------");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE username = 'admin';");
        System.out.println("----------------------------------------");
        System.out.println();
        System.out.println("验证结果: " + (passwordEncoder.matches(rawPassword, encodedPassword) ? "✓ 正确" : "✗ 错误"));
        System.out.println();
        System.out.println("========================================\n");
    }
}

