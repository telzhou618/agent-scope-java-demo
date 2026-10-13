package com.example.agent.service;

import com.alibaba.fastjson2.JSON;
import com.example.agent.dto.AgentSseEvent;
import io.agentscope.core.event.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 流事件到 SSE 事件的转换：文本/思考增量直接透传，工具调用参数按 toolCallId 累积到结束时一次性输出
 */
@Component
public class SseEventMapper {

    /**
     * 每个请求一个工具参数累积器
     */
    public Map<String, StringBuilder> newToolParamsAccumulator() {
        return new HashMap<>();
    }

    public ServerSentEvent<String> toSse(AgentSseEvent sseEvent) {
        return ServerSentEvent.<String>builder()
                .data(JSON.toJSONString(sseEvent))
                .build();
    }

    /**
     * 转换为对外 SSE 事件；返回 null 表示该事件不产生对外输出（如工具参数增量）
     */
    public AgentSseEvent toSseEvent(AgentEvent event, Map<String, StringBuilder> toolParamsAccumulator) {
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
        } else if (event instanceof ExceedMaxItersEvent e) {
            // 达到最大迭代：框架会取消未完成工具调用并强制总结，提示用户回答可能不完整
            return AgentSseEvent.builder()
                    .type("notice")
                    .content("任务步骤较多，已达到最大执行步数（" + e.getMaxIters()
                            + "），以上为基于当前信息的收尾总结，内容可能不完整。")
                    .build();
        } else if (event instanceof RequireUserConfirmEvent e) {
            // 工具调用待用户确认（agent.confirm-tools 名单内）：Agent 挂起，前端确认后携 ConfirmResult 恢复
            return AgentSseEvent.builder()
                    .type("require_confirm")
                    .toolCalls(e.getToolCalls().stream()
                            .map(call -> AgentSseEvent.ToolCallInfo.builder()
                                    .toolCallId(call.getId())
                                    .toolName(call.getName())
                                    .toolInput(call.getInput())
                                    .toolContent(call.getContent())
                                    .build())
                            .toList())
                    .build();
        }

        return null;
    }
}
