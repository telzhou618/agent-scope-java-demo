package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.dto.Result;
import com.example.agent.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope/files")
@Tag(name = "聊天文件")
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "上传聊天附件")
    @PostMapping("/upload")
    public Result<ChatAttachment> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("sessionId") String sessionId) {
        String userId = String.valueOf(StpUtil.getLoginIdAsLong());
        return Result.okData(fileStorageService.store(file, userId, sessionId));
    }
}
