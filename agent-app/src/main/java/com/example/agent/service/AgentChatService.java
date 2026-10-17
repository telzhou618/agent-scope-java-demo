package com.example.agent.service;

import com.alibaba.fastjson2.JSON;
import com.example.agent.agent.AgentRegistry;
import com.example.agent.datasource.MysqlDatasourceRegistry;
import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.AgentSseEvent;
import com.example.agent.dto.InterruptRequest;
import com.example.agent.error.BizException;
import com.example.agent.plugin.Plugin;
import com.example.agent.plugin.PluginContext;
import com.example.agent.plugin.PluginRegistry;
import com.example.agent.vo.DatasourceVO;
import com.example.agent.vo.PluginMeta;
import com.example.agent.vo.PluginVO;
import com.example.agent.vo.SkillVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

/**
 * 聊天流编排：权限校验、@ 技能/数据源改写、会话标题、消息构建、Agent 事件流驱动与 SSE 转换；另含中断与技能/数据源列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentRegistry agentRegistry;
    private final AgentPermissionService agentPermissionService;
    private final TokenQuotaService tokenQuotaService;
    private final SessionTitleService sessionTitleService;
    private final ChatMessageFactory chatMessageFactory;
    private final SseEventMapper sseEventMapper;
    private final ClasspathSkillRepository skillRepository;
    private final MysqlDatasourceRegistry datasourceRegistry;
    private final PluginRegistry pluginRegistry;

    /**
     * 流式对话：事件流转热，驱动订阅与 HTTP 连接解耦，客户端刷新/断开 SSE 后 Agent 仍跑完并落库，
     * 重新打开会话通过 getMessages 可见完整回复；/interrupt 走信号中断（InterruptControl），
     * 不依赖订阅取消，保持有效。流结束（完成/出错）后驱动订阅自然终止。
     */
    public Flux<ServerSentEvent<String>> chatSse(long loginId, AgentChatRequest request) {
        try {
            return doChatSse(loginId, request);
        } catch (BizException e) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(e.getMessage())
                    .build());
        }
    }

    private Flux<ServerSentEvent<String>> doChatSse(long loginId, AgentChatRequest request) {
        // 每个请求维护工具参数累积器
        Map<String, StringBuilder> toolParamsAccumulator = sseEventMapper.newToolParamsAccumulator();

        String userId = String.valueOf(loginId);
        // 按请求指定档位取 Agent，未知名称抛业务异常；先校验当前用户是否有该档位使用权限
        agentPermissionService.checkAgent(loginId, request.getAgentName());
        tokenQuotaService.checkQuota(loginId, request.getAgentName());
        HarnessAgent agent = agentRegistry.getAgent(request.getAgentName());

        UserMessage userMessage;
        if (request.getConfirmResults() != null && !request.getConfirmResults().isEmpty()) {
            // 工具确认恢复：消息文本仅作会话记录，真正的确认结果在 metadata 里（框架约定）
            userMessage = buildConfirmMessage(request.getConfirmResults());
        } else {
            Plugin plugin = StringUtils.hasText(request.getPlugin()) ? resolvePlugin(request.getPlugin()) : null;
            String params = plugin != null
                    ? (request.getMessage() == null ? "" : request.getMessage())
                    : chatMessageFactory.effectiveMessage(request);

            String message;
            PluginMeta pluginMeta = null;
            if (plugin != null) {
                // 插件优先：插件改写提示词后走原 Agent 流；插件对象写入 metadata 供历史回显
                pluginMeta = new PluginMeta(plugin.id(), plugin.name(), plugin.description(), params);
                message = plugin.process(new PluginContext(userId, request.getAgentName(), params, request.getAttachments()));
            } else {
                // @ 技能/@数据源 原样透传，不解析改写
                message = chatMessageFactory.applyMentions(params);
            }

            // URL 直达等路径进入的会话可能没有 meta：补建占位标题并异步生成正式标题（已有 meta 则跳过）
            if (!sessionTitleService.hasMeta(userId, request.getSessionId())) {
                sessionTitleService.createPlaceholder(userId, request.getSessionId(), request.getAgentName());
                String titleHint = StringUtils.hasText(params) ? params : (plugin != null ? plugin.name() : message);
                sessionTitleService.generateAndUpdateAsync(userId, request.getSessionId(), titleHint);
            }
            userMessage = chatMessageFactory.buildUserMessage(userId, request.getSessionId(), message,
                    request.getAttachments(), pluginMeta);
        }
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(request.getSessionId())
                .userId(userId)
                .put("requestId", request.getRequestId() == null ? "" : request.getRequestId())
                .build();

        if (log.isDebugEnabled()) {
            log.debug("Agent 请求参数：{}", JSON.toJSONString(request));
            log.debug("Agent 用户上下文：{}", JSON.toJSONString(context));
            log.info("Agent UserMessage: {}", JSON.toJSONString(userMessage));
        }
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        agent.streamEvents(userMessage, context)
                .subscribe(sink::tryEmitNext,
                        error -> {
                            log.error("Agent 流执行出错", error);
                            sink.tryEmitError(error);
                        },
                        sink::tryEmitComplete);

        return sink.asFlux()
                .doOnCancel(() -> log.info("SSE 客户端断开，Agent 继续后台运行, sessionId={}",
                        request.getSessionId()))
                .flatMap(event -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Agent 流事件：{}", JSON.toJSONString(event));
                    }
                    AgentSseEvent sseEvent = sseEventMapper.toSseEvent(event, toolParamsAccumulator);
                    if (sseEvent == null) {
                        return Flux.empty();
                    }
                    return Flux.just(sseEventMapper.toSse(sseEvent));
                })
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(error.getMessage())
                        .build()));
    }

    /**
     * 把前端的确认结果组装成恢复消息：metadata 携带 ConfirmResult 列表，Agent 从挂起处恢复执行
     */
    private UserMessage buildConfirmMessage(List<AgentChatRequest.ConfirmResultItem> items) {
        List<ConfirmResult> results = items.stream()
                .map(item -> new ConfirmResult(Boolean.TRUE.equals(item.getConfirmed()),
                        ToolUseBlock.builder()
                                .id(item.getToolCallId())
                                .name(item.getToolName())
                                .input(item.getToolInput() == null ? Map.of() : item.getToolInput())
                                .content(item.getToolContent() == null ? "" : item.getToolContent())
                                .build()))
                .toList();
        boolean allConfirmed = items.stream().allMatch(item -> Boolean.TRUE.equals(item.getConfirmed()));
        boolean noneConfirmed = items.stream().noneMatch(item -> Boolean.TRUE.equals(item.getConfirmed()));
        long approvedCount = items.stream().filter(item -> Boolean.TRUE.equals(item.getConfirmed())).count();
        String text = allConfirmed ? "确认执行"
                : noneConfirmed ? "取消执行"
                : "确认执行其中 " + approvedCount + " 项";
        return UserMessage.builder()
                .textContent(text)
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
    }

    /**
     * 中断会话：校验当前用户是否有该档位使用权限后向运行中的 Agent 发中断信号
     */
    public void interrupt(long loginId, InterruptRequest request) {
        agentPermissionService.checkAgent(loginId, request.getAgentName());
        RuntimeContext target = RuntimeContext.builder()
                .userId(String.valueOf(loginId))
                .sessionId(request.getSessionId())
                .build();
        agentRegistry.getAgent(request.getAgentName())
                .getDelegate().interrupt(target, new UserMessage("用户已取消操作"));
    }

    /**
     * 技能列表：指定了 Agent 时先校验使用权限；该档位不支持技能时返回空列表
     */
    public List<SkillVO> listSkills(long loginId, String agentName) {
        if (StringUtils.hasText(agentName)) {
            agentPermissionService.checkAgent(loginId, agentName);
        }
        if (StringUtils.hasText(agentName) && !agentRegistry.getAgentInfo(agentName).isSkills()) {
            return List.of();
        }
        return skillRepository.getAllSkills().stream()
                .map(skill -> SkillVO.builder()
                        .name(skill.getName())
                        .description(skill.getDescription())
                        .build())
                .toList();
    }

    /**
     * 数据源列表（id/名称/描述），供前端 @ 数据源下拉使用
     */
    public List<DatasourceVO> listDatasources() {
        return datasourceRegistry.list().stream()
                .map(item -> DatasourceVO.builder()
                        .id((String) item.get("id"))
                        .name((String) item.get("name"))
                        .description((String) item.get("description"))
                        .build())
                .toList();
    }

    /**
     * 插件元信息列表，供前端 / 下拉
     */
    public List<PluginVO> listPlugins() {
        return pluginRegistry.list();
    }

    private Plugin resolvePlugin(String pluginId) {
        try {
            return pluginRegistry.get(pluginId);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
    }
}