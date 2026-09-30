package com.example.agent.service;

import com.example.agent.dto.SessionMeta;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 会话标题服务：首条用户消息触发，用轻量级 flash 模型生成会话标题并持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleService {

    private static final String META_KEY = "session_meta";
    private static final String TITLE_PROMPT = "请把下面这段用户首句概括为会话标题：与用户消息同语言、"
            + "不超过20字、不加引号和任何前后缀，只输出标题本身。\n\n";
    private static final int PROMPT_PREVIEW_LIMIT = 200;
    private static final int TITLE_MAX_LEN = 20;
    private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(30);

    private final AgentStateStore stateStore;
    private final DashScopeChatModel titleModel;

    /**
     * 生成会话标题：已有标题或会话已删除返回空 Mono；生成失败回退为首条消息截断并同样持久化
     */
    public Mono<String> generateIfAbsent(String userId, String sessionId, String firstUserText) {
        return readTitle(userId, sessionId).flatMap(existing -> {
            if (existing.isPresent()) {
                return Mono.empty();
            }
            return sessionExists(userId, sessionId).flatMap(alive -> {
                if (!alive) {
                    log.info("会话已删除，跳过标题生成, sessionId={}", sessionId);
                    return Mono.empty();
                }
                return generateTitle(firstUserText)
                        .onErrorResume(e -> {
                            log.warn("会话标题生成失败，回退为首条消息截断, sessionId={}", sessionId, e);
                            return Mono.just(truncate(firstUserText, TITLE_MAX_LEN));
                        })
                        .flatMap(title -> saveTitle(userId, sessionId, title).thenReturn(title));
            });
        });
    }

    /**
     * 会话是否仍存在：以 agent_state 是否有记录为准
     */
    private Mono<Boolean> sessionExists(String userId, String sessionId) {
        return Mono.fromCallable(() -> stateStore.get(userId, sessionId, "agent_state", AgentState.class).isPresent())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Optional<String>> readTitle(String userId, String sessionId) {
        return Mono.fromCallable(() -> stateStore.get(userId, sessionId, META_KEY, SessionMeta.class)
                        .map(SessionMeta::getTitle)
                        .filter(StringUtils::hasText))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> generateTitle(String firstUserText) {
        return Mono.fromCallable(() -> {
                    String prompt = TITLE_PROMPT + truncate(firstUserText, PROMPT_PREVIEW_LIMIT);
                    ChatResponse resp = titleModel.stream(List.of(new UserMessage(prompt)), null, null)
                            .blockLast(GENERATE_TIMEOUT);
                    String title = extractText(resp);
                    if (!StringUtils.hasText(title)) {
                        throw new IllegalStateException("标题生成结果为空");
                    }
                    return truncate(title, TITLE_MAX_LEN);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> saveTitle(String userId, String sessionId, String title) {
        return Mono.<Void>fromRunnable(() -> {
            if (stateStore.get(userId, sessionId, "agent_state", AgentState.class).isEmpty()) {
                log.info("会话已删除，跳过标题写入, sessionId={}", sessionId);
                return;
            }
            stateStore.save(userId, sessionId, META_KEY, SessionMeta.builder().title(title).build());
        }).subscribeOn(Schedulers.boundedElastic());
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
