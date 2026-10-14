package com.example.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.entity.UserFile;
import com.example.agent.error.BizException;
import com.example.agent.mapper.UserFileMapper;
import com.example.agent.storage.FileStorage;
import com.example.agent.vo.UserFileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储门面：类型 / 大小 / key 校验后委托给 FileStorage（阿里云 OSS），
 * 并维护 t_file 元数据。不再落本地磁盘。
 */
@Slf4j
@Service
public class FileStorageService {

    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024;

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
    private final UserFileMapper userFileMapper;
    private final String publicBaseUrl;

    public FileStorageService(FileStorage storage, UserFileMapper userFileMapper,
                              @Value("${agent.storage.oss.public-base-url:https://cf-resources.sanguosha.cn}") String publicBaseUrl) {
        this.storage = storage;
        this.userFileMapper = userFileMapper;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * 上传聊天附件并记录文件元数据；返回的 id 为存储对象名（不含 key 前缀）
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

        UserFile record = new UserFile();
        record.setUserId(Long.valueOf(userId));
        record.setFileName(name);
        record.setExt(ext);
        record.setSize(file.getSize());
        record.setOssKey(key);
        record.setContentType(mediaType(ext));
        record.setSessionId(sessionId);
        record.setCreateTime(LocalDateTime.now());
        userFileMapper.insert(record);
        return new ChatAttachment(id, name, ext, file.getSize());
    }

    /**
     * 上传头像：公开读对象，返回固定 CDN 地址（不签名、不过期）
     */
    public String uploadAvatar(MultipartFile file, long userId) {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || name == null || name.isBlank()) {
            throw new BizException("请选择要上传的图片");
        }
        String ext = extOf(name);
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BizException("仅支持 png / jpg / jpeg / webp / gif / bmp 图片");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BizException("头像图片不能超过 5MB");
        }
        String key = "avatars/" + userId + "-" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            storage.putPublic(key, in, file.getSize(), mediaType(ext));
        } catch (IOException e) {
            throw new BizException("头像上传失败");
        }
        return publicBaseUrl + "/" + key;
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
     * 导出文件写 OSS 并返回签名下载地址（导出文件不进入 t_file）
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

    /**
     * 我的文件分页列表（含新生成的签名下载地址）
     */
    public Page<UserFileVO> pageMine(long userId, long page, long size) {
        Page<UserFile> filePage = userFileMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getUserId, userId)
                        .orderByDesc(UserFile::getId));
        Page<UserFileVO> voPage = new Page<>(filePage.getCurrent(), filePage.getSize(), filePage.getTotal());
        voPage.setRecords(filePage.getRecords().stream()
                .map(f -> UserFileVO.builder()
                        .id(f.getId())
                        .fileName(f.getFileName())
                        .ext(f.getExt())
                        .size(f.getSize())
                        .createTime(f.getCreateTime())
                        .downloadUrl(storage.presignedGetUrl(f.getOssKey()))
                        .build())
                .toList());
        return voPage;
    }

    /**
     * 删除文件：校验归属后删除 OSS 对象与元数据
     */
    public void deleteMine(long userId, long id) {
        UserFile file = userFileMapper.selectById(id);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        storage.delete(file.getOssKey());
        userFileMapper.deleteById(id);
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