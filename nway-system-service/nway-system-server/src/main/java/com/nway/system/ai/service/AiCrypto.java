package com.nway.system.ai.service;

import com.nway.system.common.exception.NwaySystemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模型 API Key 的对称加解密（AES-GCM）。
 * <p>
 * 密文格式 {@code v1:base64(iv||ciphertext)}；无前缀的历史明文按原样返回，便于种子数据导入。
 *
 * @author nway
 */
@Component
public class AiCrypto {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AiCrypto(@Value("${nway.ai.crypto-key:}") String configuredKey) {
        String material = configuredKey == null || configuredKey.isBlank()
                ? "nway-ai-default-crypto-key-please-change"
                : configuredKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化 AI 密钥失败", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new NwaySystemException("模型密钥加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (!cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new NwaySystemException("模型密钥解密失败，请重新配置", e);
        }
    }
}
