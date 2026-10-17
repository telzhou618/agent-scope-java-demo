package com.example.agent.service;

import com.example.agent.datasource.MysqlDatasourceRegistry;
import com.example.agent.entity.Dashboard;
import com.example.agent.error.BizException;
import com.example.agent.mapper.DashboardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据看板服务：代码校验、访问 key、归属隔离、查询钳制与只读校验
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardMapper dashboardMapper;

    @Mock
    private MysqlDatasourceRegistry datasourceRegistry;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void createRequiresHtmlCode() {
        BizException e = assertThrows(BizException.class,
                () -> dashboardService.create(1L, "看板", "描述", "<div>没有 html 标签</div>"));
        assertTrue(e.getMessage().contains("<html"));
        verifyNoInteractions(dashboardMapper);
    }

    @Test
    void createGeneratesUuidKeyWithoutDash() {
        when(dashboardMapper.insert(any(Dashboard.class))).thenReturn(1);

        Dashboard d = dashboardService.create(1L, "看板", "", "<!DOCTYPE html><html></html>");

        assertNotNull(d.getDkey());
        assertTrue(d.getDkey().matches("[0-9a-f]{32}"));
        assertFalse(d.getDkey().contains("-"));
    }

    @Test
    void updateRejectsOtherUser() {
        Dashboard other = new Dashboard();
        other.setId(9L);
        other.setUserId(2L);
        when(dashboardMapper.selectById(9L)).thenReturn(other);

        assertThrows(BizException.class, () -> dashboardService.updateOwned(1L, 9L, "x", null, null));
        verify(dashboardMapper, never()).updateById(any(Dashboard.class));
    }

    @Test
    void queryAppendsLimitAndExecutionHint() {
        when(datasourceRegistry.template("ds1")).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of());

        dashboardService.executeQuery("ds1", "SELECT * FROM t");

        verify(jdbcTemplate).queryForList("SELECT /*+ MAX_EXECUTION_TIME(10000) */ * FROM t LIMIT 100");
    }

    @Test
    void queryClampsOversizedLimit() {
        when(datasourceRegistry.template("ds1")).thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of());

        dashboardService.executeQuery("ds1", "SELECT * FROM t LIMIT 500");

        verify(jdbcTemplate).queryForList("SELECT /*+ MAX_EXECUTION_TIME(10000) */ * FROM t LIMIT 100");
    }

    @Test
    void queryRejectsWriteSql() {
        BizException e = assertThrows(BizException.class,
                () -> dashboardService.executeQuery("ds1", "DELETE FROM t"));
        assertTrue(e.getMessage().contains("只读"));
        verifyNoInteractions(datasourceRegistry);
    }
}