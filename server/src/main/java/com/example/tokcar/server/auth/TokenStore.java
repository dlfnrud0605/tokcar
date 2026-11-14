package com.example.tokcar.server.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 로그인 토큰. 1단계는 서버 메모리에만 둔다(서버를 다시 켜면 다시 로그인).
 * 2단계에서 액세스 토큰 + Redis 리프레시 토큰으로 바꾼다.
 */
@Component
public class TokenStore {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> users = new ConcurrentHashMap<>();

    public String issue(long userId) {
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        users.put(token, userId);
        return token;
    }

    public Optional<Long> userOf(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(users.get(token));
    }

    public void revoke(String token) {
        if (token != null) users.remove(token);
    }

    /** "Bearer xxx" 헤더에서 토큰만. 모양이 틀리면 null. */
    public static String fromHeader(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        String t = header.substring(7).strip();
        return t.isEmpty() ? null : t;
    }
}
