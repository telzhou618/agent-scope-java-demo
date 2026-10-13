package com.example.agent.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.agent.error.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 分享短链（消息/会话通用）：token = MD5(scope:userId:目标ID:日期:盐)，确定性生成——
 * 同一用户同一天对同一目标重复分享天然得到同一 token（无需去重键）；加盐防止外人枚举伪造。
 * Redis 只存一个 share:token:<scope>:<md5> → 目标ID 映射（7 天自动过期）；
 * 目标被删除不清理 Redis，访问时查不到即视为已失效。
 */
@Service
public class ShareService {

    /**
     * 分享链接有效期：7 天
     */
    private static final Duration EXPIRE = Duration.ofDays(7);

    private static final String TOKEN_KEY = "share:token:";
    private static final String SCOPE_MESSAGE = "msg";
    private static final String SCOPE_SESSION = "session";

    private final StringRedisTemplate redis;
    private final String salt;

    public ShareService(StringRedisTemplate redis,
                        @Value("${agent.share.secret:${agent.download.secret:dev-download-secret}}") String salt) {
        this.redis = redis;
        this.salt = salt;
    }

    /**
     * 消息分享路径：/share/message?t=<md5>
     */
    public String messageSharePath(long messageId, long userId) {
        String token = tokenFor(SCOPE_MESSAGE, String.valueOf(messageId), userId);
        redis.opsForValue().set(TOKEN_KEY + SCOPE_MESSAGE + ":" + token, String.valueOf(messageId), EXPIRE);
        return "/share/message?t=" + token;
    }

    /**
     * 会话分享路径：/share/session?t=<md5>
     */
    public String sessionSharePath(String sessionId, long userId) {
        String token = tokenFor(SCOPE_SESSION, sessionId, userId);
        // 值里带 userId：共享会话读历史时按槽位 userId:sessionId 取数
        redis.opsForValue().set(TOKEN_KEY + SCOPE_SESSION + ":" + token, userId + ":" + sessionId, EXPIRE);
        return "/share/session?t=" + token;
    }

    /**
     * 解析消息分享 token，返回消息 id；无效或已过期抛业务异常
     */
    public long resolveMessage(String token) {
        return Long.parseLong(resolve(SCOPE_MESSAGE, token));
    }

    /**
     * 解析会话分享 token，返回 [userId, sessionId]；无效或已过期抛业务异常
     */
    public String[] resolveSession(String token) {
        String value = resolve(SCOPE_SESSION, token);
        int split = value.indexOf(':');
        if (split <= 0) {
            throw new BizException("分享链接无效或已失效");
        }
        return new String[]{value.substring(0, split), value.substring(split + 1)};
    }

    private String resolve(String scope, String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("分享链接无效或已失效");
        }
        String value = redis.opsForValue().get(TOKEN_KEY + scope + ":" + token);
        if (value == null) {
            throw new BizException("分享链接无效或已失效");
        }
        return value;
    }

    private String tokenFor(String scope, String targetId, long userId) {
        return DigestUtil.md5Hex(scope + ":" + userId + ":" + targetId + ":" + LocalDate.now() + ":" + salt);
    }
}
