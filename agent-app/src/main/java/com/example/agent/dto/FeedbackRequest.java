package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息反馈请求")
public class FeedbackRequest {

    @NotBlank(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank(message = "消息ID不能为空")
    @Schema(description = "Assistant消息ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String messageId;

    @Pattern(regexp = "^(up|down)$", message = "feedback 只能是 up 或 down")
    @Schema(description = "反馈：up有帮助 down没帮助，不传或为空表示取消反馈")
    private String feedback;
}
