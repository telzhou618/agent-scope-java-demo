package com.example.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 导出文件下载链接签名：HMAC-SHA256(fileName:expires)，链接在有效期内免登录可下载
 */
@Service
public class DownloadSignService {

    /**
     * 链接有效期：7 天（与 sa-token 登录有效期一致）
     */
    private static final long EXPIRE_MILLIS = 7 * 24 * 3600 * 1000L;

    private final byte[] secret;

    public DownloadSignService(@Value("${agent.download.secret:dev-download-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成签名 URL 的查询串：expires=<毫秒时间戳>&sign=<hex>
     */
    public String signQuery(String fileName) {
        long expires = System.currentTimeMillis() + EXPIRE_MILLIS;
        return "expires=" + expires + "&sign=" + hmac(fileName, expires);
    }

    public boolean verify(String fileName, long expires, String sign) {
        if (sign == null || expires < System.currentTimeMillis()) {
            return false;
        }
        String expected = hmac(fileName, expires);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sign.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String fileName, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((fileName + ":" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }
}
