package org.dromara.warm.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 登录令牌签发与校验（HMAC-SHA256）。
 * <p>
 * 令牌格式 {@code base64(userName).base64(expireEpochMs).base64(hmac)}；HMAC 覆盖前两段，
 * 密钥来自 {@code warm-flow-demo.auth-secret}（未配置时启动随机生成，重启后所有会话失效）。
 * 校验在常量时间内比较，防时序侧信道。
 *
 * @author may
 * @since 2026/11/11
 */
@Component
public class DemoTokenService {

    /**
     * 令牌默认有效期：12 小时。
     */
    private static final long TTL_MS = 12 * 60 * 60 * 1000L;

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();
    private final SecureRandom random = new SecureRandom();

    public DemoTokenService(@Value("${warm-flow-demo.auth-secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            this.secret = generated;
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 为用户签发令牌。
     *
     * @param userName 用户名
     * @return 令牌
     */
    public String issue(String userName) {
        long expire = System.currentTimeMillis() + TTL_MS;
        String payload = encoder.encodeToString(userName.getBytes(StandardCharsets.UTF_8))
            + "." + encoder.encodeToString(Long.toString(expire).getBytes(StandardCharsets.UTF_8));
        return payload + "." + encoder.encodeToString(hmac(payload));
    }

    /**
     * 校验令牌并返回用户名；无效、过期或签名不符返回 {@code null}。
     *
     * @param token 令牌
     * @return 用户名或 {@code null}
     */
    public String verify(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String payload = parts[0] + "." + parts[1];
        byte[] expected = hmac(payload);
        byte[] actual;
        try {
            actual = decoder.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return null;
        }
        long expire;
        try {
            expire = Long.parseLong(new String(decoder.decode(parts[1]), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
        if (expire < System.currentTimeMillis()) {
            return null;
        }
        return new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
    }

    /**
     * 令牌有效期（毫秒），供 Cookie Max-Age 使用。
     */
    public long ttlMillis() {
        return TTL_MS;
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
