package com.example.agent.agent;

import com.example.agent.middleware.TokenUsageMiddleware;
import com.example.agent.service.ScheduleService;
import com.example.agent.service.TokenUsageService;
import com.example.agent.service.UserPermissionService;
import com.example.agent.tools.DateTimeTools;
import com.example.agent.tools.ScheduleTools;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

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
     * 需要人工确认的工具名单（agent.confirm-tools，逗号分隔）：名单内工具调用时挂起等前端确认；
     * 其余工具按显式 allow 放行（DEFAULT 模式下无规则的工具会默认 ask，必须显式放行）
     */
    @Value("${agent.confirm-tools:}")
    private List<String> confirmTools;

    @Autowired
    protected DateTimeTools dateTimeTools;

    @Autowired
    protected ScheduleService scheduleService;

    @Autowired
    protected UserPermissionService userPermissionService;

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
     * 是否支持定时任务，开启才注册 schedule_* 工具；默认关闭
     */
    protected abstract boolean scheduleEnabled();

    /**
     * 月度 token 限额（0=不限）
     */
    protected abstract Long monthlyTokenQuota();

    /**
     * 头像：每个档位一个简约的圆形渐变 + 首字母头像，颜色按档位区分
     */
    protected abstract String avatar();

    /**
     * 生成简约圆形头像（渐变底色 + 首字母）的 SVG data URI
     */
    protected static String buildAvatar(String letter, String fromColor, String toColor) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='64' height='64' viewBox='0 0 64 64'>"
                + "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>"
                + "<stop offset='0%' stop-color='" + fromColor + "'/>"
                + "<stop offset='100%' stop-color='" + toColor + "'/>"
                + "</linearGradient></defs>"
                + "<circle cx='32' cy='32' r='32' fill='url(#g)'/>"
                + "<text x='32' y='33' font-family='Arial, Helvetica, sans-serif' font-size='26' font-weight='700' "
                + "fill='#ffffff' text-anchor='middle' dominant-baseline='central'>" + letter + "</text>"
                + "</svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder()
                .encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建 HarnessAgent：公共配置与原单 Agent 保持一致，差异项由子类模板方法注入
     */
    public HarnessAgent build() {
        Toolkit toolkit = buildToolkit();
        HarnessAgent.Builder builder = HarnessAgent.builder()
                // harness 默认工作空间, 存储永久记忆等。
                .workspace(Path.of(System.getProperty("user.home") + "/.agentscope"))
                // 存储历史消息、上下文
                .stateStore(agentStateStore)
                .name(name())
                .sysPrompt("你是一个有用的助手")
                .model(buildChatModel())
                .toolkit(toolkit)

                // middleware
                .middleware(new TokenUsageMiddleware(tokenUsageService))

                // DEFAULT 模式 + 按工具名单细粒度放行/挂起确认（confirm-tools 见 application.yaml）
                .permissionContext(buildPermissionContext(toolkit))
                // 中断/重启导致悬挂工具调用（无结果）时，自动补错误结果让会话恢复，避免下次消息直接崩溃
                .enablePendingToolRecovery(true)
                .maxIters(20)   // 最大迭代：技能加载+多步查询+导出/报告的组合流轻松超过 5，留足余量仍防死循环
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
                .schedule(scheduleEnabled())
                .defaultAgent(defaultAgent())
                .monthlyTokenQuota(monthlyTokenQuota())
                .build();
    }

    /**
     * 主模型：流式输出；思考开关与思考预算按档位启用；maxTokens 兜底防长回答被默认上限截断
     */
    private DashScopeChatModel buildChatModel() {
        DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelName())
                .stream(true)
                // qwen3.7 系列服务端默认开思考，必须显式传 false 才能关闭
                .enableThinking(thinkingEnabled())
                .formatter(new DashScopeChatFormatter());
        GenerateOptions.Builder options = GenerateOptions.builder().maxTokens(8192);
        if (thinkingEnabled()) {
            options.thinkingBudget(2048);
        }
        builder.defaultOptions(options.build());
        return builder.build();
    }

    /**
     * 权限上下文：DEFAULT 模式下无规则的工具默认 ask，所以显式放行 toolkit 里全部工具，
     * 只有 agent.confirm-tools 名单内的工具挂起等用户确认（如 delete_schedule）。
     * 后台定时任务等无用户应答场景，名单内工具会被框架自动拒绝。
     */
    private PermissionContextState buildPermissionContext(Toolkit toolkit) {
        PermissionContextState.Builder context = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
        for (String toolName : toolkit.getToolNames()) {
            if (confirmTools.contains(toolName)) {
                context.addAskRule(toolName, new PermissionRule(toolName, "", PermissionBehavior.ASK, "config"));
            } else {
                context.addAllowRule(toolName, new PermissionRule(toolName, "", PermissionBehavior.ALLOW, "config"));
            }
        }
        return context.build();
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

        registerBaseTools(toolkit);
        registerLocalTools(toolkit);
        registerMcpTools(toolkit);

        return toolkit;
    }

    /**
     * 注册 MCP 工具的钩子，默认不注册；需要的档位覆写并注入具体工具 Bean
     */
    private void registerMcpTools(Toolkit toolkit) {
    }

    private void registerBaseTools(Toolkit toolkit) {
        toolkit.registerTool(dateTimeTools);
        if (scheduleEnabled()) {
            toolkit.registerTool(new ScheduleTools(scheduleService, name(), userPermissionService));
        }
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
