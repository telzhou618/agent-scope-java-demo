package com.example.agent.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多数据源注册表：启动时按 agent.datasources 配置为每个数据源构建独立 JdbcTemplate，
 * 并预加载对应使用规范（classpath:datasources/{id}/spec.md）。
 * 数据库工具通过 dataSourceId 从这里取连接与规范，不再依赖默认数据源。
 */
@Slf4j
@Component
public class MysqlDatasourceRegistry {

    private final Map<String, DatasourceEntry> entries = new LinkedHashMap<>();

    public MysqlDatasourceRegistry(AgentDatasourceProperties properties) {
        for (DatasourceConfig cfg : properties.getDatasources()) {
            String id = cfg.getId();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("数据源配置缺少 id");
            }
            if (cfg.getUrl() == null || cfg.getUrl().isBlank()) {
                throw new IllegalStateException("数据源 " + id + " 缺少 url");
            }
            if (entries.containsKey(id)) {
                throw new IllegalStateException("数据源 id 重复：" + id);
            }
            JdbcTemplate template = new JdbcTemplate(buildDataSource(cfg));
            String spec = loadSpec(id);
            entries.put(id, new DatasourceEntry(id, cfg.getName(), cfg.getDescription(), template, spec));
            log.info("已注册数据源：{}（{}）", id, cfg.getName());
        }
    }

    private DataSource buildDataSource(DatasourceConfig cfg) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(cfg.getDriverClassName());
        ds.setUrl(cfg.getUrl());
        ds.setUsername(cfg.getUsername());
        ds.setPassword(cfg.getPassword());
        return ds;
    }

    private String loadSpec(String id) {
        try {
            ClassPathResource resource = new ClassPathResource("datasources/" + id + "/spec.md");
            if (!resource.exists()) {
                throw new IllegalStateException("数据源使用规范文件缺失：classpath:" + resource.getPath());
            }
            try (var in = resource.getInputStream()) {
                return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取数据源使用规范失败：" + id, e);
        }
    }

    /**
     * 列出所有数据源元信息（id/名称/描述），不含连接信息与规范内容
     */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DatasourceEntry entry : entries.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.id());
            item.put("name", entry.name());
            item.put("description", entry.description());
            result.add(item);
        }
        return result;
    }

    /**
     * 读取指定数据源的使用规范全文
     */
    public String spec(String id) {
        return require(id).spec();
    }

    /**
     * 取指定数据源的 JdbcTemplate
     */
    public JdbcTemplate template(String id) {
        return require(id).template();
    }

    private DatasourceEntry require(String id) {
        DatasourceEntry entry = id == null ? null : entries.get(id.trim());
        if (entry == null) {
            throw new IllegalArgumentException("未知数据源：" + id + "，可用数据源：" + entries.keySet());
        }
        return entry;
    }

    private record DatasourceEntry(
            String id,
            String name,
            String description,
            JdbcTemplate template,
            String spec) {
    }
}