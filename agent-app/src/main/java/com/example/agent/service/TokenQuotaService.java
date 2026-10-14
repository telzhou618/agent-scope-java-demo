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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * token 月度限额：请求前校验 + 本月用量统计
 */
@Service
@RequiredArgsConstructor
public class TokenQuotaService {

    private final UserMapper userMapper;
    private final TokenUsageMapper tokenUsageMapper;
    private final AgentRegistry agentRegistry;
    private final AgentPermissionService agentPermissionService;

    /**
     * 校验指定用户当月是否已用完指定 Agent 的 token 额度；管理员、未配置限额（0/null）时不限
     */
    public void checkQuota(long userId, String agentName) {
        User user = userMapper.selectById(userId);
        if (user != null && user.getIsAdmin() != null && user.getIsAdmin() == 1) {
            return;
        }
        AgentInfo info = agentRegistry.getAgentInfo(agentName);
        Long quota = info.getMonthlyTokenQuota();
        if (quota == null || quota <= 0) {
            return;
        }
        long used = usedTokens(userId, agentName);
        if (used >= quota) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "本月「" + info.getDisplayName() + "」token 额度已用尽（已用 " + used + " / " + quota + "）");
        }
    }

    /**
     * 本月用量：总量 + 每个可访问 Agent 的限额与消耗（零消耗的 Agent 也列出）
     */
    public MonthlyQuotaVO monthlyUsage(long userId) {
        LocalDateTime start = monthStart();
        Map<String, Long> usedByAgent = tokenUsageMapper.sumCurrentMonthByAgent(userId, start).stream()
                .collect(Collectors.toMap(AgentUsageRow::getAgentName,
                        row -> row.getTokens() == null ? 0L : row.getTokens(),
                        Long::sum));

        List<MonthlyQuotaVO.AgentItem> agents = agentPermissionService.filterAgents(userId).stream()
                .map(info -> {
                    MonthlyQuotaVO.AgentItem item = new MonthlyQuotaVO.AgentItem();
                    item.setName(info.getName());
                    item.setDisplayName(info.getDisplayName());
                    item.setQuotaTokens(info.getMonthlyTokenQuota());
                    item.setUsedTokens(usedByAgent.getOrDefault(info.getName(), 0L));
                    return item;
                })
                .toList();

        long total = agents.stream().mapToLong(MonthlyQuotaVO.AgentItem::getUsedTokens).sum();

        MonthlyQuotaVO vo = new MonthlyQuotaVO();
        vo.setMonth(YearMonth.now().toString());
        vo.setTotalTokens(total);
        vo.setAgents(agents);
        return vo;
    }

    private long usedTokens(long userId, String agentName) {
        return tokenUsageMapper.sumCurrentMonthByAgent(userId, monthStart()).stream()
                .filter(row -> agentName.equals(row.getAgentName()))
                .mapToLong(row -> row.getTokens() == null ? 0L : row.getTokens())
                .sum();
    }

    private LocalDateTime monthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }
}