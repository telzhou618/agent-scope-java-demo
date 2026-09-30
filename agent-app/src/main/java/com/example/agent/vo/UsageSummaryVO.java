package com.example.agent.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用量统计响应：总览卡片 + 分桶数据 + 区间描述
 */
@Data
public class UsageSummaryVO {

    private Totals totals;

    private List<Bucket> buckets;

    private String description;

    @Data
    public static class Totals {
        private BigDecimal cost;
        private Long requests;
        private Long inputTokens;
        private Long outputTokens;
        private Long tokens;
    }

    @Data
    public static class Bucket {
        private String label;
        private Long tokens;
        private Long requests;
        private BigDecimal cost;
    }
}
