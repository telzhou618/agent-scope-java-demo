package com.example.agent.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.dto.UserCreateRequest;
import com.example.agent.dto.UserStatusRequest;
import com.example.agent.dto.UserUpdateRequest;
import com.example.agent.entity.User;
import com.example.agent.error.BizException;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.UserManageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理（仅管理员）：分页查询、新增、编辑、启用禁用、删除；含自我保护与内置 admin 保护规则
 */
@Service
@RequiredArgsConstructor
public class UserManageService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 用户分页列表：用户名/昵称/邮箱任一模糊命中
     */
    public Page<UserManageVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword));
        }
        wrapper.orderByDesc(User::getId);
        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);
        Page<UserManageVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream().map(UserManageVO::from).toList());
        return voPage;
    }

    public void create(UserCreateRequest request) {
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
        // 管理员不受权限限制，一律存空串
        user.setPermissions(isAdmin ? "" : joinPermissions(request.getPermissions()));
        user.setCreatedBy(operator);
        user.setUpdatedBy(operator);
        user.setRegisterTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    /**
     * 编辑用户（仅更新非空字段）
     */
    public void update(UserUpdateRequest request) {
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
        if (willBeAdmin) {
            if (request.getIsAdmin() != null || request.getPermissions() != null) {
                update.setPermissions("");
            }
        } else if (request.getPermissions() != null) {
            update.setPermissions(joinPermissions(request.getPermissions()));
        }
        // 密码非空才更新
        if (StringUtils.hasText(request.getPassword())) {
            update.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        update.setUpdatedBy(currentUsername());
        userMapper.updateById(update);
    }

    /**
     * 启用/禁用用户：禁用即踢下线
     */
    public void updateStatus(UserStatusRequest request) {
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
        if (request.getStatus() == 0) {
            StpUtil.kickout(request.getId());
        }
    }

    public void delete(Long id) {
        if (id == StpUtil.getLoginIdAsLong()) {
            throw new BizException("不能删除自己");
        }
        userMapper.deleteById(id);
        StpUtil.kickout(id);
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

    private String joinPermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return String.join(",", permissions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList());
    }
}
