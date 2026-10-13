package com.example.agent.service;

import com.example.agent.entity.User;
import com.example.agent.enums.UserPermission;
import com.example.agent.error.BizException;
import com.example.agent.error.ErrorCode;
import com.example.agent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户权限判定：管理员放行全部，普通用户按 t_user.permissions 权限码
 */
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserMapper userMapper;

    public boolean hasPermission(long userId, UserPermission permission) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        if (user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return true;
        }
        return UserPermission.contains(user.getPermissions(), permission);
    }

    public void requirePermission(long userId, UserPermission permission) {
        if (!hasPermission(userId, permission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无「" + permission.getLabel() + "」权限");
        }
    }
}