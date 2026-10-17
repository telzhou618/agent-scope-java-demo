package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "定时任务启停请求")
public class ScheduleToggleRequest {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "状态不能为空")
    @Schema(description = "1 启用 / 0 停用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
}
