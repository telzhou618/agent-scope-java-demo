package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.AgentSession;
import com.example.agent.dto.AgentSseEvent;
import com.example.agent.dto.ChatAttachment;
import com.example.agent.dto.CreateSessionRequest;
import com.example.agent.dto.FeedbackRequest;
import com.example.agent.dto.PinSessionRequest;
import com.example.agent.dto.Result;
import com.example.agent.dto.SessionIdRequest;
import com.example.agent.dto.SessionMeta;
import com.example.agent.error.BizException;
import com.example.agent.service.DocumentTextExtractor;
import com.example.agent.service.FileStorageService;
import com.example.agent.service.MessageFeedbackService;
import com.example.agent.service.SessionQueryService;
import com.example.agent.service.SessionTitleService;
import com.example.agent.vo.SkillVO;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MCP Client Agent 控制器
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/agent/scope")
@Tag(name = "AgentScope 智能体")
public class AgentScopeController {

    private final HarnessAgent harnessAgent;
    private final AgentStateStore agentStateStore;
    private final SessionTitleService sessionTitleService;
    private final SessionQueryService sessionQueryService;
    private final FileStorageService fileStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ClasspathSkillRepository skillRepository;
    private final MessageFeedbackService feedbackService;

    /**
     * 从 token 解析当前登录用户 id（字符串形式，与会话状态目录名一致）
     */
    private String loginUserId() {
        return String.valueOf(StpUtil.getLoginIdAsLong());
    }

    @PostMapping(value = "/chat_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public Flux<ServerSentEvent<String>> chatSse(@RequestBody @Validated AgentChatRequest request) {
        // 每个请求维护工具参数累积器
        Map<String, StringBuilder> toolParamsAccumulator = new HashMap<>();

        String userId = loginUserId();
        // /skill:<name> 前缀转成技能使用提示，Agent 通过 load_skill_through_path 自行加载技能详情
        String message = applySkillHint(effectiveMessage(request));
        UserMessage userMessage = buildUserMessage(userId, request.getSessionId(), message,
                request.getAttachments());
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(request.getSessionId())
                .userId(userId)
                .put("requestId", request.getRequestId() == null ? "" : request.getRequestId())
                .build();

        // 事件流转热：驱动订阅与 HTTP 连接解耦，客户端刷新/断开 SSE 后 Agent 仍跑完并落库，
        // 重新打开会话通过 getMessages 可见完整回复；/interrupt 走信号中断（InterruptControl），
        // 不依赖订阅取消，保持有效。流结束（完成/出错）后驱动订阅自然终止。
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        harnessAgent.streamEvents(userMessage, context)
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
                    AgentSseEvent sseEvent = toSseEvent(event, toolParamsAccumulator);
                    if (sseEvent == null) {
                        return Flux.empty();
                    }
                    return Flux.just(toSse(sseEvent));
                })
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(error.getMessage())
                        .build()));
    }

    @PostMapping("/createSession")
    @Operation(summary = "创建会话")
    public Result<Void> createSession(@RequestBody @Validated CreateSessionRequest request) {
        String userId = loginUserId();
        // 先同步写入占位标题，前端可立即在会话列表看到；正式标题异步生成并更新
        sessionTitleService.createPlaceholder(userId, request.getSessionId());
        sessionTitleService.generateAndUpdateAsync(userId, request.getSessionId(), request.getMessage());
        return Result.ok();
    }

    private String effectiveMessage(AgentChatRequest request) {
        boolean blank = request.getMessage() == null || request.getMessage().isBlank();
        boolean hasAttachments = request.getAttachments() != null && !request.getAttachments().isEmpty();
        if (blank && !hasAttachments) {
            throw new BizException("用户消息不能为空");
        }
        return blank ? "请查看我发送的文件" : request.getMessage();
    }

    private UserMessage buildUserMessage(String userId, String sessionId, String text,
                                         List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage("user", text);
        }
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(text).build());
        for (ChatAttachment attachment : attachments) {
            byte[] bytes = fileStorageService.readBytes(userId, sessionId, attachment.id());
            if (fileStorageService.isImage(attachment.ext())) {
                blocks.add(new ImageBlock(new Base64Source(
                        fileStorageService.mediaType(attachment.ext()),
                        java.util.Base64.getEncoder().encodeToString(bytes))));
            } else if (documentTextExtractor.isDocument(attachment.ext())) {
                blocks.add(TextBlock.builder().text(attachmentText(attachment.name(),
                        documentTextExtractor.extract(attachment.name(), attachment.ext(), bytes))).build());
            } else {
                blocks.add(TextBlock.builder().text(attachmentText(attachment.name(),
                        new String(bytes, StandardCharsets.UTF_8))).build());
            }
        }
        return new UserMessage("user", blocks.toArray(new ContentBlock[0]));
    }

    private String attachmentText(String name, String content) {
        return "【附件：" + name + "】\n" + content;
    }

    private ServerSentEvent<String> toSse(AgentSseEvent sseEvent) {
        return ServerSentEvent.<String>builder()
                .data(JSON.toJSONString(sseEvent))
                .build();
    }

    private AgentSseEvent toSseEvent(AgentEvent event, Map<String, StringBuilder> toolParamsAccumulator) {
        if (event instanceof AgentStartEvent) {
            return AgentSseEvent.builder()
                    .type("agent_start")
                    .content("")
                    .build();
        } else if (event instanceof ThinkingBlockDeltaEvent e) {
            return AgentSseEvent.builder()
                    .type("thinking")
                    .content(e.getDelta())
                    .build();

        } else if (event instanceof TextBlockDeltaEvent e) {
            return AgentSseEvent.builder()
                    .type("text_block")
                    .content(e.getDelta())
                    .build();

        } else if (event instanceof ToolCallDeltaEvent e) {
            // 只累积参数，不输出
            String toolCallId = e.getToolCallId();
            String delta = e.getDelta();
            toolParamsAccumulator.computeIfAbsent(toolCallId, k -> new StringBuilder())
                    .append(delta);
            return null;

        } else if (event instanceof ToolCallEndEvent e) {
            String toolCallId = e.getToolCallId();
            String toolParams = toolParamsAccumulator.getOrDefault(toolCallId, new StringBuilder()).toString();
            toolParamsAccumulator.remove(toolCallId);

            return AgentSseEvent.builder()
                    .type("tool_call")
                    .toolCall(AgentSseEvent.ToolCallInfo.builder()
                            .toolCallId(toolCallId)
                            .toolName(e.getToolCallName())
                            .toolParams(toolParams)
                            .build())
                    .build();

        } else if (event instanceof ToolResultTextDeltaEvent e) {
            return AgentSseEvent.builder()
                    .type("tool_result")
                    .toolCall(AgentSseEvent.ToolCallInfo.builder()
                            .toolCallId(e.getToolCallId())
                            .toolName(e.getToolCallName())
                            .toolResults(e.getDelta())
                            .build())
                    .build();

        } else if (event instanceof ToolResultEndEvent e) {
            return AgentSseEvent.builder()
                    .type("tool_end")
                    .toolCall(AgentSseEvent.ToolCallInfo.builder()
                            .toolCallId(e.getToolCallId())
                            .toolName(e.getToolCallName())
                            .state(e.getState() == null ? "success" : e.getState().getValue())
                            .build())
                    .build();

        } else if (event instanceof AgentResultEvent e) {
            String content = "";
            if (e.getResult() != null && e.getResult().getContent() != null) {
                content = e.getResult().getContent().toString();
            }
            return AgentSseEvent.builder()
                    .type("agent_result")
                    .content(content)
                    .messageId(e.getResult() == null ? null : e.getResult().getId())
                    .build();
        } else if (event instanceof AgentEndEvent e) {

            return AgentSseEvent.builder()
                    .type("agent_end")
                    .content("")
                    .build();
        }

        return null;
    }

    @GetMapping("/getMessages")
    @Operation(summary = "历史消息")
    public Result<List<Msg>> getMessages(String sessionId) {
        Optional<AgentState> agentState = agentStateStore.get(
                loginUserId(), sessionId, "agent_state", AgentState.class
        );
        if (agentState.isPresent()) {
            return Result.okData(agentState.get().getContext());
        }
        return Result.okData(new LinkedList<>());
    }

    @GetMapping("/getSessions")
    @Operation(summary = "会话列表")
    public Result<List<AgentSession>> getSessions() {
        String userId = loginUserId();
        // load 用户所有会话
        Set<String> sessionIds = agentStateStore.listSessionIds(userId);
        if (CollUtil.isEmpty(sessionIds)) {
            return Result.okData(new LinkedList<>());
        }
        // 批量读 session_meta（标题/置顶/创建时间，小 JSON）；
        // 只有没有 meta 的老会话才批量回退读首条消息，不再逐会话读取 LONGTEXT 消息上下文
        Map<String, SessionMeta> metas = sessionQueryService.listSessionMetas(userId);
        List<String> legacyIds = sessionIds.stream()
                .filter(sessionId -> !metas.containsKey(sessionId))
                .toList();
        Map<String, Msg> firstMessages = sessionQueryService.listFirstMessages(userId, legacyIds);

        // 标题优先取 meta（AI 生成/占位），缺省回退首条消息摘要；
        // 只有占位 meta、还没有消息的新会话也纳入列表
        List<AgentSession> agentSessions = sessionIds.stream().map(sessionId -> {
                    SessionMeta meta = metas.get(sessionId);
                    Msg first = firstMessages.get(sessionId);
                    String summary = Optional.ofNullable(meta)
                            .map(SessionMeta::getTitle)
                            .filter(title -> !title.isBlank())
                            .orElse(first == null ? null : first.getTextContent());
                    String timestamp = first != null && first.getTimestamp() != null
                            ? first.getTimestamp()
                            : Optional.ofNullable(meta).map(SessionMeta::getCreateTime).orElse(null);
                    if (summary == null && timestamp == null) {
                        return null;
                    }
                    return AgentSession.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .summary(summary)
                            .timestamp(timestamp)
                            .pinned(meta != null && meta.isPinned())
                            .build();
                })
                .filter(Objects::nonNull)
                // 置顶优先，组内按时间倒序
                .sorted(Comparator.comparing(AgentSession::isPinned).reversed()
                        .thenComparing(AgentSession::getTimestamp,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return Result.okData(agentSessions);
    }

    @PostMapping("/delSession")
    @Operation(summary = "删除会话")
    public Result<Void> delSessions(@RequestBody @Validated SessionIdRequest request) {
        String userId = loginUserId();
        agentStateStore.delete(userId, request.getSessionId());
        // 级联清理该会话的反馈记录
        feedbackService.deleteForSession(StpUtil.getLoginIdAsLong(), request.getSessionId());
        // 级联清理该会话的附件目录
        fileStorageService.deleteForSession(userId, request.getSessionId());
        return Result.ok();
    }

    @PostMapping("/pinSession")
    @Operation(summary = "置顶/取消置顶会话")
    public Result<Void> pinSession(@RequestBody @Validated PinSessionRequest request) {
        String userId = loginUserId();
        // 无 meta 的老会话也能置顶：新建空 meta，标题仍回退首条消息摘要
        SessionMeta meta = agentStateStore.get(userId, request.getSessionId(), "session_meta", SessionMeta.class)
                .orElseGet(SessionMeta::new);
        meta.setPinned(request.isPinned());
        agentStateStore.save(userId, request.getSessionId(), "session_meta", meta);
        return Result.ok();
    }

    @GetMapping("/skills")
    @Operation(summary = "技能列表")
    public Result<List<SkillVO>> skills() {
        List<SkillVO> result = skillRepository.getAllSkills().stream()
                .map(skill -> SkillVO.builder()
                        .name(skill.getName())
                        .description(skill.getDescription())
                        .build())
                .toList();
        return Result.okData(result);
    }

    @PostMapping("/feedback")
    @Operation(summary = "消息反馈（有帮助/没帮助，feedback 为空表示取消）")
    public Result<Void> feedback(@RequestBody @Validated FeedbackRequest request) {
        feedbackService.submit(StpUtil.getLoginIdAsLong(),
                request.getSessionId(), request.getMessageId(), request.getFeedback());
        return Result.ok();
    }

    @GetMapping("/getFeedbacks")
    @Operation(summary = "会话的反馈映射（messageId -> up/down）")
    public Result<Map<String, String>> getFeedbacks(String sessionId) {
        return Result.okData(feedbackService.listForSession(StpUtil.getLoginIdAsLong(), sessionId));
    }

    private static final String SKILL_PREFIX = "/skill:";

    /**
     * /skill:<name> 前缀转换为技能使用提示；技能存在时告知模型使用哪个技能，
     * 详情由 Agent 调内置工具 load_skill_through_path 自行加载；非技能消息原样返回
     */
    private String applySkillHint(String message) {
        if (message == null || !message.startsWith(SKILL_PREFIX)) {
            return message;
        }
        String rest = message.substring(SKILL_PREFIX.length());
        int split = -1;
        for (int i = 0; i < rest.length(); i++) {
            if (Character.isWhitespace(rest.charAt(i))) {
                split = i;
                break;
            }
        }
        String name = split < 0 ? rest : rest.substring(0, split);
        String requirement = split < 0 ? "" : rest.substring(split).trim();
        AgentSkill skill = skillRepository.getSkill(name);
        if (skill == null) {
            log.warn("技能不存在，按普通消息处理: {}", name);
            return message;
        }
        return "请使用 " + name + " 技能完成以下需求：" + requirement;
    }

    @PostMapping("/interrupt")
    @Operation(summary = "中断会话")
    public Result<Void> interrupt(@RequestBody @Validated SessionIdRequest request) {
        RuntimeContext target = RuntimeContext.builder()
                .userId(loginUserId())
                .sessionId(request.getSessionId())
                .build();
        harnessAgent.getDelegate().interrupt(target, new UserMessage("用户已取消操作"));
        return Result.ok();
    }
}


