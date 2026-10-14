package com.example.agent.agent;

import com.example.agent.tools.DataExportTools;
import com.example.agent.tools.MysqlQueryTools;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 标准档：qwen3.7-plus，开思考、带工具/MCP、支持附件，不挂技能市场
 */
@Order(2)
@Component
public class PlusAgentFactory extends AbstractAgentFactory {

    @Value("${agents.plus.model}")
    private String model;

    @Value("${agents.plus.monthly-token-quota:1000000}")
    private Long monthlyTokenQuota;

    @Autowired
    private MysqlQueryTools mysqlQueryTools;

    @Autowired
    private DataExportTools dataExportTools;

    @Override
    protected String name() {
        return "plus";
    }

    @Override
    protected String displayName() {
        return "Agent Plus";
    }

    @Override
    protected String avatar() {
        return buildAvatar("P", "#34d399", "#059669");
    }

    @Override
    protected String description() {
        return "标准档，开起思考，支持工具、MCP、技能、技能";
    }

    @Override
    protected String modelName() {
        return model;
    }

    @Override
    protected Long monthlyTokenQuota() {
        return monthlyTokenQuota;
    }

    @Override
    protected boolean thinkingEnabled() {
        return true;
    }

    @Override
    protected boolean toolsEnabled() {
        return true;
    }

    @Override
    protected boolean skillsEnabled() {
        return true;
    }

    @Override
    protected boolean attachmentsEnabled() {
        return false;
    }

    @Override
    protected boolean scheduleEnabled() {
        return false;
    }

    @Override
    protected void registerLocalTools(Toolkit toolkit) {
        toolkit.registerTool(mysqlQueryTools);
        toolkit.registerTool(dataExportTools);
    }
}
