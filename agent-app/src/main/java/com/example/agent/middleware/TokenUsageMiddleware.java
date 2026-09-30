package com.example.agent.middleware;

import com.example.agent.usage.TokenUsageService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 记录每次模型调用的 token 消耗到 t_token_usage。
 * 走框架原生 onModelCall 钩子，覆盖主模型与压缩模型的全部调用。
 */
@RequiredArgsConstructor
public class TokenUsageMiddleware implements MiddlewareBase {

    private final TokenUsageService usageService;

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return next.apply(input).doOnNext(event -> {
            if (event instanceof ModelCallEndEvent end) {
                usageService.record(ctx, agent.getName(), input.model().getModelName(), end);
            }
        });
    }
}
