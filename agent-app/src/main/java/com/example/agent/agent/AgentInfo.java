package com.example.agent.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 元信息：通过 /agents 接口返回给前端，用于档位选择与能力展示
 */
@Data
@Builder
@Schema(description = "Agent 元信息")
public class AgentInfo {

    @Schema(description = "Agent 标识名，请求时传此值（flash/plus/max）")
    private String name;

    @Schema(description = "展示名")
    private String displayName;

    @Schema(description = "头像 URL（data URI 或 http 链接）")
    private String avatar;

    @Schema(description = "能力描述")
    private String description;

    @Schema(description = "主模型名")
    private String model;

    @Schema(description = "是否开启思考")
    private boolean thinking;

    @Schema(description = "是否具备工具调用能力")
    private boolean tools;

    @Schema(description = "是否接入 MCP 工具")
    private boolean mcp;

    @Schema(description = "是否支持技能市场")
    private boolean skills;

    @Schema(description = "是否支持附件")
    private boolean attachments;

    @Schema(description = "是否默认 Agent")
    private boolean defaultAgent;
}
