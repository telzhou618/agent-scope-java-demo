package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.Result;
import com.example.agent.dto.UserFeedbackRequest;
import com.example.agent.service.UserFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈（意见反馈入口）
 */
@RestController
@AllArgsConstructor
@RequestMapping("/user/feedback")
@Tag(name = "用户反馈")
public class UserFeedbackController {

    private final UserFeedbackService feedbackService;

    @PostMapping("/submit")
    @Operation(summary = "提交反馈")
    @OperationLog("提交意见反馈")
    public Result<Void> submit(@RequestBody @Validated UserFeedbackRequest request) {
        feedbackService.submit(StpUtil.getLoginIdAsLong(), request);
        return Result.ok();
    }
}
