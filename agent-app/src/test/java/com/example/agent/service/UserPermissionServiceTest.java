package com.example.agent.service;

import com.example.agent.entity.User;
import com.example.agent.enums.UserPermission;
import com.example.agent.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * UserPermissionService 权限判定逻辑单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserPermissionService userPermissionService;

    @Test
    void adminAlwaysHasPermission() {
        User admin = new User();
        admin.setId(1L);
        admin.setIsAdmin(1);
        admin.setPermissions("");
        when(userMapper.selectById(1L)).thenReturn(admin);

        assertTrue(userPermissionService.hasPermission(1L, UserPermission.SCHEDULE));
    }

    @Test
    void normalUserWithCodeHasPermission() {
        User user = new User();
        user.setId(2L);
        user.setIsAdmin(0);
        user.setPermissions("schedule");
        when(userMapper.selectById(2L)).thenReturn(user);

        assertTrue(userPermissionService.hasPermission(2L, UserPermission.SCHEDULE));
    }

    @Test
    void normalUserWithoutCodeDenied() {
        User user = new User();
        user.setId(3L);
        user.setIsAdmin(0);
        user.setPermissions("");
        when(userMapper.selectById(3L)).thenReturn(user);

        assertFalse(userPermissionService.hasPermission(3L, UserPermission.SCHEDULE));
    }

    @Test
    void missingUserDenied() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertFalse(userPermissionService.hasPermission(9L, UserPermission.SCHEDULE));
    }
}