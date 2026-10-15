package com.example.agent.plugin;

import com.example.agent.service.TokenQuotaService;
import com.example.agent.vo.MonthlyQuotaVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 查询本月额度插件：先调用用量服务取当月额度，再注入提示词交给 Agent 按写死口径展示
 */
@Component
public class MonthlyQuotaPlugin implements Plugin {

    private final TokenQuotaService quotaService;

    @Value("${agent.plugins.monthly-quota.enabled:true}")
    private boolean enabled;

    public MonthlyQuotaPlugin(TokenQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public String id() {
        return "monthly-quota";
    }

    @Override
    public String name() {
        return "查询本月额度";
    }

    @Override
    public String description() {
        return "查询本月各档位 token 用量与剩余额度";
    }

    @Override
    public String placeholder() {
        return "查询我本月各档位的用量与剩余额度";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String process(PluginContext context) {
        MonthlyQuotaVO vo = quotaService.monthlyUsage(Long.parseLong(context.userId()));
        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户本月的 token 额度情况（").append(vo.getMonth()).append("）：\n");
        sb.append("总用量：").append(vo.getTotalTokens()).append(" tokens\n");
        for (MonthlyQuotaVO.AgentItem item : vo.getAgents()) {
            Long quota = item.getQuotaTokens();
            String quotaText = (quota == null || quota <= 0) ? "不限" : String.valueOf(quota);
            String remainText = (quota == null || quota <= 0)
                    ? "不限"
                    : String.valueOf(Math.max(0, quota - item.getUsedTokens()));
            sb.append("- ").append(item.getDisplayName()).append("（").append(item.getName()).append("）")
                    .append("：已用 ").append(item.getUsedTokens())
                    .append("，总量 ").append(quotaText)
                    .append("，剩余 ").append(remainText).append("\n");
        }
        sb.append("\n请严格依据以上数据，用表格向用户展示各档位的已用、总量（或「不限」）、剩余额度，先给结论再给明细。");
        sb.append("忽略用户输入的其他内容，不要编造数字。");
        return sb.toString();
    }
}