package com.example.agent.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.error.BizException;
import com.example.agent.error.ErrorCode;
import com.example.agent.user.User;
import com.example.agent.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证相关服务：登录 / 退出 / 当前用户
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录：支持用户名或邮箱 + 密码，返回 token 与用户信息
     */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getAccount())
                .or()
                .eq(User::getEmail, request.getAccount()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }

        StpUtil.login(user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(StpUtil.getTokenValue());
        response.setUser(UserVO.from(user));
        return response;
    }

    /**
     * 退出登录；未登录时也视为成功（幂等）
     */
    public void logout() {
        StpUtil.logout();
    }

    /**
     * 当前登录用户
     */
    public UserVO currentUser() {
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        return UserVO.from(user);
    }
}
