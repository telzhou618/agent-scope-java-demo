package com.example.agent.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.dto.LoginRequest;
import com.example.agent.dto.LoginResponse;
import com.example.agent.entity.User;
import com.example.agent.error.BizException;
import com.example.agent.error.ErrorCode;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 认证相关服务：登录 / 退出 / 当前用户
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 登录失败限流：同一账号连续失败 5 次锁定 10 分钟（计数存 Redis）
     */
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 登录：支持用户名或邮箱 + 密码，返回 token 与用户信息
     */
    public LoginResponse login(LoginRequest request) {
        String failKey = FAIL_KEY_PREFIX + request.getAccount();
        String failCount = redisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= MAX_FAIL_COUNT) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "尝试次数过多，请 10 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getAccount())
                .or()
                .eq(User::getEmail, request.getAccount()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFail(failKey);
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }

        // 登录成功清零失败计数
        redisTemplate.delete(failKey);
        StpUtil.login(user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(StpUtil.getTokenValue());
        response.setUser(UserVO.from(user));
        return response;
    }

    private void recordFail(String failKey) {
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, LOCK_DURATION);
        }
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
