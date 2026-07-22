package com.example.agent.controller;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.example.agent.dto.AgentChatRequest;
import com.example.agent.dto.AgentSession;
import com.example.agent.dto.AgentSseEvent;
import com.example.agent.dto.Result;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.*;
import io.agentscope.core.message.Msg;
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

    @PostMapping(value = "/chat_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public Flux<ServerSentEvent<String>> chatSse(@RequestBody @Validated AgentChatRequest request) {
        // 每个请求维护工具参数累积器
        Map<String, StringBuilder> toolParamsAccumulator = new HashMap<>();

        RuntimeContext context = RuntimeContext.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .build();

        return harnessAgent.streamEvents(new UserMessage("user", request.getMessage()), context)
                .flatMap(event -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Agent 流事件：{}", JSON.toJSONString(event));
                    }
                    AgentSseEvent sseEvent = toSseEvent(event, toolParamsAccumulator);
                    if (sseEvent == null) {
                        return Flux.empty();
                    }
                    String jsonData = JSON.toJSONString(sseEvent);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build());
                })
                .onErrorResume(error -> {
                    log.error("Agent 流执行出错", error);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(error.getMessage())
                            .build());
                });
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

        } else if (event instanceof AgentResultEvent e) {
            String content = "";
            if (e.getResult() != null && e.getResult().getContent() != null) {
                content = e.getResult().getContent().toString();
            }
            return AgentSseEvent.builder()
                    .type("agent_result")
                    .content(content)
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
    public Result<List<Msg>> getMessages(String userId, String sessionId) {
        Optional<AgentState> agentState = agentStateStore.get(
                userId, sessionId, "agent_state", AgentState.class
        );
        if (agentState.isPresent()) {
            return Result.okData(agentState.get().getContext());
        }
        return Result.okData(new LinkedList<>());
    }

    @GetMapping("/getSessions")
    @Operation(summary = "会话列表")
    public Result<List<AgentSession>> getSessions(String userId) {
        // load 用户所有会话
        Set<String> sessionIds = agentStateStore.listSessionIds(userId);
        if (CollUtil.isEmpty(sessionIds)) {
            return Result.okData(new LinkedList<>());
        }
        // 遍历会话，获取第一个消息作为摘要
        List<AgentSession> agentSessions = sessionIds.stream().map(sessionId -> {
                    Optional<Msg> first = agentStateStore.get(userId, sessionId, "agent_state", AgentState.class)
                            .map(AgentState::getContext)
                            .orElse(Collections.emptyList())
                            .stream().findFirst();
                    if (first.isEmpty()) {
                        return null;
                    }
                    Msg msg = first.get();
                    return AgentSession.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .summary(msg.getTextContent())
                            .timestamp(msg.getTimestamp())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentSession::getTimestamp)
                        .reversed()).toList();
        return Result.okData(agentSessions);
    }

    @GetMapping("/delSession")
    @Operation(summary = "删除会话")
    public Result<Void> delSessions(String userId, String sessionId) {
        agentStateStore.delete(userId, sessionId);
        return Result.ok();
    }

    @GetMapping("/interrupt")
    @Operation(summary = "中断会话")
    public Result<Void> interrupt(String userId, String sessionId) {
        RuntimeContext target = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
        harnessAgent.getDelegate().interrupt(target, new UserMessage("用户已取消操作"));
        return Result.ok();
    }
}


