package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志表 t_operation_log
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户 ID，未登录为 0
     */
    private Long userId;

    private String username;

    /**
     * 操作描述（来自注解 value）
     */
    private String operation;

    /**
     * HTTP 方法
     */
    private String method;

    private String path;

    /**
     * 请求参数 JSON（密码已脱敏）
     */
    private String params;

    private String ip;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * success 或 fail:原因（截断 500）
     */
    private String result;

    private LocalDateTime createTime;
}
