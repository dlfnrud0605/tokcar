package com.example.tokcar.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 네이버 중계: 가짜 네이버(내 PC 의 작은 서버)에 붙여서, 키가 서버에서만 붙고 앱 응답이 그대로 돌아오는지 본다. 진짜 네이버는 부르지 않는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
class NaverProxyTest {

    private record Seen(String path, String query, String keyId, String key) {
    }

    private static final List<Seen> SEEN = new CopyOnWriteArrayList<>();
    private static final HttpServer STUB = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/", ex -> {
                String q = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
                SEEN.add(new Seen(ex.getRequestURI().getPath(), q, ex.getRequestHeaders().getFirst("X-NCP-APIGW-API-KEY-ID"),
                        ex.getRequestHeaders().getFirst("X-NCP-APIGW-API-KEY")));
                boolean denied = q.contains("denied");
                byte[] body = (denied ? "{\"error\":{\"errorCode\":\"200\",\"message\":\"Authentication Failed\"}}"
                        : "{\"ok\":true,\"path\":\"" + ex.getRequestURI().getPath() + "\"}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(denied ? 401 : 200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            s.start();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        r.add("naver.maps.base-url", () -> base);
        r.add("naver.search.base-url", () -> base);
        r.add("naver.maps.client-id", () -> "maps-id");
        r.add("naver.maps.client-secret", () -> "maps-secret");
        r.add("naver.search.client-id", () -> "search-id");
        r.add("naver.search.client-secret", () -> "search-secret");
    }

    @AfterAll
    static void stop() {
        STUB.stop(0);
    }

    @LocalServerPort
    int port;

    @Test
    void needsLogin() {
        TestClient anon = new TestClient(port);
        assertThat(anon.get("/api/naver/reverse-geocode?coords=127.1,37.4").status()).isEqualTo(401);
    }

    @Test
    void reverseGeocodeGetsTheMapsKeyFromTheServerAndPassesTheAnswerThrough() {
        TestClient papa = TestClient.login(port, "papa", "papa1234!");
        SEEN.clear();
        var res = papa.get("/api/naver/reverse-geocode?coords=127.0947,37.4127&orders=addr&output=json");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.body().get("path").asString()).isEqualTo("/map-reversegeocode/v2/gc");
        Seen s = SEEN.getLast();
        assertThat(s.query()).isEqualTo("coords=127.0947,37.4127&orders=addr&output=json");
        assertThat(s.keyId()).isEqualTo("maps-id");
        assertThat(s.key()).isEqualTo("maps-secret");
    }

    @Test
    void geocodeAndDirectionsUseTheMapsKeyAndLocalSearchUsesTheSearchKey() {
        TestClient papa = TestClient.login(port, "papa", "papa1234!");
        SEEN.clear();
        assertThat(papa.get("/api/naver/geocode?query=%EC%82%B0%EC%84%B1%EB%8C%80%EB%A1%9C&count=1").body().get("path").asString()).isEqualTo("/map-geocode/v2/geocode");
        assertThat(papa.get("/api/naver/directions?start=127.1,37.4&goal=127.2,37.5").body().get("path").asString()).isEqualTo("/map-direction/v1/driving");
        assertThat(papa.get("/api/naver/local-search?query=%EC%9D%B4%EB%A7%88%ED%8A%B8&display=5").body().get("path").asString()).isEqualTo("/search/v1/local");
        assertThat(SEEN.get(0).keyId()).isEqualTo("maps-id");
        assertThat(SEEN.get(1).keyId()).isEqualTo("maps-id");
        assertThat(SEEN.get(2).keyId()).isEqualTo("search-id");
        assertThat(SEEN.get(2).key()).isEqualTo("search-secret");
    }

    @Test
    void unknownServiceIsNotFound() {
        TestClient papa = TestClient.login(port, "papa", "papa1234!");
        assertThat(papa.get("/api/naver/anything-else?x=1").status()).isEqualTo(404);
    }

    @Test
    void naverRejectingTheServersKeyBecomesAGenericBadGatewayWithoutLeakingNaversAnswer() {
        TestClient papa = TestClient.login(port, "papa", "papa1234!");
        var res = papa.get("/api/naver/reverse-geocode?coords=1,2&denied=1");
        assertThat(res.status()).isEqualTo(502);
        assertThat(res.body().get("error").get("code").asString()).isEqualTo("NAVER_AUTH_FAILED");
        assertThat(res.body().toString()).doesNotContain("Authentication Failed");
    }

    @Test
    void oneUserIsLimitedPerMinute() {
        TestClient minji = TestClient.login(port, "minji", "minji1234!");
        int last = 0;
        for (int i = 0; i < 130 && last != 429; i++) last = minji.get("/api/naver/geocode?query=a").status();
        assertThat(last).isEqualTo(429);
    }
}
