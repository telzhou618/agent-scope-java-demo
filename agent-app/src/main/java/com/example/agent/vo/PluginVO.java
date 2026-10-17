package com.example.agent.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 插件元信息（供前端 / 下拉）
 */
@Data
@Builder
public class PluginVO {

    private String id;

    private String name;

    private String description;

    private String placeholder;
}