package com.example.agent.service;

import cn.dev33.satoken.stp.StpInterface;
import com.example.agent.entity.User;
import com.example.agent.enums.UserPermission;
import com.example.agent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色数据源：按 t_user.is_admin 判定 admin/user，供 @SaCheckRole 注解鉴权使用
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    /**
     * 返回用户权限码：管理员返回全部权限码，普通用户返回 t_user.permissions 中的码
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        if (user != null && user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return UserPermission.allCodes();
        }
        return user == null ? List.of() : UserPermission.parse(user.getPermissions());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        if (user != null && user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return List.of("admin");
        }
        return List.of("user");
    }
}
