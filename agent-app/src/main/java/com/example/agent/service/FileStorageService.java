package com.example.agent.service;

import com.example.agent.dto.ChatAttachment;
import com.example.agent.error.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final long MAX_SIZE = 10 * 1024 * 1024;

    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");
    private static final Set<String> TEXT_EXTS = Set.of("txt", "md", "csv", "json", "log", "xml", "yaml", "yml",
            "html", "sql", "java", "py", "js", "ts", "vue", "css", "c", "cpp", "h", "go", "rs", "sh",
            "properties", "ini", "toml");
    private static final Set<String> DOC_EXTS = Set.of("pdf", "doc", "docx", "xls", "xlsx");

    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "bmp", "image/bmp"
    );

    private final Path rootDir;

    public FileStorageService(@Value("${agent.upload.dir}") String uploadDir) {
        this.rootDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public ChatAttachment store(MultipartFile file, String userId, String sessionId) {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || name.isBlank()) {
            throw new BizException("请选择要上传的文件");
        }
        String ext = extOf(name);
        if (!isAllowed(ext)) {
            throw new BizException("不支持的文件类型：" + ext);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("文件大小不能超过 10MB");
        }
        String id = UUID.randomUUID() + "." + ext;
        try {
            Path dir = sessionDir(userId, sessionId);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(id));
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }
        return new ChatAttachment(id, name, ext, file.getSize());
    }

    public byte[] readBytes(String userId, String sessionId, String id) {
        if (id.indexOf('/') >= 0 || id.indexOf('\\') >= 0 || id.contains("..")) {
            throw new BizException("附件标识无效");
        }
        String ext = extOf(id);
        if (!isAllowed(ext)) {
            throw new BizException("不支持的附件类型：" + ext);
        }
        Path path = sessionDir(userId, sessionId).resolve(id).normalize();
        if (!path.startsWith(rootDir)) {
            throw new BizException("附件路径无效");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BizException("附件读取失败");
        }
    }

    /**
     * 级联删除会话附件目录，路径穿越校验保证只删预期目录；删除失败仅记日志不影响主流程
     */
    public void deleteForSession(String userId, String sessionId) {
        Path dir = sessionDir(userId, sessionId).normalize();
        if (!dir.startsWith(rootDir) || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            log.warn("删除会话附件目录失败, dir={}", dir, e);
        }
    }

    /**
     * 会话附件目录：<root>/<userId>/<sessionId>，sessionId 来自客户端需防路径穿越
     */
    private Path sessionDir(String userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()
                || sessionId.indexOf('/') >= 0 || sessionId.indexOf('\\') >= 0 || sessionId.contains("..")) {
            throw new BizException("会话ID无效");
        }
        return rootDir.resolve(userId).resolve(sessionId);
    }

    public boolean isImage(String ext) {
        return IMAGE_EXTS.contains(ext);
    }

    /**
     * 数据导出目录：<root>/exports
     */
    public Path exportsDir() {
        return rootDir.resolve("exports");
    }

    /**
     * 校验并返回导出文件路径，防路径穿越，只允许 CSV
     */
    public Path exportFile(String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0 || fileName.contains("..")
                || !fileName.toLowerCase().endsWith(".csv")) {
            throw new BizException("文件名无效");
        }
        Path path = exportsDir().resolve(fileName).normalize();
        if (!path.startsWith(exportsDir().normalize()) || !Files.exists(path)) {
            throw new BizException("文件不存在或已过期");
        }
        return path;
    }

    public String mediaType(String ext) {
        return MEDIA_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private boolean isAllowed(String ext) {
        return IMAGE_EXTS.contains(ext) || TEXT_EXTS.contains(ext) || DOC_EXTS.contains(ext);
    }

    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BizException("文件缺少扩展名");
        }
        return name.substring(dot + 1).toLowerCase();
    }
}
