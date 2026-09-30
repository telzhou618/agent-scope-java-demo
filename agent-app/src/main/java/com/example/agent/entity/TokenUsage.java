package com.example.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * token 消耗记录 t_token_usage
 */
@Data
@TableName("t_token_usage")
public class TokenUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String agentName;

    private String modelName;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer cachedTokens;

    private Double durationSeconds;

    private BigDecimal cost;

    private String replyId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
