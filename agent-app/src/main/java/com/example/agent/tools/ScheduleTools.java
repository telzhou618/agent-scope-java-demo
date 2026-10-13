package com.example.agent.tools;

import com.example.agent.enums.UserPermission;
import com.example.agent.error.BizException;
import com.example.agent.service.ScheduleService;
import com.example.agent.service.UserPermissionService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定时任务工具：用户用自然语言让 Agent 创建/查询/修改/删除/立即执行定时任务（如「每周一把上周的运营报告发我」）。
 * delete_schedule、trigger_schedule 在 agent.confirm-tools 名单内：调用会被权限层挂起，等前端用户确认后才真正执行。
 * 非 Spring Bean：由 Agent 工厂按档位实例化，绑定当前档位名。
 */
@Slf4j
public class ScheduleTools {

    /**
     * RuntimeContext 标记：定时任务后台执行时置 true，用于禁用再次操作定时任务，避免循环创建
     */
    public static final String CTX_SCHEDULED_EXECUTION = "scheduledExecution";

    private final ScheduleService scheduleService;
    private final String agentName;
    private final UserPermissionService userPermissionService;

    public ScheduleTools(ScheduleService scheduleService, String agentName, UserPermissionService userPermissionService) {
        this.scheduleService = scheduleService;
        this.agentName = agentName;
        this.userPermissionService = userPermissionService;
    }

    @Tool(name = "create_schedule", description = "创建定时任务：当用户要求定期做某件事（如每周报告、每日摘要）时使用。"
            + "cron 为 Quartz 6 段表达式（秒 分 时 日 月 周），例如每周一 9 点是 0 0 9 ? * MON，每天 8 点是 0 0 8 * * ?")
    public Map<String, Object> createSchedule(
            @ToolParam(name = "description", description = "用户需求原文，到点时将作为消息发给 Agent 执行") String description,
            @ToolParam(name = "cron", description = "Quartz 6 段 cron 表达式（秒 分 时 日 月 周）") String cron,
            RuntimeContext context) {
        if (isScheduledExecution(context)) {
            return fail("定时任务自动执行中，禁止再次操作定时任务");
        }
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        try {
            LocalDateTime nextRunAt = scheduleService.create(userId, agentName, description, cron);
            log.info("定时任务创建成功, userId={}, agent={}, cron={}", userId, agentName, cron);
            Map<String, Object> result = ok();
            result.put("nextRunAt", String.valueOf(nextRunAt));
            return result;
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "list_schedules", description = "查询当前用户的定时任务列表（id、描述、cron、状态、上次/下次执行时间）")
    public Map<String, Object> listSchedules(RuntimeContext context) {
        if (isScheduledExecution(context)) {
            return fail("定时任务自动执行中，禁止再次操作定时任务");
        }
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        Map<String, Object> result = ok();
        result.put("tasks", scheduleService.listForUser(userId));
        return result;
    }

    @Tool(name = "update_schedule", description = "修改定时任务的描述或执行周期（cron），先用 list_schedules 拿到任务 id；"
            + "只传需要改的字段，cron 非空时校验合法性并重排触发器")
    public Map<String, Object> updateSchedule(
            @ToolParam(name = "id", description = "任务 ID") long id,
            @ToolParam(name = "description", required = false, description = "新的需求描述，不传则不修改") String description,
            @ToolParam(name = "cron", required = false, description = "新的 Quartz 6 段 cron 表达式，不传则不修改") String cron,
            RuntimeContext context) {
        if (isScheduledExecution(context)) {
            return fail("定时任务自动执行中，禁止再次操作定时任务");
        }
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        try {
            LocalDateTime nextRunAt = scheduleService.update(userId, id, description, cron);
            log.info("定时任务修改成功, userId={}, taskId={}", userId, id);
            Map<String, Object> result = ok();
            result.put("nextRunAt", String.valueOf(nextRunAt));
            return result;
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "delete_schedule", description = "删除定时任务，先用 list_schedules 拿到任务 id；删除后任务不再执行且不可恢复")
    public Map<String, Object> deleteSchedule(
            @ToolParam(name = "id", description = "任务 ID") long id,
            RuntimeContext context) {
        if (isScheduledExecution(context)) {
            return fail("定时任务自动执行中，禁止再次操作定时任务");
        }
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        try {
            scheduleService.delete(userId, id);
            log.info("定时任务删除成功, userId={}, taskId={}", userId, id);
            return ok();
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "trigger_schedule", description = "立即手动执行一次定时任务（不等下一个周期），先用 list_schedules 拿到任务 id；"
            + "执行结果会发到用户的消息中心")
    public Map<String, Object> triggerSchedule(
            @ToolParam(name = "id", description = "任务 ID") long id,
            RuntimeContext context) {
        if (isScheduledExecution(context)) {
            return fail("定时任务自动执行中，禁止再次操作定时任务");
        }
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        try {
            scheduleService.triggerNow(userId, id);
            log.info("定时任务手动触发, userId={}, taskId={}", userId, id);
            Map<String, Object> result = ok();
            result.put("note", "任务已在后台开始执行，完成后结果会发到消息中心");
            return result;
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    private boolean isScheduledExecution(RuntimeContext context) {
        return context != null && Boolean.TRUE.equals(context.get(CTX_SCHEDULED_EXECUTION));
    }

    private Long loginUserId(RuntimeContext context) {
        if (context == null || context.getUserId() == null) {
            return null;
        }
        try {
            return Long.valueOf(context.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> ok() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return result;
    }

    private Map<String, Object> fail(String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", error);
        return result;
    }
}
