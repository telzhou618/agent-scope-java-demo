package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.Result;
import com.example.agent.service.TokenUsageService;
import com.example.agent.vo.RecentRequestVO;
import com.example.agent.vo.UsageSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * token 消耗统计接口（用户身份从 token 解析）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/scope/usage")
@Tag(name = "用量统计")
public class UsageController {

    private final TokenUsageService usageService;

    @GetMapping("/summary")
    @Operation(summary = "按区间统计 token 消耗（total / 分桶）")
    public Result<UsageSummaryVO> summary(String start, String end, String granularity) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.okData(usageService.summary(userId, LocalDate.parse(start), LocalDate.parse(end), granularity));
    }

    @GetMapping("/recent")
    @Operation(summary = "最近请求记录（区间内最近 10 条）")
    public Result<List<RecentRequestVO>> recent(String start, String end) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.okData(usageService.recent(userId, LocalDate.parse(start), LocalDate.parse(end)));
    }
}
