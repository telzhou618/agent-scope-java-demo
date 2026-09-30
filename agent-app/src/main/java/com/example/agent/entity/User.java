package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 t_user
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（登录账号）
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    private String password;

    private String nickname;

    private String email;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 状态：1 正常，0 禁用
     */
    private Integer status;

    private LocalDateTime registerTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
