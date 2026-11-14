package com.example.tokcar.server.ws;

import com.example.tokcar.server.chat.ChatService;
import com.example.tokcar.server.chat.ChatService.RoomChange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 방 만들기 · 초대 · 나가기를 실시간으로 알린다(REST 로 바꾼 뒤).
 * 멤버마다 자기가 보는 모양의 room.updated(안 읽은 수가 사람마다 다름) → 안내 글 message.new 순서.
 * 빠진 사람에겐 room.removed, 나가면서 끝난 경로 공유는 message.updated.
 */
@Component
public class RoomEvents {

    private final ChatService chat;
    private final Sessions sessions;
    private final ObjectMapper json;

    RoomEvents(ChatService chat, Sessions sessions, ObjectMapper json) {
        this.chat = chat;
        this.sessions = sessions;
        this.json = json;
    }

    public void changed(RoomChange c) {
        for (long uid : c.members()) roomUpdated(uid, c.roomId());
        if (c.endedRoute() != null) send(c.members(), "message.updated", "message", c.endedRoute());
        if (c.system() != null) send(c.members(), "message.new", "message", c.system());
        if (!c.removed().isEmpty()) send(c.removed(), "room.removed", "roomId", c.roomId());
    }

    /** (개발용) 방의 대화 · 경로 공유를 모두 지웠다: room.cleared 로 앱이 그 방의 메시지를 비우게 하고, 방 모양(안 읽은 수 · 마지막 글)은 room.updated 로 다시 보낸다. */
    public void cleared(long roomId, List<Long> members) {
        send(members, "room.cleared", "roomId", roomId);
        for (long uid : members) roomUpdated(uid, roomId);
    }

    private void roomUpdated(long uid, long roomId) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", "room.updated");
        f.put("room", chat.roomFor(uid, roomId));
        sessions.sendToUsers(List.of(uid), json.writeValueAsString(f), null);
    }

    private void send(List<Long> to, String type, String key, Object value) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", type);
        f.put(key, value);
        sessions.sendToUsers(to, json.writeValueAsString(f), null);
    }
}
