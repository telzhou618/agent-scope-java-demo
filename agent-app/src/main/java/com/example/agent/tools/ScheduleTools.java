package com.example.agent.tools;

import com.example.agent.error.BizException;
import com.example.agent.service.ScheduleService;
import com.example.agent.vo.ScheduledTaskVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务工具：用户用自然语言让 Agent 创建/查询定时任务（如「每周一把上周的运营报告发我」）。
 * 删除/启停不走工具，由用户在「定时任务」页面手动操作。
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
            ToolExecutionContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long userId = loginUserId(context);
        if (userId == null) {
            result.put("success", false);
            result.put("error", "无法识别当前用户");
            return result;
        }
        try {
            LocalDateTime nextRunAt = scheduleService.create(userId, agentName, description, cron);
            result.put("success", true);
            result.put("nextRunAt", String.valueOf(nextRunAt));
            log.info("定时任务创建成功, userId={}, agent={}, cron={}", userId, agentName, cron);
        } catch (BizException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Tool(name = "list_schedules", description = "查询当前用户的定时任务列表（描述、cron、状态、上次/下次执行时间）")
    public Object listSchedules(ToolExecutionContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return Map.of("success", false, "error", "无法识别当前用户");
        }
        List<ScheduledTaskVO> tasks = scheduleService.listForUser(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tasks", tasks);
        return result;
    }

    private Long loginUserId(ToolExecutionContext context) {
        RuntimeContext runtimeContext = context == null ? null : context.get(RuntimeContext.class);
        if (runtimeContext == null || runtimeContext.getUserId() == null) {
            return null;
        }
        try {
            return Long.valueOf(runtimeContext.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
