package com.example.agent.tools;

import com.example.agent.enums.UserPermission;
import com.example.agent.service.ScheduleService;
import com.example.agent.service.UserPermissionService;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 定时任务工具防循环创建单元测试：定时任务执行轮次内禁止建/改任务，其余工具不受影响
 */
@ExtendWith(MockitoExtension.class)
class ScheduleToolsTest {

    private static final long USER_ID = 1L;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private UserPermissionService userPermissionService;

    @Test
    void taskRunCannotCreateSchedule() {
        when(userPermissionService.hasPermission(USER_ID, UserPermission.SCHEDULE)).thenReturn(true);

        Map<String, Object> result = tools().createSchedule(
                "每日天气", "查询杭州天气并输出报告", "0 0 8 * * ?", taskRunContext());

        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("error")).contains("不能再创建"));
        verifyNoInteractions(scheduleService);
    }

    @Test
    void taskRunCannotUpdateSchedule() {
        when(userPermissionService.hasPermission(USER_ID, UserPermission.SCHEDULE)).thenReturn(true);

        Map<String, Object> result = tools().updateSchedule(1L, "新标题", null, null, taskRunContext());

        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("error")).contains("不能再修改"));
        verifyNoInteractions(scheduleService);
    }

    @Test
    void normalRunCanCreateSchedule() {
        when(userPermissionService.hasPermission(USER_ID, UserPermission.SCHEDULE)).thenReturn(true);
        when(scheduleService.create(USER_ID, "max", "每日天气", "查询杭州天气", "0 0 8 * * ?"))
                .thenReturn(LocalDateTime.of(2026, 10, 18, 8, 0));

        Map<String, Object> result = tools().createSchedule(
                "每日天气", "查询杭州天气", "0 0 8 * * ?", normalContext());

        assertTrue((Boolean) result.get("success"));
        assertEquals("2026-10-18T08:00", String.valueOf(result.get("nextRunAt")));
        verify(scheduleService).create(USER_ID, "max", "每日天气", "查询杭州天气", "0 0 8 * * ?");
    }

    @Test
    void taskRunStillAllowsListSchedule() {
        when(userPermissionService.hasPermission(USER_ID, UserPermission.SCHEDULE)).thenReturn(true);
        when(scheduleService.listForUser(USER_ID)).thenReturn(List.of());

        Map<String, Object> result = tools().listSchedules(taskRunContext());

        assertTrue((Boolean) result.get("success"));
        verify(scheduleService).listForUser(USER_ID);
    }

    private ScheduleTools tools() {
        return new ScheduleTools(scheduleService, "max", userPermissionService);
    }

    private RuntimeContext normalContext() {
        return RuntimeContext.builder()
                .userId(String.valueOf(USER_ID))
                .sessionId("session-1")
                .build();
    }

    private RuntimeContext taskRunContext() {
        return RuntimeContext.builder()
                .userId(String.valueOf(USER_ID))
                .sessionId("task-1-20261018080000")
                .put(ScheduleTools.SCHEDULED_TASK_RUN_KEY, 1L)
                .build();
    }
}
