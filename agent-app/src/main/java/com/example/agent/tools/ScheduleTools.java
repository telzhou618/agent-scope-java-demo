package com.example.agent.tools;

import com.example.agent.error.BizException;
import com.example.agent.service.ScheduleService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定时任务工具：用户用自然语言让 Agent 创建/查询/修改/删除定时任务（如「每周一把上周的运营报告发我」）。
 * delete_schedule 在 agent.confirm-tools 名单内：调用会被权限层挂起，等前端用户确认后才真正执行。
 * 非 Spring Bean：由 Agent 工厂按档位实例化，绑定当前档位名。
 */
@Slf4j
public class ScheduleTools {

    private final ScheduleService scheduleService;
    private final String agentName;

    public ScheduleTools(ScheduleService scheduleService, String agentName) {
        this.scheduleService = scheduleService;
        this.agentName = agentName;
    }

    @Tool(name = "create_schedule", description = "创建定时任务：当用户要求定期做某件事（如每周报告、每日摘要）时使用。"
            + "cron 为 Quartz 6 段表达式（秒 分 时 日 月 周），例如每周一 9 点是 0 0 9 ? * MON，每天 8 点是 0 0 8 * * ?")
    public Map<String, Object> createSchedule(
            @ToolParam(name = "description", description = "用户需求原文，到点时将作为消息发给 Agent 执行") String description,
            @ToolParam(name = "cron", description = "Quartz 6 段 cron 表达式（秒 分 时 日 月 周）") String cron,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
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
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
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
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
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
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        try {
            scheduleService.delete(userId, id);
            log.info("定时任务删除成功, userId={}, taskId={}", userId, id);
            return ok();
        } catch (BizException e) {
            return fail(e.getMessage());
        }
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
