package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息反馈 t_message_feedback
 */
@Data
@TableName("t_message_feedback")
public class MessageFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String messageId;

    /** up 有帮助 / down 没帮助 */
    private String feedback;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
