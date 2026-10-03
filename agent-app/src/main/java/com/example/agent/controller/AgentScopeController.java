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
import com.example.agent.dto.Result;
import com.example.agent.dto.SessionMeta;
import com.example.agent.error.BizException;
import com.example.agent.service.DocumentTextExtractor;
import com.example.agent.service.FileStorageService;
import com.example.agent.service.MessageFeedbackService;
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
        UserMessage userMessage = buildUserMessage(userId, message, request.getAttachments());
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(request.getSessionId())
                .userId(userId)
                .put("requestId", request.getRequestId() == null ? "" : request.getRequestId())
                .build();

        return harnessAgent.streamEvents(userMessage, context)
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
                .onErrorResume(error -> {
                    log.error("Agent 流执行出错", error);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(error.getMessage())
                            .build());
                });
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

    private UserMessage buildUserMessage(String userId, String text, List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new UserMessage("user", text);
        }
        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(TextBlock.builder().text(text).build());
        for (ChatAttachment attachment : attachments) {
            byte[] bytes = fileStorageService.readBytes(userId, attachment.id());
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
        // 遍历会话：标题优先取 meta（AI 生成/占位），缺省回退首条消息摘要；
        // 只有占位 meta、还没有消息的新会话也纳入列表
        List<AgentSession> agentSessions = sessionIds.stream().map(sessionId -> {
                    Optional<Msg> first = agentStateStore.get(userId, sessionId, "agent_state", AgentState.class)
                            .map(AgentState::getContext)
                            .orElse(Collections.emptyList())
                            .stream().findFirst();
                    Optional<SessionMeta> meta = agentStateStore.get(userId, sessionId, "session_meta", SessionMeta.class);
                    String summary = meta.map(SessionMeta::getTitle)
                            .filter(title -> !title.isBlank())
                            .orElse(first.map(Msg::getTextContent).orElse(null));
                    String timestamp = first.map(Msg::getTimestamp)
                            .orElse(meta.map(SessionMeta::getCreateTime).orElse(null));
                    if (summary == null && timestamp == null) {
                        return null;
                    }
                    return AgentSession.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .summary(summary)
                            .timestamp(timestamp)
                            .pinned(meta.map(SessionMeta::isPinned).orElse(false))
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

    @GetMapping("/delSession")
    @Operation(summary = "删除会话")
    public Result<Void> delSessions(String sessionId) {
        agentStateStore.delete(loginUserId(), sessionId);
        // 级联清理该会话的反馈记录
        feedbackService.deleteForSession(StpUtil.getLoginIdAsLong(), sessionId);
        return Result.ok();
    }

    @GetMapping("/pinSession")
    @Operation(summary = "置顶/取消置顶会话")
    public Result<Void> pinSession(String sessionId, boolean pinned) {
        String userId = loginUserId();
        // 无 meta 的老会话也能置顶：新建空 meta，标题仍回退首条消息摘要
        SessionMeta meta = agentStateStore.get(userId, sessionId, "session_meta", SessionMeta.class)
                .orElseGet(SessionMeta::new);
        meta.setPinned(pinned);
        agentStateStore.save(userId, sessionId, "session_meta", meta);
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

    @GetMapping("/interrupt")
    @Operation(summary = "中断会话")
    public Result<Void> interrupt(String sessionId) {
        RuntimeContext target = RuntimeContext.builder()
                .userId(loginUserId())
                .sessionId(sessionId)
                .build();
        harnessAgent.getDelegate().interrupt(target, new UserMessage("用户已取消操作"));
        return Result.ok();
    }
}


