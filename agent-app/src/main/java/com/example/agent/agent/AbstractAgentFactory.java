package com.example.agent.agent;

import com.example.agent.middleware.TokenUsageMiddleware;
import com.example.agent.service.TokenUsageService;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Agent 工厂基类：三档 Agent 的公共构建逻辑（MCP 连接、Toolkit、压缩模型、HarnessAgent 公共配置）
 * 集中在模板方法 build()，子类只声明差异项（主模型、思考开关、MCP 工具、技能、附件）
 */
public abstract class AbstractAgentFactory {

    @Value("${mcp.server.url}")
    private String mcpServerUrl;

    @Value("${mcp.server.endpoint}")
    private String mcpEndpoint;

    @Value("${mcp.server.token}")
    private String mcpToken;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Autowired
    protected AgentStateStore agentStateStore;

    @Autowired
    protected ClasspathSkillRepository skillRepository;

    @Autowired
    protected TokenUsageService tokenUsageService;

    /**
     * Agent 标识名，直接写入 t_token_usage.agent_name
     */
    protected abstract String name();

    /**
     * 展示名，如 "Agent Flash"
     */
    protected abstract String displayName();

    /**
     * 能力描述
     */
    protected abstract String description();

    /**
     * 主模型名
     */
    protected abstract String modelName();

    /**
     * 是否开启思考
     */
    protected abstract boolean thinkingEnabled();

    /**
     * 是否注册 MCP 工具
     */
    protected abstract boolean toolsEnabled();

    /**
     * 是否挂载技能市场
     */
    protected abstract boolean skillsEnabled();

    /**
     * 是否支持附件
     */
    protected abstract boolean attachmentsEnabled();

    /**
     * 是否默认 Agent，仅 flash 覆写为 true
     */
    protected boolean defaultAgent() {
        return false;
    }

    /**
     * 头像：默认给一个品牌渐变的占位头像，后续可按档位接入真实资源
     */
    private static final String DEFAULT_AVATAR =
            "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20viewBox='0%200%20100%20100'%3E"
            + "%3Cdefs%3E%3ClinearGradient%20id='g'%20x1='0'%20y1='0'%20x2='1'%20y2='1'%3E"
            + "%3Cstop%20offset='0%25'%20stop-color='%236366f1'/%3E"
            + "%3Cstop%20offset='100%25'%20stop-color='%23a855f7'/%3E"
            + "%3C/linearGradient%3E%3C/defs%3E"
            + "%3Ccircle%20cx='50'%20cy='50'%20r='50'%20fill='url(%23g)'/%3E"
            + "%3C/svg%3E";

    protected String avatar() {
        return DEFAULT_AVATAR;
    }

    /**
     * 构建 HarnessAgent：公共配置与原单 Agent 保持一致，差异项由子类模板方法注入
     */
    public HarnessAgent build() {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                // harness 默认工作空间, 存储永久记忆等。
                .workspace(Path.of(System.getProperty("user.home") + "/.agentscope"))
                // 存储历史消息、上下文
                .stateStore(agentStateStore)
                .name(name())
                .sysPrompt("你是一个有用的助手")
                .model(buildChatModel())
                .toolkit(buildToolkit())

                // middleware
                .middleware(new TokenUsageMiddleware(tokenUsageService))

                // 工具权限一律不验证，危险，生产环境不建议
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                // 中断/重启导致悬挂工具调用（无结果）时，自动补错误结果让会话恢复，避免下次消息直接崩溃
                .enablePendingToolRecovery(true)
                .maxIters(5)    // 最大迭代
                .maxRetries(1)  // 工具最大重试次数
                .defaultSessionId("default-session-id")

                // 压缩
                .compaction(CompactionConfig.builder()
                        .model(buildCompactionModel())
                        .triggerMessages(30)     // 30 条触发
                        .keepMessages(10)        // 压缩后保留最近 10 条原文
                        .build())

                // 大工具结果卸载压缩
                .toolResultEviction(ToolResultEvictionConfig.defaults())

                .disableMemoryHooks()            // 停掉 flush + 后台 consolidation，不生成 memory/*.md / MEMORY.md
                .disableMemoryTools()            // 不注册 memory_search / memory_get / session_search 工具
                .disableWorkspaceContext()       // 不把 workspace 中的结构化上下文注入 system prompt
                .disableFilesystemTools()        // 禁用框架自带的文件读写操作工具
                .disableShellTool()              // 禁用框架自带的 shell execute 工具
                .disableSubagents()              // 禁用子代理工具 agent_spawn / agent_send / agent_list
                .disableDynamicSubagents();      // 禁用动态子代理（运行时生成子代理规格）

        if (skillsEnabled()) {
            // Classpath 技能市场，Agent 通过 load_skill_through_path 自行加载技能
            builder.skillRepository(skillRepository);
        }
        return builder.build();
    }

    /**
     * Agent 元信息，供 /agents 接口返回
     */
    public AgentInfo agentInfo() {
        return AgentInfo.builder()
                .name(name())
                .displayName(displayName())
                .avatar(avatar())
                .description(description())
                .model(modelName())
                .thinking(thinkingEnabled())
                .tools(toolsEnabled())
                .mcp(toolsEnabled())
                .skills(skillsEnabled())
                .attachments(attachmentsEnabled())
                .defaultAgent(defaultAgent())
                .build();
    }

    /**
     * 主模型：流式输出；思考开关与思考预算按档位启用
     */
    private DashScopeChatModel buildChatModel() {
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelName())
                .stream(true)
                // qwen3.7 系列服务端默认开思考，必须显式传 false 才能关闭
                .enableThinking(thinkingEnabled())
                .formatter(new DashScopeChatFormatter());
        if (thinkingEnabled()) {
            builder.defaultOptions(GenerateOptions.builder()
                    .thinkingBudget(2048)
                    .build());
        }
        return builder.build();
    }

    /**
     * Toolkit：支持 MCP 的档位建立 streamable-http 连接并注册工具；其余档位给空 Toolkit
     */
    private Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();
        if (!toolsEnabled()) {
            return toolkit;
        }
        var mcpClientBuilder = McpClientBuilder.create("http-mcp")
                .streamableHttpTransport(mcpServerUrl + mcpEndpoint)
                .timeout(Duration.ofSeconds(30));
        // token 为空时不附加 Authorization 头
        if (StringUtils.hasText(mcpToken)) {
            mcpClientBuilder.header("Authorization", "Bearer " + mcpToken);
        }
        McpClientWrapper mcpClientWrapper = mcpClientBuilder.buildAsync().block();
        toolkit.registerMcpClient(mcpClientWrapper).block();
        registerLocalTools(toolkit);
        return toolkit;
    }

    /**
     * 注册本地 @Tool 工具的钩子，默认不注册；需要的档位覆写并注入具体工具 Bean
     */
    protected void registerLocalTools(Toolkit toolkit) {
    }

    /**
     * 压缩模型：轻量级低成本 flash，走流式
     */
    private DashScopeChatModel buildCompactionModel() {
        return DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName("qwen-flash")
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();
    }
}
