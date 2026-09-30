package com.example.agent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 聚合查询的单桶结果（对应 mapper 里的 SELECT 别名）
 */
@Data
public class UsageBucketRow {

    private String bucket;

    private Long inputTokens;

    private Long outputTokens;

    private BigDecimal cost;

    private Long requests;
}
