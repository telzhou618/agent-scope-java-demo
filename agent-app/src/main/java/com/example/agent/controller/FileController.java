package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.dto.Result;
import com.example.agent.error.BizException;
import com.example.agent.service.DownloadSignService;
import com.example.agent.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope/files")
@Tag(name = "聊天文件")
public class FileController {

    private final FileStorageService fileStorageService;

    private final DownloadSignService downloadSignService;

    @Operation(summary = "上传聊天附件")
    @PostMapping("/upload")
    @OperationLog("上传文件")
    public Result<ChatAttachment> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("sessionId") String sessionId) {
        String userId = String.valueOf(StpUtil.getLoginIdAsLong());
        return Result.okData(fileStorageService.store(file, userId, sessionId));
    }

    @Operation(summary = "下载数据导出文件（签名链接，有效期内免登录）")
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName,
                                             @RequestParam long expires,
                                             @RequestParam String sign) {
        if (!downloadSignService.verify(fileName, expires, sign)) {
            throw new BizException("下载链接无效或已过期");
        }
        Path path = fileStorageService.exportFile(fileName);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(path));
    }
}
