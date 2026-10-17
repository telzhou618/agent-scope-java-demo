package com.example.agent.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserPermission 枚举辅助方法的单元测试
 */
class UserPermissionTest {

    @Test
    void parseHandlesNullOrBlank() {
        assertEquals(List.of(), UserPermission.parse(null));
        assertEquals(List.of(), UserPermission.parse(""));
        assertEquals(List.of(), UserPermission.parse("  "));
    }

    @Test
    void parseTrimsAndDropsEmpty() {
        assertEquals(List.of("schedule", "other"), UserPermission.parse(" schedule, , other "));
    }

    @Test
    void containsMatchesCode() {
        assertTrue(UserPermission.contains("schedule,other", UserPermission.SCHEDULE));
        assertFalse(UserPermission.contains("other", UserPermission.SCHEDULE));
        assertFalse(UserPermission.contains(null, UserPermission.SCHEDULE));
    }

    @Test
    void allCodesHasEveryValue() {
        assertEquals(Arrays.stream(UserPermission.values()).map(UserPermission::getCode).toList(),
                UserPermission.allCodes());
    }
}