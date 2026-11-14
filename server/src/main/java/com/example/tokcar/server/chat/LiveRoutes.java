package com.example.tokcar.server.chat;

import com.example.tokcar.server.chat.Views.Live;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 경로 공유의 실시간 값(차 위치 · 남은 시간 · traffic). DB 에 저장하지 않고 마지막 값만 메모리에 둔다.
 * 서버를 다시 켜면 비지만, 차가 5초 안에 다시 보낸다. 서버를 여러 대로 늘리면 Redis 로 옮긴다.
 */
@Component
public class LiveRoutes {

    private record Owner(long roomId, long userId) {
    }

    /** 메시지 id → 마지막 값. */
    private final Map<Long, Live> live = new ConcurrentHashMap<>();
    /** (방, 사람) → 진행 중인 경로 공유 메시지 id. 5초마다 DB 를 보지 않으려고 둔다(없으면 DB 에서 찾음). */
    private final Map<Owner, Long> active = new ConcurrentHashMap<>();

    public Live get(long messageId) {
        return live.get(messageId);
    }

    void put(long messageId, Live value) {
        live.put(messageId, value);
    }

    Long activeOf(long roomId, long userId) {
        return active.get(new Owner(roomId, userId));
    }

    void started(long roomId, long userId, long messageId) {
        active.put(new Owner(roomId, userId), messageId);
    }

    /** 방의 실시간 값을 모두 비운다(개발용 방 초기화: 메시지를 지웠으니 가리킬 곳이 없다). */
    public void forgetRoom(long roomId, Collection<Long> messageIds) {
        active.keySet().removeIf(o -> o.roomId() == roomId);
        messageIds.forEach(live::remove);
    }

    void ended(long roomId, long userId, long messageId) {
        active.remove(new Owner(roomId, userId), messageId);
        live.remove(messageId);
    }
}
