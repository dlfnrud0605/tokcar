package com.example.tokcar.server.dev;

import com.example.tokcar.server.auth.AuthInterceptor;
import com.example.tokcar.server.ws.RoomEvents;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 개발 · 시연용(local 프로필에서만 있음). prod 에는 이 주소가 없다. */
@RestController
@Profile("local")
class DevController {

    private final DevReset reset;
    private final RoomEvents events;

    DevController(DevReset reset, RoomEvents events) {
        this.reset = reset;
        this.events = events;
    }

    /** 방을 처음 상태로 돌린다(대화 · 경로 공유 삭제, 최근 목적지 처음 값). 방 사람들에겐 room.cleared 가 간다. */
    @PostMapping("/api/dev/rooms/{roomId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetRoom(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable long roomId) {
        events.cleared(roomId, reset.resetRoom(uid, roomId));
    }
}
