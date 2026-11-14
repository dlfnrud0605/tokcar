# TokCar 가족 서버

차량 앱(`../tokcar`)과 폰 앱(`../phone-app`)이 같이 쓰는 서버. Java 21 · Spring Boot 4 · 순수 WebSocket.

- 명세: [`docs/api.html`](docs/api.html) · 테이블: [`docs/erd.html`](docs/erd.html)
- 1단계: 가계정 2개 로그인, 친구 목록, 방 하나에서 대화 · 현재 위치 · 경로 공유(실시간) · 이 방에서 간 곳

## 실행 (로컬)

```bash
./gradlew bootRun
```

- 주소 `http://localhost:8080`, WebSocket `ws://localhost:8080/ws`
- DB는 `./data/tokcar.mv.db`(H2, PostgreSQL 모드). 지우면 처음부터 다시 만들어진다.
- 가계정(local 프로필에서만 만들어짐): `papa`(아빠, 차량 앱) · `minji`(민지, 폰 앱). 비밀번호는 `src/main/resources/application-local.properties`.

앱에서 붙을 때

| 기기 | 주소 |
|---|---|
| 안드로이드 에뮬레이터 | `http://10.0.2.2:8080` |
| USB로 연결한 폰 | `adb reverse tcp:8080 tcp:8080` 후 `http://localhost:8080` |
| 같은 Wi-Fi의 폰 | `http://<이 PC의 IP>:8080` |

## 테스트

```bash
./gradlew test
```

`ServerFlowTest`가 서버를 띄우고 아빠 · 민지로 REST · WebSocket을 직접 불러 본다(로그인 잠김, 권한, 대화 · 읽음, 경로 공유 실시간 · 2초 묶음, 위치 1년 정리, 이 방에서 간 곳).

## 구조

| 패키지 | 내용 |
|---|---|
| `domain` | 엔티티 · 저장소. 테이블은 Flyway `db/migration/V1__init.sql`이 만들고 JPA는 맞는지만 확인(`ddl-auto=validate`) |
| `auth` | 로그인 · 토큰(1단계는 서버 메모리) · `/api/**` 토큰 확인 |
| `chat` | 방 · 메시지 · 경로 공유 · 이 방에서 간 곳(`ChatService`), REST, 실시간 값(`LiveRoutes`, 메모리), 위치 정리 |
| `ws` | `/ws` 연결 · 프레임 처리 · 방 사람들에게 보내기 · route.live 2초 묶음 |
| `dev` | local 가계정 |

## AWS로 옮길 때 남은 일

- `SPRING_PROFILES_ACTIVE=prod` + `DB_URL` · `DB_USER` · `DB_PASSWORD`(RDS PostgreSQL)
- 토큰을 액세스 토큰 + Redis 리프레시 토큰으로, 서버 여러 대면 `LiveRoutes` · WebSocket 전달을 Redis로
- HTTPS(wss) 앞단, WebSocket 허용 Origin 좁히기
