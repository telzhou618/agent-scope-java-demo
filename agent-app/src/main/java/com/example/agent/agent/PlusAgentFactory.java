package com.example.agent.agent;

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

    @Override
    protected String name() {
        return "plus";
    }

    @Override
    protected String displayName() {
        return "Agent Plus";
    }

    @Override
    protected String description() {
        return "标准档，开思考，支持 MCP 工具与附件，不支持技能";
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
        return false;
    }

    @Override
    protected boolean attachmentsEnabled() {
        return true;
    }
}
