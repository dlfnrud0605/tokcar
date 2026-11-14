import 'package:flutter/material.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';

import '../config/map_config.dart';
import '../core/store.dart';
import '../data/place_search.dart';
import 'chat.dart';
import 'common.dart';
import 'icons.dart';
import 'theme.dart';

/// [내 위치 보내기]: 지도에서 위치 고르기(카카오T 탑승 위치처럼).
/// 가운데 핀은 화면에 고정되고 지도를 끌어 옮긴다. 멈추면 그 자리 주소를 찾고, 위 검색창으로 장소명 · 주소를 찾아 그곳으로 갈 수 있다.
/// 아래는 대화방 위치 카드와 같은 모양으로 「보내면 이렇게 보여요」를 미리 보이고 [이 위치 보내기].
/// 제목은 자동: 검색으로 고른 장소면 그 이름, 아니면 주소를 줄여 「분당구 정자일로」까지.
class PickPlace extends StatefulWidget {
  const PickPlace(this.s, this.p, {super.key});
  final TokStore s;
  final PickState p;

  @override
  State<PickPlace> createState() => _PickPlaceState();
}

class _PickPlaceState extends State<PickPlace> {
  NaverMapController? _map;
  int _moved = 0;
  final _query = TextEditingController();
  final _queryFocus = FocusNode();
  /// 손으로 지도를 끄는 중: 핀이 들리고 그림자가 보인다.
  bool _lifted = false;

  @override
  void didUpdateWidget(PickPlace old) {
    super.didUpdateWidget(old);
    final p = widget.p;
    // 검색 결과를 고르면 그 자리로 지도를 옮기고 이름 칸을 채운다
    if (p.moveSeq != _moved) {
      _moved = p.moveSeq;
      _map?.updateCamera(NCameraUpdate.scrollAndZoomTo(target: NLatLng(p.lat, p.lon), zoom: 16.5)..setAnimation(duration: const Duration(milliseconds: 500)));
    }
  }

  @override
  void dispose() {
    _query.dispose();
    _queryFocus.dispose();
    super.dispose();
  }

  Future<void> _idle() async {
    if (_lifted) setState(() => _lifted = false);
    final c = _map;
    if (c == null) return;
    final pos = await c.getCameraPosition();
    widget.s.pickMoved(pos.target.latitude, pos.target.longitude);
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.s, p = widget.p;
    final pad = MediaQuery.paddingOf(context);
    final hits = p.hits;
    final ink = CardInk(mine: false);
    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      label: '보낼 위치 고르기',
      child: ColoredBox(
        color: K.map,
        child: Stack(children: [
          Positioned.fill(
            child: useNaverMap
                ? NaverMap(
                    forceGesture: true,
                    options: NaverMapViewOptions(
                      initialCameraPosition: NCameraPosition(target: NLatLng(p.lat, p.lon), zoom: 16),
                      nightModeEnable: K.dark,
                      locale: const Locale('ko'),
                      scaleBarEnable: false,
                      locationButtonEnable: false,
                      rotationGesturesEnable: false,
                      tiltGesturesEnable: false,
                      logoAlign: NLogoAlign.leftBottom,
                      logoMargin: EdgeInsets.only(left: u(Dims.cardInset), bottom: u(420) + pad.bottom),
                    ),
                    onMapReady: (c) => _map = c,
                    onCameraChange: (reason, _) {
                      if (reason == NCameraUpdateReason.gesture && !_lifted) setState(() => _lifted = true);
                    },
                    onCameraIdle: _idle,
                  )
                : const SizedBox.shrink(),
          ),
          // 가운데 고정 핀: 외곽선 없는 물방울 핀(30x38). 뾰족한 끝이 지도 한가운데를 가리키고, 끄는 동안 들리며 그림자 점이 보인다
          IgnorePointer(
            child: Center(
              child: SizedBox(
                width: 30,
                height: 76,
                child: Stack(clipBehavior: Clip.none, children: [
                  // 바닥 그림자: 한 가지 색(검정 30%)으로 늘 보이고, 끄는 동안(핀이 들림) 같은 색 그대로 조금 커진다
                  Positioned(
                    left: 0,
                    right: 0,
                    top: 35,
                    child: Center(
                      child: AnimatedScale(
                        scale: _lifted ? 1.4 : 1,
                        duration: const Duration(milliseconds: 150),
                        child: Container(
                          width: 12,
                          height: 5,
                          decoration: BoxDecoration(color: Colors.black.withValues(alpha: .3), borderRadius: BorderRadius.circular(6)),
                        ),
                      ),
                    ),
                  ),
                  AnimatedPositioned(
                    duration: const Duration(milliseconds: 150),
                    curve: Curves.easeOut,
                    left: 0,
                    top: _lifted ? -14 : 0,
                    child: const SizedBox(width: 30, height: 38, child: CustomPaint(painter: _DropPin())),
                  ),
                ]),
              ),
            ),
          ),
          // 위: 뒤로 + 검색창, 그 아래 결과 목록
          Positioned(
            left: u(Dims.cardInset),
            right: u(Dims.cardInset),
            top: pad.top + u(28),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Row(children: [
                IconCircle(Ic.back, '위치 고르기 닫기', s.closePick),
                SizedBox(width: u(16)),
                Expanded(
                  child: Container(
                    height: u(96),
                    padding: EdgeInsets.symmetric(horizontal: u(24)),
                    decoration: BoxDecoration(
                      color: K.card,
                      borderRadius: BorderRadius.circular(u(48)),
                      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: .18), blurRadius: u(20), offset: Offset(0, u(6)))],
                    ),
                    child: Row(children: [
                      Ico(Ic.search, u(34), K.sub),
                      SizedBox(width: u(12)),
                      Expanded(
                        child: TextField(
                          controller: _query,
                          focusNode: _queryFocus,
                          textInputAction: TextInputAction.search,
                          onChanged: s.setPickQuery,
                          onSubmitted: (_) => s.pickSearch(),
                          onTapOutside: (_) => _queryFocus.unfocus(),
                          style: TextStyle(fontSize: u(30), color: K.ink),
                          cursorColor: K.blue,
                          decoration: InputDecoration.collapsed(hintText: '장소 · 주소 검색', hintStyle: TextStyle(fontSize: u(30), color: K.sub)),
                        ),
                      ),
                      if (p.searching) SizedBox(width: u(32), height: u(32), child: CircularProgressIndicator(strokeWidth: u(4), color: K.blue)),
                    ]),
                  ),
                ),
              ]),
              if (hits != null || p.searchNote != null)
                Container(
                  margin: EdgeInsets.only(top: u(12)),
                  constraints: BoxConstraints(maxHeight: u(560)),
                  decoration: BoxDecoration(
                    color: K.card,
                    borderRadius: BorderRadius.circular(u(28)),
                    boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: .18), blurRadius: u(20), offset: Offset(0, u(6)))],
                  ),
                  child: p.searchNote != null && (hits == null || hits.isEmpty)
                      ? Padding(padding: EdgeInsets.all(u(28)), child: T(p.searchNote!, 26, color: K.sub, lines: 3))
                      : ListView.separated(
                          padding: EdgeInsets.symmetric(vertical: u(8)),
                          shrinkWrap: true,
                          itemCount: hits!.length,
                          separatorBuilder: (_, _) => Divider(height: 1, color: K.line),
                          itemBuilder: (_, i) => _HitRow(hits[i], p, () {
                            _queryFocus.unfocus();
                            s.pickHit(hits[i]);
                          }),
                        ),
                ),
            ]),
          ),
          // 아래: 대화방 위치 카드 모양 미리 보기 + [이 위치 보내기]
          Positioned(
            left: u(Dims.cardInset),
            right: u(Dims.cardInset),
            bottom: pad.bottom + u(40),
            child: DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(u(36)),
                boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: .28), blurRadius: u(36), offset: Offset(0, u(12)))],
              ),
              child: ChatCard(mine: false, radius: 36, children: [
                Tag('위치 공유', K.hereTag, K.hereTagInk, icon: Ic.pin),
                SizedBox(height: u(12)),
                if (p.addrBusy) ...[
                  T('주소를 찾고 있어요…', 38, color: K.sub, weight: FontWeight.w800),
                  T(' ', 26),
                ] else ...[
                  T(p.title.isNotEmpty ? p.title : '${p.lat.toStringAsFixed(5)}, ${p.lon.toStringAsFixed(5)}', 38, color: ink.fg, weight: FontWeight.w800),
                  T(p.addr.isNotEmpty ? p.addr : (p.addrNote ?? ''), 26, color: ink.sub, lines: 2),
                ],
                SizedBox(height: u(18)),
                Tap(
                  label: '이 위치 보내기',
                  radius: Dims.r,
                  enabled: !p.addrBusy,
                  onTap: s.sendPicked,
                  child: Opacity(
                    opacity: p.addrBusy ? .5 : 1,
                    child: Container(
                      height: u(80),
                      decoration: BoxDecoration(gradient: K.grad, borderRadius: BorderRadius.circular(u(Dims.r))),
                      child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Ico(Ic.send, u(26), K.onGrad),
                        SizedBox(width: u(8)),
                        T('이 위치 보내기', 28, color: K.onGrad, weight: FontWeight.bold),
                      ]),
                    ),
                  ),
                ),
              ]),
            ),
          ),
        ]),
      ),
    );
  }
}

/// 외곽선 없는 물방울 핀(38x48 그림을 30x38에 맞춤): 앱 그라데이션 몸통 + 흰 점, 아래로 옅은 그림자.
class _DropPin extends CustomPainter {
  const _DropPin();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 38, size.height / 48);
    final body = Path()
      ..moveTo(19, 47)
      ..cubicTo(19, 47, 3, 29, 3, 18)
      ..arcToPoint(const Offset(35, 18), radius: const Radius.circular(16))
      ..cubicTo(35, 29, 19, 47, 19, 47)
      ..close();
    canvas.drawShadow(body, Colors.black, 2, false);
    canvas.drawPath(body, Paint()..shader = LinearGradient(colors: [K.g1, K.g2], begin: Alignment.topLeft, end: Alignment.bottomRight).createShader(const Rect.fromLTWH(3, 2, 32, 45)));
    canvas.drawCircle(const Offset(19, 18), 6, Paint()..color = Colors.white);
  }

  @override
  bool shouldRepaint(_DropPin old) => false;
}

/// 검색 결과 한 줄: 장소 이름(없으면 주소) · 주소 · 핀에서 거리.
class _HitRow extends StatelessWidget {
  const _HitRow(this.h, this.p, this.onTap);
  final PlaceHit h;
  final PickState p;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final d = distM(p.lat, p.lon, h.lat, h.lon);
    final far = d >= 1000 ? '${(d / 1000).toStringAsFixed(d >= 10000 ? 0 : 1)}km' : '${d.round()}m';
    return Tap(
      label: h.name.isEmpty ? h.addr : '${h.name}, ${h.addr}',
      onTap: onTap,
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: u(28), vertical: u(18)),
        child: Row(children: [
          Ico(Ic.pin, u(30), K.blue),
          SizedBox(width: u(16)),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              T(h.name.isEmpty ? h.addr : h.name, 28, color: K.ink, weight: FontWeight.bold, lines: 1),
              if (h.name.isNotEmpty) T(h.addr, 22, color: K.sub, lines: 1),
            ]),
          ),
          SizedBox(width: u(12)),
          T(far, 22, color: K.sub),
        ]),
      ),
    );
  }
}
