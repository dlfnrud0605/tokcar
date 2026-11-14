#!/usr/bin/env bash
# 한 번에: 차량 앱 빌드 → 에뮬레이터에 설치·실행 → 가족 서버 시작(이 터미널에서 계속 돎, 끄려면 Ctrl+C).
# 사전 준비: Pleos Connect 에뮬레이터(v2.1.0)를 미리 켜 둔다. Java 21, Android SDK 필요.
# 차 앱은 서버가 있어야 동작한다(더미 모드 없음). 에뮬레이터의 10.0.2.2 는 이 PC 라서 이 PC 에서 서버를 켜면 연결된다.
# 폰 앱은 따로: cd phone-app && flutter run -d <폰 에뮬레이터> --dart-define-from-file=env.json   (env.json 은 phone-app/env.example.json 참고)
# 네이버 키(주소 · 경로 · 장소 검색)는 server/secrets.properties 에 둔다(없어도 서버와 앱은 뜸, 그 기능만 꺼짐).
# 환경 변수: ANDROID_HOME(기본 ~/Android/Sdk 또는 macOS ~/Library/Android/sdk), ADB_SERIAL(에뮬레이터가 둘 이상일 때: `adb devices` 로 확인한 기기 ID)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
if [[ "$(uname)" == "Darwin" ]]; then DEFAULT_SDK="$HOME/Library/Android/sdk"; else DEFAULT_SDK="$HOME/Android/Sdk"; fi
export ANDROID_HOME="${ANDROID_HOME:-$DEFAULT_SDK}"
ADB="$ANDROID_HOME/platform-tools/adb"
[[ -x "$ADB" ]] || ADB="$(command -v adb || true)"
[[ -n "$ADB" ]] || { echo "adb 를 찾을 수 없음: ANDROID_HOME 을 확인하세요" >&2; exit 1; }
[[ -n "${ADB_SERIAL:-}" ]] && ADB=("$ADB" -s "$ADB_SERIAL") || ADB=("$ADB")

"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "켜진 에뮬레이터가 없음: Pleos Connect 에뮬레이터를 먼저 켜세요(둘 이상이면 adb devices 로 기기 ID 를 확인해 ADB_SERIAL=<기기ID> 로 지정)" >&2; exit 1; }

echo "== 차량 앱 빌드 =="
./gradlew :tokcar:assembleDebug -q
APK="tokcar/build/outputs/apk/debug/tokcar-debug.apk"

echo "== 설치 · 실행 =="
# Pleos 에뮬레이터는 화면이 사용자 10 으로 돌아서 --user 10 이 필요하다. 다른 에뮬레이터라면 안 붙여도 된다.
"${ADB[@]}" install -r --user 10 "$APK" >/dev/null 2>&1 || "${ADB[@]}" install -r "$APK" >/dev/null || {
  echo "설치 실패: 이미 서명이 다른 com.example.tokcar 가 깔려 있으면 'adb uninstall --user 10 com.example.tokcar' 후 다시 실행하세요" >&2; exit 1; }
"${ADB[@]}" shell am start --user 10 -n com.example.tokcar/.TokCarActivity >/dev/null 2>&1 \
  || "${ADB[@]}" shell am start -n com.example.tokcar/.TokCarActivity >/dev/null

echo "== 가족 서버 =="
if curl -s -o /dev/null --max-time 2 http://127.0.0.1:8080/; then
  echo "서버가 이미 켜져 있음(http://127.0.0.1:8080). 이 스크립트는 끝납니다."
  exit 0
fi
[[ -f server/secrets.properties ]] || echo "(알림) server/secrets.properties 가 없어서 주소 변환 · 경로 · 장소 검색은 꺼진 채로 돕니다. server/secrets.properties.example 참고"
echo "차 앱 로그인: 아이디 papa · 비밀번호 papa1234!   (끄려면 Ctrl+C)"
cd server
exec ./gradlew bootRun
