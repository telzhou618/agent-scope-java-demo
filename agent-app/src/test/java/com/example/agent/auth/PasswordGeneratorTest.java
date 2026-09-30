package com.example.agent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 生成 BCrypt 密码哈希，用于 SQL 种子数据（如 admin123）。
 * 这是一个密码生成工具，不是单元测试。
 */
class PasswordGeneratorTest {

    @Test
    void generateBcryptHash() {
        String raw = "admin123";
        String hash = new BCryptPasswordEncoder().encode(raw);
        System.out.println("=============================================");
        System.out.println("原始密码: " + raw);
        System.out.println("BCrypt 哈希: " + hash);
        System.out.println("=============================================");
    }
}
