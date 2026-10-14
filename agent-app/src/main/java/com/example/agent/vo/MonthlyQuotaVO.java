package com.example.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 本月 token 限额视图：总量 + 每个 Agent 的限额与消耗
 */
@Data
public class MonthlyQuotaVO {

    private String month;

    private long totalTokens;

    private List<AgentItem> agents;

    @Data
    public static class AgentItem {
        private String name;
        private String displayName;
        private String avatar;
        /** 0 = 不限 */
        private Long quotaTokens;
        private long usedTokens;
    }
}