package com.example.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "定时任务新增请求")
public class ScheduleCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "Agent 不能为空")
    @Schema(description = "执行 Agent 名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String agentName;

    @NotBlank(message = "任务内容不能为空")
    @Schema(description = "任务内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @NotBlank(message = "cron 表达式不能为空")
    @Schema(description = "Quartz 6 段 cron 表达式（秒 分 时 日 月 周）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cron;
}