package com.example.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件消息回显元数据：随用户消息 metadata["plugin"] 落库，前端直接取 name/args 渲染，无需再查插件列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PluginMeta {

    private String id;

    private String name;

    private String description;

    /** 用户输入的原始消息（参数） */
    private String args;
}