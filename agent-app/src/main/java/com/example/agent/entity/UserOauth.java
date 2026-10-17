package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth 第三方登录绑定 t_user_oauth
 */
@Data
@TableName("t_user_oauth")
public class UserOauth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 渠道：github 等 */
    private String channel;

    /** 渠道侧用户唯一标识 */
    private String openId;

    private LocalDateTime createTime;
}