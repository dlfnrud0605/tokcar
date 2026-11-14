package com.example.tokcar.server.dev;

import com.example.tokcar.server.chat.LiveRoutes;
import com.example.tokcar.server.common.ApiException;
import com.example.tokcar.server.domain.MessageLocationRepository;
import com.example.tokcar.server.domain.MessageRepository;
import com.example.tokcar.server.domain.Room;
import com.example.tokcar.server.domain.RoomMember;
import com.example.tokcar.server.domain.RoomMemberRepository;
import com.example.tokcar.server.domain.RoomRepository;
import com.example.tokcar.server.domain.RouteShareRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 전에 방을 처음 상태로 돌린다(local 프로필에서만 있음). 그 방의 대화 · 현재 위치 · 경로 공유를 모두 지우고,
 * 읽음 위치와 마지막 글 표시를 비우고, 「이 방에서 간 곳」을 처음 값으로 되돌린다. 방 자체와 멤버는 그대로다.
 */
@Service
@Profile("local")
@Transactional
public class DevReset {

    private static final Logger log = LoggerFactory.getLogger(DevReset.class);

    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final MessageRepository messages;
    private final MessageLocationRepository locations;
    private final RouteShareRepository routes;
    private final LiveRoutes live;
    private final DevSeed seed;

    DevReset(RoomRepository rooms, RoomMemberRepository members, MessageRepository messages, MessageLocationRepository locations,
             RouteShareRepository routes, LiveRoutes live, DevSeed seed) {
        this.rooms = rooms;
        this.members = members;
        this.messages = messages;
        this.locations = locations;
        this.routes = routes;
        this.live = live;
        this.seed = seed;
    }

    /** 방 멤버만 할 수 있다. 돌려주는 값은 알려야 할 멤버 id. */
    public List<Long> resetRoom(long uid, long roomId) {
        if (members.findById(new RoomMember.Key(roomId, uid)).isEmpty()) throw ApiException.notMember();
        List<Long> ids = messages.idsOf(roomId);
        live.forgetRoom(roomId, ids);
        locations.deleteAllOf(roomId);
        routes.deleteAllOf(roomId);
        messages.deleteAllOf(roomId);    // 영속성 컨텍스트도 비운다: 아래에서 방 · 멤버를 다시 읽는다
        Room room = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("방"));
        room.setLastMsgId(null);
        List<RoomMember> all = members.findByIdRoomId(roomId);
        all.forEach(RoomMember::resetRead);
        seed.resetPlacesOf(room);
        log.info("「{}」 방을 처음 상태로 돌렸음(메시지 {}개 삭제)", room.getName(), ids.size());
        return all.stream().map(RoomMember::getUserId).toList();
    }
}
