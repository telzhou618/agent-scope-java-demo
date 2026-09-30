package com.example.agent.service;

import com.example.agent.error.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

@Service
public class DocumentTextExtractor {

    private static final int MAX_CHARS = 100_000;

    private static final Set<String> DOC_EXTS = Set.of("pdf", "doc", "docx", "xls", "xlsx");

    private final DataFormatter cellFormatter = new DataFormatter();

    public boolean isDocument(String ext) {
        return DOC_EXTS.contains(ext);
    }

    public String extract(String name, String ext, byte[] bytes) {
        try {
            String text = switch (ext) {
                case "pdf" -> extractPdf(bytes);
                case "doc" -> extractDoc(bytes);
                case "docx" -> extractDocx(bytes);
                case "xls", "xlsx" -> extractSheet(bytes);
                default -> throw new BizException("不支持的附件类型：" + name);
            };
            if (text == null || text.isBlank()) {
                throw new BizException("文件解析失败：" + name);
            }
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "…" : text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文件解析失败：" + name);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDoc(byte[] bytes) throws IOException {
        try (WordExtractor extractor = new WordExtractor(new ByteArrayInputStream(bytes))) {
            return extractor.getText();
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractSheet(byte[] bytes) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (Sheet sheet : workbook) {
                sb.append('[').append(sheet.getSheetName()).append("]\n");
                for (Row row : sheet) {
                    int last = Math.max(row.getLastCellNum(), 0);
                    for (int i = 0; i < last; i++) {
                        if (i > 0) {
                            sb.append('\t');
                        }
                        sb.append(cellFormatter.formatCellValue(row.getCell(i)));
                    }
                    sb.append('\n');
                }
            }
            return sb.toString();
        }
    }
}
