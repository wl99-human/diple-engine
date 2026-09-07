package com.google.payments.diple.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class HmacSignatureValidator {

    private final String secretKey;

    public HmacSignatureValidator(@Value("${diple.security.hmac-secret:diple-prod-faang-secret-key-2026-sha256}") String secretKey) {
        this.secretKey = secretKey;
    }

    public String computeHmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failure", e);
        }
    }

    public boolean isValidSignature(String payload, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        String expectedSignature = computeHmacSha256(payload);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
