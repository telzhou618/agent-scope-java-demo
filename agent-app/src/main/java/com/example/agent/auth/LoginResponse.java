package com.example.agent.auth;

import lombok.Data;

/**
 * 登录响应：token + 用户信息
 */
@Data
public class LoginResponse {

    private String token;

    private UserVO user;
}
