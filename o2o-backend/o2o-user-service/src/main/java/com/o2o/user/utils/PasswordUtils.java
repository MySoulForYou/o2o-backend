package com.o2o.user.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtils {

    // 引入 Spring Security 官方的 BCrypt 密码加密器
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 对密码进行 BCrypt 加密
     *
     * @param password 明文密码
     * @return 加密后的密文（密文中已自动融合了随机生成的盐值）
     */
    public static String encrypt(String password) {
        if (password == null) {
            return null;
        }
        return ENCODER.encode(password);
    }

    /**
     * 校验密码是否正确
     *
     * @param password          用户输入的明文密码
     * @param encryptedPassword 数据库中存的密文密码（含盐）
     * @return 是否匹配
     */
    public static boolean verify(String password, String encryptedPassword) {
        if (password == null || encryptedPassword == null) {
            return false;
        }
        // 调用 matches 方法，BCrypt 会自动从密文中提取出盐来对明文进行二次哈希对比
        return ENCODER.matches(password, encryptedPassword);
    }
}
