package com.example.agent.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 轻量快速档：qwen3.7-flash，不思考、无工具/MCP、无技能、不支持附件，默认 Agent
 */
@Order(1)
@Component
public class FlashAgentFactory extends AbstractAgentFactory {

    @Value("${agents.flash.model}")
    private String model;

    @Override
    protected String name() {
        return "flash";
    }

    @Override
    protected String displayName() {
        return "Agent Flash";
    }

    @Override
    protected String description() {
        return "轻量快速档，适合简单问答，不支持工具、技能与附件";
    }

    @Override
    protected String modelName() {
        return model;
    }

    @Override
    protected boolean thinkingEnabled() {
        return false;
    }

    @Override
    protected boolean toolsEnabled() {
        return false;
    }

    @Override
    protected boolean skillsEnabled() {
        return false;
    }

    @Override
    protected boolean attachmentsEnabled() {
        return false;
    }

    @Override
    protected boolean defaultAgent() {
        return true;
    }
}
