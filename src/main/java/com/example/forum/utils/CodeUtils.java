package com.example.forum.utils;

import java.util.Random;

public class CodeUtils {

    /**
     * 生成随机验证码
     *
     * @return 6位随机验证码,String类型
     */
    public static String generateCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }
}