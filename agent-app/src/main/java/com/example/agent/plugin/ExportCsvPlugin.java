package com.example.agent.plugin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 导出 CSV 插件：改写提示词，引导 Agent 使用 export_sql_to_csv 工具
 */
@Component
public class ExportCsvPlugin implements Plugin {

    @Value("${agent.plugins.export-csv.enabled:true}")
    private boolean enabled;

    @Override
    public String id() {
        return "export-csv";
    }

    @Override
    public String name() {
        return "导出 CSV";
    }

    @Override
    public String description() {
        return "把查询结果导出为 CSV 文件";
    }

    @Override
    public String placeholder() {
        return "请输入要导出的数据或查询条件";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String process(PluginContext context) {
        return "请使用数据导出工具（export_sql_to_csv）完成以下需求，必要时先用 list_data_sources 选择数据源："
                + context.message();
    }
}