# TokCar 폰 앱 (Flutter, `phone-app/`)

가족이 쓰는 폰 앱입니다. 차량 앱(저장소 루트의 `tokcar/`), 서버(`server/`)와 함께 동작합니다. 서버 없이 실행하면 내장 더미 데이터로 돕니다.

차량 앱(`tokcar/`, Jetpack Compose)의 코드를 보고 같은 화면을 Flutter로 옮긴 폰 앱. 색·크기·문구는 차량 앱 값 그대로이고, 크기는 폭 841 창 기준 값을 폰 폭에 맞춰 줄임(폭 390 → 0.46배, `lib/ui/theme.dart`의 `u()`).

```bash
cd phone-app
flutter run                       # 연결된 폰·폰 에뮬레이터에서 실행
flutter build apk --debug         # build/app/outputs/flutter-apk/app-debug.apk
flutter test                      # 상태 로직·화면 테스트
```

- 패키지 이름 `com.example.tokcar_mobile` → 차량 앱(`com.example.appa_eonjewa`)과 한 기기에 같이 설치됨.
- 로그인: 아이디 papa · 비밀번호 papa1234! (번호 010-1234-5678). 인증번호는 아무 6자리.
- 설정 탭 아래 "개발용 · 시험"에서 가족이 현재 위치·경로 공유·글을 보내는 상황을 흉내 냄.

## 차량 앱과 다른 점

폰에서는 길안내를 하지 않아서 안내에 묶인 화면을 모두 뺐다.

| 차량 앱 | 모바일 |
|---|---|
| 대화방 위쪽 경로 띠(목적지 · [안내]) | 없음 |
| 목록 방 카드 아래 목적지 띠([안내]/[안내 종료]/[+ 목적지]) | 없음(방 카드는 대화 행만) |
| 아래 파란 카드(15초 뒤 공유 · 공유 중 · 공유할 방 묻기) | 없음 |
| 목적지 · 공유할 방 고르기 · 경로 바꾸기 팝업 | 없음 |
| 가족 현재 위치 카드의 [안내][경유지] | **[지도 보기]**: 지도에 그 자리 핀만 보임(경로·차 없음, 「엄마의 위치 · 보낸 시각」) |
| 가족 경로 공유 카드의 [안내] | **[지도 보기]**: 지도에서 보낸 사람의 차가 경로를 따라 움직임(모바일에만 있는 화면) |
| 주행 중(기어 D) 글 입력 막기, 받은 메시지 읽어 주기 | 없음 |
| 말하기: Pleos SpeechToText SDK | 폰 음성 인식(`speech_to_text` 패키지, 한국어) |
| 설정 "실제 내비 쓰기", "내비에서 직접 안내 시작" | 없음 |
| 넓은 창(1400dp 이상) 두 칸 | 없음(폰은 한 칸) |

- 친구 탭 내 카드의 두 번째 줄은 차량 앱의 "운전 중/대기 중" 대신 "나"(대화상대 서랍과 같은 표기).
- 지도 보기는 아직 위치를 실시간으로 받는 서버가 없어서, 받은 카드 값(남은 분·진행률)에서 시작해 2초마다 1분씩 줄여 흉내 낸다(시안과 같음). 지도는 그림이고 실제 지도·좌표가 아님. 지도 색(`map*`)은 차량 앱에 없어서 `tokcar_mobile.html` 값을 씀.

## 구조 (`lib/`)

| 파일 | 내용 |
|---|---|
| `core/store.dart` | `TokStore`(모든 상태와 동작, 차량 앱 `core/Store.kt`에서 안내·공유를 뺀 것) |
| `core/text.dart`, `core/countries.dart` | 조사·전화번호·주소·남은 시간 도우미, 나라별 번호 |
| `core/voice.dart` | 말하기(음성 인식) |
| `data/` | 모델, `seed.dart`(더미 데이터 읽기), `server.dart`(`FamilyServer` · `FakeServer`) |
| `ui/` | 화면: `home`(목록·친구·설정·탭바), `chat`(대화방·카드·입력 바·서랍), `map_view`, `sheets`(팝업), `login`, `flags`, `theme`(색·크기), `icons`(SVG 아이콘) |

- 더미 서버 데이터는 차량 앱과 같은 파일(`assets/dummy/seed.json`, `../tokcar/src/main/assets/dummy/seed.json`을 복사). 진짜 서버가 생기면 `FamilyServer`를 구현해 `main.dart`의 `FakeServer` 자리에 끼우면 됨.
- 앱 아이콘: 안드로이드 런처는 차량 앱의 적응형 벡터 아이콘을 그대로 복사(`res/drawable/ic_launcher_*.xml`, minSdk 26). 앱 안 아이콘(`assets/tk_icon.svg`)은 `tools/vector_to_svg.py`로 차량 앱 `tk_icon.xml`에서 만든다.
- 화면 코드는 낮/밤 색(`K`)과 배율(`u`)을 전역에서 읽으므로 화면 위젯을 `const`로 만들지 않는다(const 위젯은 낮/밤이 바뀔 때 다시 그려지지 않음).


## 알려진 한계

- iOS는 빌드·실행해 보지 않음(리눅스에서 만듦). iOS 앱 아이콘은 Flutter 기본 아이콘 그대로.
- 실제 폰에서 말하기(음성 인식)는 확인하지 못함. 기기에 음성 인식 서비스(Google 앱 등)가 있어야 함.
- 내 위치 보내기의 주소는 더미(`seed.json`의 `me.addr`)이고 폰 GPS는 아직 안 읽음.
