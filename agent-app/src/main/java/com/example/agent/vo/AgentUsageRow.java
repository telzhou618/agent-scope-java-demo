package com.example.agent.vo;

import lombok.Data;

/**
 * 按 Agent 聚合的单月 token 消耗行（对应 mapper 里的 SELECT 别名）
 */
@Data
public class AgentUsageRow {

    private String agentName;

    private Long tokens;
}