package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "中断会话请求")
public class InterruptRequest {

    @NotBlank(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank(message = "Agent 不能为空")
    @Schema(description = "Agent 标识名（flash/plus/max）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentName;
}
