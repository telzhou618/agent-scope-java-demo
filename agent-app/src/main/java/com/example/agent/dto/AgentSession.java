package com.example.agent.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSession {

    private String userId;

    private String sessionId;
    /**
     * 摘要
     */
    private String summary;
    /**
     * 首次发送消息时间
     */
    private String timestamp;

    /**
     * 是否置顶
     */
    private boolean pinned;

    /**
     * 会话所属 Agent 标识名（flash/plus/max）
     */
    private String agentName;

}
