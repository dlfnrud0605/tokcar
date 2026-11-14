import 'dart:math' as math;
import 'dart:ui' show PathMetric;

import 'package:flutter/material.dart';

import '../config/map_config.dart';
import '../core/store.dart';
import '../data/models.dart';
import 'chat.dart';
import 'common.dart';
import 'icons.dart';
import 'naver_map_layer.dart';
import 'theme.dart';

/// 지도 보기(모바일에만 있음): 가족이 보낸 위치 카드의 [지도 보기]로 연다. (디자인: design/map_design.html — 프로필 A · 경로선 C · 목적지 C)
/// 위: 뒤로 가기 버튼만(보낸 사람 칩은 없음). 가운데: 지도. 경로 공유는 남은 길만 점선으로, 도착 깃발, 보낸 사람 프로필 마커(사각형 라운드)가 경로를 따라 움직임.
/// 현재 위치는 그 자리에 프로필 마커 하나. 아래: 경로 공유 카드(남은 시간·진행 막대가 같이 줄어듦) 또는 현재 위치 카드.
/// 키가 없거나 인증에 실패하면 아래 [_MapPainter] 의 그림 지도(실제 지도·좌표가 아님)로 같은 디자인을 보여 준다.
class MapView extends StatefulWidget {
  const MapView(this.s, this.mp, {super.key});
  final TokStore s;
  final MapState mp;

  @override
  State<MapView> createState() => _MapViewState();
}

class _MapViewState extends State<MapView> with TickerProviderStateMixin {
  /// 그림 지도(키가 없을 때)의 물결용. 네이버 지도가 뜰 때는 쓰지 않으므로 필요할 때 처음 만든다(닫는 중에 처음 만들면 오류가 남).
  AnimationController? _pulseCtl;
  AnimationController get _pulse => _pulseCtl ??= AnimationController(vsync: this, duration: const Duration(milliseconds: 1800))..repeat();

  @override
  void dispose() {
    _pulseCtl?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.s, mp = widget.mp;
    final p = mp.dest ?? s.place(mp.placeId);
    final pad = MediaQuery.paddingOf(context);
    final here = mp.here;
    final arrived = !here && (mp.end == RouteEnd.arrived || (mp.end == null && mp.remain <= 0 && mp.pct == null));
    final reduce = MediaQuery.disableAnimationsOf(context);
    final ink = CardInk(mine: false);
    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      label: here ? '${mp.who}의 위치 공유' : '${mp.who}의 차 위치',
      child: TapQuiet(
        child: ColoredBox(
          color: K.map,
          child: Stack(children: [
            // 지도: 키가 있으면 네이버 지도(안드로이드), 없거나 인증에 실패하면 그림 지도. 진행률이 바뀌면 차가 그 자리까지 움직인다.
            Positioned.fill(
              child: ValueListenableBuilder<String?>(
                valueListenable: naverAuthFailed,
                builder: (_, failed, _) {
                  if (useNaverMap && failed == null && p != null) {
                    final size = MediaQuery.sizeOf(context);
                    return NaverMapLayer(
                      key: ValueKey('${mp.roomId}/${mp.who}/${mp.placeId}/${mp.kind}'),
                      here: here,
                      start: here ? (p.lat, p.lon) : s.routeStart(mp),
                      dest: (p.lat, p.lon),
                      who: mp.who,
                      whoColor: s.memberColor(mp.who),
                      directions: s.directions,
                      progress: mp.progress,
                      topInset: mapBare ? 0 : pad.top + u(36) + u(80) + u(24),
                      bottomInset: mapBare ? 0 : size.height * (here ? .30 : .42),
                      bare: mapBare,
                    );
                  }
                  return TweenAnimationBuilder<double>(
                    tween: Tween(end: mp.progress),
                    duration: Duration(milliseconds: reduce ? 0 : 600),
                    curve: Curves.easeInOut,
                    builder: (_, f, _) => AnimatedBuilder(
                      animation: _pulse,
                      builder: (_, _) => CustomPaint(painter: _MapPainter(f, reduce ? null : _pulse.value, here: here, who: mp.who, whoColor: s.memberColor(mp.who))),
                    ),
                  );
                },
              ),
            ),
            // 네이버 인증에 실패하면(401 등) 그림 지도로 바뀌었다고 알림
            ValueListenableBuilder<String?>(
              valueListenable: naverAuthFailed,
              builder: (_, failed, _) => failed == null
                  ? const SizedBox.shrink()
                  : Positioned(
                      left: u(Dims.cardInset),
                      right: u(Dims.cardInset),
                      top: pad.top + u(36) + u(80) + u(16),
                      child: Container(
                        padding: EdgeInsets.symmetric(horizontal: u(20), vertical: u(12)),
                        decoration: BoxDecoration(color: K.red, borderRadius: BorderRadius.circular(u(20))),
                        child: T('네이버 지도 인증 실패: $failed', 22, color: K.onRed, weight: FontWeight.bold, lines: 3),
                      ),
                    ),
            ),
            if (!mapBare)
              Positioned(
                left: u(Dims.cardInset),
                top: pad.top + u(36),
                child: IconCircle(Ic.back, '지도 닫기', s.closeMap),
              ),
            if (p != null && !mapBare)
              Positioned(
                left: u(Dims.cardInset),
                right: u(Dims.cardInset),
                bottom: pad.bottom + u(56),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(u(36)),
                    boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: .28), blurRadius: u(36), offset: Offset(0, u(12)))],
                  ),
                  child: ChatCard(mine: false, radius: 36, children: [
                    if (here) ...[
                      Tag('위치 공유', K.hereTag, K.hereTagInk, icon: Ic.pin),
                      SizedBox(height: u(12)),
                      T(p.title, 38, color: ink.fg, weight: FontWeight.w800),
                      T(p.fullAddr, 26, color: ink.sub, lines: 2),
                      T('${mp.time}에 보낸 위치예요', 24, color: ink.sub),
                    ] else ...[
                      Tag('경로 공유', K.route, K.routeInk, icon: Ic.route),
                      SizedBox(height: u(12)),
                      ...routeBody(ink,
                          name: p.title, from: mp.from, to: p.fullAddr, min: mp.remain, pct: (mp.progress * 100).round(), eta: mp.eta, arrived: arrived, stopped: mp.end == RouteEnd.stopped, traffic: mp.traffic),
                    ],
                  ]),
                ),
              ),
          ]),
        ),
      ),
    );
  }
}

/// 키가 없을 때 보이는 그림 지도(실제 지도 아님). 지도 화면과 같은 디자인: 남은 길 점선, 도착 깃발, 프로필 네모.
class _MapPainter extends CustomPainter {
  _MapPainter(this.f, this.pulse, {required this.here, required this.who, required this.whoColor});
  /// 현재 위치: 경로 없이 그 자리에 프로필 네모 하나만 그린다.
  final bool here;
  /// 진행률(0~1): 프로필 위치와 남은 경로.
  final double f;
  /// 프로필 둘레 물결(0~1). null 이면 그리지 않음(움직임 줄이기).
  final double? pulse;
  final String who;
  final int whoColor;

  static const _w = 841.0, _h = 1820.0;

  /// 경로: M170 880 C 170 780 340 780 340 670 S 300 520 450 440 S 620 380 650 300 (S 는 앞 제어점을 뒤집은 C 로 풀어 씀)
  static final Path _route = Path()
    ..moveTo(170, 880)
    ..cubicTo(170, 780, 340, 780, 340, 670)
    ..cubicTo(340, 560, 300, 520, 450, 440)
    ..cubicTo(600, 360, 620, 380, 650, 300);
  static final PathMetric _metric = _route.computeMetrics().first;

  @override
  void paint(Canvas canvas, Size size) {
    final k = math.max(size.width / _w, size.height / _h);
    canvas.save();
    canvas.translate((size.width - _w * k) / 2, (size.height - _h * k) / 2);
    canvas.scale(k);
    canvas.drawRect(const Rect.fromLTWH(0, 0, _w, _h), Paint()..color = K.map);
    canvas.translate(0, 305);

    Paint stroke(Color c, double w) => Paint()
      ..color = c
      ..style = PaintingStyle.stroke
      ..strokeWidth = w
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    // 물길
    canvas.drawPath(Path()..moveTo(-20, 700)..cubicTo(200, 620, 420, 760, 880, 640), stroke(K.mapWater, 90)..strokeCap = StrokeCap.butt);
    // 건물 덩어리
    final block = Paint()..color = K.mapBlock;
    for (final b in const [
      [40, -180, 220, 170], [560, -160, 220, 150], [40, 120, 220, 150], [560, 140, 220, 190],
      [60, 880, 260, 180], [520, 900, 260, 170], [40, 1180, 240, 160], [560, 1200, 220, 170],
    ]) {
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(b[0].toDouble(), b[1].toDouble(), b[2].toDouble(), b[3].toDouble()), const Radius.circular(18)), block);
    }
    // 도로
    final road = stroke(K.mapRoad, 20);
    for (final l in const [
      [-20, 340, 880, 300], [300, -380, 330, 1530], [-20, 1100, 880, 1060], [-20, -60, 880, -90], [-20, 1400, 880, 1380], [620, -380, 560, 1530],
    ]) {
      canvas.drawLine(Offset(l[0].toDouble(), l[1].toDouble()), Offset(l[2].toDouble(), l[3].toDouble()), road);
    }
    final casing = K.dark ? const Color(0xFFEAF0FF) : Colors.white;
    final box = 80 * 1.0; // 프로필 네모(차량 앱 dp 80 = 뒤로 가기 버튼 크기)

    void profile(Offset c) {
      final pv = pulse;
      if (pv != null) {
        final e = Curves.easeOut.transform(pv);
        final r = box * (.62 + .45 * e);
        canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: c, width: r * 2, height: r * 2), Radius.circular(r * .68)), Paint()..color = K.g1.withValues(alpha: .35 * (1 - e)));
      }
      final r = box * .34;
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: c + const Offset(0, 4), width: box, height: box), Radius.circular(r)), Paint()..color = Colors.black.withValues(alpha: .25));
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: c, width: box, height: box), Radius.circular(r)), Paint()..color = casing);
      final (bg, ink) = K.tones[toneIndexOf(whoColor)];
      final inner = box - 12;
      canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: c, width: inner, height: inner), Radius.circular(inner * .34)), Paint()..color = bg);
      final tp = TextPainter(
        text: TextSpan(text: who.isEmpty ? '' : who.characters.first, style: TextStyle(fontSize: box * .4, fontWeight: FontWeight.w800, color: ink)),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, c - Offset(tp.width / 2, tp.height / 2));
    }

    if (here) {
      profile(const Offset(400, 600));
      canvas.restore();
      return;
    }
    final len = _metric.length, d = len * f.clamp(0.0, 1.0);
    // 남은 길만 점선(테두리 점 + 파랑 점)
    for (var o = d; o <= len; o += 34) {
      final pos = _metric.getTangentForOffset(o)!.position;
      canvas.drawCircle(pos, 15, Paint()..color = casing.withValues(alpha: .95));
    }
    for (var o = d; o <= len; o += 34) {
      canvas.drawCircle(_metric.getTangentForOffset(o)!.position, 9, Paint()..color = K.g1);
    }
    // 프로필: 경로 위 지금 자리. 깃발을 나중에 그려서 겹치면 목적지가 프로필 위에 보인다.
    profile(_metric.getTangentForOffset(d)?.position ?? const Offset(170, 880));
    // 도착: 깃발(경로 공유 초록). 깃대 아래 끝이 도착 위치.
    const tip = Offset(650, 300);
    final top = tip + const Offset(0, -90);
    final flag = Path()
      ..moveTo(top.dx, top.dy)
      ..lineTo(top.dx + 52, top.dy + 17)
      ..lineTo(top.dx, top.dy + 36)
      ..close();
    canvas.drawOval(Rect.fromCenter(center: tip, width: 32, height: 12), Paint()..color = Colors.black.withValues(alpha: .25));
    canvas.drawLine(tip, top, stroke(casing, 14));
    canvas.drawPath(flag, stroke(casing, 12));
    canvas.drawPath(flag, Paint()..color = casing);
    canvas.drawLine(tip, top, stroke(K.g2, 6));
    canvas.drawPath(flag, Paint()..color = K.route);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_MapPainter old) => true; // 물결이 계속 움직이고 낮/밤 색도 바뀔 수 있어서 늘 다시 그림
}
