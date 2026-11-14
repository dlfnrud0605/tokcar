package com.example.tokcar.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.tokcar.server.chat.LocationCleanup;
import com.example.tokcar.server.domain.Message;
import com.example.tokcar.server.domain.MessageLocation;
import com.example.tokcar.server.domain.MessageLocationRepository;
import com.example.tokcar.server.domain.MessageRepository;
import com.example.tokcar.server.domain.Room;
import com.example.tokcar.server.domain.RoomMember;
import com.example.tokcar.server.domain.RoomMemberRepository;
import com.example.tokcar.server.domain.RoomRepository;
import com.example.tokcar.server.domain.User;
import com.example.tokcar.server.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

/** 실제 서버를 띄우고 아빠(차) · 민지(폰)로 붙어서 API 명세대로 도는지 본다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
class ServerFlowTest {

    static final String PAPA_PW = "papa1234!";
    static final String MINJI_PW = "minji1234!";

    @LocalServerPort
    int port;
    @Autowired
    UserRepository users;
    @Autowired
    RoomRepository rooms;
    @Autowired
    RoomMemberRepository members;
    @Autowired
    MessageRepository messages;
    @Autowired
    MessageLocationRepository locations;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    LocationCleanup cleanup;

    TestClient papa;
    TestClient minji;
    long family;

    @BeforeEach
    void setUp() {
        papa = TestClient.login(port, "papa", PAPA_PW);
        minji = TestClient.login(port, "minji", MINJI_PW);
        family = roomNamed(papa, "우리 가족").get("id").asLong();
    }

    @AfterEach
    void tearDown() {
        papa.close();
        minji.close();
    }

    // ───── 로그인 ─────

    @Test
    void wrongPasswordCountsDownThenLocksAndRightOneResets() {
        users.save(new User("locky", encoder.encode("right1!"), "+821099990001", "KR", "잠김"));
        TestClient c = new TestClient(port);
        var r = c.post("/api/auth/login", "{\"loginId\":\"locky\",\"password\":\"x\"}");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.body().get("error").get("code").asString()).isEqualTo("BAD_CREDENTIALS");
        assertThat(r.body().get("error").get("left").asInt()).isEqualTo(4);
        assertThat(c.post("/api/auth/login", "{\"loginId\":\"locky\",\"password\":\"right1!\"}").status()).isEqualTo(200);
        for (int i = 0; i < 4; i++) c.post("/api/auth/login", "{\"loginId\":\"locky\",\"password\":\"x\"}");
        r = c.post("/api/auth/login", "{\"loginId\":\"locky\",\"password\":\"x\"}");
        assertThat(r.status()).isEqualTo(423);
        assertThat(c.post("/api/auth/login", "{\"loginId\":\"locky\",\"password\":\"right1!\"}").status()).isEqualTo(423);
    }

    @Test
    void unknownIdHasNoLeftCount() {
        var r = new TestClient(port).post("/api/auth/login", "{\"loginId\":\"nobody\",\"password\":\"x\"}");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.body().get("error").has("left")).isFalse();
    }

    @Test
    void apiNeedsTokenAndLogoutDropsIt() {
        assertThat(new TestClient(port).get("/api/me").status()).isEqualTo(401);
        TestClient c = TestClient.login(port, "papa", PAPA_PW);
        var me = c.get("/api/me").body();
        assertThat(me.get("loginId").asString()).isEqualTo("papa");
        assertThat(c.post("/api/auth/logout", "").status()).isEqualTo(204);
        assertThat(c.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void webSocketWithoutValidTokenIsRefused() {
        TestClient c = new TestClient(port);
        assertThrows(CompletionException.class, () -> c.openWs(null).join());
        assertThrows(CompletionException.class, () -> c.openWs("wrong").join());
    }

    // ───── 조회 ─────

    @Test
    void friendsAndRoomsHideLoginIdAndPhone() {
        JsonNode friends = papa.get("/api/friends").body();
        assertThat(friends.size()).isEqualTo(10); // 민지 + 가짜 친구 9명
        assertThat(names(friends)).contains("민지", "엄마", "김 팀장");
        assertThat(friends.get(0).has("statusMsg")).isTrue();
        assertThat(friends.get(0).has("loginId")).isFalse();
        assertThat(friends.toString()).doesNotContain("+82");
        assertThat(names(minji.get("/api/friends").body())).containsExactlyInAnyOrder("아빠", "엄마", "수진", "지우", "하은");

        assertThat(names(papa.get("/api/rooms").body())).contains("우리 가족", "어머니집", "동창 모임", "출근 카풀");
        assertThat(names(minji.get("/api/rooms").body())).containsExactlyInAnyOrder("우리 가족", "학원 친구들", "수진", "엄마");
        assertThat(names(papa.get("/api/rooms").body())).doesNotContain("학원 친구들"); // 민지 방은 아빠에게 안 보임
        JsonNode room = roomNamed(minji, "우리 가족");
        assertThat(room.get("members").size()).isEqualTo(3); // 아빠 · 민지 · 엄마
        assertThat(room.get("readUpTo").size()).isEqualTo(3);
        assertThat(room.toString()).doesNotContain("papa").doesNotContain("+82");
    }

    @Test
    void nonMemberIsRejectedOnRestAndSocket() {
        Room solo = rooms.save(new Room("혼자 방", papa.id));
        members.save(new RoomMember(solo.getId(), papa.id));
        var r = minji.get("/api/rooms/" + solo.getId() + "/messages");
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.body().get("error").get("code").asString()).isEqualTo("NOT_MEMBER");
        minji.connect().send("{\"type\":\"text.send\",\"ref\":\"x1\",\"roomId\":" + solo.getId() + ",\"text\":\"hi\"}");
        JsonNode e = minji.next("error");
        assertThat(e.get("ref").asString()).isEqualTo("x1");
        assertThat(e.get("code").asString()).isEqualTo("NOT_MEMBER");
        assertThat(minji.get("/api/rooms/9999/messages").status()).isEqualTo(404);
    }

    // ───── 대화 · 읽음 ─────

    @Test
    void textIsAckedToSenderPushedToOthersAndReadComesBack() {
        papa.connect();
        minji.connect();
        long minjiUnreadBefore = roomNamed(minji, "우리 가족").get("unread").asLong();

        papa.send("{\"type\":\"text.send\",\"ref\":\"c-1\",\"roomId\":" + family + ",\"text\":\"  지금 출발  \"}");
        JsonNode ack = papa.next("ack");
        assertThat(ack.get("ref").asString()).isEqualTo("c-1");
        JsonNode m = ack.get("message");
        assertThat(m.get("kind").asString()).isEqualTo("TEXT");
        assertThat(m.get("text").asString()).isEqualTo("지금 출발");
        assertThat(m.get("senderId").asLong()).isEqualTo(papa.id);
        assertThat(m.get("createdAt").asString()).endsWith("Z"); // ISO-8601 UTC
        long id = m.get("id").asLong();

        JsonNode pushed = minji.next("message.new").get("message");
        assertThat(pushed.get("id").asLong()).isEqualTo(id);
        assertThat(papa.none("message.new", Duration.ofMillis(200))).isTrue(); // 보낸 연결엔 ack 만

        JsonNode room = roomNamed(minji, "우리 가족");
        assertThat(room.get("unread").asLong()).isEqualTo(minjiUnreadBefore + 1);
        assertThat(room.get("lastMessage").get("id").asLong()).isEqualTo(id);

        minji.send("{\"type\":\"read\",\"roomId\":" + family + ",\"msgId\":" + id + "}");
        JsonNode read = papa.next("read");
        assertThat(read.get("userId").asLong()).isEqualTo(minji.id);
        assertThat(read.get("msgId").asLong()).isEqualTo(id);
        assertThat(roomNamed(minji, "우리 가족").get("unread").asLong()).isZero();

        // 이미 읽은 곳보다 앞은 다시 알리지 않음
        minji.next("read"); // 자기 것도 받음(방 전원)
        minji.send("{\"type\":\"read\",\"roomId\":" + family + ",\"msgId\":" + id + "}");
        assertThat(papa.none("read", Duration.ofMillis(300))).isTrue();

        JsonNode page = minji.get("/api/rooms/" + family + "/messages?after=" + (id - 1)).body();
        assertThat(page.get("messages").get(0).get("id").asLong()).isEqualTo(id);
    }

    @Test
    void tooLongTextAndBadFramesGetErrors() {
        papa.connect();
        papa.send("{\"type\":\"text.send\",\"ref\":\"c-2\",\"roomId\":" + family + ",\"text\":\"" + "가".repeat(1001) + "\"}");
        assertThat(papa.next("error").get("code").asString()).isEqualTo("TOO_LONG");
        papa.send("not json");
        assertThat(papa.next("error").get("code").asString()).isEqualTo("BAD_REQUEST");
        papa.send("{\"type\":\"nope\"}");
        assertThat(papa.next("error").get("code").asString()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void locationIsKeptAYearThenOnlyTheCoordinatesGo() {
        minji.connect();
        minji.send("{\"type\":\"location.send\",\"ref\":\"l-1\",\"roomId\":" + family
                + ",\"location\":{\"name\":\"정자동\",\"addr\":\"성남시 분당구 정자일로 95\",\"lat\":37.3671,\"lon\":127.1086}}");
        JsonNode m = minji.next("ack").get("message");
        assertThat(m.get("kind").asString()).isEqualTo("LOCATION");
        assertThat(m.get("location").get("lat").asDouble()).isEqualTo(37.3671);

        Instant old = Instant.now().minus(LocationCleanup.KEEP).minus(Duration.ofDays(1));
        Message oldMsg = messages.save(new Message(family, minji.id, Message.Kind.LOCATION, null, old));
        locations.save(new MessageLocation(oldMsg.getId(), "옛날", "옛 주소", 37.0, 127.0));

        assertThat(cleanup.run()).isEqualTo(1);
        JsonNode list = papa.get("/api/rooms/" + family + "/messages?after=" + (oldMsg.getId() - 1)).body().get("messages");
        JsonNode gone = find(list, oldMsg.getId());
        assertThat(gone.get("kind").asString()).isEqualTo("LOCATION");
        assertThat(gone.get("location").isNull()).isTrue();
    }

    // ───── 경로 공유 ─────

    @Test
    void routeShareLivesInMemoryAndOnlyStartDestEndTouchTheCard() {
        papa.connect();
        minji.connect();
        papa.send("{\"type\":\"route.start\",\"ref\":\"r-1\",\"roomId\":" + family + ","
                + "\"from\":{\"name\":\"\",\"addr\":\"성남시 분당구 서현로 210\",\"lat\":37.3836,\"lon\":127.1232},"
                + "\"dest\":{\"name\":\"민지 학원\",\"addr\":\"성남시 분당구 정자일로 95\",\"lat\":37.3671,\"lon\":127.1086},"
                + "\"live\":{\"carLat\":37.3836,\"carLon\":127.1232,\"remainSec\":1080,\"pct\":0,"
                + "\"etaAt\":\"2026-11-13T09:30:00Z\",\"traffic\":null}}");
        JsonNode card = papa.next("ack").get("message");
        long id = card.get("id").asLong();
        assertThat(card.get("route").get("state").asString()).isEqualTo("ACTIVE");
        assertThat(card.get("route").get("live").get("remainSec").asInt()).isEqualTo(1080);
        assertThat(minji.next("message.new").get("message").get("id").asLong()).isEqualTo(id);

        // 진행 중인데 또 시작하면 안 됨
        papa.send("{\"type\":\"route.start\",\"ref\":\"r-2\",\"roomId\":" + family + ","
                + "\"from\":{\"addr\":\"a\",\"lat\":37,\"lon\":127},\"dest\":{\"addr\":\"b\",\"lat\":37,\"lon\":127}}");
        assertThat(papa.next("error").get("code").asString()).isEqualTo("ROUTE_ACTIVE");

        // 실시간 값: 민지에게 route.live, 보낸 차엔 안 감
        papa.send("{\"type\":\"route.live\",\"roomId\":" + family + ",\"carLat\":37.3770,\"carLon\":127.1119,"
                + "\"remainSec\":1020,\"pct\":6,\"etaAt\":\"2026-11-13T09:30:00Z\","
                + "\"traffic\":[[0,0.4,\"FAST\"],[0.4,0.55,\"SLOW\"],[0.55,1,\"FAST\"]]}");
        JsonNode live = minji.next("route.live");
        assertThat(live.get("messageId").asLong()).isEqualTo(id);
        assertThat(live.get("live").get("pct").asInt()).isEqualTo(6);
        assertThat(live.get("live").get("traffic").get(1).get(2).asString()).isEqualTo("SLOW");
        assertThat(live.get("live").get("traffic").get(1).get(0).asDouble()).isEqualTo(0.4);

        // 새로 연 폰도 REST 로 마지막 실시간 값을 받음
        JsonNode viaRest = find(minji.get("/api/rooms/" + family + "/messages").body().get("messages"), id);
        assertThat(viaRest.get("route").get("live").get("remainSec").asInt()).isEqualTo(1020);

        // 2초 안에 또 오면 기다렸다가 마지막 값만 한 번
        papa.send("{\"type\":\"route.live\",\"roomId\":" + family + ",\"carLat\":37.37,\"carLon\":127.11,"
                + "\"remainSec\":1000,\"pct\":8,\"etaAt\":\"2026-11-13T09:30:00Z\"}");
        papa.send("{\"type\":\"route.live\",\"roomId\":" + family + ",\"carLat\":37.37,\"carLon\":127.11,"
                + "\"remainSec\":990,\"pct\":9,\"etaAt\":\"2026-11-13T09:30:00Z\"}");
        assertThat(minji.none("route.live", Duration.ofMillis(500))).isTrue();
        assertThat(minji.next("route.live").get("live").get("pct").asInt()).isEqualTo(9);

        // 잘못된 traffic
        papa.send("{\"type\":\"route.live\",\"roomId\":" + family + ",\"carLat\":37.37,\"carLon\":127.11,"
                + "\"remainSec\":990,\"pct\":9,\"etaAt\":\"2026-11-13T09:30:00Z\",\"traffic\":[[0.5,0.2,\"FAST\"]]}");
        assertThat(papa.next("error").get("code").asString()).isEqualTo("BAD_REQUEST");

        // 목적지 변경 → 카드가 바뀜
        papa.send("{\"type\":\"route.dest\",\"roomId\":" + family
                + ",\"dest\":{\"name\":\"서현역\",\"addr\":\"성남시 분당구 서현로 170\",\"lat\":37.385,\"lon\":127.123}}");
        JsonNode upd = minji.next("message.updated").get("message");
        assertThat(upd.get("id").asLong()).isEqualTo(id);
        assertThat(upd.get("route").get("dest").get("name").asString()).isEqualTo("서현역");
        papa.next("message.updated");

        // 도착 → 실시간 값은 사라지고 카드에 도착이 남음
        papa.send("{\"type\":\"route.end\",\"roomId\":" + family + ",\"reason\":\"ARRIVED\"}");
        JsonNode end = minji.next("message.updated").get("message").get("route");
        assertThat(end.get("state").asString()).isEqualTo("ARRIVED");
        assertThat(end.get("endedAt").isNull()).isFalse();
        assertThat(end.get("live").isNull()).isTrue();
        papa.next("message.updated");

        papa.send("{\"type\":\"route.live\",\"roomId\":" + family + ",\"carLat\":37.37,\"carLon\":127.11,"
                + "\"remainSec\":0,\"pct\":100,\"etaAt\":\"2026-11-13T09:30:00Z\"}");
        assertThat(papa.next("error").get("code").asString()).isEqualTo("NO_ACTIVE_ROUTE");
    }

    // ───── 방 만들기 · 초대 · 나가기 ─────

    @Test
    void createInviteAndLeaveRoomsWithSystemMessages() {
        long mom = users.findByLoginId("mom1").orElseThrow().getId();
        long sujin = users.findByLoginId("sujin").orElseThrow().getId();
        TestClient momC = TestClient.login(port, "mom1", "family1234!");
        papa.connect();
        minji.connect();
        momC.connect();

        // 아빠가 민지와 방을 만든다(이름 비우면 친구 이름으로)
        var r = papa.post("/api/rooms", "{\"name\":\"\",\"memberIds\":[" + minji.id + "]}");
        assertThat(r.status()).isEqualTo(201);
        long room = r.body().get("id").asLong();
        assertThat(r.body().get("name").asString()).isEqualTo("민지");
        assertThat(r.body().get("members").size()).isEqualTo(2);
        JsonNode added = minji.next("room.updated").get("room");
        assertThat(added.get("id").asLong()).isEqualTo(room);
        JsonNode sys = minji.next("message.new").get("message");
        assertThat(sys.get("kind").asString()).isEqualTo("SYSTEM");
        assertThat(sys.get("senderId").isNull()).isTrue();
        assertThat(sys.get("text").asString()).isEqualTo("아빠가 방을 만들었어요 · 민지 초대");
        papa.next("room.updated");
        papa.next("message.new");

        // 친구가 아니면 못 넣음(아빠 친구에 수진 없음), 이미 있으면 못 넣음, 멤버가 아니면 못 넣음
        assertThat(papa.post("/api/rooms/" + room + "/members", "{\"userIds\":[" + sujin + "]}").body().get("error").get("code").asString()).isEqualTo("NOT_FRIEND");
        assertThat(papa.post("/api/rooms/" + room + "/members", "{\"userIds\":[" + minji.id + "]}").status()).isEqualTo(409);
        assertThat(momC.post("/api/rooms/" + room + "/members", "{\"userIds\":[" + minji.id + "]}").status()).isEqualTo(403);
        assertThat(papa.post("/api/rooms", "{\"memberIds\":[]}").status()).isEqualTo(400);

        // 민지가 엄마를 초대 → 엄마에게 방이 생기고 모두 안내 글을 받음
        var inv = minji.post("/api/rooms/" + room + "/members", "{\"userIds\":[" + mom + "]}");
        assertThat(inv.status()).isEqualTo(200);
        assertThat(inv.body().get("members").size()).isEqualTo(3);
        assertThat(momC.next("room.updated").get("room").get("id").asLong()).isEqualTo(room);
        assertThat(momC.next("message.new").get("message").get("text").asString()).isEqualTo("민지가 엄마를 초대했어요");
        assertThat(papa.next("room.updated").get("room").get("members").size()).isEqualTo(3);
        minji.next("room.updated");
        minji.next("message.new"); // 초대 안내
        assertThat(momC.get("/api/rooms/" + room + "/messages").status()).isEqualTo(200);

        // 아빠가 경로 공유 중에 나가면 공유도 멈춤
        papa.send("{\"type\":\"route.start\",\"ref\":\"q\",\"roomId\":" + room + ",\"from\":{\"addr\":\"a\",\"lat\":37,\"lon\":127},\"dest\":{\"addr\":\"b\",\"lat\":37.1,\"lon\":127.1}}");
        long card = papa.next("ack").get("message").get("id").asLong();
        assertThat(minji.next("message.new").get("message").get("id").asLong()).isEqualTo(card);
        assertThat(papa.post("/api/rooms/" + room + "/members/me", "").status()).isEqualTo(405); // 나가기는 DELETE
        var left = papa.delete("/api/rooms/" + room + "/members/me");
        assertThat(left.status()).isEqualTo(204);
        assertThat(papa.next("room.removed").get("roomId").asLong()).isEqualTo(room);
        JsonNode stopped = minji.next("message.updated").get("message");
        assertThat(stopped.get("id").asLong()).isEqualTo(card);
        assertThat(stopped.get("route").get("state").asString()).isEqualTo("STOPPED");
        assertThat(minji.next("message.new").get("message").get("text").asString()).isEqualTo("아빠가 나갔어요");
        assertThat(minji.next("room.updated").get("room").get("members").size()).isEqualTo(2);
        assertThat(papa.get("/api/rooms/" + room + "/messages").status()).isEqualTo(403);
        assertThat(names(papa.get("/api/rooms").body())).doesNotContain("민지");
        momC.close();
    }

    // ───── 이 방에서 간 곳 ─────

    @Test
    void placesKeepThreeNewestAndRepeatMovesToTop() {
        for (String n : List.of("A곳", "B곳", "C곳", "D곳"))
            assertThat(papa.post("/api/rooms/" + family + "/places", place(n)).status()).isEqualTo(200);
        assertThat(names(papa.get("/api/rooms/" + family + "/places").body())).containsExactly("D곳", "C곳", "B곳");

        JsonNode after = papa.post("/api/rooms/" + family + "/places", place("B곳")).body();
        assertThat(names(after)).containsExactly("B곳", "D곳", "C곳");
        assertThat(names(minji.get("/api/rooms/" + family + "/places").body())).containsExactly("B곳", "D곳", "C곳");

        var bad = papa.post("/api/rooms/" + family + "/places", "{\"name\":\"x\",\"addr\":\"y\",\"lat\":99,\"lon\":127}");
        assertThat(bad.status()).isEqualTo(400);
    }

    /** (개발용) 방 초기화: 대화 · 경로 공유가 모두 지워지고 최근 목적지가 처음 값으로 돌아오며, 방 사람들에겐 room.cleared 가 간다. */
    @Test
    void devResetClearsTheRoomAndRestoresItsRecentPlaces() {
        papa.connect();
        minji.connect();
        papa.send("{\"type\":\"text.send\",\"ref\":\"c-9\",\"roomId\":" + family + ",\"text\":\"지우기 전\"}");
        papa.next("ack");
        minji.next("message.new");
        assertThat(papa.post("/api/rooms/" + family + "/places", place("A곳")).status()).isEqualTo(200);
        assertThat(roomNamed(minji, "우리 가족").get("unread").asLong()).isPositive();

        assertThat(papa.post("/api/dev/rooms/" + family + "/reset", "{}").status()).isEqualTo(204);

        assertThat(papa.get("/api/rooms/" + family + "/messages").body().get("messages").size()).isZero();
        assertThat(names(papa.get("/api/rooms/" + family + "/places").body())).containsExactly("집", "이마트 분당점");
        JsonNode room = roomNamed(minji, "우리 가족");
        assertThat(room.get("unread").asLong()).isZero();
        assertThat(room.get("lastMessage").isNull()).isTrue();
        assertThat(minji.next("room.cleared").get("roomId").asLong()).isEqualTo(family);
        assertThat(minji.next("room.updated").get("room").get("unread").asLong()).isZero();
    }

    @Test
    void devResetIsForMembersOnly() {
        long other = roomNamed(papa, "어머니집").get("id").asLong();
        assertThat(minji.post("/api/dev/rooms/" + other + "/reset", "{}").status()).isEqualTo(403);
    }

    private static String place(String name) {
        return "{\"name\":\"" + name + "\",\"addr\":\"" + name + " 주소\",\"lat\":37.3,\"lon\":127.1}";
    }

    private static List<String> names(JsonNode arr) {
        return StreamSupport.stream(arr.spliterator(), false).map(n -> n.get("name").asString()).toList();
    }

    private static JsonNode roomNamed(TestClient c, String name) {
        for (JsonNode r : c.get("/api/rooms").body()) if (r.get("name").asString().equals(name)) return r;
        throw new AssertionError(name + " 방 없음");
    }

    private static JsonNode find(JsonNode list, long id) {
        for (JsonNode n : list) if (n.get("id").asLong() == id) return n;
        throw new AssertionError("메시지 " + id + " 없음: " + list);
    }
}
