package com.example.agent.plugin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 创建定时任务插件：改写提示词，引导 Agent 使用 create_schedule 工具
 */
@Component
public class SchedulePlugin implements Plugin {

    @Value("${agent.plugins.schedule.enabled:true}")
    private boolean enabled;

    @Override
    public String id() {
        return "schedule";
    }

    @Override
    public String name() {
        return "创建定时任务";
    }

    @Override
    public String description() {
        return "把自然语言需求转成定时任务";
    }

    @Override
    public String placeholder() {
        return "请输入定期要做的事和执行时机";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String process(PluginContext context) {
        return "请使用定时任务工具（create_schedule）完成以下需求：" + context.message();
    }
}