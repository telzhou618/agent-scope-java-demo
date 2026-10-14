package com.example.agent.service;

import com.example.agent.dto.ChatAttachment;
import com.example.agent.error.BizException;
import com.example.agent.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储门面：类型 / 大小 / key 校验后委托给 FileStorage（阿里云 OSS）。
 * 不再落本地磁盘。
 */
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

    private final FileStorage storage;

    public FileStorageService(FileStorage storage) {
        this.storage = storage;
    }

    /**
     * 上传聊天附件；返回的 id 为存储对象名（不含 key 前缀）
     */
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
        String key = keyFor(userId, sessionId, id);
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), mediaType(ext));
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }
        return new ChatAttachment(id, name, ext, file.getSize());
    }

    /**
     * 附件字节（非图片：文本/文档提取用）
     */
    public byte[] readBytes(String userId, String sessionId, String id) {
        return storage.get(keyFor(userId, sessionId, id));
    }

    /**
     * 附件签名下载地址（图片给 LLM 用）
     */
    public String presignedUrl(String userId, String sessionId, String id) {
        return storage.presignedGetUrl(keyFor(userId, sessionId, id));
    }

    /**
     * 删除会话：级联删除该会话下所有附件
     */
    public void deleteForSession(String userId, String sessionId) {
        requireSessionId(sessionId);
        storage.deletePrefix("chat/" + userId + "/" + sessionId + "/");
    }

    /**
     * 导出文件写 OSS 并返回签名下载地址
     */
    public String putExport(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0 || fileName.contains("..")) {
            throw new BizException("文件名无效");
        }
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".csv") || lower.endsWith(".docx"))) {
            throw new BizException("文件名无效");
        }
        String contentType = lower.endsWith(".docx")
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "text/csv";
        String key = "exports/" + fileName;
        try (InputStream in = new ByteArrayInputStream(content)) {
            storage.put(key, in, content.length, contentType);
        } catch (IOException ignore) {
            // ByteArrayInputStream 不会抛出 IOException
        }
        return storage.presignedGetUrl(key);
    }

    public boolean isImage(String ext) {
        return IMAGE_EXTS.contains(ext);
    }

    public String mediaType(String ext) {
        return MEDIA_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private String keyFor(String userId, String sessionId, String id) {
        requireSessionId(sessionId);
        if (id == null || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0 || id.contains("..")) {
            throw new BizException("附件标识无效");
        }
        String ext = extOf(id);
        if (!isAllowed(ext)) {
            throw new BizException("不支持的附件类型：" + ext);
        }
        return "chat/" + userId + "/" + sessionId + "/" + id;
    }

    private void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()
                || sessionId.indexOf('/') >= 0 || sessionId.indexOf('\\') >= 0 || sessionId.contains("..")) {
            throw new BizException("会话ID无效");
        }
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