package com.example.agent.tools;

import com.example.agent.datasource.MysqlDatasourceRegistry;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据源元信息工具：列出可查询数据源、读取某个数据源的使用规范。
 * 执行任何 SQL 查询前，先用 list_data_sources 选定数据源，再用 read_data_source_spec 了解该数据源规范。
 */
@Component
@RequiredArgsConstructor
public class DataSourceTools {

    private final MysqlDatasourceRegistry registry;

    @Tool(name = "list_data_sources", description = "列出当前可查询的数据源（id、名称、简要描述），不含连接信息与使用规范。执行 SQL 查询前先调用它选定数据源 id。")
    public List<Map<String, Object>> listDataSources() {
        return registry.list();
    }

    @Tool(name = "read_data_source_spec", description = "读取指定数据源的使用规范全文（表命名约定、字段说明、注意事项等）。选定数据源后、执行任何查询前必须先调用它了解规范。")
    public String readDataSourceSpec(
            @ToolParam(name = "dataSourceId", description = "数据源 id，来自 list_data_sources 的返回") String dataSourceId) {
        return registry.spec(dataSourceId);
    }
}