package com.example.tokcar.server.chat;

import com.example.tokcar.server.auth.AuthInterceptor;
import com.example.tokcar.server.chat.Views.FriendView;
import com.example.tokcar.server.chat.Views.MessagesPage;
import com.example.tokcar.server.chat.Views.Place;
import com.example.tokcar.server.chat.Views.RoomPlaceView;
import com.example.tokcar.server.chat.Views.RoomView;
import com.example.tokcar.server.ws.RoomEvents;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 친구 · 방 · 메시지 · 이 방에서 간 곳 조회(보내기는 WebSocket). */
@RestController
public class RoomController {

    public record NewRoom(String name, List<Long> memberIds) {
    }

    public record Invite(List<Long> userIds) {
    }

    private final ChatService chat;
    private final RoomEvents events;

    public RoomController(ChatService chat, RoomEvents events) {
        this.chat = chat;
        this.events = events;
    }

    /** 방 만들기: 나 + 고른 친구. 만든 방(내가 보는 모양)을 돌려주고, 멤버 모두에게 room.updated · 안내 글을 보낸다. */
    @PostMapping("/api/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomView create(@RequestAttribute(AuthInterceptor.UID) long uid, @RequestBody NewRoom req) {
        var change = chat.createRoom(uid, req.name(), req.memberIds());
        events.changed(change);
        return chat.roomFor(uid, change.roomId());
    }

    /** 초대: 내 친구 중 방에 없는 사람을 넣는다. 바뀐 방을 돌려준다. */
    @PostMapping("/api/rooms/{roomId}/members")
    public RoomView invite(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable long roomId, @RequestBody Invite req) {
        var change = chat.invite(uid, roomId, req.userIds());
        events.changed(change);
        return chat.roomFor(uid, roomId);
    }

    /** 나가기. 내 다른 기기에도 room.removed 가 간다. */
    @DeleteMapping("/api/rooms/{roomId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable long roomId) {
        events.changed(chat.leave(uid, roomId));
    }

    @GetMapping("/api/friends")
    public List<FriendView> friends(@RequestAttribute(AuthInterceptor.UID) long uid) {
        return chat.friends(uid);
    }

    @GetMapping("/api/rooms")
    public List<RoomView> rooms(@RequestAttribute(AuthInterceptor.UID) long uid) {
        return chat.rooms(uid);
    }

    @GetMapping("/api/rooms/{roomId}/messages")
    public MessagesPage messages(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable long roomId,
                                 @RequestParam(required = false) Long before,
                                 @RequestParam(required = false) Long after,
                                 @RequestParam(required = false) Integer limit) {
        return chat.messages(uid, roomId, before, after, limit);
    }

    @GetMapping("/api/rooms/{roomId}/places")
    public List<RoomPlaceView> places(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable long roomId) {
        return chat.places(uid, roomId);
    }

    /** 차량 앱이 이 방에서 경로 안내를 시작할 때. 반영된 뒤의 목록(최신순 최대 3개). */
    @PostMapping("/api/rooms/{roomId}/places")
    public List<RoomPlaceView> addPlace(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable long roomId,
                                        @RequestBody Place place) {
        return chat.addPlace(uid, roomId, place);
    }
}
