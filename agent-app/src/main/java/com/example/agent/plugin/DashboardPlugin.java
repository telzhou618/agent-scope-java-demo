package com.example.agent.plugin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据看板插件：改写提示词，引导 Agent 加载「数据看板」技能，由技能指引生成独立页面
 */
@Component
public class DashboardPlugin implements Plugin {

    @Value("${agent.plugins.dashboard.enabled:true}")
    private boolean enabled;

    @Override
    public String id() {
        return "dashboard";
    }

    @Override
    public String name() {
        return "数据看板";
    }

    @Override
    public String description() {
        return "把需求做成独立的数据看板页面";
    }

    @Override
    public String placeholder() {
        return "描述你想看的数据、维度和图表";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String process(PluginContext context) {
        return "请创建数据看板：加载「数据看板」技能（dashboard），按技能指引完成以下需求——"
                + context.message();
    }
}