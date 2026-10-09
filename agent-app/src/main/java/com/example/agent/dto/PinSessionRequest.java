package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "置顶/取消置顶会话请求")
public class PinSessionRequest {

    @NotBlank(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @Schema(description = "是否置顶", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean pinned;
}
