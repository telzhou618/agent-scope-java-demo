package com.example.agent.service;

import com.example.agent.entity.User;
import com.example.agent.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * StpInterfaceImpl 权限码数据源单元测试（REST 鉴权链 @SaCheckPermission 的唯一数据源）
 */
@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private StpInterfaceImpl stpInterface;

    @Test
    void adminGetsAllPermissionCodes() {
        User admin = new User();
        admin.setIsAdmin(1);
        admin.setPermissions("");
        when(userMapper.selectById(1L)).thenReturn(admin);

        List<String> perms = stpInterface.getPermissionList(1L, "login");

        assertTrue(perms.contains("schedule"));
    }

    @Test
    void normalUserWithScheduleGetsScheduleCode() {
        User user = new User();
        user.setIsAdmin(0);
        user.setPermissions("schedule");
        when(userMapper.selectById(2L)).thenReturn(user);

        assertEquals(List.of("schedule"), stpInterface.getPermissionList(2L, "login"));
    }

    @Test
    void normalUserWithoutCodeGetsEmpty() {
        User user = new User();
        user.setIsAdmin(0);
        user.setPermissions("");
        when(userMapper.selectById(3L)).thenReturn(user);

        assertEquals(List.of(), stpInterface.getPermissionList(3L, "login"));
    }

    @Test
    void missingUserGetsEmpty() {
        when(userMapper.selectById(9L)).thenReturn(null);

        assertEquals(List.of(), stpInterface.getPermissionList(9L, "login"));
    }
}