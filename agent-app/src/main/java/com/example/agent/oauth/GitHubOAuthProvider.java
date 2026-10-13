package com.example.agent.oauth;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.agent.error.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * GitHub OAuth（Authorization Code 流程）
 */
@Slf4j
@Component
public class GitHubOAuthProvider implements OAuthProvider {

    private static final String CHANNEL = "github";
    private static final String AUTHORIZE = "https://github.com/login/oauth/authorize";
    private static final String ACCESS_TOKEN = "https://github.com/login/oauth/access_token";
    private static final String USER_API = "https://api.github.com/user";
    private static final String EMAILS_API = "https://api.github.com/user/emails";

    @Value("${agent.oauth.github.enabled:false}")
    private boolean enabled;

    @Value("${agent.oauth.github.client-id:}")
    private String clientId;

    @Value("${agent.oauth.github.client-secret:}")
    private String clientSecret;

    @Value("${agent.oauth.github.redirect-uri:}")
    private String redirectUri;

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public boolean enabled() {
        return enabled && StrUtil.isNotBlank(clientId) && StrUtil.isNotBlank(clientSecret);
    }

    @Override
    public String authorizeUrl(String state) {
        return AUTHORIZE + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state)
                + "&scope=" + encode("read:user user:email");
    }

    @Override
    public OAuthUser resolve(String code) {
        String accessToken = exchangeToken(code);
        return fetchUser(accessToken);
    }

    private String exchangeToken(String code) {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);
        try (HttpResponse response = HttpRequest.post(ACCESS_TOKEN)
                .header("Accept", "application/json")
                .body(body)
                .timeout(15_000)
                .execute()) {
            JSONObject json = JSON.parseObject(response.body());
            String token = json.getString("access_token");
            if (StrUtil.isBlank(token)) {
                log.warn("GitHub 换取 access_token 失败: {}", response.body());
                throw new BizException("GitHub 授权失败");
            }
            return token;
        }
    }

    private OAuthUser fetchUser(String accessToken) {
        try (HttpResponse response = HttpRequest.get(USER_API)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .timeout(15_000)
                .execute()) {
            JSONObject json = JSON.parseObject(response.body());
            String email = json.getString("email");
            if (StrUtil.isBlank(email)) {
                email = primaryEmail(accessToken);
            }
            return OAuthUser.builder()
                    .channel(CHANNEL)
                    .openId(json.getString("id"))
                    .login(json.getString("login"))
                    .avatar(json.getString("avatar_url"))
                    .email(email)
                    .build();
        }
    }

    /** 用户主邮箱未公开时，从 /user/emails 取主邮箱 */
    private String primaryEmail(String accessToken) {
        try (HttpResponse response = HttpRequest.get(EMAILS_API)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .timeout(15_000)
                .execute()) {
            JSONArray emails = JSON.parseArray(response.body());
            if (emails == null) {
                return null;
            }
            for (Object item : emails) {
                JSONObject email = (JSONObject) item;
                if (Boolean.TRUE.equals(email.getBoolean("primary")) && Boolean.TRUE.equals(email.getBoolean("verified"))) {
                    return email.getString("email");
                }
            }
        } catch (Exception ignore) {
            // 邮箱非必需
        }
        return null;
    }

    private String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}