package com.example.tokcar.server.geo;

import com.example.tokcar.server.auth.AuthInterceptor;
import com.example.tokcar.server.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이버 지도 API 를 앱 대신 부르는 중계. 비밀 키(Client Secret)는 이 서버에만 있고 앱(APK)에는 넣지 않는다.
 * 앱은 로그인한 사용자 토큰으로 이 주소를 부르고, 서버가 키를 붙여 네이버에 보낸 뒤 응답을 그대로 돌려준다(앱의 응답 해석은 그대로).
 *
 * 열어 둔 서비스(주소와 경로는 고정, 쿼리만 그대로 넘김):
 *  - reverse-geocode : 좌표 → 주소   (Maps Application 키)
 *  - geocode         : 주소 → 좌표   (Maps Application 키)
 *  - directions      : 경로(Directions 5) (Maps Application 키)
 *  - local-search    : 장소명 검색(NAVER API HUB 지역 검색, 별도 Application 키)
 * 응답은 저장하지 않는다. 한 사용자가 1분에 [LIMIT_PER_MINUTE]번까지.
 */
@RestController
public class NaverProxyController {

    static final int LIMIT_PER_MINUTE = 120;
    private static final int MAX_QUERY = 2000;

    private record Route(boolean search, String path) {
    }

    private static final Map<String, Route> ROUTES = Map.of(
            "reverse-geocode", new Route(false, "/map-reversegeocode/v2/gc"),
            "geocode", new Route(false, "/map-geocode/v2/geocode"),
            "directions", new Route(false, "/map-direction/v1/driving"),
            "local-search", new Route(true, "/search/v1/local"));

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<Long, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final String mapsBase, searchBase, mapsId, mapsSecret, searchId, searchSecret;

    public NaverProxyController(
            @Value("${naver.maps.base-url:https://maps.apigw.ntruss.com}") String mapsBase,
            @Value("${naver.search.base-url:https://naverapihub.apigw.ntruss.com}") String searchBase,
            @Value("${naver.maps.client-id:}") String mapsId, @Value("${naver.maps.client-secret:}") String mapsSecret,
            @Value("${naver.search.client-id:}") String searchId, @Value("${naver.search.client-secret:}") String searchSecret) {
        this.mapsBase = mapsBase;
        this.searchBase = searchBase;
        this.mapsId = mapsId.strip();
        this.mapsSecret = mapsSecret.strip();
        this.searchId = searchId.strip();
        this.searchSecret = searchSecret.strip();
    }

    @GetMapping("/api/naver/{service}")
    ResponseEntity<byte[]> proxy(@RequestAttribute(AuthInterceptor.UID) long uid, @PathVariable String service, HttpServletRequest req)
            throws InterruptedException {
        Route route = ROUTES.get(service);
        if (route == null) throw ApiException.notFound("서비스");
        String id = route.search() ? searchId : mapsId;
        String secret = route.search() ? searchSecret : mapsSecret;
        if (id.isEmpty() || secret.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NAVER_NOT_CONFIGURED", "서버에 네이버 키가 설정되어 있지 않아요");
        }
        String query = req.getQueryString() == null ? "" : req.getQueryString();
        if (query.length() > MAX_QUERY) throw ApiException.badRequest("요청이 너무 길어요");
        tooMany(uid);

        URI uri = URI.create((route.search() ? searchBase : mapsBase) + route.path() + (query.isEmpty() ? "" : "?" + query));
        HttpRequest up = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET()
                .header("X-NCP-APIGW-API-KEY-ID", id).header("X-NCP-APIGW-API-KEY", secret).header("Accept", "application/json").build();
        try {
            HttpResponse<byte[]> res = http.send(up, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                // 키 문제는 앱이 알 필요 없다(응답 본문에 키 정보가 섞이지 않게 막는다)
                throw new ApiException(HttpStatus.BAD_GATEWAY, "NAVER_AUTH_FAILED", "서버의 네이버 키가 거절됐어요");
            }
            return ResponseEntity.status(res.statusCode()).contentType(MediaType.APPLICATION_JSON).body(res.body());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "NAVER_UNREACHABLE", "네이버에 연결하지 못했어요");
        }
    }

    /** 1분에 [LIMIT_PER_MINUTE]번을 넘으면 429. 네이버 사용량 한도를 지키려는 안전장치다. */
    private void tooMany(long uid) {
        long now = System.currentTimeMillis();
        Deque<Long> q = recent.computeIfAbsent(uid, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= LIMIT_PER_MINUTE) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "잠시 뒤에 다시 해 주세요");
            }
            q.addLast(now);
        }
    }
}
