package com.example.agent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据库自由查询工具：表列表、表结构、只读 SQL 查询。
 * 使用当前 MySQL 数据源，无表白名单，仅通过 SqlValidator 做只读/注入校验。
 */
@Service
@RequiredArgsConstructor
public class MysqlQueryTools {
    /**
     * SELECT/WITH 查询未显式 LIMIT 时自动追加的上限，防止结果集过大
     */
    private static final int DEFAULT_ROW_LIMIT = 100;

    private static final Pattern LIMIT_CLAUSE = Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);
    private final JdbcTemplate jdbcTemplate;

    @Tool(name = "list_tables", description = "查询数据库中所有的表及其注释")
    public List<Map<String, Object>> listTables() {
        String sql = "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.tables WHERE TABLE_SCHEMA = DATABASE()";
        return jdbcTemplate.queryForList(sql);
    }

    @Tool(name = "get_table_info", description = "获取指定数据表的详细结构信息（字段、类型、注释等）")
    public List<Map<String, Object>> getTableInfo(
            @ToolParam(name = "tableName", description = "数据表名称") String tableName) {
        if (!SqlValidator.isValidIdentifier(tableName)) {
            throw new IllegalArgumentException("安全限制：非法的表名 " + tableName);
        }
        return jdbcTemplate.queryForList("SHOW FULL COLUMNS FROM " + tableName);
    }

    @Tool(name = "execute_query", description = "执行只读的 SQL 查询语句（SELECT），返回结果集；未加 LIMIT 时默认最多返回 "
            + DEFAULT_ROW_LIMIT + " 行")
    public List<Map<String, Object>> executeQuery(
            @ToolParam(name = "sql", description = "要执行的 SELECT 查询语句") String sql) {
        String validated = SqlValidator.validateReadonlyQuery(sql);
        return jdbcTemplate.queryForList(applyDefaultLimit(validated));
    }

    private String applyDefaultLimit(String sql) {
        boolean selectable = sql.regionMatches(true, 0, "SELECT", 0, 6)
                || sql.regionMatches(true, 0, "WITH", 0, 4);
        if (selectable && !LIMIT_CLAUSE.matcher(sql).find()) {
            return sql + " LIMIT " + DEFAULT_ROW_LIMIT;
        }
        return sql;
    }
}
