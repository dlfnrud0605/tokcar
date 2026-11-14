package com.example.tokcar.server.ws;

import com.example.tokcar.server.chat.ChatService;
import com.example.tokcar.server.chat.Views.MessageView;
import com.example.tokcar.server.chat.Views.ReadMark;
import com.example.tokcar.server.common.ApiException;
import com.example.tokcar.server.common.Check;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * /ws 연결 하나. 앱 → 서버 프레임을 받아 {@link ChatService} 로 처리하고,
 * 결과를 보낸 연결(ack · error)과 방 사람들(message.new · message.updated · route.live · read)에게 알린다.
 */
@Component
class ChatSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketHandler.class);

    private final ChatService chat;
    private final Sessions sessions;
    private final RouteLiveRelay relay;
    private final ObjectMapper json;

    ChatSocketHandler(ChatService chat, Sessions sessions, RouteLiveRelay relay, ObjectMapper json) {
        this.chat = chat;
        this.sessions = sessions;
        this.relay = relay;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        sessions.add(ws, uid(ws), (String) ws.getAttributes().get(WsAuth.TOKEN));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        sessions.remove(ws);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        String ref = null;
        try {
            JsonNode node = json.readTree(message.getPayload());
            if (node == null || !node.isObject()) throw ApiException.badRequest("JSON 객체여야 해요");
            ref = node.hasNonNull("ref") ? node.get("ref").asString() : null;
            String type = node.hasNonNull("type") ? node.get("type").asString() : "";
            handle(ws, uid(ws), type, ref, node);
        } catch (ApiException e) {
            error(ws, ref, e.code(), e.getMessage());
        } catch (JacksonException e) {
            error(ws, ref, "BAD_REQUEST", "프레임 모양이 맞지 않아요");
        } catch (RuntimeException e) {
            log.error("프레임 처리 실패", e);
            error(ws, ref, "INTERNAL", "서버에서 문제가 생겼어요");
        }
    }

    private void handle(WebSocketSession ws, long uid, String type, String ref, JsonNode node) {
        switch (type) {
            case "text.send" -> {
                var f = json.treeToValue(node, Frames.TextSend.class);
                created(ws, ref, chat.sendText(uid, room(f.roomId()), f.text()));
            }
            case "location.send" -> {
                var f = json.treeToValue(node, Frames.LocationSend.class);
                created(ws, ref, chat.sendLocation(uid, room(f.roomId()), f.location()));
            }
            case "route.start" -> {
                var f = json.treeToValue(node, Frames.RouteStart.class);
                created(ws, ref, chat.startRoute(uid, room(f.roomId()), f.from(), f.dest(), f.live()));
            }
            case "route.live" -> {
                var f = json.treeToValue(node, Frames.RouteLive.class);
                long roomId = room(f.roomId());
                relay.relay(roomId, chat.updateLive(uid, roomId, f.toLive()), ws.getId());
            }
            case "route.dest" -> {
                var f = json.treeToValue(node, Frames.RouteDest.class);
                updated(chat.changeDest(uid, room(f.roomId()), f.dest()));
            }
            case "route.end" -> {
                var f = json.treeToValue(node, Frames.RouteEnd.class);
                MessageView m = chat.endRoute(uid, room(f.roomId()), f.reason());
                relay.forget(m.id());
                updated(m);
            }
            case "read" -> {
                var f = json.treeToValue(node, Frames.Read.class);
                long roomId = room(f.roomId());
                ReadMark r = chat.read(uid, roomId, f.msgId());
                if (r != null) {
                    Map<String, Object> out = frame("read");
                    out.put("roomId", roomId);
                    out.put("userId", r.userId());
                    out.put("msgId", r.msgId());
                    sessions.sendToUsers(chat.memberIds(roomId), write(out), null);
                }
            }
            default -> throw ApiException.badRequest("모르는 type: " + type);
        }
    }

    /** 새 메시지: 보낸 연결엔 ack, 방 사람들(보낸 사람의 다른 기기 포함)에겐 message.new. */
    private void created(WebSocketSession ws, String ref, MessageView m) {
        Map<String, Object> ack = frame("ack");
        ack.put("ref", ref);
        ack.put("message", m);
        sessions.send(ws.getId(), write(ack));
        Map<String, Object> out = frame("message.new");
        out.put("message", m);
        sessions.sendToUsers(chat.memberIds(m.roomId()), write(out), ws.getId());
    }

    /** 경로 공유 카드가 바뀜: 방 전원에게 같은 id 카드를 통째로. */
    private void updated(MessageView m) {
        Map<String, Object> out = frame("message.updated");
        out.put("message", m);
        sessions.sendToUsers(chat.memberIds(m.roomId()), write(out), null);
    }

    private void error(WebSocketSession ws, String ref, String code, String message) {
        Map<String, Object> out = frame("error");
        if (ref != null) out.put("ref", ref);
        out.put("code", code);
        out.put("message", message);
        sessions.send(ws.getId(), write(out));
    }

    private static Map<String, Object> frame(String type) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", type);
        return f;
    }

    private String write(Object o) {
        return json.writeValueAsString(o);
    }

    private static long room(Long roomId) {
        return Check.present(roomId, "roomId");
    }

    private static long uid(WebSocketSession ws) {
        return (Long) ws.getAttributes().get(WsAuth.UID);
    }
}
