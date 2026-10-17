package com.example.agent.plugin;

import com.example.agent.dto.ChatAttachment;

import java.util.List;

/**
 * 插件执行上下文
 *
 * @param userId      登录用户 id（字符串形式）
 * @param agentName   当前档位名
 * @param message     用户输入的原始参数（可含 @ 技能/数据源，原样透传）
 * @param attachments 附件列表
 */
public record PluginContext(
        String userId,
        String agentName,
        String message,
        List<ChatAttachment> attachments) {
}