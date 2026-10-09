package com.example.agent.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.Result;
import com.example.agent.dto.FeedbackStatusRequest;
import com.example.agent.dto.UserCreateRequest;
import com.example.agent.dto.UserStatusRequest;
import com.example.agent.dto.UserUpdateRequest;
import com.example.agent.entity.User;
import com.example.agent.entity.UserFeedback;
import com.example.agent.error.BizException;
import com.example.agent.mapper.OperationLogMapper;
import com.example.agent.mapper.UserFeedbackMapper;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.UserFeedbackVO;
import com.example.agent.vo.UserManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理：仅管理员可用
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/manage")
@SaCheckRole("admin")
@Validated
@Tag(name = "用户管理")
public class UserManageController {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OperationLogMapper operationLogMapper;
    private final UserFeedbackMapper userFeedbackMapper;

    @GetMapping("/page")
    @Operation(summary = "用户分页列表")
    public Result<Page<UserManageVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 用户名/昵称/邮箱任一模糊命中
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword));
        }
        wrapper.orderByDesc(User::getId);
        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);
        Page<UserManageVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream().map(UserManageVO::from).toList());
        return Result.okData(voPage);
    }

    @PostMapping("/create")
    @Operation(summary = "新增用户")
    @OperationLog("新增用户")
    public Result<Void> create(@RequestBody @Validated UserCreateRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        boolean isAdmin = Boolean.TRUE.equals(request.getIsAdmin());
        String operator = currentUsername();

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar() == null ? "" : request.getAvatar());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setIsAdmin(isAdmin ? 1 : 0);
        // 管理员不受 Agent 白名单限制，一律存空串
        user.setAgents(isAdmin ? "" : joinAgents(request.getAgents()));
        user.setCreatedBy(operator);
        user.setUpdatedBy(operator);
        user.setRegisterTime(LocalDateTime.now());
        userMapper.insert(user);
        return Result.ok();
    }

    @PutMapping("/update")
    @Operation(summary = "编辑用户（仅更新非空字段）")
    @OperationLog("编辑用户")
    public Result<Void> update(@RequestBody @Validated UserUpdateRequest request) {
        User existing = userMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException("用户不存在");
        }
        // 不能禁用或降权自己
        if (request.getId() == StpUtil.getLoginIdAsLong()) {
            if ((request.getStatus() != null && request.getStatus() == 0)
                    || Boolean.FALSE.equals(request.getIsAdmin())) {
                throw new BizException("不能禁用或降权自己");
            }
        }
        // 内置 admin 账号不允许取消管理员身份
        if ("admin".equals(existing.getUsername()) && Boolean.FALSE.equals(request.getIsAdmin())) {
            throw new BizException("内置 admin 账号不能取消管理员身份");
        }

        User update = new User();
        update.setId(request.getId());
        if (request.getNickname() != null) {
            update.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            update.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            update.setAvatar(request.getAvatar());
        }
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        if (request.getIsAdmin() != null) {
            update.setIsAdmin(request.getIsAdmin() ? 1 : 0);
        }
        // 目标最终是管理员时 agents 强制清空；否则按请求更新
        boolean willBeAdmin = request.getIsAdmin() != null
                ? request.getIsAdmin()
                : existing.getIsAdmin() != null && existing.getIsAdmin() == 1;
        if (willBeAdmin) {
            if (request.getIsAdmin() != null || request.getAgents() != null) {
                update.setAgents("");
            }
        } else if (request.getAgents() != null) {
            update.setAgents(joinAgents(request.getAgents()));
        }
        // 密码非空才更新
        if (StringUtils.hasText(request.getPassword())) {
            update.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        update.setUpdatedBy(currentUsername());
        userMapper.updateById(update);
        return Result.ok();
    }

    @PostMapping("/status")
    @Operation(summary = "启用/禁用用户")
    @OperationLog("启用禁用用户")
    public Result<Void> status(@RequestBody @Validated UserStatusRequest request) {
        if (request.getId() == StpUtil.getLoginIdAsLong()) {
            throw new BizException("不能禁用自己");
        }
        User existing = userMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException("用户不存在");
        }
        User update = new User();
        update.setId(request.getId());
        update.setStatus(request.getStatus());
        update.setUpdatedBy(currentUsername());
        userMapper.updateById(update);
        // 禁用即踢下线
        if (request.getStatus() == 0) {
            StpUtil.kickout(request.getId());
        }
        return Result.ok();
    }

    @GetMapping("/logs/page")
    @Operation(summary = "操作日志分页列表")
    public Result<Page<com.example.agent.entity.OperationLog>> logPage(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword) {
        // 实体类与 @OperationLog 注解同名，此处使用全限定名引用实体
        LambdaQueryWrapper<com.example.agent.entity.OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 用户名/操作描述/请求路径任一模糊命中
            wrapper.and(w -> w.like(com.example.agent.entity.OperationLog::getUsername, keyword)
                    .or().like(com.example.agent.entity.OperationLog::getOperation, keyword)
                    .or().like(com.example.agent.entity.OperationLog::getPath, keyword));
        }
        wrapper.orderByDesc(com.example.agent.entity.OperationLog::getId);
        Page<com.example.agent.entity.OperationLog> logPage = operationLogMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.okData(logPage);
    }

    @GetMapping("/feedbacks/page")
    @Operation(summary = "意见反馈分页列表")
    public Result<Page<UserFeedbackVO>> feedbackPage(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(UserFeedback::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            // 反馈内容/联系方式任一模糊命中
            wrapper.and(w -> w.like(UserFeedback::getContent, keyword)
                    .or().like(UserFeedback::getContact, keyword));
        }
        wrapper.orderByDesc(UserFeedback::getId);
        Page<UserFeedback> feedbackPage = userFeedbackMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查提交人，回填 username（用户已删除时填空串）
        List<Long> userIds = feedbackPage.getRecords().stream()
                .map(UserFeedback::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        Page<UserFeedbackVO> voPage = new Page<>(feedbackPage.getCurrent(), feedbackPage.getSize(), feedbackPage.getTotal());
        voPage.setRecords(feedbackPage.getRecords().stream().map(feedback -> {
            UserFeedbackVO vo = UserFeedbackVO.from(feedback);
            User submitter = userMap.get(feedback.getUserId());
            vo.setUsername(submitter == null ? "" : submitter.getUsername());
            return vo;
        }).toList());
        return Result.okData(voPage);
    }

    @PostMapping("/feedbacks/status")
    @Operation(summary = "处理意见反馈")
    @OperationLog("处理意见反馈")
    public Result<Void> feedbackStatus(@RequestBody @Validated FeedbackStatusRequest request) {
        UserFeedback existing = userFeedbackMapper.selectById(request.getId());
        if (existing == null) {
            throw new BizException("反馈不存在");
        }
        UserFeedback update = new UserFeedback();
        update.setId(request.getId());
        update.setStatus(request.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        userFeedbackMapper.updateById(update);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @OperationLog("删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        if (id == StpUtil.getLoginIdAsLong()) {
            throw new BizException("不能删除自己");
        }
        userMapper.deleteById(id);
        StpUtil.kickout(id);
        return Result.ok();
    }

    /**
     * 当前登录管理员用户名，写入 created_by/updated_by
     */
    private String currentUsername() {
        User operator = userMapper.selectById(StpUtil.getLoginIdAsLong());
        return operator == null ? "" : operator.getUsername();
    }

    private String joinAgents(List<String> agents) {
        if (agents == null || agents.isEmpty()) {
            return "";
        }
        return String.join(",", agents.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList());
    }
}
