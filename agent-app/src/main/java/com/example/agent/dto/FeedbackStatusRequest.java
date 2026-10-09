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
}
