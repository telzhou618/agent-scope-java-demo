package com.example.agent.service;

import cn.hutool.core.collection.CollUtil;
import com.example.agent.agent.AgentRegistry;
import com.example.agent.dto.AgentSession;
import com.example.agent.dto.CreateSessionRequest;
import com.example.agent.dto.SessionMeta;
import com.example.agent.error.BizException;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 会话管理：创建、历史消息、列表、删除（级联反馈与附件）、置顶
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManageService {

    private final AgentStateStore agentStateStore;
    private final AgentRegistry agentRegistry;
    private final AgentPermissionService agentPermissionService;
    private final SessionTitleService sessionTitleService;
    private final SessionQueryService sessionQueryService;
    private final FileStorageService fileStorageService;
    private final MessageFeedbackService feedbackService;

    /**
     * 创建会话：先同步写入占位标题，前端可立即在会话列表看到；正式标题异步生成并更新
     */
    public void createSession(long loginId, CreateSessionRequest request) {
        String userId = String.valueOf(loginId);
        // agentName 为空时落默认 Agent（flash）；未知名称抛业务异常
        String agentName = StringUtils.hasText(request.getAgentName())
                ? request.getAgentName() : agentRegistry.getDefaultAgentName();
        // 校验存在性与使用权限（未知名称抛业务异常，无权限抛 403）
        agentPermissionService.checkAgent(loginId, agentName);
        sessionTitleService.createPlaceholder(userId, request.getSessionId(), agentName);
        sessionTitleService.generateAndUpdateAsync(userId, request.getSessionId(), request.getMessage());
    }

    public List<Msg> listMessages(String userId, String sessionId) {
        Optional<AgentState> agentState = agentStateStore.get(
                userId, sessionId, "agent_state", AgentState.class
        );
        if (agentState.isPresent()) {
            return agentState.get().getContext();
        }
        return new LinkedList<>();
    }

    /**
     * 会话列表：标题/置顶/创建时间全部来自 session_meta（小 JSON），不读消息上下文；
     * 无 meta 的会话视为脏数据，直接从列表排除；置顶优先，组内按时间倒序
     */
    public List<AgentSession> listSessions(String userId) {
        Set<String> sessionIds = agentStateStore.listSessionIds(userId);
        if (CollUtil.isEmpty(sessionIds)) {
            return new LinkedList<>();
        }
        Map<String, SessionMeta> metas = sessionQueryService.listSessionMetas(userId);
        return sessionIds.stream()
                .map(sessionId -> {
                    SessionMeta meta = metas.get(sessionId);
                    if (meta == null) {
                        return null;
                    }
                    return AgentSession.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .summary(meta.getTitle())
                            .timestamp(meta.getCreateTime())
                            .pinned(meta.isPinned())
                            .agentName(meta.getAgentName())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentSession::isPinned).reversed()
                        .thenComparing(AgentSession::getTimestamp,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 删除会话：级联清理反馈记录与附件目录
     */
    public void deleteSession(long loginId, String sessionId) {
        String userId = String.valueOf(loginId);
        agentStateStore.delete(userId, sessionId);
        feedbackService.deleteForSession(loginId, sessionId);
        fileStorageService.deleteForSession(userId, sessionId);
    }

    public void pinSession(String userId, String sessionId, boolean pinned) {
        SessionMeta meta = agentStateStore.get(userId, sessionId, "session_meta", SessionMeta.class)
                .orElseThrow(() -> new BizException("会话不存在"));
        meta.setPinned(pinned);
        agentStateStore.save(userId, sessionId, "session_meta", meta);
    }
}
