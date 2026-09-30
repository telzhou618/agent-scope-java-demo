package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.LoginRequest;
import com.example.agent.dto.LoginResponse;
import com.example.agent.dto.Result;
import com.example.agent.service.AuthService;
import com.example.agent.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录 / 退出 / 当前用户
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "认证")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "登录（用户名或邮箱 + 密码）")
    public Result<LoginResponse> login(@RequestBody @Validated LoginRequest request) {
        return Result.okData(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @GetMapping("/current")
    @Operation(summary = "当前登录用户")
    public Result<UserVO> current() {
        return Result.okData(authService.currentUser());
    }
}
