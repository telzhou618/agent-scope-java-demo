package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent 聊天请求")
public class AgentChatRequest {

    @NotBlank(message = "用户消息不能为空")
    @Schema(description = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @NotBlank(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;
}
