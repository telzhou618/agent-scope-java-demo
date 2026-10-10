package com.example.agent.tools;

import com.example.agent.service.DownloadSignService;
import com.example.agent.service.FileStorageService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据分析报告工具：把 Agent 的分析结论和可选的 SQL 结果表生成 Word（docx）报告，返回下载链接
 */
@Slf4j
@Service
public class ReportTools {

    /**
     * 报告数据表的最大行数，避免表格过长
     */
    private static final int MAX_TABLE_ROWS = 500;

    private static final String FONT = "微软雅黑";

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final DownloadSignService downloadSignService;

    public ReportTools(JdbcTemplate jdbcTemplate, FileStorageService fileStorageService,
                       DownloadSignService downloadSignService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
        this.downloadSignService = downloadSignService;
    }

    @Tool(name = "generate_report", description = "生成数据分析报告（Word 文档），包含标题、分析正文和可选的数据表，"
            + "返回下载链接；当用户要求生成报告/周报/月报时使用")
    public Map<String, Object> generateReport(
            @ToolParam(name = "title", description = "报告标题") String title,
            @ToolParam(name = "summary", description = "分析正文，用你的数据分析结论撰写，换行分段") String summary,
            @ToolParam(name = "sql", required = false,
                    description = "可选：只读 SELECT，结果作为数据表附在报告中，最多 " + MAX_TABLE_ROWS + " 行") String sql,
            @ToolParam(name = "fileName", required = false, description = "文件名（不含扩展名），可为空") String fileName) {
        try {
            List<Map<String, Object>> rows = null;
            boolean truncated = false;
            if (sql != null && !sql.isBlank()) {
                rows = jdbcTemplate.queryForList(SqlValidator.validateReadonlyQuery(sql));
                truncated = rows.size() > MAX_TABLE_ROWS;
                if (truncated) {
                    rows = rows.subList(0, MAX_TABLE_ROWS);
                }
            }

            String storedName = buildStoredName(fileName, title);
            Path path = fileStorageService.exportsDir().resolve(storedName);
            Files.createDirectories(path.getParent());
            writeDocx(path, title, summary, rows, truncated);

            log.info("报告生成成功：{}", storedName);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileName", storedName);
            result.put("tableRows", rows == null ? 0 : rows.size());
            result.put("truncated", truncated);
            result.put("downloadUrl", "/agent/scope/files/download/" + storedName
                    + "?" + downloadSignService.signQuery(storedName));
            return result;
        } catch (Exception e) {
            log.error("报告生成异常", e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "报告生成失败: " + e.getMessage());
            return result;
        }
    }

    private void writeDocx(Path path, String title, String summary,
                           List<Map<String, Object>> rows, boolean truncated) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            // 标题
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            titlePara.setSpacingAfter(200);
            style(titlePara.createRun(), 22, true).setText(title);

            // 生成时间
            XWPFParagraph metaPara = doc.createParagraph();
            metaPara.setAlignment(ParagraphAlignment.CENTER);
            metaPara.setSpacingAfter(400);
            XWPFRun metaRun = style(metaPara.createRun(), 10, false);
            metaRun.setColor("888888");
            metaRun.setText("生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            // 分析正文：按换行分段
            for (String line : summary.split("\\n")) {
                String text = line.trim();
                if (text.isEmpty()) {
                    continue;
                }
                XWPFParagraph para = doc.createParagraph();
                para.setSpacingAfter(120);
                // Markdown 风格的 "### 小节标题" 转成加粗小节
                if (text.startsWith("#")) {
                    style(para.createRun(), 14, true).setText(text.replaceFirst("^#+\\s*", ""));
                } else {
                    style(para.createRun(), 12, false).setText(text);
                }
            }

            // 数据表
            if (rows != null && !rows.isEmpty()) {
                XWPFParagraph tableTitle = doc.createParagraph();
                tableTitle.setSpacingBefore(240);
                style(tableTitle.createRun(), 14, true).setText("数据明细");

                List<String> columns = new ArrayList<>(rows.get(0).keySet());
                XWPFTable table = doc.createTable(rows.size() + 1, columns.size());
                table.setWidth("100%");

                XWPFTableRow header = table.getRow(0);
                for (int c = 0; c < columns.size(); c++) {
                    XWPFTableCell cell = header.getCell(c);
                    cell.setColor("E8EEF9");
                    setCellText(cell, columns.get(c), true);
                }
                for (int r = 0; r < rows.size(); r++) {
                    Map<String, Object> row = rows.get(r);
                    XWPFTableRow tableRow = table.getRow(r + 1);
                    for (int c = 0; c < columns.size(); c++) {
                        Object value = row.get(columns.get(c));
                        setCellText(tableRow.getCell(c), value == null ? "" : value.toString(), false);
                    }
                }

                if (truncated) {
                    XWPFParagraph note = doc.createParagraph();
                    XWPFRun noteRun = style(note.createRun(), 10, false);
                    noteRun.setColor("888888");
                    noteRun.setText("注：数据量较大，仅展示前 " + MAX_TABLE_ROWS + " 行。");
                }
            }

            try (OutputStream os = Files.newOutputStream(path)) {
                doc.write(os);
            }
        }
    }

    private XWPFRun style(XWPFRun run, int fontSize, boolean bold) {
        run.setFontFamily(FONT, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(fontSize);
        run.setBold(bold);
        return run;
    }

    private void setCellText(XWPFTableCell cell, String text, boolean header) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        style(para.createRun(), header ? 11 : 10, header).setText(text);
    }

    private String buildStoredName(String fileName, String title) {
        String base = (fileName == null || fileName.isBlank())
                ? (title == null || title.isBlank() ? "数据分析报告" : title.trim())
                : fileName.trim();
        base = base.replaceAll("[/\\\\:*?\"<>|]", "_");
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return base + "_" + ts + "_" + rand + ".docx";
    }
}
