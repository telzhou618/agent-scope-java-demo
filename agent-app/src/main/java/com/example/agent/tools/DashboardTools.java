package com.example.agent.tools;

import com.example.agent.entity.Dashboard;
import com.example.agent.error.BizException;
import com.example.agent.service.DashboardService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板工具：Agent 通过它创建/查询/修改/删除数据看板，按 user_id 隔离。
 * 非 Spring Bean：由 Agent 工厂实例化，注入对外访问基址（用于拼访问链接）。
 */
@Slf4j
public class DashboardTools {

    /**
     * 运行时上下文标记键：当前登录 token（AgentChatService 写入），用于给看板链接拼 token
     */
    public static final String CURRENT_TOKEN_KEY = "token";

    private final DashboardService dashboardService;
    private final String publicBaseUrl;

    public DashboardTools(DashboardService dashboardService, String publicBaseUrl) {
        this.dashboardService = dashboardService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Tool(name = "create_dashboard", description = "创建数据看板：把用户需求做成一个完整独立的单文件 HTML 页面并落库，返回可直接打开的访问地址。"
            + "code 必须是完整单文件 HTML（含 <!DOCTYPE html> 与 <html> 标签、<meta charset>）；第三方库用公共 CDN；"
            + "数据可通过 fetch 调 POST /agent/scope/query（body {datasourceId, sql}，header Authorization: Bearer <token>，token 从页面 URL 的 token 参数读取）获取，也可以硬编码在页面里。")
    public Map<String, Object> createDashboard(
            @ToolParam(name = "name", description = "看板名称，简短可读") String name,
            @ToolParam(name = "description", description = "看板描述") String description,
            @ToolParam(name = "code", description = "完整单文件 HTML 页面代码") String code,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        try {
            Dashboard dashboard = dashboardService.create(userId, name, description, code);
            Map<String, Object> result = ok();
            result.put("id", dashboard.getId());
            result.put("key", dashboard.getDkey());
            result.put("name", dashboard.getName());
            result.put("url", buildUrl(dashboard.getDkey(), context.get(CURRENT_TOKEN_KEY)));
            log.info("看板创建成功, userId={}, key={}", userId, dashboard.getDkey());
            return result;
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "list_dashboards", description = "查询当前用户的数据看板列表（id、key、名称、描述），不含页面代码")
    public Map<String, Object> listDashboards(RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Dashboard d : dashboardService.listForUser(userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("key", d.getDkey());
            item.put("name", d.getName());
            item.put("description", d.getDescription() == null ? "" : d.getDescription());
            items.add(item);
        }
        Map<String, Object> result = ok();
        result.put("dashboards", items);
        return result;
    }

    @Tool(name = "get_dashboard", description = "读取当前用户某个数据看板的完整信息（含页面代码），用于修改前先看现有实现")
    public Map<String, Object> getDashboard(
            @ToolParam(name = "id", description = "看板 ID") long id,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        try {
            Dashboard d = dashboardService.getOwned(userId, id);
            Map<String, Object> result = ok();
            result.put("id", d.getId());
            result.put("key", d.getDkey());
            result.put("name", d.getName());
            result.put("description", d.getDescription() == null ? "" : d.getDescription());
            result.put("code", d.getCode());
            result.put("url", buildUrl(d.getDkey(), context.get(CURRENT_TOKEN_KEY)));
            return result;
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "update_dashboard", description = "修改当前用户的数据看板（名称/描述/页面代码，非空才更新）；code 仍要求完整单文件 HTML")
    public Map<String, Object> updateDashboard(
            @ToolParam(name = "id", description = "看板 ID") long id,
            @ToolParam(name = "name", required = false, description = "新名称，不传不修改") String name,
            @ToolParam(name = "description", required = false, description = "新描述，不传不修改") String description,
            @ToolParam(name = "code", required = false, description = "新页面代码，不传不修改") String code,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        try {
            dashboardService.updateOwned(userId, id, name, description, code);
            return ok();
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "delete_dashboard", description = "删除当前用户的数据看板，删除后不可恢复")
    public Map<String, Object> deleteDashboard(
            @ToolParam(name = "id", description = "看板 ID") long id,
            RuntimeContext context) {
        Long userId = loginUserId(context);
        if (userId == null) {
            return fail("无法识别当前用户");
        }
        try {
            dashboardService.deleteOwned(userId, id);
            return ok();
        } catch (BizException e) {
            return fail(e.getMessage());
        }
    }

    private String buildUrl(String key, Object token) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        if (token == null || token.toString().isBlank()) {
            return base + "/dashboard/" + key;
        }
        return base + "/dashboard/" + key + "?token=" + token;
    }

    private Long loginUserId(RuntimeContext context) {
        if (context == null || context.getUserId() == null) {
            return null;
        }
        try {
            return Long.valueOf(context.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> ok() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return result;
    }

    private Map<String, Object> fail(String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", error);
        return result;
    }
}