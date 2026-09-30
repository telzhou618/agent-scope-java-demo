package com.example.agent.service;

import com.example.agent.dto.ChatAttachment;
import com.example.agent.error.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    public ChatAttachment store(MultipartFile file, String userId) {
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
            Path dir = rootDir.resolve(userId);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(id));
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }
        return new ChatAttachment(id, name, ext, file.getSize());
    }

    public byte[] readBytes(String userId, String id) {
        if (id.indexOf('/') >= 0 || id.indexOf('\\') >= 0 || id.contains("..")) {
            throw new BizException("附件标识无效");
        }
        String ext = extOf(id);
        if (!isAllowed(ext)) {
            throw new BizException("不支持的附件类型：" + ext);
        }
        Path path = rootDir.resolve(userId).resolve(id).normalize();
        if (!path.startsWith(rootDir)) {
            throw new BizException("附件路径无效");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BizException("附件读取失败");
        }
    }

    public boolean isImage(String ext) {
        return IMAGE_EXTS.contains(ext);
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
