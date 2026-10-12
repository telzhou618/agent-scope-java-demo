package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.annotation.OperationLog;
import com.example.agent.dto.IdRequest;
import com.example.agent.dto.Result;
import com.example.agent.dto.ScheduleToggleRequest;
import com.example.agent.service.ScheduleService;
import com.example.agent.vo.ScheduledTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务管理：任务由 Agent 对话创建（create_schedule 工具），此处仅查看/启停/删除
 */
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope/schedule")
@Tag(name = "定时任务")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/list")
    @Operation(summary = "我的定时任务列表")
    public Result<List<ScheduledTaskVO>> list() {
        return Result.okData(scheduleService.listForUser(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/toggle")
    @Operation(summary = "启用/停用任务")
    @OperationLog("启停定时任务")
    public Result<Void> toggle(@RequestBody @Validated ScheduleToggleRequest request) {
        scheduleService.toggle(StpUtil.getLoginIdAsLong(), request.getId(), request.getStatus());
        return Result.ok();
    }

    @PostMapping("/delete")
    @Operation(summary = "删除任务")
    @OperationLog("删除定时任务")
    public Result<Void> delete(@RequestBody @Validated IdRequest request) {
        scheduleService.delete(StpUtil.getLoginIdAsLong(), request.getId());
        return Result.ok();
    }
}
