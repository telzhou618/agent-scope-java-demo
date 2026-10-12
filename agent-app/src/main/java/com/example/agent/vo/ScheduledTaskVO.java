package com.example.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务列表项；下次执行时间从 Quartz trigger 实时读取
 */
@Data
@Builder
public class ScheduledTaskVO {

    private Long id;

    private String agentName;

    private String description;

    private String cron;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime lastRunAt;

    private LocalDateTime nextRunAt;

    private LocalDateTime createTime;
}
