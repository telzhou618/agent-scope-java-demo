package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈 t_user_feedback
 */
@Data
@TableName("t_user_feedback")
public class UserFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** bug 问题 / idea 建议 / other 其他 */
    private String type;

    private String content;

    private String contact;

    /** 0 待处理 / 1 已处理 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
