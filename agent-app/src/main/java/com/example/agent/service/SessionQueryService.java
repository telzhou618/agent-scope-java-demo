package com.example.agent.service;

import com.example.agent.dto.SessionMeta;
import com.example.agent.mapper.SessionStateMapper;
import com.example.agent.vo.SessionStateRow;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话列表批量查询：一次性读完全部 session_meta，避免 getSessions 逐会话读 LONGTEXT 上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionQueryService {

    private final SessionStateMapper sessionStateMapper;

    /**
     * 用户全部会话的 meta，key 为去掉 userId 前缀的 sessionId
     */
    public Map<String, SessionMeta> listSessionMetas(String userId) {
        Map<String, SessionMeta> result = new HashMap<>();
        for (SessionStateRow row : sessionStateMapper.listSessionMetas(userId)) {
            try {
                result.put(stripPrefix(userId, row.getSessionId()),
                        JsonUtils.getJsonCodec().fromJson(row.getStateData(), SessionMeta.class));
            } catch (Exception e) {
                log.warn("解析 session_meta 失败, sessionId={}", row.getSessionId(), e);
            }
        }
        return result;
    }

    /**
     * 批量读取指定会话的首条消息（仅用于无 meta 的老会话回退摘要/时间），key 为 sessionId
     */
    public Map<String, Msg> listFirstMessages(String userId, Collection<String> sessionIds) {
        Map<String, Msg> result = new HashMap<>();
        if (sessionIds == null || sessionIds.isEmpty()) {
            return result;
        }
        String prefix = userId + ":";
        var slotIds = sessionIds.stream().map(id -> prefix + id).toList();
        for (SessionStateRow row : sessionStateMapper.listAgentStates(slotIds)) {
            try {
                AgentState state = JsonUtils.getJsonCodec().fromJson(row.getStateData(), AgentState.class);
                if (state.getContext() != null && !state.getContext().isEmpty()) {
                    result.put(stripPrefix(userId, row.getSessionId()), state.getContext().get(0));
                }
            } catch (Exception e) {
                log.warn("解析 agent_state 失败, sessionId={}", row.getSessionId(), e);
            }
        }
        return result;
    }

    private String stripPrefix(String userId, String slotId) {
        String prefix = userId + ":";
        return slotId != null && slotId.startsWith(prefix) ? slotId.substring(prefix.length()) : slotId;
    }
}
