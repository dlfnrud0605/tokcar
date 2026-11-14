package com.example.tokcar.server;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 테스트용 앱 흉내: REST 호출 + WebSocket 으로 받은 프레임 모으기. */
final class TestClient {

    static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    record Res(int status, JsonNode body) {
    }

    private final String base;
    String token;
    long id;
    private WebSocket ws;
    private final List<JsonNode> frames = new ArrayList<>();

    TestClient(int port) {
        this.base = "http://localhost:" + port;
    }

    static TestClient login(int port, String loginId, String pw) {
        TestClient c = new TestClient(port);
        Res r = c.post("/api/auth/login", "{\"loginId\":\"" + loginId + "\",\"password\":\"" + pw + "\"}");
        if (r.status() != 200) fail("로그인 실패 " + r);
        c.token = r.body().get("token").asString();
        c.id = r.body().get("me").get("id").asLong();
        return c;
    }

    Res get(String path) {
        return call(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    Res post(String path, String body) {
        return call(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    Res delete(String path) {
        return call(HttpRequest.newBuilder(URI.create(base + path)).DELETE());
    }

    private Res call(HttpRequest.Builder b) {
        if (token != null) b.header("Authorization", "Bearer " + token);
        try {
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Res(r.statusCode(), r.body().isEmpty() ? null : JSON.readTree(r.body()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ───── WebSocket ─────

    TestClient connect() {
        ws = openWs(token).join();
        return this;
    }

    java.util.concurrent.CompletableFuture<WebSocket> openWs(String tok) {
        var b = HTTP.newWebSocketBuilder();
        if (tok != null) b.header("Authorization", "Bearer " + tok);
        return b.buildAsync(URI.create(base.replace("http", "ws") + "/ws"), new WebSocket.Listener() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                buf.append(data);
                if (last) {
                    JsonNode n = JSON.readTree(buf.toString());
                    buf.setLength(0);
                    synchronized (frames) {
                        frames.add(n);
                        frames.notifyAll();
                    }
                }
                w.request(1);
                return null;
            }
        });
    }

    void send(String frame) {
        ws.sendText(frame, true).join();
    }

    /** 이 type 의 프레임이 올 때까지 기다려 꺼낸다(다른 type 은 남겨 둠). */
    JsonNode next(String type) {
        long until = System.currentTimeMillis() + 5_000;
        synchronized (frames) {
            while (true) {
                for (Iterator<JsonNode> it = frames.iterator(); it.hasNext(); ) {
                    JsonNode n = it.next();
                    if (type.equals(n.get("type").asString())) {
                        it.remove();
                        return n;
                    }
                }
                long left = until - System.currentTimeMillis();
                if (left <= 0) fail(type + " 프레임이 안 옴. 받은 것: " + frames);
                try {
                    frames.wait(left);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /** 이만큼 기다려도 이 type 이 안 오는지. */
    boolean none(String type, Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        synchronized (frames) {
            return frames.stream().noneMatch(n -> type.equals(n.get("type").asString()));
        }
    }

    void close() {
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }
}
