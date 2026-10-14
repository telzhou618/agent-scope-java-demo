package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.agent.entity.ScheduledTask;
import com.example.agent.error.BizException;
import com.example.agent.mapper.ScheduledTaskMapper;
import com.example.agent.vo.ScheduledTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * 定时任务：业务表 t_scheduled_task + Quartz 触发器双写；
 * 触发状态（下次执行时间等）以 Quartz 为准，业务表只存业务属性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String TRIGGER_GROUP = "agent-task";
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    private final ScheduledTaskMapper taskMapper;
    private final Scheduler scheduler;

    /**
     * 创建任务：校验 cron → 落库 → 注册 Quartz Job/Trigger；返回下次执行时间
     */
    public LocalDateTime create(long userId, String agentName, String title, String description, String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new BizException("cron 表达式不合法: " + cron);
        }
        ScheduledTask task = new ScheduledTask();
        task.setUserId(userId);
        task.setAgentName(agentName);
        task.setTitle(title == null ? "" : title);
        task.setDescription(description);
        task.setCron(cron);
        task.setStatus(1);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        try {
            Trigger trigger = buildTrigger(task);
            scheduler.scheduleJob(buildJobDetail(task.getId()), trigger);
            return toLocalDateTime(trigger.getNextFireTime());
        } catch (SchedulerException e) {
            // 触发器注册失败时回滚业务行，避免留下永不触发的死任务
            taskMapper.deleteById(task.getId());
            throw new BizException("定时任务注册失败: " + e.getMessage());
        }
    }

    public List<ScheduledTaskVO> listForUser(long userId) {
        List<ScheduledTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getUserId, userId)
                .orderByDesc(ScheduledTask::getId));
        return tasks.stream().map(this::toVO).toList();
    }

    public void toggle(long userId, long id, int status) {
        ScheduledTask task = requireOwn(userId, id);
        task.setStatus(status);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        try {
            if (status == 1) {
                scheduler.resumeTrigger(triggerKey(id));
            } else {
                scheduler.pauseTrigger(triggerKey(id));
            }
        } catch (SchedulerException e) {
            throw new BizException("触发器状态更新失败: " + e.getMessage());
        }
    }

    public void delete(long userId, long id) {
        ScheduledTask task = requireOwn(userId, id);
        try {
            scheduler.deleteJob(jobKey(task.getId()));
        } catch (SchedulerException e) {
            throw new BizException("触发器删除失败: " + e.getMessage());
        }
        taskMapper.deleteById(task.getId());
    }

    /**
     * 修改任务（title/描述/cron 非空才更新）；cron 变化时重排触发器，返回下次执行时间
     */
    public LocalDateTime update(long userId, long id, String title, String description, String cron) {
        ScheduledTask task = requireOwn(userId, id);
        boolean cronChanged = false;
        if (StringUtils.hasText(title)) {
            task.setTitle(title);
        }
        if (StringUtils.hasText(description)) {
            task.setDescription(description);
        }
        if (StringUtils.hasText(cron) && !cron.equals(task.getCron())) {
            if (!CronExpression.isValidExpression(cron)) {
                throw new BizException("cron 表达式不合法: " + cron);
            }
            task.setCron(cron);
            cronChanged = true;
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        if (cronChanged) {
            try {
                scheduler.rescheduleJob(triggerKey(task.getId()), buildTrigger(task));
            } catch (SchedulerException e) {
                throw new BizException("触发器重排失败: " + e.getMessage());
            }
        }
        return nextFireTime(task.getId());
    }

    public ScheduledTask findById(long id) {
        return taskMapper.selectById(id);
    }

    /**
     * 立即触发一次执行（不影响正常周期）；停用状态的任务也可手动触发
     */
    public void triggerNow(long userId, long id) {
        ScheduledTask task = requireOwn(userId, id);
        try {
            scheduler.triggerJob(jobKey(task.getId()));
        } catch (SchedulerException e) {
            throw new BizException("触发失败: " + e.getMessage());
        }
    }

    public void markRun(long id) {
        ScheduledTask update = new ScheduledTask();
        update.setId(id);
        update.setLastRunAt(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private ScheduledTask requireOwn(long userId, long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BizException("定时任务不存在");
        }
        return task;
    }

    private ScheduledTaskVO toVO(ScheduledTask task) {
        return ScheduledTaskVO.builder()
                .id(task.getId())
                .agentName(task.getAgentName())
                .title(task.getTitle())
                .description(task.getDescription())
                .cron(task.getCron())
                .status(task.getStatus())
                .lastRunAt(task.getLastRunAt())
                .nextRunAt(nextFireTime(task.getId()))
                .createTime(task.getCreateTime())
                .build();
    }

    /**
     * 下次执行时间从 Quartz trigger 实时读取；trigger 不存在（如已删除）时返回 null
     */
    private LocalDateTime nextFireTime(long taskId) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(taskId));
            return trigger == null ? null : toLocalDateTime(trigger.getNextFireTime());
        } catch (SchedulerException e) {
            log.warn("读取触发器失败, taskId={}", taskId, e);
            return null;
        }
    }

    private JobDetail buildJobDetail(long taskId) {
        return JobBuilder.newJob(ScheduledTaskJob.class)
                .withIdentity(jobKey(taskId))
                .usingJobData("taskId", taskId)
                .storeDurably(false)
                .build();
    }

    private Trigger buildTrigger(ScheduledTask task) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(task.getId()))
                .forJob(jobKey(task.getId()))
                // 错过的周期不补跑，等下一次正常触发
                .withSchedule(CronScheduleBuilder.cronSchedule(task.getCron())
                        .inTimeZone(TIME_ZONE)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    private JobKey jobKey(long taskId) {
        return JobKey.jobKey("task-" + taskId, TRIGGER_GROUP);
    }

    private TriggerKey triggerKey(long taskId) {
        return TriggerKey.triggerKey("task-" + taskId, TRIGGER_GROUP);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.of("Asia/Shanghai"));
    }
}
