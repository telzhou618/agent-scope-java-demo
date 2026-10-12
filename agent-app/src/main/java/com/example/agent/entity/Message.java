package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息中心 t_message（report 定时报告 / system 系统通知）
 */
@Data
@TableName("t_message")
public class Message {

    public static final String TYPE_REPORT = "report";
    public static final String TYPE_SYSTEM = "system";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 */
    private Long userId;

    /** report / system */
    private String type;

    /** 发送方 Agent 名（flash/plus/max），系统消息为空串 */
    private String sender;

    private String title;

    /** 正文（Markdown） */
    private String content;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    /** 来源定时任务，系统消息为 null */
    private Long taskId;

    /** success / failed */
    private String runStatus;

    private LocalDateTime createTime;
}
