package com.example.tokcar.server.ws;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** 열린 WebSocket 연결. 한 사람이 차 + 폰처럼 여러 개를 가질 수 있다. 서버 한 대 기준(여러 대면 Redis Pub/Sub). */
@Component
public class Sessions {

    private static final Logger log = LoggerFactory.getLogger(Sessions.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT = 512 * 1024;

    private record Conn(long uid, String token, WebSocketSession ws) {
    }

    private final Map<String, Conn> byId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> byUser = new ConcurrentHashMap<>();

    void add(WebSocketSession ws, long uid, String token) {
        // 여러 스레드가 같은 연결로 보내도 안전하게(느린 연결이 다른 사람을 막지 않게 시간 · 크기 제한)
        var safe = new ConcurrentWebSocketSessionDecorator(ws, SEND_TIME_LIMIT_MS, BUFFER_LIMIT);
        byId.put(ws.getId(), new Conn(uid, token, safe));
        byUser.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(ws.getId());
    }

    void remove(WebSocketSession ws) {
        Conn c = byId.remove(ws.getId());
        if (c == null) return;
        byUser.computeIfPresent(c.uid(), (k, set) -> {
            set.remove(ws.getId());
            return set.isEmpty() ? null : set;
        });
    }

    /** 그 연결 하나에만. */
    void send(String sessionId, String json) {
        Conn c = byId.get(sessionId);
        if (c != null) push(c, json);
    }

    /** 이 사람들의 모든 연결에. exceptSessionId 연결은 빼고(null 이면 다). */
    public void sendToUsers(Collection<Long> uids, String json, String exceptSessionId) {
        for (long uid : uids) {
            for (String sid : byUser.getOrDefault(uid, Set.of())) {
                if (sid.equals(exceptSessionId)) continue;
                Conn c = byId.get(sid);
                if (c != null) push(c, json);
            }
        }
    }

    /** 로그아웃: 그 토큰으로 열린 연결을 닫는다. */
    public void closeByToken(String token) {
        for (Conn c : byId.values()) {
            if (!c.token().equals(token)) continue;
            try {
                c.ws().close(CloseStatus.NORMAL.withReason("logout"));
            } catch (IOException e) {
                log.debug("연결 닫기 실패 {}", c.ws().getId(), e);
            }
        }
    }

    private void push(Conn c, String json) {
        try {
            c.ws().sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            // 끊긴 연결: 닫히면 afterConnectionClosed 에서 빠진다
            log.debug("보내기 실패 {}: {}", c.ws().getId(), e.getMessage());
        }
    }
}
