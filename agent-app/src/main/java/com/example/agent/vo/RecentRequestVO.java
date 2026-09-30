package com.example.agent.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 最近请求记录
 */
@Data
public class RecentRequestVO {

    private String sessionId;

    /**
     * 会话标题，未生成时回退为 sessionId
     */
    private String sessionTitle;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 输入 token 数（原始值）
     */
    private Long inputTokens;

    /**
     * 输出 token 数（原始值）
     */
    private Long outputTokens;

    /**
     * 调用耗时（秒）
     */
    private Double durationSeconds;

    /**
     * 费用（元）
     */
    private BigDecimal cost;

    /**
     * 请求时间
     */
    private LocalDateTime createTime;
}
