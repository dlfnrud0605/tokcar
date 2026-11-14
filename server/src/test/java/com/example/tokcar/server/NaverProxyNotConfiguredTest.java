package com.example.tokcar.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** 서버에 네이버 키가 없으면(secrets.properties · 환경 변수 없음) 중계는 네이버를 부르지 않고 503 으로 알려 준다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "naver.maps.client-id=", "naver.maps.client-secret=", "naver.search.client-id=", "naver.search.client-secret=",
        "naver.maps.base-url=http://127.0.0.1:1", "naver.search.base-url=http://127.0.0.1:1"})
@ActiveProfiles({"local", "test"})
class NaverProxyNotConfiguredTest {

    @LocalServerPort
    int port;

    @Test
    void withoutKeysItAnswers503AndNeverCallsNaver() {
        TestClient papa = TestClient.login(port, "papa", "papa1234!");
        var res = papa.get("/api/naver/reverse-geocode?coords=127.1,37.4");
        assertThat(res.status()).isEqualTo(503);
        assertThat(res.body().get("error").get("code").asString()).isEqualTo("NAVER_NOT_CONFIGURED");
        assertThat(papa.get("/api/naver/local-search?query=a").status()).isEqualTo(503);
    }
}
