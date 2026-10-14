package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务 t_scheduled_task（触发状态由 Quartz QRTZ_* 表承载，此表只存业务属性）
 */
@Data
@TableName("t_scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 创建时档位 flash/plus/max */
    private String agentName;

    /** 任务标题 */
    private String title;

    /** 需求原文 */
    private String description;

    /** Quartz cron（6 段，含秒） */
    private String cron;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime lastRunAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
