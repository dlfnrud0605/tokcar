package com.example.tokcar.server.ws;

import com.example.tokcar.server.chat.ChatService;
import com.example.tokcar.server.chat.LiveRoutes;
import com.example.tokcar.server.chat.Views.Live;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * route.live 를 방 사람들에게 넘긴다. 같은 공유는 {@link #MIN_GAP_MS} 에 한 번까지만 보내고,
 * 그 사이에 더 오면 메모리 값만 바뀐 채 기다렸다가 마지막 값을 한 번 보낸다.
 */
@Component
class RouteLiveRelay {

    static final long MIN_GAP_MS = 2_000;

    private final ChatService chat;
    private final LiveRoutes live;
    private final Sessions sessions;
    private final ObjectMapper json;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "route-live-relay");
        t.setDaemon(true);
        return t;
    });

    private final Map<Long, Long> lastSentAt = new ConcurrentHashMap<>();
    /** 기다리는 중인 공유 → 보낸 연결(그 연결은 빼고 보냄). */
    private final Map<Long, String> pending = new ConcurrentHashMap<>();

    RouteLiveRelay(ChatService chat, LiveRoutes live, Sessions sessions, ObjectMapper json) {
        this.chat = chat;
        this.live = live;
        this.sessions = sessions;
        this.json = json;
    }

    void relay(long roomId, long messageId, String fromSession) {
        long now = System.currentTimeMillis();
        long last = lastSentAt.getOrDefault(messageId, 0L);
        if (now - last >= MIN_GAP_MS) {
            lastSentAt.put(messageId, now);
            send(roomId, messageId, fromSession);
        } else if (pending.putIfAbsent(messageId, fromSession) == null) {
            timer.schedule(() -> {
                String from = pending.remove(messageId);
                lastSentAt.put(messageId, System.currentTimeMillis());
                send(roomId, messageId, from);
            }, last + MIN_GAP_MS - now, TimeUnit.MILLISECONDS);
        }
    }

    /** 공유가 끝나면 기록을 지운다. */
    void forget(long messageId) {
        lastSentAt.remove(messageId);
        pending.remove(messageId);
    }

    private void send(long roomId, long messageId, String fromSession) {
        Live l = live.get(messageId);
        if (l == null) return; // 그 사이에 끝남
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", "route.live");
        f.put("roomId", roomId);
        f.put("messageId", messageId);
        f.put("live", l);
        sessions.sendToUsers(chat.memberIds(roomId), json.writeValueAsString(f), fromSession);
    }

    @PreDestroy
    void stop() {
        timer.shutdownNow();
    }
}
