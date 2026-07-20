package com.example.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSseEvent {
    private String type;       // thinking | reasoning | text_block | data_block | tool_call | tool_result | answer | done
    private String content;    // 文本增量或完整文本
    private String role;
    private ToolCallInfo toolCall;  // 工具调用信息

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallInfo {
        private String toolCallId;  // 工具调用 ID
        private String toolName;    // 工具名称
        private String toolParams;  // 完整参数（JSON 字符串），tool_call 时使用
        private String toolResults;  // 工具调用结果（JSON 字符串），tool_result 时使用
    }
}

