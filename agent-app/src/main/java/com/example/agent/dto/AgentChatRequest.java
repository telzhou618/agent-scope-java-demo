package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    @Schema(description = "附件列表")
    private List<ChatAttachment> attachments;
}
