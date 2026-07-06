package com.example.rbac.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Refresh Token 발급/검증/회전/폐기 + Access Token 블랙리스트 (Redis 저장)
 *
 * Redis 키 전략:
 * - refresh:{tokenHash}      → username (TTL = jwt.refresh-validity-ms)
 * - refresh:user:{username} → Set&lt;tokenHash&gt; (역방향 인덱스, 로그아웃 시 일괄 삭제용)
 * - blacklist:{jti}         → "revoked" (TTL = 등록 시점 기준 Access Token 남은 유효시간)
 *
 * 두 기능(Refresh Token, 블랙리스트)이 결국 같은 저장소(Redis)를 같은 방식(StringRedisTemplate)으로
 * 쓰기 때문에 하나의 서비스로 묶었다. 저장 수단이 서로 다를 때는 클래스를 분리하는 편이 맞지만
 * (예: 이전 버전에서 Refresh Token은 H2/JPA, 블랙리스트는 인메모리였을 때), 지금은 그 이유가 없다.
 *
 * 회전(rotation): refresh 요청마다 기존 토큰을 즉시 삭제(DEL)하고 새 토큰을 발급한다.
 * 탈취된 refresh token이 재사용(replay)되는 것을 막기 위함이다.
 */
@Service
public class TokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_TOKENS_KEY_PREFIX = "refresh:user:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
    private static final String REVOKED_MARKER = "revoked";

    private final StringRedisTemplate redisTemplate;
    private final long refreshValidityMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(StringRedisTemplate redisTemplate,
                         @Value("${jwt.refresh-validity-ms}") long refreshValidityMs) {
        this.redisTemplate = redisTemplate;
        this.refreshValidityMs = refreshValidityMs;
    }

    // ==================== Refresh Token ====================

    public String issueRefreshToken(String username) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + tokenHash, username, Duration.ofMillis(refreshValidityMs));
        redisTemplate.opsForSet().add(USER_TOKENS_KEY_PREFIX + username, tokenHash);

        return rawToken;
    }

    public RotationResult validateAndRotateRefreshToken(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);
        String key = REFRESH_KEY_PREFIX + tokenHash;

        String username = redisTemplate.opsForValue().get(key);
        if (username == null) {
            throw new BadCredentialsException("유효하지 않은 refresh token 입니다.");
        }

        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(USER_TOKENS_KEY_PREFIX + username, tokenHash);

        String newRawToken = issueRefreshToken(username);
        return new RotationResult(newRawToken, username);
    }

    public void revokeAllRefreshTokens(String username) {
        String userTokensKey = USER_TOKENS_KEY_PREFIX + username;
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userTokensKey);

        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            List<String> keys = tokenHashes.stream().map(h -> REFRESH_KEY_PREFIX + h).toList();
            redisTemplate.delete(keys);
        }

        redisTemplate.delete(userTokensKey);
    }

    // ==================== Access Token 블랙리스트 ====================

    public void blacklistAccessToken(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + jti, REVOKED_MARKER, ttl);
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
    }

    // ==================== 공통 ====================

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record RotationResult(String refreshToken, String username) {}
}
