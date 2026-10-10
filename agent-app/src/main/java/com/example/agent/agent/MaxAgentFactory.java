package com.example.agent.agent;

import com.example.agent.tools.DataExportTools;
import com.example.agent.tools.MysqlQueryTools;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 旗舰档：qwen3.7-max，开思考、带工具/MCP、挂技能市场、支持附件，注册数据库查询工具
 */
@Order(3)
@Component
public class MaxAgentFactory extends AbstractAgentFactory {

    @Value("${agents.max.model}")
    private String model;

    @Autowired
    private MysqlQueryTools mysqlQueryTools;

    @Autowired
    private DataExportTools dataExportTools;

    @Override
    protected String name() {
        return "max";
    }

    @Override
    protected String displayName() {
        return "Agent Max";
    }

    @Override
    protected String description() {
        return "旗舰档，开思考，支持 MCP 工具、技能市场与附件";
    }

    @Override
    protected String modelName() {
        return model;
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
        return true;
    }

    @Override
    protected void registerLocalTools(Toolkit toolkit) {
        toolkit.registerTool(mysqlQueryTools);
        toolkit.registerTool(dataExportTools);
    }
}
