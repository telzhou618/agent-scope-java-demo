package com.example.agent.agent;

import com.example.agent.error.BizException;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 注册表：启动时由各档位工厂构建全部 HarnessAgent，按标识名存取
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, HarnessAgent> agents = new LinkedHashMap<>();
    private final Map<String, AgentInfo> infos = new LinkedHashMap<>();
    private final String defaultAgentName;

    public AgentRegistry(List<AbstractAgentFactory> factories) {
        String defaultName = null;
        for (AbstractAgentFactory factory : factories) {
            AgentInfo info = factory.agentInfo();
            agents.put(info.getName(), factory.build());
            infos.put(info.getName(), info);
            if (info.isDefaultAgent()) {
                defaultName = info.getName();
            }
        }
        this.defaultAgentName = defaultName;
        log.info("已注册 Agent: {}, 默认: {}", agents.keySet(), defaultAgentName);
    }

    /**
     * 按标识名取 Agent，未知名称抛业务异常
     */
    public HarnessAgent getAgent(String name) {
        HarnessAgent agent = agents.get(name);
        if (agent == null) {
            throw new BizException("未知 Agent: " + name);
        }
        return agent;
    }

    /**
     * 按标识名取 Agent 元信息，未知名称抛业务异常
     */
    public AgentInfo getAgentInfo(String name) {
        AgentInfo info = infos.get(name);
        if (info == null) {
            throw new BizException("未知 Agent: " + name);
        }
        return info;
    }

    /**
     * 全部档位元信息
     */
    public List<AgentInfo> listAgents() {
        return List.copyOf(infos.values());
    }

    /**
     * 默认 Agent 标识名（flash）
     */
    public String getDefaultAgentName() {
        return defaultAgentName;
    }
}
