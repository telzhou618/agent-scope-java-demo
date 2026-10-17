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
     * 运行时上下文标记键：本轮由定时任务触发的任务 ID（ScheduledTaskJob 写入）。
     * 命中时禁止再创建/修改定时任务，避免定时任务执行过程中不断派生子任务
     */
    public static final String SCHEDULED_TASK_RUN_KEY = "scheduledTaskId";

    private final ScheduleService scheduleService;
    private final String agentName;
    private final UserPermissionService userPermissionService;

    public ScheduleTools(ScheduleService scheduleService, String agentName, UserPermissionService userPermissionService) {
        this.scheduleService = scheduleService;
        this.agentName = agentName;
        this.userPermissionService = userPermissionService;
    }

    @Tool(name = "create_schedule", description = "创建定时任务：当用户要求定期/周期性执行某件事时使用。"
            + "title 写一个简短可读的任务标题；执行周期写入 cron 字段；到点要做的具体事情写入 description 字段，description 用 Markdown 格式写清楚要做什么（目标、步骤、产出、输出格式），只写任务本身，"
            + "禁止出现任何执行频次、执行时间、周期、定时、cron 等字眼（如「每天」「每周一」「定期」「几点」），周期一律由 cron 表达。"
            + "cron 为 Quartz 6 段表达式（秒 分 时 日 月 周），例如每周一 9 点是 0 0 9 ? * MON，每天 8 点是 0 0 8 * * ?")
    public Map<String, Object> createSchedule(
            @ToolParam(name = "title", description = "简短可读的任务标题，例如「每周运营报告」") String title,
            @ToolParam(name = "description", description = "到点后要执行的具体任务内容，用 Markdown 格式写清楚（做什么、步骤、产出、输出格式）。只写任务本身，禁止出现任何执行频次、执行时间、周期、定时、cron 等字眼（如「每天」「每周一」「定期」「几点」），周期由 cron 表达。") String description,
            @ToolParam(name = "cron", description = "Quartz 6 段 cron 表达式（秒 分 时 日 月 周）") String cron,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        if (inScheduledTaskRun(context)) {
            return fail("定时任务执行过程中不能再创建定时任务");
        }
        try {
            LocalDateTime nextRunAt = scheduleService.create(userId, agentName, title, description, cron);
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
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        Map<String, Object> result = ok();
        result.put("tasks", scheduleService.listForUser(userId));
        return result;
    }

    @Tool(name = "update_schedule", description = "修改定时任务的标题、执行内容或执行周期（cron），先用 list_schedules 拿到任务 id。"
            + "只传需要改的字段；修改 description 时用 Markdown 格式写任务内容，禁止出现任何执行频次、执行时间、周期、定时、cron 等字眼，周期由 cron 表达；cron 非空时校验合法性并重排触发器")
    public Map<String, Object> updateSchedule(
            @ToolParam(name = "id", description = "任务 ID") long id,
            @ToolParam(name = "title", required = false, description = "修改后的简短任务标题，不传则不修改") String title,
            @ToolParam(name = "description", required = false, description = "修改后的具体任务内容，用 Markdown 格式写清楚（做什么、步骤、产出、输出格式）。只写任务本身，禁止出现任何执行频次、执行时间、周期、定时、cron 等字眼，周期由 cron 表达；不传则不修改。") String description,
            @ToolParam(name = "cron", required = false, description = "新的 Quartz 6 段 cron 表达式，不传则不修改") String cron,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        if (!userPermissionService.hasPermission(userId, UserPermission.SCHEDULE)) {
            return fail("无「定时任务」权限");
        }
        if (inScheduledTaskRun(context)) {
            return fail("定时任务执行过程中不能再修改定时任务");
        }
        try {
            LocalDateTime nextRunAt = scheduleService.update(userId, id, null, title, description, cron);
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

    /**
     * 当前是否在定时任务的执行轮次中：本轮上下文带 ScheduledTaskJob 写入的任务 ID 标记
     */
    private boolean inScheduledTaskRun(RuntimeContext context) {
        return context != null && context.get(SCHEDULED_TASK_RUN_KEY) != null;
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
