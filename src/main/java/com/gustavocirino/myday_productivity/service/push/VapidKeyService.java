package com.gustavocirino.myday_productivity.service.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;

@Service
public class VapidKeyService {

    private final String configuredPublicKey;
    private final String configuredPrivateKey;

    private volatile ECPublicKey publicKey;
    private volatile ECPrivateKey privateKey;

    public VapidKeyService(
            @Value("${webpush.vapid.publicKey:}") String configuredPublicKey,
            @Value("${webpush.vapid.privateKey:}") String configuredPrivateKey) {
        this.configuredPublicKey = configuredPublicKey == null ? "" : configuredPublicKey.trim();
        this.configuredPrivateKey = configuredPrivateKey == null ? "" : configuredPrivateKey.trim();
    }

    public synchronized void ensureInitialized() {
        if (publicKey != null && privateKey != null)
            return;

        try {
            if (!configuredPublicKey.isBlank() && !configuredPrivateKey.isBlank()) {
                ECParameterSpec ecSpec = getEcSpec();
                this.publicKey = decodePublicKey(configuredPublicKey, ecSpec);
                this.privateKey = decodePrivateKey(configuredPrivateKey, ecSpec);
                return;
            }

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = kpg.generateKeyPair();
            this.publicKey = (ECPublicKey) keyPair.getPublic();
            this.privateKey = (ECPrivateKey) keyPair.getPrivate();
        } catch (NoSuchAlgorithmException | java.security.InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Falha ao gerar chaves VAPID", e);
        }
    }

    public ECPublicKey getPublicKey() {
        ensureInitialized();
        return publicKey;
    }

    public ECPrivateKey getPrivateKey() {
        ensureInitialized();
        return privateKey;
    }

    public String getPublicKeyBase64Url() {
        ensureInitialized();
        byte[] uncompressed = encodePublicKeyUncompressed(publicKey);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
    }

    private static ECParameterSpec getEcSpec() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao obter parâmetros EC secp256r1", e);
        }
    }

    private static ECPublicKey decodePublicKey(String base64Url, ECParameterSpec ecSpec) {
        byte[] decoded = Base64.getUrlDecoder().decode(base64Url);
        if (decoded.length != 65 || decoded[0] != 0x04) {
            throw new IllegalArgumentException("PublicKey VAPID inválida (esperado formato não-comprimido)");
        }

        byte[] xb = new byte[32];
        byte[] yb = new byte[32];
        System.arraycopy(decoded, 1, xb, 0, 32);
        System.arraycopy(decoded, 33, yb, 0, 32);

        ECPoint point = new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb));
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            return (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(point, ecSpec));
        } catch (Exception e) {
            throw new IllegalArgumentException("Falha ao decodificar public key VAPID", e);
        }
    }

    private static ECPrivateKey decodePrivateKey(String base64Url, ECParameterSpec ecSpec) {
        byte[] decoded = Base64.getUrlDecoder().decode(base64Url);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("PrivateKey VAPID inválida (esperado 32 bytes)");
        }

        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            return (ECPrivateKey) kf.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, decoded), ecSpec));
        } catch (Exception e) {
            throw new IllegalArgumentException("Falha ao decodificar private key VAPID", e);
        }
    }

    private static byte[] encodePublicKeyUncompressed(ECPublicKey key) {
        ECPoint w = key.getW();
        byte[] x = toUnsignedFixedLength(w.getAffineX(), 32);
        byte[] y = toUnsignedFixedLength(w.getAffineY(), 32);

        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(x, 0, out, 1, 32);
        System.arraycopy(y, 0, out, 33, 32);
        return out;
    }

    private static byte[] toUnsignedFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length)
            return raw;

        byte[] out = new byte[length];
        int srcPos = Math.max(0, raw.length - length);
        int copyLen = Math.min(raw.length, length);
        System.arraycopy(raw, srcPos, out, length - copyLen, copyLen);
        return out;
    }
}
