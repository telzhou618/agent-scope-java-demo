package com.example.agent.tools;

import com.example.agent.datasource.MysqlDatasourceRegistry;
import com.example.agent.service.FileStorageService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据导出工具：在指定数据源执行只读 SELECT，将结果导出为 CSV 上传到 OSS 并返回签名下载链接
 */
@Slf4j
@Service
public class DataExportTools {

    private static final int MAX_ROWS = 20000;

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MysqlDatasourceRegistry registry;
    private final FileStorageService fileStorageService;

    public DataExportTools(MysqlDatasourceRegistry registry, FileStorageService fileStorageService) {
        this.registry = registry;
        this.fileStorageService = fileStorageService;
    }

    @Tool(name = "export_sql_to_csv", description = "在指定数据源执行 SELECT 查询并将结果导出为 CSV 文件，返回下载链接；当用户要求导出/下载数据时使用。执行前先用 read_data_source_spec 读取该数据源使用规范。")
    public Map<String, Object> exportSqlToCsv(
            @ToolParam(name = "dataSourceId", description = "数据源 id，来自 list_data_sources") String dataSourceId,
            @ToolParam(name = "sql", description = "SELECT 查询语句，最多导出 " + MAX_ROWS + " 行") String sql,
            @ToolParam(name = "fileName", required = false, description = "导出文件名（不含扩展名），可为空") String fileName) {
        try {
            List<Map<String, Object>> rows = registry.template(dataSourceId).queryForList(SqlValidator.validateReadonlyQuery(sql));
            if (rows.isEmpty()) {
                return error("查询结果为空，未生成文件");
            }
            boolean truncated = rows.size() > MAX_ROWS;
            if (truncated) {
                rows = rows.subList(0, MAX_ROWS);
            }

            String storedName = buildStoredName(fileName);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeCsv(rows, out);
            String downloadUrl = fileStorageService.putExport(storedName, out.toByteArray());

            log.info("SQL导出成功：{}，共 {} 条", storedName, rows.size());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileName", storedName);
            result.put("rowCount", rows.size());
            result.put("truncated", truncated);
            result.put("downloadUrl", downloadUrl);
            return result;
        } catch (Exception e) {
            log.error("导出异常", e);
            return error("导出失败: " + e.getMessage());
        }
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }

    /**
     * 存储文件名：用户给的名称做安全清洗（去路径字符），拼时间戳和随机后缀
     */
    private String buildStoredName(String fileName) {
        String base = (fileName == null || fileName.isBlank()) ? "数据导出" : fileName.trim();
        base = base.replaceAll("[/\\\\:*?\"<>|]", "_");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return base + "_" + ts + "_" + rand + ".csv";
    }

    /**
     * 写 CSV：UTF-8 + BOM（兼容 Excel 直接打开），首行列名
     */
    private void writeCsv(List<Map<String, Object>> rows, OutputStream out) throws IOException {
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(0xFEFF); // BOM，兼容 Excel 直接打开
            writer.write(columns.stream().map(this::escape).collect(Collectors.joining(",")));
            writer.write("\n");
            for (Map<String, Object> row : rows) {
                writer.write(columns.stream()
                        .map(col -> escape(formatValue(row.get(col))))
                        .collect(Collectors.joining(",")));
                writer.write("\n");
            }
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATETIME);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DATETIME);
        }
        if (value instanceof LocalDate ld) {
            return ld.format(DATE);
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATETIME);
        }
        return value.toString();
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}