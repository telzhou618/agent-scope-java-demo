package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent 聊天请求")
public class AgentChatRequest {

    @Schema(description = "用户消息，有附件时可为空")
    private String message;

    @NotBlank(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank(message = "Agent 不能为空")
    @Schema(description = "Agent 标识名（flash/plus/max）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentName;

    @Schema(description = "请求ID")
    private String requestId;

    @Schema(description = "技能名（输入框选中的 /skill:<name>），独立传参，不拼进用户消息")
    private String skill;

    @Schema(description = "工具确认结果：响应 require_confirm 事件时携带，确认/拒绝挂起的工具调用")
    private List<ConfirmResultItem> confirmResults;

    @Schema(description = "附件列表")
    private List<ChatAttachment> attachments;

    @Data
    @Schema(description = "单个工具调用的确认结果")
    public static class ConfirmResultItem {
        @Schema(description = "工具调用 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String toolCallId;

        @Schema(description = "工具名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String toolName;

        @Schema(description = "工具入参（原样回传 require_confirm 事件里的 toolInput）")
        private Map<String, Object> toolInput;

        @Schema(description = "ToolUseBlock 原始 content（原样回传 require_confirm 事件里的 toolContent，框架校验需要）")
        private String toolContent;

        @Schema(description = "true 确认执行 / false 拒绝", requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean confirmed;
    }
}
