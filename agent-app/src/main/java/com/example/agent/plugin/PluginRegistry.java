package com.example.agent.plugin;

import com.example.agent.vo.PluginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件注册表：自动收集所有 Plugin 实现类，按 enabled 过滤，提供 id→Plugin 查找与元信息列表
 */
@Slf4j
@Component
public class PluginRegistry {

    private final Map<String, Plugin> plugins = new LinkedHashMap<>();

    public PluginRegistry(List<Plugin> pluginList) {
        for (Plugin plugin : pluginList) {
            if (!plugin.enabled()) {
                log.info("插件已禁用：{}（{}）", plugin.id(), plugin.name());
                continue;
            }
            if (plugins.containsKey(plugin.id())) {
                throw new IllegalStateException("插件 id 重复：" + plugin.id());
            }
            plugins.put(plugin.id(), plugin);
            log.info("已注册插件：{}（{}）", plugin.id(), plugin.name());
        }
    }

    /** 查找插件；未知 id 抛 IllegalArgumentException */
    public Plugin get(String id) {
        Plugin plugin = id == null ? null : plugins.get(id.trim());
        if (plugin == null) {
            throw new IllegalArgumentException("未知插件：" + id + "，可用插件：" + plugins.keySet());
        }
        return plugin;
    }

    /** 插件元信息列表（不含 enabled/内部实现） */
    public List<PluginVO> list() {
        return plugins.values().stream()
                .map(p -> PluginVO.builder()
                        .id(p.id())
                        .name(p.name())
                        .description(p.description())
                        .placeholder(p.placeholder())
                        .build())
                .toList();
    }
}