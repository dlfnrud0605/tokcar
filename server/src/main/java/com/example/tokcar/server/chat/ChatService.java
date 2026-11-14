package com.example.tokcar.server.chat;

import com.example.tokcar.server.chat.Views.FriendView;
import com.example.tokcar.server.chat.Views.Live;
import com.example.tokcar.server.chat.Views.MessageView;
import com.example.tokcar.server.chat.Views.MessagesPage;
import com.example.tokcar.server.chat.Views.Place;
import com.example.tokcar.server.chat.Views.ReadMark;
import com.example.tokcar.server.chat.Views.RoomPlaceView;
import com.example.tokcar.server.chat.Views.RoomView;
import com.example.tokcar.server.chat.Views.RouteView;
import com.example.tokcar.server.chat.Views.TrafficSeg;
import com.example.tokcar.server.chat.Views.UserView;
import com.example.tokcar.server.common.ApiException;
import com.example.tokcar.server.common.Check;
import com.example.tokcar.server.domain.FriendshipRepository;
import com.example.tokcar.server.domain.Message;
import com.example.tokcar.server.domain.MessageLocation;
import com.example.tokcar.server.domain.MessageLocationRepository;
import com.example.tokcar.server.domain.MessageRepository;
import com.example.tokcar.server.domain.Room;
import com.example.tokcar.server.domain.RoomMember;
import com.example.tokcar.server.domain.RoomMemberRepository;
import com.example.tokcar.server.domain.RoomPlace;
import com.example.tokcar.server.domain.RoomPlaceRepository;
import com.example.tokcar.server.domain.RoomRepository;
import com.example.tokcar.server.domain.RouteShare;
import com.example.tokcar.server.domain.RouteShareRepository;
import com.example.tokcar.server.domain.User;
import com.example.tokcar.server.domain.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 방 · 메시지 · 경로 공유 · 이 방에서 간 곳. REST 와 WebSocket 이 같이 쓴다(보내기 · 알리기는 부르는 쪽이). */
@Service
@Transactional
public class ChatService {

    static final int PAGE = 50;
    static final int PAGE_MAX = 100;
    static final int AFTER_MAX = 500;
    static final int TEXT_MAX = 1000;
    static final int TRAFFIC_MAX = 200;
    static final int REMAIN_MAX_SEC = 7 * 24 * 3600;

    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final RoomPlaceRepository places;
    private final MessageRepository messages;
    private final MessageLocationRepository locations;
    private final RouteShareRepository routes;
    private final LiveRoutes live;

    public ChatService(UserRepository users, FriendshipRepository friendships, RoomRepository rooms,
                       RoomMemberRepository members, RoomPlaceRepository places, MessageRepository messages,
                       MessageLocationRepository locations, RouteShareRepository routes, LiveRoutes live) {
        this.users = users;
        this.friendships = friendships;
        this.rooms = rooms;
        this.members = members;
        this.places = places;
        this.messages = messages;
        this.locations = locations;
        this.routes = routes;
        this.live = live;
    }

    // ───── 조회 ─────

    @Transactional(readOnly = true)
    public List<FriendView> friends(long uid) {
        List<Long> ids = friendships.findByIdUserId(uid).stream().map(f -> f.getFriendId()).toList();
        return users.findAllById(ids).stream()
                .sorted(Comparator.comparing(User::getName))
                .map(FriendView::of)
                .toList();
    }

    /** 내 방 목록. 최근 대화 순(대화가 없는 방은 뒤로). */
    @Transactional(readOnly = true)
    public List<RoomView> rooms(long uid) {
        List<RoomMember> mine = members.findByIdUserId(uid);
        List<RoomView> out = new ArrayList<>();
        for (Room r : rooms.findAllById(mine.stream().map(RoomMember::getRoomId).toList())) out.add(roomView(uid, r));
        out.sort(Comparator.comparing((RoomView v) -> v.lastMessage() == null ? 0L : v.lastMessage().id()).reversed());
        return out;
    }

    /** [uid] 가 보는 방 하나(멤버 · 읽은 곳 · 마지막 메시지 · 안 읽은 수). 방 목록과 room.updated 가 같이 쓴다. */
    @Transactional(readOnly = true)
    public RoomView roomFor(long uid, long roomId) {
        requireMember(roomId, uid);
        return roomView(uid, rooms.getReferenceById(roomId));
    }

    private RoomView roomView(long uid, Room r) {
        List<RoomMember> in = members.findByIdRoomId(r.getId());
        Map<Long, User> people = users.findAllById(in.stream().map(RoomMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        long myRead = in.stream().filter(m -> m.getUserId() == uid).findFirst().map(RoomMember::getLastReadMsgId).orElse(0L);
        MessageView last = r.getLastMsgId() == null ? null
                : messages.findById(r.getLastMsgId()).map(m -> toViews(List.of(m)).getFirst()).orElse(null);
        return new RoomView(r.getId(), r.getName(),
                in.stream().map(m -> UserView.of(people.get(m.getUserId()))).toList(),
                in.stream().map(m -> new ReadMark(m.getUserId(), m.getLastReadMsgId())).toList(),
                last, messages.unread(r.getId(), myRead, uid));
    }

    // ───── 방 만들기 · 초대 · 나가기 ─────

    /**
     * 방이 바뀐 결과. [members] = 지금 멤버(room.updated 받을 사람), [removed] = 방에서 빠진 사람(room.removed),
     * [system] = 서버가 남긴 안내 글, [endedRoute] = 나가면서 끝난 내 경로 공유 카드(없으면 null).
     */
    public record RoomChange(long roomId, List<Long> members, List<Long> removed, MessageView system, MessageView endedRoute) {
    }

    static final int ROOM_NAME_MAX = 30;
    static final int ROOM_MEMBERS_MAX = 50;

    /** 새 방: 나 + 고른 친구들. 이름이 비면 친구 이름을 이어 붙인다. 「아빠가 방을 만들었어요 · 민지, 엄마 초대」를 남긴다. */
    public RoomChange createRoom(long uid, String name, List<Long> memberIds) {
        List<User> picked = friendsToAdd(uid, memberIds, List.of());
        String names = picked.stream().map(User::getName).collect(Collectors.joining(", "));
        String n = Check.optText(name, "name", ROOM_NAME_MAX);
        if (n.isEmpty()) n = names.length() > ROOM_NAME_MAX ? names.substring(0, ROOM_NAME_MAX - 1) + "…" : names;
        Room room = rooms.save(new Room(n, uid));
        members.save(new RoomMember(room.getId(), uid));
        for (User u : picked) members.save(new RoomMember(room.getId(), u.getId()));
        members.flush();
        Message sys = system(room.getId(), Korean.ga(nameOf(uid)) + " 방을 만들었어요 · " + names + " 초대");
        return new RoomChange(room.getId(), memberIds(room.getId()), List.of(), view(sys), null);
    }

    /** 초대: 방 멤버가 자기 친구 중 아직 방에 없는 사람을 넣는다. 「아빠가 수진, 지우를 초대했어요」. */
    public RoomChange invite(long uid, long roomId, List<Long> userIds) {
        requireMember(roomId, uid);
        List<User> picked = friendsToAdd(uid, userIds, memberIds(roomId));
        for (User u : picked) members.save(new RoomMember(roomId, u.getId()));
        members.flush();
        String names = picked.stream().map(User::getName).collect(Collectors.joining(", "));
        Message sys = system(roomId, Korean.ga(nameOf(uid)) + " " + Korean.eul(names) + " 초대했어요");
        return new RoomChange(roomId, memberIds(roomId), List.of(), view(sys), null);
    }

    /** 나가기: 내 경로 공유가 진행 중이면 멈추고, 「민지가 나갔어요」를 남긴다. 방은 남은 사람이 없어도 지우지 않는다. */
    public RoomChange leave(long uid, long roomId) {
        RoomMember me = requireMember(roomId, uid);
        MessageView ended = routes.findActive(roomId, uid).map(r -> {
            r.end(RouteShare.State.STOPPED);
            live.ended(roomId, uid, r.getMessageId());
            return view(messages.getReferenceById(r.getMessageId()));
        }).orElse(null);
        members.delete(me);
        members.flush();
        Message sys = system(roomId, Korean.ga(nameOf(uid)) + " 나갔어요");
        return new RoomChange(roomId, memberIds(roomId), List.of(uid), view(sys), ended);
    }

    /** 넣을 사람 확인: 비어 있지 않고, 나 · 이미 멤버가 아니고, 내 친구여야 한다. 중복은 하나로. */
    private List<User> friendsToAdd(long uid, List<Long> ids, List<Long> already) {
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("초대할 친구를 골라 주세요");
        List<Long> want = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (want.contains(uid)) throw ApiException.badRequest("나는 이미 방에 있어요");
        if (want.stream().anyMatch(already::contains)) throw ApiException.conflict("ALREADY_MEMBER", "이미 방에 있는 사람이 있어요");
        if (already.size() + want.size() + (already.isEmpty() ? 1 : 0) > ROOM_MEMBERS_MAX)
            throw ApiException.badRequest("한 방에는 " + ROOM_MEMBERS_MAX + "명까지예요");
        var mine = friendships.findByIdUserId(uid).stream().map(f -> f.getFriendId()).collect(Collectors.toSet());
        if (!mine.containsAll(want)) throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_FRIEND", "친구만 초대할 수 있어요");
        Map<Long, User> byId = users.findAllById(want).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return want.stream().map(byId::get).toList();
    }

    private Message system(long roomId, String text) {
        Message m = messages.save(new Message(roomId, null, Message.Kind.SYSTEM, text));
        rooms.getReferenceById(roomId).setLastMsgId(m.getId());
        return m;
    }

    private String nameOf(long uid) {
        return users.findById(uid).map(User::getName).orElse("누군가");
    }

    /** before · after 중 하나만. 둘 다 없으면 최신 limit 개. 늘 오래된 것 → 새것 순. */
    @Transactional(readOnly = true)
    public MessagesPage messages(long uid, long roomId, Long before, Long after, Integer limit) {
        requireMember(roomId, uid);
        if (before != null && after != null) throw ApiException.badRequest("before 와 after 는 같이 쓸 수 없어요");
        List<Message> got;
        boolean more;
        if (after != null) {
            got = messages.after(roomId, after, PageRequest.of(0, AFTER_MAX + 1));
            more = got.size() > AFTER_MAX;
            if (more) got = got.subList(0, AFTER_MAX);
        } else {
            int n = limit == null ? PAGE : Check.range(limit, "limit", 1, PAGE_MAX);
            got = before == null ? messages.latest(roomId, PageRequest.of(0, n + 1))
                    : messages.before(roomId, before, PageRequest.of(0, n + 1));
            more = got.size() > n;
            got = new ArrayList<>(more ? got.subList(0, n) : got);
            Collections.reverse(got);
        }
        return new MessagesPage(toViews(got), more);
    }

    @Transactional(readOnly = true)
    public List<Long> memberIds(long roomId) {
        return members.findByIdRoomId(roomId).stream().map(RoomMember::getUserId).toList();
    }

    // ───── 이 방에서 간 곳 ─────

    @Transactional(readOnly = true)
    public List<RoomPlaceView> places(long uid, long roomId) {
        requireMember(roomId, uid);
        return placeList(roomId);
    }

    /** 이 방에서 경로 안내를 시작한 곳. 같은 곳(이름 + 주소)이면 맨 위로, 넘치면 오래된 것부터 지운다. */
    public List<RoomPlaceView> addPlace(long uid, long roomId, Place p) {
        requireMember(roomId, uid);
        Check.present(p, "place");
        String addr = Check.text(p.addr(), "addr", 120);
        String name = Check.optText(p.name(), "name", 60);
        if (name.isEmpty()) name = addr.length() > 60 ? addr.substring(0, 60) : addr;
        double lat = Check.lat(p.lat()), lon = Check.lon(p.lon());
        places.findByRoomIdAndNameAndAddr(roomId, name, addr).ifPresent(old -> {
            places.delete(old);
            places.flush(); // 지운 뒤에 넣어야 (방, 이름, 주소) 유니크에 안 걸린다
        });
        places.save(new RoomPlace(roomId, name, addr, lat, lon));
        List<RoomPlace> all = places.findByRoomIdOrderByIdDesc(roomId);
        if (all.size() > RoomPlace.MAX) places.deleteAll(all.subList(RoomPlace.MAX, all.size()));
        return placeList(roomId);
    }

    private List<RoomPlaceView> placeList(long roomId) {
        return places.findByRoomIdOrderByIdDesc(roomId).stream().limit(RoomPlace.MAX)
                .map(p -> new RoomPlaceView(p.getId(), p.getName(), p.getAddr(), p.getLat(), p.getLon()))
                .toList();
    }

    // ───── 보내기 ─────

    public MessageView sendText(long uid, long roomId, String text) {
        requireMember(roomId, uid);
        String t = Check.present(text, "text").strip();
        if (t.isEmpty()) throw ApiException.badRequest("text 값이 비어 있어요");
        if (t.length() > TEXT_MAX) throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_LONG", "글은 " + TEXT_MAX + "자까지예요");
        Message m = post(uid, roomId, Message.Kind.TEXT, t);
        return view(m);
    }

    public MessageView sendLocation(long uid, long roomId, Place p) {
        requireMember(roomId, uid);
        Check.present(p, "location");
        String addr = Check.text(p.addr(), "addr", 120);
        String name = Check.optText(p.name(), "name", 60);
        double lat = Check.lat(p.lat()), lon = Check.lon(p.lon());
        Message m = post(uid, roomId, Message.Kind.LOCATION, null);
        locations.save(new MessageLocation(m.getId(), name, addr, lat, lon));
        return view(m);
    }

    // ───── 경로 공유 ─────

    public MessageView startRoute(long uid, long roomId, Place from, Place dest, Live first) {
        requireMember(roomId, uid);
        Check.present(from, "from");
        Check.present(dest, "dest");
        String fromAddr = Check.text(from.addr(), "from.addr", 120);
        double fromLat = Check.lat(from.lat()), fromLon = Check.lon(from.lon());
        String destAddr = Check.text(dest.addr(), "dest.addr", 120);
        String destName = Check.optText(dest.name(), "dest.name", 60);
        double destLat = Check.lat(dest.lat()), destLon = Check.lon(dest.lon());
        Live firstLive = first == null ? null : checkLive(first);
        if (routes.findActive(roomId, uid).isPresent())
            throw ApiException.conflict("ROUTE_ACTIVE", "이 방에 진행 중인 경로 공유가 이미 있어요");

        Message m = post(uid, roomId, Message.Kind.ROUTE, null);
        routes.save(new RouteShare(m.getId(), fromAddr, fromLat, fromLon, destName, destAddr, destLat, destLon));
        live.started(roomId, uid, m.getId());
        if (firstLive != null) live.put(m.getId(), firstLive);
        return view(m);
    }

    /** 실시간 값만 메모리에 덮어쓴다(DB 에 쓰지 않음). 반영된 경로 공유 메시지 id. */
    @Transactional(readOnly = true)
    public long updateLive(long uid, long roomId, Live in) {
        requireMember(roomId, uid);
        Live checked = checkLive(in);
        long id = activeRouteId(uid, roomId);
        live.put(id, checked);
        return id;
    }

    public MessageView changeDest(long uid, long roomId, Place dest) {
        requireMember(roomId, uid);
        Check.present(dest, "dest");
        String addr = Check.text(dest.addr(), "dest.addr", 120);
        String name = Check.optText(dest.name(), "dest.name", 60);
        double lat = Check.lat(dest.lat()), lon = Check.lon(dest.lon());
        RouteShare r = routes.findActive(roomId, uid).orElseThrow(ChatService::noActive);
        r.changeDest(name, addr, lat, lon);
        return view(messages.getReferenceById(r.getMessageId()));
    }

    public MessageView endRoute(long uid, long roomId, String reason) {
        requireMember(roomId, uid);
        RouteShare.State how;
        try {
            how = RouteShare.State.valueOf(Check.present(reason, "reason"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("reason 은 ARRIVED 또는 STOPPED 예요");
        }
        if (how == RouteShare.State.ACTIVE) throw ApiException.badRequest("reason 은 ARRIVED 또는 STOPPED 예요");
        RouteShare r = routes.findActive(roomId, uid).orElseThrow(ChatService::noActive);
        r.end(how);
        live.ended(roomId, uid, r.getMessageId());
        return view(messages.getReferenceById(r.getMessageId()));
    }

    // ───── 읽음 ─────

    /** 여기까지 읽음. 지금 값보다 클 때만 바뀌고, 바뀌었으면 알릴 값. */
    public ReadMark read(long uid, long roomId, Long msgId) {
        RoomMember me = requireMember(roomId, uid);
        long id = Check.present(msgId, "msgId");
        Message m = messages.findById(id).filter(x -> x.getRoomId() == roomId)
                .orElseThrow(() -> ApiException.notFound("메시지"));
        return me.readUpTo(m.getId()) ? new ReadMark(uid, m.getId()) : null;
    }

    // ───── 정리 ─────

    /** 보낸 지 오래된 「현재 위치」 좌표를 지운다. 메시지는 남아서 앱엔 「위치를 보냈어요」로 보인다. */
    public int deleteLocationsSentBefore(Instant cut) {
        return locations.deleteSentBefore(cut);
    }

    // ───── 안쪽 ─────

    /** 메시지 저장 + 방 목록 순서 + 보낸 사람은 자기 메시지를 읽은 것으로. */
    private Message post(long uid, long roomId, Message.Kind kind, String text) {
        Message m = messages.save(new Message(roomId, uid, kind, text));
        rooms.getReferenceById(roomId).setLastMsgId(m.getId());
        members.getReferenceById(new RoomMember.Key(roomId, uid)).readUpTo(m.getId());
        return m;
    }

    private RoomMember requireMember(long roomId, long uid) {
        if (!rooms.existsById(roomId)) throw ApiException.notFound("방");
        return members.findById(new RoomMember.Key(roomId, uid)).orElseThrow(ApiException::notMember);
    }

    private long activeRouteId(long uid, long roomId) {
        Long cached = live.activeOf(roomId, uid);
        if (cached != null) return cached;
        RouteShare r = routes.findActive(roomId, uid).orElseThrow(ChatService::noActive);
        live.started(roomId, uid, r.getMessageId()); // 서버를 다시 켠 뒤 처음
        return r.getMessageId();
    }

    private static ApiException noActive() {
        return ApiException.conflict("NO_ACTIVE_ROUTE", "이 방에 진행 중인 내 경로 공유가 없어요");
    }

    private Live checkLive(Live in) {
        double lat = Check.lat(in.carLat()), lon = Check.lon(in.carLon());
        int remain = Check.range(in.remainSec(), "remainSec", 0, REMAIN_MAX_SEC);
        int pct = Check.range(in.pct(), "pct", 0, 100);
        Instant eta = Check.present(in.etaAt(), "etaAt");
        List<TrafficSeg> traffic = in.traffic();
        if (traffic != null) {
            if (traffic.size() > TRAFFIC_MAX) throw ApiException.badRequest("traffic 은 " + TRAFFIC_MAX + "칸까지예요");
            for (TrafficSeg s : traffic) {
                if (s == null || s.level() == null || !(s.from() >= 0 && s.from() < s.to() && s.to() <= 1))
                    throw ApiException.badRequest("traffic 칸은 [0 ≤ from < to ≤ 1, 단계] 여야 해요");
            }
            traffic = List.copyOf(traffic);
        }
        return new Live(lat, lon, remain, pct, eta, traffic, Instant.now());
    }

    private MessageView view(Message m) {
        return toViews(List.of(m)).getFirst();
    }

    /** 메시지들을 앱에 줄 모양으로. 위치 · 경로는 한 번에 모아 읽는다. */
    private List<MessageView> toViews(List<Message> list) {
        List<Long> locIds = list.stream().filter(m -> m.getKind() == Message.Kind.LOCATION).map(Message::getId).toList();
        List<Long> routeIds = list.stream().filter(m -> m.getKind() == Message.Kind.ROUTE).map(Message::getId).toList();
        Map<Long, MessageLocation> locs = locIds.isEmpty() ? Map.of() : locations.findAllById(locIds).stream()
                .collect(Collectors.toMap(MessageLocation::getMessageId, Function.identity()));
        Map<Long, RouteShare> rts = routeIds.isEmpty() ? Map.of() : routes.findAllById(routeIds).stream()
                .collect(Collectors.toMap(RouteShare::getMessageId, Function.identity()));
        return list.stream().map(m -> {
            MessageLocation l = locs.get(m.getId());
            RouteShare r = rts.get(m.getId());
            Place place = l == null ? null : new Place(l.getPlaceName(), l.getPlaceAddr(), l.getLat(), l.getLon());
            RouteView route = r == null ? null : new RouteView(r.getState(),
                    new Place("", r.getFromAddr(), r.getFromLat(), r.getFromLon()),
                    new Place(r.getDestName(), r.getDestAddr(), r.getDestLat(), r.getDestLon()),
                    r.getEndedAt(),
                    r.getState() == RouteShare.State.ACTIVE ? live.get(m.getId()) : null);
            return new MessageView(m.getId(), m.getRoomId(), m.getSenderId(), m.getKind(), m.getCreatedAt(),
                    m.getText(), place, route);
        }).toList();
    }
}
