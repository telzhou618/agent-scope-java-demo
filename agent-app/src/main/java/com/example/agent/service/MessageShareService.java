package com.example.agent.service;

import com.example.agent.error.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 消息分享链接签名：HMAC-SHA256(messageId:expires)，链接在有效期内免登录可访问
 */
@Service
public class MessageShareService {

    /**
     * 分享链接有效期：7 天
     */
    private static final long EXPIRE_MILLIS = 7 * 24 * 3600 * 1000L;

    private final byte[] secret;

    public MessageShareService(@Value("${agent.share.secret:${agent.download.secret:dev-download-secret}}") String secret) {
        this.secret = ("message-share:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成签名分享路径（前端拼 origin 即可用）：/share/message?id=<id>&expires=<毫秒时间戳>&sign=<hex>
     */
    public String signPath(long messageId) {
        long expires = System.currentTimeMillis() + EXPIRE_MILLIS;
        return "/share/message?id=" + messageId + "&expires=" + expires + "&sign=" + hmac(messageId, expires);
    }

    /**
     * 验签失败抛业务异常
     */
    public void verifyOrThrow(long messageId, long expires, String sign) {
        if (sign == null || expires < System.currentTimeMillis()) {
            throw new BizException("分享链接无效或已过期");
        }
        String expected = hmac(messageId, expires);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8))) {
            throw new BizException("分享链接无效或已过期");
        }
    }

    private String hmac(long messageId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((messageId + ":" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }
}
