package com.example.agent.vo;

import lombok.Data;

/**
 * agentscope_sessions 表的一行原始状态数据（批量查询用）
 */
@Data
public class SessionStateRow {

    private String sessionId;

    private String stateData;
}
