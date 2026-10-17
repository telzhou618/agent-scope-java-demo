package com.example.agent.tools;

import com.example.agent.entity.Dashboard;
import com.example.agent.service.DashboardService;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据看板工具：访问链接拼接、无法识别用户时拒绝、按用户隔离
 */
@ExtendWith(MockitoExtension.class)
class DashboardToolsTest {

    @Mock
    private DashboardService dashboardService;

    @Test
    void createReturnsUrlWithTokenAndStripsTrailingSlash() {
        Dashboard d = new Dashboard();
        d.setId(1L);
        d.setDkey("abc123");
        d.setName("看板");
        when(dashboardService.create(1L, "看板", "", "<html></html>")).thenReturn(d);

        DashboardTools tools = new DashboardTools(dashboardService, "http://localhost:8082/");
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("1")
                .sessionId("s")
                .put(DashboardTools.CURRENT_TOKEN_KEY, "tok123")
                .build();

        Map<String, Object> result = tools.createDashboard("看板", "", "<html></html>", ctx);

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("http://localhost:8082/dashboard/abc123?token=tok123", result.get("url"));
    }

    @Test
    void createFailsWithoutUser() {
        DashboardTools tools = new DashboardTools(dashboardService, "http://localhost:8082");

        Map<String, Object> result = tools.createDashboard("看板", "", "<html></html>", null);

        assertEquals(Boolean.FALSE, result.get("success"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    void listFiltersByOwner() {
        when(dashboardService.listForUser(7L)).thenReturn(List.of());

        DashboardTools tools = new DashboardTools(dashboardService, "http://localhost:8082");
        RuntimeContext ctx = RuntimeContext.builder().userId("7").sessionId("s").build();

        Map<String, Object> result = tools.listDashboards(ctx);

        assertEquals(Boolean.TRUE, result.get("success"));
        verify(dashboardService).listForUser(7L);
    }
}