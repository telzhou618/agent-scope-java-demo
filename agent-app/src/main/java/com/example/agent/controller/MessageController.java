package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.IdRequest;
import com.example.agent.dto.Result;
import com.example.agent.service.MessageService;
import com.example.agent.service.MessageShareService;
import com.example.agent.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息中心
 */
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope/message")
@Tag(name = "消息中心")
public class MessageController {

    private final MessageService messageService;
    private final MessageShareService messageShareService;

    @GetMapping("/page")
    @Operation(summary = "消息分页列表（倒序，不含正文）")
    public Result<Page<MessageVO>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return Result.okData(messageService.page(StpUtil.getLoginIdAsLong(), page, size));
    }

    @GetMapping("/unreadCount")
    @Operation(summary = "未读消息数（顶栏角标）")
    public Result<Long> unreadCount() {
        return Result.okData(messageService.unreadCount(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/detail")
    @Operation(summary = "消息详情（含正文）")
    public Result<MessageVO> detail(@RequestParam long id) {
        return Result.okData(messageService.detail(StpUtil.getLoginIdAsLong(), id));
    }

    @GetMapping("/shareLink")
    @Operation(summary = "生成分享链接（7 天有效，免登录可访问）")
    public Result<String> shareLink(@RequestParam long id) {
        // 校验消息归属当前用户后才签发链接
        messageService.detail(StpUtil.getLoginIdAsLong(), id);
        return Result.okData(messageShareService.signPath(id, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/shared")
    @Operation(summary = "分享的消息详情（UUID 短链，7 天内免登录）")
    public Result<MessageVO> shared(@RequestParam String t) {
        return Result.okData(messageService.sharedDetail(messageShareService.resolve(t)));
    }

    @PostMapping("/read")
    @Operation(summary = "标记已读")
    public Result<Void> markRead(@RequestBody @Validated IdRequest request) {
        messageService.markRead(StpUtil.getLoginIdAsLong(), request.getId());
        return Result.ok();
    }

    @PostMapping("/delete")
    @Operation(summary = "删除消息")
    @OperationLog("删除消息")
    public Result<Void> delete(@RequestBody @Validated IdRequest request) {
        messageService.delete(StpUtil.getLoginIdAsLong(), request.getId());
        return Result.ok();
    }
}
