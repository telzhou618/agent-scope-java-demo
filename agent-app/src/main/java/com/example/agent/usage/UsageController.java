package com.example.agent.usage;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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
}
