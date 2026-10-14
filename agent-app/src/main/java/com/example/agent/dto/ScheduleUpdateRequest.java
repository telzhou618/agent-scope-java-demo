package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "定时任务编辑请求")
public class ScheduleUpdateRequest {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "任务标题，不传则不改")
    private String title;

    @Schema(description = "执行 Agent 名，不传则不改")
    private String agentName;

    @Schema(description = "任务内容，不传则不改")
    private String description;

    @Schema(description = "Quartz 6 段 cron 表达式，不传则不改")
    private String cron;
}