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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务工具在后台执行中应被禁用，避免循环创建/修改定时任务
 */
@ExtendWith(MockitoExtension.class)
class ScheduleToolsTest {

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private UserPermissionService userPermissionService;

    private ScheduleTools tools() {
        return new ScheduleTools(scheduleService, "max", userPermissionService);
    }

    @Test
    void scheduledExecutionBlocksCreateSchedule() {
        ScheduleTools tools = tools();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("1")
                .sessionId("task-1-20200101000000")
                .put(ScheduleTools.CTX_SCHEDULED_EXECUTION, Boolean.TRUE)
                .build();

        Map<String, Object> result = tools.createSchedule("每周报告", "0 0 9 ? * MON", ctx);

        assertFalse((Boolean) result.get("success"));
        verify(userPermissionService, never()).hasPermission(anyLong(), any(UserPermission.class));
        verify(scheduleService, never()).create(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void normalExecutionCreatesScheduleWhenPermitted() {
        when(userPermissionService.hasPermission(1L, UserPermission.SCHEDULE)).thenReturn(true);
        when(scheduleService.create(1L, "max", "每周报告", "0 0 9 ? * MON")).thenReturn(LocalDateTime.now());

        ScheduleTools tools = tools();
        RuntimeContext ctx = RuntimeContext.builder().userId("1").sessionId("s1").build();

        Map<String, Object> result = tools.createSchedule("每周报告", "0 0 9 ? * MON", ctx);

        assertTrue((Boolean) result.get("success"));
        verify(scheduleService).create(1L, "max", "每周报告", "0 0 9 ? * MON");
    }
}