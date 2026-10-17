package com.example.agent.plugin;

/**
 * 聊天插件：用户在输入框开头用 / 选择，后端优先执行。
 * 所有插件统一返回「改写后的用户消息」，随后走原 Agent 流，会话自动持久化。
 */
public interface Plugin {

    /** 唯一标识，前端 /plugins 返回，聊天字段 plugin 使用 */
    String id();

    /** 展示名 */
    String name();

    /** 描述（下拉列表展示） */
    String description();

    /** 冒号后的参数提示语 */
    String placeholder();

    /** 是否启用（由 yml agent.plugins.<id>.enabled 注入） */
    boolean enabled();

    /** 改写用户消息；可在内部调用任意 Service，把查到的数据拼入提示词 */
    String process(PluginContext context);
}