package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.PasswordUpdateRequest;
import com.example.agent.dto.ProfileUpdateRequest;
import com.example.agent.dto.Result;
import com.example.agent.entity.User;
import com.example.agent.error.BizException;
import com.example.agent.mapper.UserMapper;
import com.example.agent.service.AuthService;
import com.example.agent.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心：登录用户自助修改资料与密码
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Validated
@Tag(name = "个人中心")
public class UserProfileController {

    private final UserMapper userMapper;
    private final AuthService authService;
    private final BCryptPasswordEncoder passwordEncoder;

    @PutMapping("/profile")
    @Operation(summary = "修改个人资料（仅更新非空字段）")
    @OperationLog("修改个人资料")
    public Result<UserVO> updateProfile(@RequestBody ProfileUpdateRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
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
        return Result.okData(authService.currentUser());
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    @OperationLog("修改密码")
    public Result<Void> updatePassword(@RequestBody @Validated PasswordUpdateRequest request) {
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
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
        return Result.ok();
    }
}
