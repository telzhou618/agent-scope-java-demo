package com.example.agent.service;

import com.example.agent.dto.SessionMeta;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 会话标题服务：创建会话时先写入占位标题，再异步用轻量级 flash 模型生成正式标题并更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleService {

    private static final String META_KEY = "session_meta";
    public static final String PLACEHOLDER_TITLE = "新会话";
    private static final String TITLE_PROMPT = "请把下面这段用户首句概括为会话标题：与用户消息同语言、"
            + "不超过20字、不加引号和任何前后缀，只输出标题本身。\n\n";
    private static final int PROMPT_PREVIEW_LIMIT = 200;
    private static final int TITLE_MAX_LEN = 20;
    private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AgentStateStore stateStore;
    private final DashScopeChatModel titleModel;

    /**
     * 会话是否已有 meta（URL 直达等路径进入的会话可能没有）
     */
    public boolean hasMeta(String userId, String sessionId) {
        return stateStore.get(userId, sessionId, META_KEY, SessionMeta.class).isPresent();
    }

    /**
     * 创建会话占位标题：同步写入，立即返回；已有 meta（重复创建）则跳过
     */
    public void createPlaceholder(String userId, String sessionId, String agentName) {
        Optional<SessionMeta> existing = stateStore.get(userId, sessionId, META_KEY, SessionMeta.class);
        if (existing.isPresent()) {
            return;
        }
        stateStore.save(userId, sessionId, META_KEY, SessionMeta.builder()
                .title(PLACEHOLDER_TITLE)
                .createTime(TIME_FORMATTER.format(LocalDateTime.now()))
                .agentName(agentName)
                .build());
    }

    /**
     * 异步生成正式标题并更新 meta：会话已删除或标题已被覆盖时跳过；失败回退为首条消息截断
     */
    public void generateAndUpdateAsync(String userId, String sessionId, String firstUserText) {
        Mono.fromRunnable(() -> doGenerateAndUpdate(userId, sessionId, firstUserText))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, e -> log.warn("异步生成会话标题失败, sessionId={}", sessionId, e));
    }

    private void doGenerateAndUpdate(String userId, String sessionId, String firstUserText) {
        if (!stateStore.exists(userId, sessionId)) {
            log.info("会话已删除，跳过标题生成, sessionId={}", sessionId);
            return;
        }
        String title;
        try {
            title = generateTitle(firstUserText);
        } catch (Exception e) {
            log.warn("会话标题生成失败，回退为首条消息截断, sessionId={}", sessionId, e);
            title = truncate(firstUserText, TITLE_MAX_LEN);
        }
        if (!stateStore.exists(userId, sessionId)) {
            log.info("会话已删除，跳过标题写入, sessionId={}", sessionId);
            return;
        }
        Optional<SessionMeta> current = stateStore.get(userId, sessionId, META_KEY, SessionMeta.class);
        String currentTitle = current.map(SessionMeta::getTitle).orElse(null);
        if (StringUtils.hasText(currentTitle) && !PLACEHOLDER_TITLE.equals(currentTitle)) {
            log.info("会话标题已存在，跳过标题写入, sessionId={}", sessionId);
            return;
        }
        stateStore.save(userId, sessionId, META_KEY, SessionMeta.builder()
                .title(title)
                .createTime(current.map(SessionMeta::getCreateTime).orElse(null))
                .pinned(current.map(SessionMeta::isPinned).orElse(false))
                .agentName(current.map(SessionMeta::getAgentName).orElse(null))
                .build());
    }

    private String generateTitle(String firstUserText) {
        String prompt = TITLE_PROMPT + truncate(firstUserText, PROMPT_PREVIEW_LIMIT);
        ChatResponse resp = titleModel.stream(List.of(new UserMessage(prompt)), null, null)
                .blockLast(GENERATE_TIMEOUT);
        String title = extractText(resp);
        if (!StringUtils.hasText(title)) {
            throw new IllegalStateException("标题生成结果为空");
        }
        return truncate(title, TITLE_MAX_LEN);
    }

    private String extractText(ChatResponse resp) {
        if (resp == null || resp.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : resp.getContent()) {
            if (block instanceof TextBlock text) {
                sb.append(text.getText());
            }
        }
        return sb.toString().trim();
    }

    private String truncate(String text, int limit) {
        return text == null ? "" : (text.length() <= limit ? text : text.substring(0, limit));
    }
}
