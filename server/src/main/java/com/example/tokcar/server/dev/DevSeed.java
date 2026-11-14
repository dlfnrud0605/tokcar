package com.example.tokcar.server.dev;

import com.example.tokcar.server.domain.Friendship;
import com.example.tokcar.server.domain.Message;
import com.example.tokcar.server.domain.MessageRepository;
import com.example.tokcar.server.domain.FriendshipRepository;
import com.example.tokcar.server.domain.Room;
import com.example.tokcar.server.domain.RoomMember;
import com.example.tokcar.server.domain.RoomMemberRepository;
import com.example.tokcar.server.domain.RoomPlace;
import com.example.tokcar.server.domain.RoomPlaceRepository;
import com.example.tokcar.server.domain.RoomRepository;
import com.example.tokcar.server.domain.User;
import com.example.tokcar.server.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로필 가계정: 아빠(papa, 차량 앱) · 민지(minji, 폰 앱). 서로 친구이고 「우리 가족」 방에 같이 있다.
 * 없을 때만 만들고, 켤 때마다 로그인 실패 횟수를 0으로 되돌린다(1단계엔 잠김을 푸는 화면이 없음).
 * 가짜 가족 · 친구(엄마, 어머니, 누나, 동창 넷, 회사 둘)와 방 셋(어머니집 · 동창 모임 · 출근 카풀)도 넣는다.
 * 앱의 assets/dummy/seed.json 과 같은 사람 · 방 · 첫 대화. 가짜 계정도 로그인해 볼 수 있다(비밀번호는 tokcar.seed.extra-password).
 */
@Component
@Profile("local")
public class DevSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final MessageRepository messages;
    private final RoomPlaceRepository places;
    private final PasswordEncoder encoder;
    private final String papaPw;
    private final String minjiPw;
    private final String extraPw;

    /** 가짜 사람: 로그인 아이디, 이름, 번호(seed.json 의 tel), 상태 메시지. */
    private record Fake(String loginId, String name, String tel, String status) {
    }

    private static final List<Fake> FAKES = List.of(
            new Fake("mom1", "엄마", "01011110001", "오늘도 수고했어요"),
            new Fake("mother", "어머니", "01011110003", null),
            new Fake("nuna", "누나", "01011110004", "주말에 갈게"),
            new Fake("chul", "철수", "01011110005", "7시 모임!"),
            new Fake("young", "영호", "01011110006", null),
            new Fake("minsuk", "민석", "01011110007", null),
            new Fake("jong", "종민", "01011110008", null),
            new Fake("kim", "김 팀장", "01011110009", "외근 중"),
            new Fake("park", "박 대리", "01011110010", "내일 8시 정문 앞"));

    /** 민지의 가짜 친구(학원 친구들). 아빠 친구 목록엔 없다. */
    private static final List<Fake> MINJI_FAKES = List.of(
            new Fake("sujin", "수진", "01011110011", "시험 끝!"),
            new Fake("jiwoo", "지우", "01011110012", null),
            new Fake("haeun", "하은", "01011110013", "떡볶이 먹을 사람"));

    /** 가짜 방: 이름, 아빠 말고 멤버, 첫 대화(보낸 사람 아이디, 글), 아빠가 안 읽은 수(seed.json 의 unread). */
    private record FakeRoom(String name, List<String> members, List<String[]> msgs, int unread) {
    }

    private static final List<FakeRoom> FAKE_ROOMS = List.of(
            new FakeRoom("어머니집", List.of("mother", "nuna"),
                    List.of(new String[] {"nuna", "이번 주말에 올 수 있어?"}, new String[] {"mother", "밥 해 놓을게"}), 0),
            new FakeRoom("동창 모임", List.of("chul", "young", "minsuk", "jong"),
                    List.of(new String[] {"chul", "7시에 시작이야"}, new String[] {"young", "나 좀 늦을 듯"}), 2),
            new FakeRoom("출근 카풀", List.of("kim", "park"),
                    List.of(new String[] {"park", "내일 8시 정문 앞"}, new String[] {"kim", "확인"}), 0));

    public DevSeed(UserRepository users, FriendshipRepository friendships, RoomRepository rooms,
                   RoomMemberRepository members, MessageRepository messages, RoomPlaceRepository places, PasswordEncoder encoder,
                   @Value("${tokcar.seed.papa-password}") String papaPw,
                   @Value("${tokcar.seed.minji-password}") String minjiPw,
                   @Value("${tokcar.seed.extra-password}") String extraPw) {
        this.users = users;
        this.friendships = friendships;
        this.rooms = rooms;
        this.members = members;
        this.messages = messages;
        this.places = places;
        this.encoder = encoder;
        this.papaPw = papaPw;
        this.minjiPw = minjiPw;
        this.extraPw = extraPw;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByLoginId("papa").isEmpty()) {
            User papa = users.save(new User("papa", encoder.encode(papaPw), "+821012345678", "KR", "아빠"));
            User minji = users.save(new User("minji", encoder.encode(minjiPw), "+821011110002", "KR", "민지"));
            papa.setStatusMsg("운전 중엔 답장이 늦어요");
            minji.setStatusMsg("시험 기간 📚");
            friendships.save(new Friendship(papa.getId(), minji.getId()));
            friendships.save(new Friendship(minji.getId(), papa.getId()));
            Room family = rooms.save(new Room("우리 가족", papa.getId()));
            members.save(new RoomMember(family.getId(), papa.getId()));
            members.save(new RoomMember(family.getId(), minji.getId()));
            log.info("가계정 papa · minji 와 「우리 가족」 방(id {})을 만들었음", family.getId());
        }
        if (users.findByLoginId(FAKES.getFirst().loginId()).isEmpty()) seedFakes();
        if (users.findByLoginId(MINJI_FAKES.getFirst().loginId()).isEmpty()) seedMinjiFakes();
        seedRoomPlaces();
        users.findAll().forEach(u -> u.setFailedLogins(0));
    }

    /** 가짜 친구 · 방. 이미 있으면(엄마 계정이 있으면) 건너뛴다. */
    private void seedFakes() {
        User papa = users.findByLoginId("papa").orElseThrow();
        User minji = users.findByLoginId("minji").orElseThrow();
        String hash = encoder.encode(extraPw);
        Map<String, User> by = new LinkedHashMap<>();
        for (Fake f : FAKES) {
            User u = users.save(new User(f.loginId(), hash, "+82" + f.tel().substring(1), "KR", f.name()));
            u.setStatusMsg(f.status());
            by.put(f.loginId(), u);
            friendships.save(new Friendship(papa.getId(), u.getId())); // 아빠 친구 목록엔 모두
        }
        // 엄마 · 민지는 서로 친구, 엄마는 「우리 가족」 방에도
        User mom = by.get("mom1");
        friendships.save(new Friendship(minji.getId(), mom.getId()));
        friendships.save(new Friendship(mom.getId(), minji.getId()));
        friendships.save(new Friendship(mom.getId(), papa.getId()));
        rooms.findAll().stream().filter(r -> r.getName().equals("우리 가족")).findFirst()
                .ifPresent(family -> members.save(new RoomMember(family.getId(), mom.getId())));

        Instant t = Instant.now().minus(Duration.ofHours(3));
        for (FakeRoom fr : FAKE_ROOMS) {
            Room room = rooms.save(new Room(fr.name(), papa.getId()));
            RoomMember me = members.save(new RoomMember(room.getId(), papa.getId()));
            for (String m : fr.members()) members.save(new RoomMember(room.getId(), by.get(m).getId()));
            Long last = null;
            int i = 0;
            for (String[] msg : fr.msgs()) {
                Message saved = messages.save(new Message(room.getId(), by.get(msg[0]).getId(), Message.Kind.TEXT, msg[1], t.plus(Duration.ofMinutes(i++))));
                last = saved.getId();
                members.findById(new RoomMember.Key(room.getId(), saved.getSenderId())).ifPresent(sm -> sm.readUpTo(saved.getId()));
                // 아빠는 끝에서 unread 개를 빼고 읽음
                if (fr.msgs().size() - i >= fr.unread()) me.readUpTo(saved.getId());
            }
            room.setLastMsgId(last);
            t = t.plus(Duration.ofMinutes(30));
        }
        log.info("가짜 친구 {}명과 방 {}개를 넣었음", FAKES.size(), FAKE_ROOMS.size());
    }

    /** 민지의 가짜 친구 · 방: 학원 친구들 단톡, 수진과 둘이, 엄마와 둘이. 이미 있으면(수진 계정이 있으면) 건너뛴다. */
    private void seedMinjiFakes() {
        User minji = users.findByLoginId("minji").orElseThrow();
        User mom = users.findByLoginId("mom1").orElseThrow();
        String hash = encoder.encode(extraPw);
        Map<String, User> by = new LinkedHashMap<>();
        by.put("minji", minji);
        by.put("mom1", mom);
        for (Fake f : MINJI_FAKES) {
            User u = users.save(new User(f.loginId(), hash, "+82" + f.tel().substring(1), "KR", f.name()));
            u.setStatusMsg(f.status());
            by.put(f.loginId(), u);
            friendships.save(new Friendship(minji.getId(), u.getId()));
            friendships.save(new Friendship(u.getId(), minji.getId()));
        }
        Instant t = Instant.now().minus(Duration.ofHours(2));
        t = room(minji, "학원 친구들", List.of("sujin", "jiwoo", "haeun"), List.of(
                new String[] {"sujin", "오늘 수학 숙제 뭐였지?"}, new String[] {"jiwoo", "42쪽까지"},
                new String[] {"haeun", "끝나고 떡볶이 ㄱ?"}), 1, by, t);
        t = room(minji, "수진", List.of("sujin"), List.of(
                new String[] {"sujin", "내일 학원 같이 가자"}, new String[] {"minji", "ㅇㅋ 4시에 정문"}), 0, by, t);
        room(minji, "엄마", List.of("mom1"), List.<String[]>of(
                new String[] {"mom1", "학원 끝나면 연락해"}), 1, by, t);
        log.info("민지의 가짜 친구 {}명과 방 3개를 넣었음", MINJI_FAKES.size());
    }

    /** 방 하나: [owner] 가 만들고, 첫 대화를 넣고, owner 는 끝에서 [unread] 개를 빼고 읽은 것으로. 다음 방 시각을 돌려준다. */
    private Instant room(User owner, String name, List<String> others, List<String[]> msgs, int unread, Map<String, User> by, Instant t) {
        Room room = rooms.save(new Room(name, owner.getId()));
        RoomMember me = members.save(new RoomMember(room.getId(), owner.getId()));
        for (String m : others) members.save(new RoomMember(room.getId(), by.get(m).getId()));
        Long last = null;
        int i = 0;
        for (String[] msg : msgs) {
            Message saved = messages.save(new Message(room.getId(), by.get(msg[0]).getId(), Message.Kind.TEXT, msg[1], t.plus(Duration.ofMinutes(i++))));
            last = saved.getId();
            members.findById(new RoomMember.Key(room.getId(), saved.getSenderId())).ifPresent(sm -> sm.readUpTo(saved.getId()));
            if (msgs.size() - i >= unread) me.readUpTo(saved.getId());
        }
        room.setLastMsgId(last);
        return t.plus(Duration.ofMinutes(30));
    }

    /** 장소: 이름, 주소, 위도, 경도(앱의 assets/dummy/seed.json 장소와 같음). */
    private record Spot(String name, String addr, double lat, double lon) {
    }

    private static final Spot HOME = new Spot("집", "성남시 수정구 산성대로 501", 37.456205, 127.162272);
    private static final Spot WORK = new Spot("회사", "성남시 분당구 판교로 235", 37.4020, 127.1086);
    private static final Spot MOM = new Spot("어머니집", "경기도 광주시 오포로 215", 37.3550, 127.2290);
    private static final Spot CLUB = new Spot("강남 모임 장소", "서울특별시 강남구 테헤란로 152", 37.5000, 127.0364);
    private static final Spot MART = new Spot("이마트 분당점", "성남시 분당구 성남대로 331", 37.3836, 127.1210);
    private static final Spot STN = new Spot("판교역", "성남시 분당구 판교역로 160", 37.3948, 127.1112);

    /** 방 이름 → 「이 방에서 간 곳」(최근 것이 앞). 앱 seed.json 의 histories 와 같다. */
    private static final Map<String, List<Spot>> ROOM_SPOTS = Map.of(
            "우리 가족", List.of(HOME, MART),
            "어머니집", List.of(MOM, MART),
            "동창 모임", List.of(CLUB, STN),
            "출근 카풀", List.of(WORK, STN));

    /**
     * 가짜 방마다 「이 방에서 간 곳」을 넣어서, 차 앱을 새로 깔아도 방마다 최근 목적지가 있게 한다(방 목록 카드가 「아직 없어요」가 되지 않게).
     * 방에 이미 기록이 있으면(직접 안내해서 생긴 것) 건드리지 않는다. 서버를 켤 때마다 한 번 확인하므로 몇 번 켜도 같다.
     */
    private void seedRoomPlaces() {
        for (Room room : rooms.findAll()) seedPlacesOf(room);
    }

    private void seedPlacesOf(Room room) {
        List<Spot> spots = ROOM_SPOTS.get(room.getName());
        if (spots == null || !places.findByRoomIdOrderByIdDesc(room.getId()).isEmpty()) return;
        // id 가 큰 것이 최근이라, 목록의 맨 앞(가장 최근)을 마지막에 넣는다
        for (Spot s : spots.reversed()) places.save(new RoomPlace(room.getId(), s.name(), s.addr(), s.lat(), s.lon()));
        log.info("「{}」 방의 최근 목적지 {}곳을 넣었음", room.getName(), spots.size());
    }

    /** 개발용 방 초기화: 방의 「이 방에서 간 곳」을 모두 지우고 처음 값으로 되돌린다(가짜 방 이름이 아니면 비워 둔다). */
    public void resetPlacesOf(Room room) {
        places.deleteAll(places.findByRoomIdOrderByIdDesc(room.getId()));
        places.flush(); // 지운 뒤에 넣어야 (방, 이름, 주소) 유니크에 안 걸린다
        seedPlacesOf(room);
    }
}
