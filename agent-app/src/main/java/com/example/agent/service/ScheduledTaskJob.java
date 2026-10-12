package com.example.agent.service;

import com.example.agent.agent.AgentRegistry;
import com.example.agent.entity.Message;
import com.example.agent.entity.ScheduledTask;
import com.example.agent.entity.User;
import com.example.agent.mapper.UserMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时任务执行体：按任务创建时的档位在后台跑一次完整 Agent 会话，产出的正文写入消息中心。
 * 执行记录为独立后台会话（task-<id>-<时间戳>），不写 session_meta，不出现在侧栏会话列表。
 */
@Slf4j
@DisallowConcurrentExecution
public class ScheduledTaskJob extends QuartzJobBean {

    /**
     * 单次执行超时：超时按失败处理，任务进入下个周期
     */
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(5);

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AgentRegistry agentRegistry;

    @Autowired
    private UserMapper userMapper;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        Long taskId = context.getMergedJobDataMap().getLong("taskId");
        ScheduledTask task = scheduleService.findById(taskId);
        if (task == null) {
            log.warn("定时任务已不存在，跳过执行, taskId={}", taskId);
            return;
        }
        // 创建人被禁用/删除时跳过执行，任务保留待下个周期再检查
        User owner = userMapper.selectById(task.getUserId());
        if (owner == null || (owner.getStatus() != null && owner.getStatus() == 0)) {
            log.info("定时任务创建人已禁用或删除，跳过执行, taskId={}, userId={}", taskId, task.getUserId());
            return;
        }

        String runDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        try {
            String content = runAgent(task, runDate);
            messageService.create(task.getUserId(), Message.TYPE_REPORT, task.getAgentName(),
                    task.getDescription() + "（" + runDate + "）", content, task.getId(), "success");
            log.info("定时任务执行成功, taskId={}", taskId);
        } catch (Exception e) {
            log.error("定时任务执行失败, taskId={}", taskId, e);
            messageService.create(task.getUserId(), Message.TYPE_REPORT, task.getAgentName(),
                    task.getDescription() + "（" + runDate + "）生成失败",
                    "**本次定时任务执行失败**：" + e.getMessage()
                            + "\n\n任务未中断，将于下个周期正常重试；如需立即生成，可直接在对话中向 Agent 发起请求。",
                    task.getId(), "failed");
        }
        scheduleService.markRun(taskId);
    }

    /**
     * 跑一轮 Agent 并取最终正文文本
     */
    private String runAgent(ScheduledTask task, String runDate) {
        String sessionId = "task-" + task.getId() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(String.valueOf(task.getUserId()))
                .sessionId(sessionId)
                .put("requestId", sessionId)
                .build();
        StringBuilder result = new StringBuilder();
        agentRegistry.getAgent(task.getAgentName())
                .streamEvents(new UserMessage("user", task.getDescription()), runtimeContext)
                .doOnNext(event -> {
                    if (event instanceof AgentResultEvent e && e.getResult() != null
                            && e.getResult().getContent() != null) {
                        result.setLength(0);
                        result.append(extractText(e.getResult().getContent()));
                    }
                })
                .blockLast(RUN_TIMEOUT);
        String content = result.toString().trim();
        if (content.isEmpty()) {
            throw new IllegalStateException("Agent 未产出有效内容");
        }
        return content;
    }

    private String extractText(List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock text) {
                sb.append(text.getText());
            }
        }
        return sb.toString().trim();
    }
}
