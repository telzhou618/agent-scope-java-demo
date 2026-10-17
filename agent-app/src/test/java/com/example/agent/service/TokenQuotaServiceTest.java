package com.example.agent.service;

import com.example.agent.agent.AgentInfo;
import com.example.agent.agent.AgentRegistry;
import com.example.agent.entity.User;
import com.example.agent.error.BizException;
import com.example.agent.error.ErrorCode;
import com.example.agent.mapper.TokenUsageMapper;
import com.example.agent.mapper.UserMapper;
import com.example.agent.vo.AgentUsageRow;
import com.example.agent.vo.MonthlyQuotaVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenQuotaService 配额校验与本月用量统计逻辑单元测试
 */
@ExtendWith(MockitoExtension.class)
class TokenQuotaServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private TokenUsageMapper tokenUsageMapper;

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private AgentPermissionService agentPermissionService;

    @InjectMocks
    private TokenQuotaService tokenQuotaService;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setIsAdmin(0);
        return u;
    }

    private User admin(long id) {
        User u = new User();
        u.setId(id);
        u.setIsAdmin(1);
        return u;
    }

    private AgentInfo info(String name, Long quota) {
        return AgentInfo.builder().name(name).displayName("Agent " + name.substring(0, 1).toUpperCase() + name.substring(1))
                .monthlyTokenQuota(quota).build();
    }

    private AgentUsageRow row(String agent, long tokens) {
        AgentUsageRow r = new AgentUsageRow();
        r.setAgentName(agent);
        r.setTokens(tokens);
        return r;
    }

    @Test
    void adminExempt() {
        when(userMapper.selectById(1L)).thenReturn(admin(1L));

        assertDoesNotThrow(() -> tokenQuotaService.checkQuota(1L, "max"));
    }

    @Test
    void unlimitedQuotaNeverThrows() {
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(agentRegistry.getAgentInfo("max")).thenReturn(info("max", 0L));

        assertDoesNotThrow(() -> tokenQuotaService.checkQuota(2L, "max"));
    }

    @Test
    void underQuotaAllowed() {
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(agentRegistry.getAgentInfo("max")).thenReturn(info("max", 100L));
        when(tokenUsageMapper.sumCurrentMonthByAgent(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(row("max", 30L)));

        assertDoesNotThrow(() -> tokenQuotaService.checkQuota(2L, "max"));
    }

    @Test
    void exactQuotaThrows() {
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(agentRegistry.getAgentInfo("max")).thenReturn(info("max", 100L));
        when(tokenUsageMapper.sumCurrentMonthByAgent(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(row("max", 100L)));

        BizException ex = assertThrows(BizException.class, () -> tokenQuotaService.checkQuota(2L, "max"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    @Test
    void overQuotaThrows() {
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(agentRegistry.getAgentInfo("max")).thenReturn(info("max", 100L));
        when(tokenUsageMapper.sumCurrentMonthByAgent(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(row("max", 150L)));

        BizException ex = assertThrows(BizException.class, () -> tokenQuotaService.checkQuota(2L, "max"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
        assertTrue(ex.getMessage().contains("Agent Max"));
    }

    @Test
    void monthlyUsageIncludesZeroUsageAndTotals() {
        when(agentPermissionService.filterAgents(2L)).thenReturn(List.of(
                info("flash", 100L), info("max", 0L)));
        when(tokenUsageMapper.sumCurrentMonthByAgent(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(row("flash", 30L)));

        MonthlyQuotaVO result = tokenQuotaService.monthlyUsage(2L);

        assertEquals(30L, result.getTotalTokens());
        assertEquals(2, result.getAgents().size());

        MonthlyQuotaVO.AgentItem flash = result.getAgents().get(0);
        assertEquals("flash", flash.getName());
        assertEquals(100L, flash.getQuotaTokens().longValue());
        assertEquals(30L, flash.getUsedTokens());

        MonthlyQuotaVO.AgentItem max = result.getAgents().get(1);
        assertEquals("max", max.getName());
        assertEquals(0L, max.getUsedTokens());
    }

    @Test
    void usesMonthStartWindow() {
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(agentRegistry.getAgentInfo("max")).thenReturn(info("max", 100L));
        when(tokenUsageMapper.sumCurrentMonthByAgent(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(row("max", 30L)));

        tokenQuotaService.checkQuota(2L, "max");

        LocalDateTime expected = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        verify(tokenUsageMapper).sumCurrentMonthByAgent(eq(2L), argThat(start -> start.equals(expected)));
    }
}