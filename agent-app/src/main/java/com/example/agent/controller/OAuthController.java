package com.example.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.agent.dto.Result;
import com.example.agent.oauth.OAuthProvider;
import com.example.agent.oauth.OAuthProviderRegistry;
import com.example.agent.oauth.OAuthUser;
import com.example.agent.service.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 第三方 OAuth 登录：渠道可扩展，各渠道 yml 开关控制
 */
@Slf4j
@Controller
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final String STATE_KEY = "oauth:state:";

    private final OAuthProviderRegistry registry;
    private final OAuthService oauthService;
    private final StringRedisTemplate redis;

    @Value("${agent.oauth.frontend-redirect:http://localhost:5174}")
    private String frontendRedirect;

    @GetMapping("/providers")
    @ResponseBody
    public Result<List<String>> providers() {
        return Result.okData(registry.enabledChannels());
    }

    @GetMapping("/{channel}/authorize")
    public String authorize(@PathVariable String channel) {
        OAuthProvider provider = registry.require(channel);
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(STATE_KEY + state, channel, STATE_TTL);
        return "redirect:" + provider.authorizeUrl(state);
    }

    @GetMapping("/{channel}/callback")
    public String callback(@PathVariable String channel,
                           @RequestParam(required = false) String code,
                           @RequestParam(required = false) String state) {
        try {
            OAuthProvider provider = registry.require(channel);
            if (state == null || !channel.equals(redis.opsForValue().get(STATE_KEY + state))) {
                return redirectWithError("授权状态校验失败，请重试");
            }
            redis.delete(STATE_KEY + state);
            if (code == null || code.isBlank()) {
                return redirectWithError("未取得授权码");
            }
            OAuthUser oauthUser = provider.resolve(code);
            long userId = oauthService.loginOrRegister(oauthUser);
            StpUtil.login(userId);
            return "redirect:" + frontendRedirect + "/login?token=" + StpUtil.getTokenValue();
        } catch (Exception e) {
            log.error("OAuth 登录失败, channel={}", channel, e);
            return redirectWithError(e.getMessage());
        }
    }

    private String redirectWithError(String message) {
        return "redirect:" + frontendRedirect + "/login?oauthError="
                + java.net.URLEncoder.encode(message == null ? "OAuth 登录失败" : message,
                java.nio.charset.StandardCharsets.UTF_8);
    }
}