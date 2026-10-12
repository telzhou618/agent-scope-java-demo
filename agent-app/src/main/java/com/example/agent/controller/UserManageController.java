package com.example.agent.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.Result;
import com.example.agent.dto.FeedbackStatusRequest;
import com.example.agent.dto.UserCreateRequest;
import com.example.agent.dto.UserStatusRequest;
import com.example.agent.dto.UserUpdateRequest;
import com.example.agent.service.OperationLogService;
import com.example.agent.service.UserFeedbackService;
import com.example.agent.service.UserManageService;
import com.example.agent.vo.UserFeedbackVO;
import com.example.agent.vo.UserManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理：仅管理员可用；业务逻辑在各 Service 中
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/manage")
@SaCheckRole("admin")
@Validated
@Tag(name = "用户管理")
public class UserManageController {

    private final UserManageService userManageService;
    private final OperationLogService operationLogService;
    private final UserFeedbackService userFeedbackService;

    @GetMapping("/page")
    @Operation(summary = "用户分页列表")
    public Result<Page<UserManageVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword) {
        return Result.okData(userManageService.page(page, size, keyword));
    }

    @PostMapping("/create")
    @Operation(summary = "新增用户")
    @OperationLog("新增用户")
    public Result<Void> create(@RequestBody @Validated UserCreateRequest request) {
        userManageService.create(request);
        return Result.ok();
    }

    @PutMapping("/update")
    @Operation(summary = "编辑用户（仅更新非空字段）")
    @OperationLog("编辑用户")
    public Result<Void> update(@RequestBody @Validated UserUpdateRequest request) {
        userManageService.update(request);
        return Result.ok();
    }

    @PostMapping("/status")
    @Operation(summary = "启用/禁用用户")
    @OperationLog("启用禁用用户")
    public Result<Void> status(@RequestBody @Validated UserStatusRequest request) {
        userManageService.updateStatus(request);
        return Result.ok();
    }

    @GetMapping("/logs/page")
    @Operation(summary = "操作日志分页列表")
    public Result<Page<com.example.agent.entity.OperationLog>> logPage(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword) {
        // 实体类与 @OperationLog 注解同名，此处使用全限定名引用实体
        return Result.okData(operationLogService.page(page, size, keyword));
    }

    @GetMapping("/feedbacks/page")
    @Operation(summary = "意见反馈分页列表")
    public Result<Page<UserFeedbackVO>> feedbackPage(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) String keyword) {
        return Result.okData(userFeedbackService.page(page, size, status, keyword));
    }

    @PostMapping("/feedbacks/status")
    @Operation(summary = "处理意见反馈")
    @OperationLog("处理意见反馈")
    public Result<Void> feedbackStatus(@RequestBody @Validated FeedbackStatusRequest request) {
        userFeedbackService.updateStatus(request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @OperationLog("删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userManageService.delete(id);
        return Result.ok();
    }
}
