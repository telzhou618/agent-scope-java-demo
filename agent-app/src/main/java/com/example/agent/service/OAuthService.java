package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.entity.User;
import com.example.agent.entity.UserOauth;
import com.example.agent.mapper.UserMapper;
import com.example.agent.mapper.UserOauthMapper;
import com.example.agent.oauth.OAuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OAuth 登录：按渠道 openId 查绑定，首次登录自动注册（默认只给 flash 权限）
 */
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserMapper userMapper;
    private final UserOauthMapper userOauthMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 查询或注册，返回本地用户 id
     */
    public long loginOrRegister(OAuthUser oauthUser) {
        UserOauth binding = userOauthMapper.selectOne(new LambdaQueryWrapper<UserOauth>()
                .eq(UserOauth::getChannel, oauthUser.getChannel())
                .eq(UserOauth::getOpenId, oauthUser.getOpenId()));
        if (binding != null) {
            return binding.getUserId();
        }
        return register(oauthUser);
    }

    private long register(OAuthUser oauthUser) {
        String username = uniqueUsername(oauthUser.getLogin());
        User user = new User();
        user.setUsername(username);
        // 占位随机密码：该用户仅能通过 OAuth 登录，无法密码登录
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(StringUtils.hasText(oauthUser.getLogin()) ? oauthUser.getLogin() : username);
        user.setEmail(oauthUser.getEmail() == null ? "" : oauthUser.getEmail());
        user.setAvatar(oauthUser.getAvatar() == null ? "" : oauthUser.getAvatar());
        user.setStatus(1);
        user.setIsAdmin(0);
        // 默认只授权 flash 档位
        user.setAgents("flash");
        user.setCreatedBy("oauth:" + oauthUser.getChannel());
        user.setUpdatedBy("oauth:" + oauthUser.getChannel());
        user.setRegisterTime(LocalDateTime.now());
        userMapper.insert(user);

        UserOauth binding = new UserOauth();
        binding.setUserId(user.getId());
        binding.setChannel(oauthUser.getChannel());
        binding.setOpenId(oauthUser.getOpenId());
        binding.setCreateTime(LocalDateTime.now());
        userOauthMapper.insert(binding);
        return user.getId();
    }

    private String uniqueUsername(String login) {
        String base = StringUtils.hasText(login) ? login : "github_user";
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, base));
        if (count == null || count == 0) {
            return base;
        }
        String candidate = base + "_gh";
        int suffix = 2;
        while (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, candidate)) != 0) {
            candidate = base + "_gh" + suffix++;
        }
        return candidate;
    }
}