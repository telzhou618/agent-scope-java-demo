package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 处理意见反馈请求
 */
@Data
@Schema(description = "处理意见反馈请求")
public class FeedbackStatusRequest {

    @NotNull(message = "反馈ID不能为空")
    @Schema(description = "反馈ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    @Schema(description = "状态：0待处理 1已处理", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "处理结果（可选，标记已处理时填写）")
    private String result;

    @Schema(description = "标记已处理时是否给提交用户发系统通知（默认 false）")
    private Boolean notify;
}
