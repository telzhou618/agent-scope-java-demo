package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.PasswordUpdateRequest;
import com.example.agent.dto.ProfileUpdateRequest;
import com.example.agent.dto.Result;
import com.example.agent.service.FileStorageService;
import com.example.agent.service.UserProfileService;
import com.example.agent.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心：登录用户自助修改资料与密码
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@Validated
@Tag(name = "个人中心")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final FileStorageService fileStorageService;

    @PostMapping("/avatar")
    @Operation(summary = "上传头像")
    @OperationLog("上传头像")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.okData(fileStorageService.uploadAvatar(file, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/profile")
    @Operation(summary = "修改个人资料（仅更新非空字段）")
    @OperationLog("修改个人资料")
    public Result<UserVO> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return Result.okData(userProfileService.updateProfile(StpUtil.getLoginIdAsLong(), request));
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    @OperationLog("修改密码")
    public Result<Void> updatePassword(@RequestBody @Validated PasswordUpdateRequest request) {
        userProfileService.updatePassword(StpUtil.getLoginIdAsLong(), request);
        return Result.ok();
    }
}
