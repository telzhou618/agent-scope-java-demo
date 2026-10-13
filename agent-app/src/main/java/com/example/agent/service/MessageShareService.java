package com.example.agent.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.agent.error.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 消息分享：短链 token = MD5(userId:messageId:日期:盐)，确定性生成——同一用户同一天对同一消息
 * 重复分享天然得到同一 token（无需去重键）；加盐防止外人枚举伪造。
 * Redis 只存一个 share:token:<md5> → messageId 映射（7 天自动过期）；
 * 消息删除不清理 Redis，访问时消息不存在即视为已失效。
 */
@Service
public class MessageShareService {

    /**
     * 分享链接有效期：7 天
     */
    private static final Duration EXPIRE = Duration.ofDays(7);

    private static final String TOKEN_KEY = "share:token:";

    private final StringRedisTemplate redis;
    private final String salt;

    public MessageShareService(StringRedisTemplate redis,
                               @Value("${agent.share.secret:${agent.download.secret:dev-download-secret}}") String salt) {
        this.redis = redis;
        this.salt = salt;
    }

    /**
     * 生成分享路径：/share/message?t=<md5>
     */
    public String signPath(long messageId, long userId) {
        String token = tokenFor(messageId, userId);
        // 重复分享时刷新有效期
        redis.opsForValue().set(TOKEN_KEY + token, String.valueOf(messageId), EXPIRE);
        return "/share/message?t=" + token;
    }

    /**
     * 解析分享 token；无效或已过期抛业务异常
     */
    public long resolve(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("分享链接无效或已失效");
        }
        String messageId = redis.opsForValue().get(TOKEN_KEY + token);
        if (messageId == null) {
            throw new BizException("分享链接无效或已失效");
        }
        return Long.parseLong(messageId);
    }

    private String tokenFor(long messageId, long userId) {
        return DigestUtil.md5Hex("message-share:" + userId + ":" + messageId + ":" + LocalDate.now() + ":" + salt);
    }
}
