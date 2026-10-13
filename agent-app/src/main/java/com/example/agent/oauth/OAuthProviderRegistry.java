package com.example.agent.oauth;

import com.example.agent.error.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 已启用的 OAuth 渠道聚合：按 channel 索引
 */
@Component
public class OAuthProviderRegistry {

    private final Map<String, OAuthProvider> providers;

    public OAuthProviderRegistry(List<OAuthProvider> providers) {
        this.providers = providers.stream()
                .filter(OAuthProvider::enabled)
                .collect(Collectors.toMap(OAuthProvider::channel, Function.identity()));
    }

    public List<String> enabledChannels() {
        return providers.keySet().stream().sorted().toList();
    }

    public OAuthProvider require(String channel) {
        OAuthProvider provider = providers.get(channel);
        if (provider == null) {
            throw new BizException("该登录方式未开启");
        }
        return provider;
    }
}