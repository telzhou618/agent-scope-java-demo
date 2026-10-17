package com.example.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.dto.Result;
import com.example.agent.service.FileStorageService;
import com.example.agent.vo.UserFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天文件：上传走 OSS，下载统一走 OSS 签名链接（见 FileStorageService）。
 * 权限：仅拥有「上传文件」权限的用户可访问。
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope/files")
@SaCheckPermission("upload_file")
@Tag(name = "聊天文件")
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "上传聊天附件")
    @PostMapping("/upload")
    @OperationLog("上传文件")
    public Result<ChatAttachment> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("sessionId") String sessionId) {
        String userId = String.valueOf(StpUtil.getLoginIdAsLong());
        return Result.okData(fileStorageService.store(file, userId, sessionId));
    }

    @Operation(summary = "我的文件列表（分页）")
    @GetMapping("/my/list")
    public Result<Page<UserFileVO>> myFiles(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.okData(fileStorageService.pageMine(StpUtil.getLoginIdAsLong(), page, size));
    }

    @Operation(summary = "删除我的文件")
    @DeleteMapping("/{id}")
    @OperationLog("删除文件")
    public Result<Void> delete(@PathVariable Long id) {
        fileStorageService.deleteMine(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }
}