package com.example.agent.config;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Configuration
public class AgentConfig {

    @Value("${mcp.server.url}")
    private String mcpServerUrl;

    @Value("${mcp.server.endpoint}")
    private String mcpEndpoint;

    @Value("${mcp.server.token}")
    private String mcpToken;

    @Value("${spring.ai.dashscope.chat.model}")
    private String modelName;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;


    @Bean
    public AgentStateStore agentStateStore() {
        return new JsonFileAgentStateStore(Path.of(
                System.getProperty("user.home") + "/.act/agent/state"
        ));
    }

    @Bean
    public HarnessAgent harnessAgent() {
        // MCP连接
        McpClientWrapper mcpClientWrapper = McpClientBuilder.create("http-mcp")
                .streamableHttpTransport(mcpServerUrl + mcpEndpoint)
                .header("Authorization", "Bearer " + mcpToken)
                .timeout(Duration.ofSeconds(30))
                .buildAsync()
                .block();
        // 注册工具和 MCP
        Toolkit toolkit = new Toolkit();
        toolkit.registerMcpClient(mcpClientWrapper).block();

        // 主模型
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelName)
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(
                        GenerateOptions.builder()
                                .thinkingBudget(2048)
                                .build())
                .build();
        // 压缩模型，用轻量级低成本的, 推荐 flash 模型
        DashScopeChatModel compactionModel = DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName("qwen3.6-flash")
                .formatter(new DashScopeChatFormatter())
                .build();

        return HarnessAgent.builder()
                // harness 默认工作空间, 存储永久记忆等。
                .workspace(Path.of(System.getProperty("user.home") + "/.agentscope"))
                // 存储历史消息、上下文
                .stateStore(agentStateStore())
                .name("actAgent")
                .sysPrompt("你是一个有用的助手")
                .model(model)
                .toolkit(toolkit)

                // 工具权限一律不验证，危险，生产环境不建议
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .maxIters(5)    // 最大迭代
                .maxRetries(1)  // 工具最大重试次数
                .defaultSessionId("default-session-id")

                // 压缩
                .compaction(CompactionConfig.builder()
                        .model(compactionModel)
                        .triggerMessages(30)     // 30 条触发
                        .keepMessages(10)        // 压缩后保留最近 10 条原文
                        .build())

                // 大工具结果卸载压缩
                .toolResultEviction(ToolResultEvictionConfig.defaults())

                .disableMemoryHooks()            // 停掉 flush + 后台 consolidation，不生成 memory/*.md / MEMORY.md
                .disableMemoryTools()            // 不注册 memory_search / memory_get / session_search 工具.di
                .disableWorkspaceContext()       // 止把 workspace 中的结构化上下文注入 system prompt,system prompt 更干净

                .build();
    }
}
