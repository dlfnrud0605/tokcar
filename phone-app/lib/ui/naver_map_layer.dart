import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';

import '../core/geo.dart';
import '../data/directions.dart';
import 'common.dart';
import 'profile_marker.dart';
import 'theme.dart';

/// 지도 보기 화면의 진짜 네이버 지도(안드로이드). 마커·경로선만 그리고, 위의 뒤로 가기와 아래 카드는 [MapView]가 얹는다.
/// (디자인: design/map_design.html 의 프로필 A · 경로선 C · 목적지 C)
/// - 경로 공유([here] = false): 지금 위치에서 도착까지 **남은 길만** 동그란 점을 반복한 점선(경로선 전용 오버레이의 패턴 이미지)으로, 도착 깃발, 보낸 사람 프로필 마커(사각형 라운드, 꼬리 없음).
///   [progress]가 바뀌면 프로필 마커가 따라 움직이고 지나온 길은 지워짐. 겹치면 목적지가 프로필 위에 보임.
/// - 현재 위치([here] = true): 그 자리에 프로필 마커 하나.
/// 경로선: [directions]로 도로 경로를 받으면 그 길을 따라가고, 못 받으면 출발~도착을 이은 곡선(실제 도로 아님)으로 그린다.
class NaverMapLayer extends StatefulWidget {
  const NaverMapLayer({
    super.key, required this.here, required this.start, required this.dest, required this.progress,
    required this.topInset, required this.bottomInset, required this.who, required this.whoColor, this.directions, this.bare = false,
  });
  final bool here;
  final LL start, dest;
  /// 마커의 얼굴: 보낸 사람 이름(첫 글자)과 그 사람의 얼굴 색.
  final String who;
  final int whoColor;
  /// 도로 경로를 구하는 곳. null 이거나 실패하면 출발~도착을 이은 곡선으로 그린다.
  final RouteResolver? directions;
  final double progress;
  /// 위 버튼·아래 카드에 가리지 않게 지도 중심을 밀어 주는 여백.
  final double topInset, bottomInset;
  /// 지도만 보이게(마커·경로선 없음): 시안용 지도 사진을 찍을 때.
  final bool bare;

  @override
  State<NaverMapLayer> createState() => _NaverMapLayerState();
}

class _NaverMapLayerState extends State<NaverMapLayer> {
  late List<LL> _route = routePoints(widget.start, widget.dest);
  NMarker? _who, _destMarker;
  /// 남은 길. 일반 선(NPolylineOverlay)의 점선 대신 경로선 전용 NPathOverlay 의 「패턴 이미지」로 동그란 점을 같은 간격으로 반복해 그린다.
  NPathOverlay? _path;
  /// 도로 경로를 못 불러온 이유(곡선으로 보이는 중이면 안내 칩에 보임).
  String? _routeNote;

  /// 점 하나의 크기(dp, 테두리 포함)와 점 사이 간격(점 중심 사이 dp).
  static const _dotSize = 10.0, _dotRing = 2.0, _dotInterval = 24.0;

  @override
  void initState() {
    super.initState();
    if (!widget.here && widget.directions != null && !widget.bare) _loadRoad();
  }

  static NLatLng _n(LL p) => NLatLng(p.$1, p.$2);

  /// 남은 길(지금 위치 ~ 도착). 목록으로 만들어 넘겨야 함(지연 계산 목록은 안드로이드로 전달 못 함).
  List<NLatLng> _rest() => remaining(_route, widget.progress).map(_n).toList();

  /// 도로 경로를 한 번 구해서 곡선을 바꾼다. 지도가 아직 준비 전이면 준비되면서 이 경로로 그린다.
  Future<void> _loadRoad() async {
    try {
      final r = await widget.directions!.route(widget.start, widget.dest);
      if (!mounted) return;
      _route = r.path;
      // 경로가 이상해 보일 때 확인용: 출발·도착 좌표, 받은 경로의 점 개수와 양 끝, 길이(m), 진행률
      debugPrint('[MapRoute] 요청 start=${widget.start} goal=${widget.dest} → 점 ${r.path.length}개 첫점=${r.path.first} 끝점=${r.path.last} '
          '길이=${lengthM(r.path).round()}m (API거리 ${r.distanceM}m) 진행률=${widget.progress.toStringAsFixed(2)}');
      await _applyRoute();
    } on DirectionsException catch (e) {
      if (mounted) setState(() => _routeNote = e.reason);
    } catch (e) {
      debugPrint('[Directions] $e');
      if (mounted) setState(() => _routeNote = '도로 경로를 불러오지 못했어요');
    }
  }

  NaverMapController? _c;

  /// 이미 그려 둔 선·마커를 새 경로로 바꾸고 화면을 경로에 맞춘다.
  Future<void> _applyRoute() async {
    final c = _c;
    if (c == null || _path == null) return; // 지도가 아직 준비 전: _onReady 가 _route 로 그림
    _path?.setCoords(_rest());
    _destMarker?.setPosition(_n(_route.last));
    _who?.setPosition(_n(pointAt(_route, widget.progress)));
    await _fit(c);
  }

  Future<void> _fit(NaverMapController c) => c.updateCamera(
        NCameraUpdate.fitBounds(NLatLngBounds.from(_route.map(_n)), padding: EdgeInsets.fromLTRB(56, widget.topInset + 24, 56, 56))
          ..setAnimation(animation: NCameraAnimation.easing, duration: const Duration(milliseconds: 600)),
      );

  // ───── 마커 그림(위젯 → 이미지) ─────

  static Color get _casingColor => ProfileMarker.casing;

  /// 물결 한 바퀴를 몇 장으로 나눌지와 장당 시간. 한 바퀴 = 12 × 150ms = 1.8초(시안의 펄스와 같은 속도).
  static const _pulseFrames = 12, _pulseStepMs = 150;

  Widget _profile(double? t) => ProfileMarker(who: widget.who, color: widget.whoColor, t: t);

  /// 물결 애니메이션: 프레임 이미지를 미리 만들어 두고 타이머로 마커 아이콘을 차례로 바꿔 끼운다.
  /// 움직임 줄이기 설정이면 하지 않는다(정적 프로필만).
  Timer? _pulse;
  Future<void> _startPulse(bool reduce) async {
    if (reduce) return;
    final c = u(Dims.iconBtn) * ProfileMarker.canvasScale;
    final frames = <NOverlayImage>[];
    for (var i = 0; i < _pulseFrames; i++) {
      // ignore: use_build_context_synchronously — 아직 이 화면이 살아 있을 때만 이어서 만든다(아래 mounted 검사)
      frames.add(await NOverlayImage.fromWidget(widget: _profile(i / _pulseFrames), size: Size(c, c), context: context));
      if (!mounted) return;
    }
    var i = 0;
    _pulse = Timer.periodic(const Duration(milliseconds: _pulseStepMs), (_) {
      i = (i + 1) % frames.length;
      _who?.setIcon(frames[i]);
    });
  }

  @override
  void dispose() {
    _pulse?.cancel();
    super.dispose();
  }

  /// 경로선의 점 하나: 둥근 점(경로 파랑 g1) + 둘레 테두리(시안과 같음: 전체 10, 파랑 6 + 테두리 2씩). 정사각형 이미지라 어떤 방향·길이에서도 동그랗게 보인다.
  Widget get _dotIcon => SizedBox(
        width: _dotSize,
        height: _dotSize,
        child: DecoratedBox(decoration: BoxDecoration(color: K.g1, shape: BoxShape.circle, border: Border.all(color: _casingColor, width: _dotRing))),
      );

  static const _flagW = 30.0, _flagH = 44.0, _poleX = 5.0, _poleBottom = 40.0;

  /// 목적지: 깃발(경로 공유 초록). 깃대 아래 끝이 도착 위치.
  Widget get _flagIcon => SizedBox(width: _flagW, height: _flagH, child: CustomPaint(painter: _FlagPainter(K.route, _casingColor, K.g2)));

  Future<void> _onReady(NaverMapController c) async {
    _c = c;
    if (widget.bare) return;
    // ignore: use_build_context_synchronously — 이미지 만들기는 아직 이 화면이 살아 있을 때만 이어서 한다(아래 mounted 검사)
    Future<NOverlayImage> img(Widget w, Size s) => NOverlayImage.fromWidget(widget: w, size: s, context: context);
    final reduce = MediaQuery.disableAnimationsOf(context);
    final p = u(Dims.iconBtn) * ProfileMarker.canvasScale;
    final profile = await img(_profile(null), Size(p, p));
    if (!mounted) return;
    if (widget.here) {
      final who = NMarker(id: 'who', position: _n(widget.dest), icon: profile, anchor: NPoint.relativeCenter);
      await c.addOverlay(who);
      if (!mounted) return;
      _who = who;
      unawaited(_startPulse(reduce));
      return;
    }
    final flag = await img(_flagIcon, const Size(_flagW, _flagH));
    if (!mounted) return;
    final used = _route; // 이 경로로 그린다(그리는 사이에 도로 경로가 도착하면 아래에서 바꿈)
    final rest = remaining(used, widget.progress).map(_n).toList();
    debugPrint('[MapRoute] 그리기 경로점 ${used.length}개 남은길 점 ${rest.length}개 프로필=${pointAt(used, widget.progress)} 깃발=${used.last} 진행률=${widget.progress.toStringAsFixed(2)}');
    final dot = await img(_dotIcon, const Size(_dotSize, _dotSize));
    if (!mounted) return;
    // 선 자체(몸통·외곽선·지나온 부분)는 모두 투명으로: 색을 안 정하면 기본값이 흰색이라 점 아래에 하얀 선이 같이 그려짐. 패턴 이미지(점)만 남긴다.
    final path = NPathOverlay(
      id: 'route', coords: rest, width: _dotSize, patternImage: dot, patternInterval: _dotInterval,
      color: Colors.transparent, outlineWidth: 0, outlineColor: Colors.transparent,
      passedColor: Colors.transparent, passedOutlineColor: Colors.transparent,
    );
    // 겹침 순서: 프로필(기본값) 위에 목적지가 오도록 목적지의 전역 z-index 를 더 크게
    final whoMarker = NMarker(id: 'who', position: _n(pointAt(used, widget.progress)), icon: profile, anchor: NPoint.relativeCenter);
    final destMarker = NMarker(id: 'dest', position: _n(used.last), icon: flag, anchor: const NPoint(_poleX / _flagW, _poleBottom / _flagH))
      ..setGlobalZIndex(whoMarker.globalZIndex + 1);
    await c.addOverlayAll({path, whoMarker, destMarker});
    if (!mounted) return;
    _path = path;
    _who = whoMarker;
    _destMarker = destMarker;
    unawaited(_startPulse(reduce)); // 프로필 둘레 물결(프레임 이미지를 만든 뒤 시작)
    if (!identical(used, _route)) await _applyRoute(); // 그리는 사이에 도로 경로가 도착했으면 지금 바꿈
  }

  @override
  void didUpdateWidget(NaverMapLayer old) {
    super.didUpdateWidget(old);
    if (widget.here || old.progress == widget.progress) return;
    _who?.setPosition(_n(pointAt(_route, widget.progress)));
    _path?.setCoords(_rest());
  }

  @override
  Widget build(BuildContext context) {
    final mid = ((widget.start.$1 + widget.dest.$1) / 2, (widget.start.$2 + widget.dest.$2) / 2);
    final center = widget.here ? widget.dest : mid;
    return Stack(children: [
      Positioned.fill(
        child: NaverMap(
          forceGesture: true, // 위에 겹친 Flutter 화면 때문에 지도 이동·확대가 막히지 않게
          options: NaverMapViewOptions(
            initialCameraPosition: NCameraPosition(target: _n(center), zoom: widget.here ? 15.5 : 12),
            nightModeEnable: K.dark,
            locale: const Locale('ko'),
            // 위쪽 여백은 0: 로고 위치가 이 여백 아래부터 계산돼서, 위 버튼을 피하는 여백은 화면 맞춤(fitBounds)에서 준다
            contentPadding: EdgeInsets.only(bottom: widget.bottomInset),
            scaleBarEnable: false,
            locationButtonEnable: false,
            rotationGesturesEnable: false,
            tiltGesturesEnable: false,
            // 오른쪽 위, 뒤로 가기 버튼과 같은 높이(버튼 가운데 높이에 로고 가운데를 맞춤)·같은 바깥 여백
            logoAlign: NLogoAlign.rightTop,
            logoMargin: EdgeInsets.only(right: u(Dims.cardInset), top: widget.bare ? 8 : math.max(8, widget.topInset - u(64) - 9)),
          ),
          onMapReady: (c) async {
            await _onReady(c);
            if (!widget.here) await _fit(c);
          },
        ),
      ),
      // 도로 경로를 못 불러와 곡선으로 보이는 중이면 이유를 알림(지도 위쪽 왼쪽: 오른쪽 위는 NAVER 로고)
      if (_routeNote != null)
        Positioned(
          left: u(Dims.cardInset),
          right: u(Dims.cardInset),
          top: widget.topInset + u(8),
          child: Align(
            alignment: Alignment.topLeft,
            child: Container(
              padding: EdgeInsets.symmetric(horizontal: u(16), vertical: u(8)),
              decoration: BoxDecoration(color: K.strong.withValues(alpha: .85), borderRadius: BorderRadius.circular(u(16))),
              child: T('곡선으로 표시 · $_routeNote', 20, color: K.onStrong, weight: FontWeight.bold, lines: 2),
            ),
          ),
        ),
    ]);
  }
}

/// 깃발 그림(30x44 칸): 깃대 아래 끝이 (5, 40). 테두리 → 깃대 → 깃발 순으로 그려 어두운 지도에서도 도드라지게 한다.
class _FlagPainter extends CustomPainter {
  const _FlagPainter(this.flag, this.casing, this.pole);
  final Color flag, casing, pole;

  @override
  void paint(Canvas canvas, Size size) {
    const x = 5.0, bottom = 40.0, top = 6.0;
    final flagPath = Path()
      ..moveTo(x, top)
      ..lineTo(x + 22, top + 7)
      ..lineTo(x, top + 15)
      ..close();
    Paint stroke(Color c, double w) => Paint()
      ..color = c
      ..style = PaintingStyle.stroke
      ..strokeWidth = w
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    // 그림자
    canvas.drawOval(Rect.fromCenter(center: const Offset(x, bottom), width: 14, height: 5), Paint()..color = Colors.black.withValues(alpha: .25));
    // 테두리
    canvas.drawLine(const Offset(x, bottom), const Offset(x, top), stroke(casing, 6));
    canvas.drawPath(flagPath, stroke(casing, 5));
    canvas.drawPath(flagPath, Paint()..color = casing);
    // 깃대 + 깃발
    canvas.drawLine(const Offset(x, bottom), const Offset(x, top), stroke(pole, 2.6));
    canvas.drawPath(flagPath, Paint()..color = flag);
    canvas.drawPath(flagPath, stroke(flag, 1));
  }

  @override
  bool shouldRepaint(_FlagPainter old) => old.flag != flag || old.casing != casing || old.pole != pole;
}
