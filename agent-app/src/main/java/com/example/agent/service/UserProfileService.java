package com.example.agent.service;

import com.example.agent.dto.PasswordUpdateRequest;
import com.example.agent.dto.ProfileUpdateRequest;
import com.example.agent.entity.User;
import com.example.agent.error.BizException;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 个人中心：登录用户自助修改资料与密码
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 修改个人资料（仅更新非空字段），返回最新用户信息
     */
    public UserVO updateProfile(long userId, ProfileUpdateRequest request) {
        User update = new User();
        update.setId(userId);
        if (request.getNickname() != null) {
            update.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            update.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            update.setAvatar(request.getAvatar());
        }
        userMapper.updateById(update);
        return authService.currentUser();
    }

    public void updatePassword(long userId, PasswordUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在或已被删除");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);
    }
}
