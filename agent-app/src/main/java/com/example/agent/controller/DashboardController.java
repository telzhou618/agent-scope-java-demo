package com.example.agent.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.QueryRequest;
import com.example.agent.dto.Result;
import com.example.agent.entity.Dashboard;
import com.example.agent.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据看板：独立页面出口（token 校验）+ 通用数据查询接口
 */
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 独立看板页面：/dashboard/** 已从 Header 登录拦截器放行，这里用 URL 里的 token 手动校验
     */
    @GetMapping(value = "/dashboard/{key}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable("key") String key,
                                       @RequestParam(value = "token", required = false) String token) {
        if (!validToken(token)) {
            return ResponseEntity.status(401)
                    .contentType(MediaType.TEXT_HTML)
                    .body(pageDoc("链接已失效", "token 缺失或已过期，请回到应用内重新打开数据看板链接。"));
        }
        Dashboard dashboard = dashboardService.findByKey(key);
        if (dashboard == null) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(pageDoc("看板不存在", "该数据看板已被删除，或链接有误。"));
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(dashboard.getCode());
    }

    /**
     * 通用数据查询接口：数据源 ID + 只读 SQL，结果 ≤100 行、执行 ≤10s
     */
    @PostMapping("/agent/scope/query")
    public Result<List<Map<String, Object>>> query(@RequestBody @Validated QueryRequest request) {
        return Result.okData(dashboardService.executeQuery(request.getDatasourceId(), request.getSql()));
    }

    private boolean validToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return StpUtil.getLoginIdByToken(token) != null;
        } catch (NotLoginException e) {
            return false;
        }
    }

    private String pageDoc(String title, String message) {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>" + title
                + "</title></head><body style=\"font-family:system-ui;padding:40px;color:#333\">"
                + "<h2>" + title + "</h2><p>" + message + "</p></body></html>";
    }
}