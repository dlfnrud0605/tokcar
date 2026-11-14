// ignore_for_file: non_constant_identifier_names, constant_identifier_names
import 'package:flutter/material.dart';

/// 차량 앱(tokcar/ ui/theme/Theme.kt)의 색을 그대로 옮김. 낮 「남색」 / 밤 「하늘」.
/// 역할: 파랑 = 주 단추·내 것, 흰색 = 남의 것, 진한 남색 = 위치·경로 카드, 빨강 = 종료·안 읽음, 초록 = 진행·남은 시간.
class AppColors {
  const AppColors({
    required this.bg, required this.card, required this.ink, required this.sub, required this.block, required this.line, required this.dash,
    required this.bubIn, required this.via, required this.viaOn,
    required this.blue, required this.onBlue, required this.blueSoft, required this.blueInk,
    required this.g1, required this.g2, required this.onGrad, required this.onGradSub,
    required this.bub1, required this.bub2, required this.onBub,
    required this.strong, required this.onStrong,
    required this.hereTag, required this.hereTagInk, required this.route, required this.routeInk,
    required this.red, required this.onRed, required this.chip, required this.chipInk, required this.ic1, required this.ic2,
    required this.tones, required this.meBg, required this.meInk,
    required this.map, required this.mapBlock, required this.mapRoad, required this.mapWater,
    required this.dark,
  });

  final Color bg, card, ink, sub, block, line, dash;
  /// 상대 말풍선·상대가 보낸 위치·경로 카드의 바탕, 메시지 입력칸, 켜진 단추의 바탕.
  final Color bubIn, via, viaOn;
  final Color blue, onBlue, blueSoft, blueInk;
  /// 그라데이션 두 색(버튼·로그인 패널·내 말풍선).
  final Color g1, g2, onGrad, onGradSub;
  final Color bub1, bub2, onBub;
  /// 진한 면(토스트·말하기)과 그 위 글자.
  final Color strong, onStrong;
  final Color hereTag, hereTagInk, route, routeInk;
  final Color red, onRed;
  final Color chip, chipInk;
  /// 선택된 탭 아이콘 그라데이션(밝은 쪽 → 진한 쪽).
  final Color ic1, ic2;
  /// 얼굴 톤 t0..t5 (배경, 글자). 나는 [meBg]/[meInk].
  final List<(Color, Color)> tones;
  final Color meBg, meInk;
  /// 지도 보기 화면(모바일에만 있음). 색은 화면 시안의 --map* 값.
  final Color map, mapBlock, mapRoad, mapWater;
  final bool dark;

  /// 웹 시안의 기본 방향 ↘: 왼쪽 위가 밝은 쪽(g1), 오른쪽 아래가 진한 쪽(g2).
  LinearGradient get grad => LinearGradient(colors: [g1, g2], begin: Alignment.topLeft, end: Alignment.bottomRight);
  LinearGradient get bubGrad => LinearGradient(colors: [bub1, bub2], begin: Alignment.topLeft, end: Alignment.bottomRight);
}

const LightColors = AppColors(
  bg: Color(0xFFEEF1F7), card: Colors.white, ink: Color(0xFF10203F), sub: Color(0xFF5A6782),
  block: Color(0xFFE2E7F1), line: Color(0xFFD3DBEA), dash: Color(0xFFA9B6CF),
  bubIn: Color(0xFFE2E7F1), via: Color(0xFFC9CFDC), viaOn: Color(0xFFB7C1D7),
  blue: Color(0xFF1F3A7A), onBlue: Colors.white, blueSoft: Color(0xFFDDE3F0), blueInk: Color(0xFF16295A),
  g1: Color(0xFF3A5FB0), g2: Color(0xFF14254F), onGrad: Colors.white, onGradSub: Colors.white,
  bub1: Color(0xFF3A5FB0), bub2: Color(0xFF14254F), onBub: Colors.white,
  strong: Color(0xFF0E1A38), onStrong: Colors.white,
  hereTag: Color(0xFF9DB4EE), hereTagInk: Colors.white, route: Color(0xFF2BD4A0), routeInk: Colors.white,
  red: Color(0xFFE5482D), onRed: Colors.white,
  chip: Colors.white, chipInk: Color(0xFF16295A), ic1: Color(0xFF3A5FB0), ic2: Color(0xFF14254F),
  tones: [
    (Color(0xFFDDE3F0), Color(0xFF16295A)), (Color(0xFFD4EEFF), Color(0xFF0B67A8)), (Color(0xFFD6F1F4), Color(0xFF0B6470)),
    (Color(0xFFE2E5FF), Color(0xFF3A43C2)), (Color(0xFFE6EDF7), Color(0xFF2E4F7E)), (Color(0xFFDDE7FA), Color(0xFF3558A8)),
  ],
  meBg: Color(0xFFE2E7F1), meInk: Color(0xFF10203F),
  map: Color(0xFFE4ECF8), mapBlock: Color(0xFFD5E0F1), mapRoad: Colors.white, mapWater: Color(0xFFCFE0F2),
  dark: false,
);

const DarkColors = AppColors(
  bg: Color(0xFF0B1222), card: Color(0xFF141E36), ink: Color(0xFFEAF0FF), sub: Color(0xFF93A3C4),
  block: Color(0xFF1C2945), line: Color(0xFF263457), dash: Color(0xFF3A4C78),
  bubIn: Color(0xFF1C2945), via: Color(0xFF35415B), viaOn: Color(0xFF384F6E),
  blue: Color(0xFF9AD7FF), onBlue: Color(0xFF0B1222), blueSoft: Color(0xFF1D345D), blueInk: Color(0xFFBDE5FF),
  g1: Color(0xFF2F5696), g2: Color(0xFF1F3761), onGrad: Color(0xFFEAF0FF), onGradSub: Color(0xFFEAF0FF),
  bub1: Color(0xFF2F5696), bub2: Color(0xFF1F3761), onBub: Color(0xFFEAF0FF),
  strong: Color(0xFFEAF0FF), onStrong: Color(0xFF0B1222),
  hereTag: Color(0xFF2A5FE0), hereTagInk: Colors.white, route: Color(0xFF0E8F6E), routeInk: Colors.white,
  red: Color(0xFFF0604A), onRed: Colors.white,
  chip: Color(0xFFEAF0FF), chipInk: Color(0xFF1F3761), ic1: Color(0xFFB8E3FF), ic2: Color(0xFF9AD7FF),
  tones: [
    (Color(0xFF1D345D), Color(0xFFBDE5FF)), (Color(0xFF163A55), Color(0xFF8FD0FF)), (Color(0xFF15393F), Color(0xFF86D9E2)),
    (Color(0xFF24295C), Color(0xFFAEB5FF)), (Color(0xFF1F2D45), Color(0xFFA9C0E2)), (Color(0xFF1E2C4E), Color(0xFF9FB6EA)),
  ],
  meBg: Color(0xFF1C2945), meInk: Color(0xFFEAF0FF),
  map: Color(0xFF101A30), mapBlock: Color(0xFF1A2745), mapRoad: Color(0xFF26365C), mapWater: Color(0xFF14304F),
  dark: true,
);

/// 지금 색(낮/밤). 앱 맨 위([TokCarRoot])가 그릴 때마다 시스템 밝기에 맞춰 바꾼다. 화면 코드에서 K.card, K.ink 처럼 쓴다.
AppColors K = LightColors;

/// 크기 배율. 차량 앱의 dp 값은 폭 841 창 기준이라, 모바일 시안(tokcar_mobile.html)처럼 폰 폭에 맞춰 줄인다(폭 390 → 0.46배).
double _scale = 390 / 841;

/// 차량 앱의 dp 값 → 이 화면의 크기.
double u(num v) => v * _scale;

/// 화면 폭으로 배율을 정한다(태블릿처럼 넓으면 600 폭까지만 키움).
void setScaleForWidth(double width) => _scale = width.clamp(320.0, 600.0) / 841;

/// 크기(차량 앱 dp, 쓸 때 [u]로 바꿈).
class Dims {
  /// 화면 좌우 여백.
  static const gut = 32.0;
  /// 대화방 메시지·제목줄의 좌우 끝.
  static const cardInset = 48.0;
  /// 큰 단추·입력칸 높이.
  static const hL = 100.0;
  /// 아래 탭바·대화방 입력 바 높이(같음).
  static const barH = 148.0;
  /// 아이콘 단추(동그라미) 지름.
  static const iconBtn = 80.0;
  static const actW = 161.0;
  static const actH = 80.0;
  /// 큰 것(단추·입력칸·카드) 모서리.
  static const r = 30.0;
  /// 작은 태그 모서리.
  static const rTag = 14.0;
}

/// 방·친구 얼굴 색(진한 색) → 연한 톤 번호. 모르는 색은 0(파랑 톤).
int toneIndexOf(int color) {
  switch (color & 0xFFFFFF) {
    case 0xC2410C:
      return 0;
    case 0x92600A:
    case 0xA16207:
      return 1;
    case 0x0F766E:
      return 2;
    case 0x0E7490:
    case 0x1D4ED8:
    case 0x0369A1:
      return 3;
    case 0x5B3FD0:
    case 0x6D28D9:
    case 0x4338CA:
      return 4;
    case 0xBE185D:
    case 0x9D174D:
      return 5;
    default:
      return 0;
  }
}
