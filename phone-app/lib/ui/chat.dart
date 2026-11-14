import 'package:flutter/material.dart';

import '../core/store.dart';
import '../core/text.dart';
import '../data/models.dart';
import 'common.dart';
import 'icons.dart';
import 'theme.dart';

/// 대화방: 제목줄(뒤로 · 이름+인원 · 메뉴) + 메시지 + 입력 바.
/// 차량 앱의 위쪽 경로 띠([안내])와 아래 파란 공유 카드는 모바일에 없음(길안내를 하지 않음).
class ChatRoom extends StatefulWidget {
  const ChatRoom(this.s, this.room, {super.key});
  final TokStore s;
  final Room room;

  @override
  State<ChatRoom> createState() => _ChatRoomState();
}

class _ChatRoomState extends State<ChatRoom> {
  double _barH = 0; // 입력 바의 실제 높이(여러 줄이면 커짐): + 메뉴가 그 바로 위에 뜨게 함

  @override
  Widget build(BuildContext context) {
    final s = widget.s, room = widget.room;
    return ColoredBox(
      color: K.bg,
      child: Stack(children: [
        Column(children: [
          Container(
            height: u(132),
            padding: EdgeInsets.symmetric(horizontal: u(Dims.cardInset)),
            child: Row(children: [
              IconCircle(Ic.back, '목록으로', s.back),
              SizedBox(width: u(20)),
              Expanded(
                child: Row(children: [
                  Flexible(child: T(room.name, 42, weight: FontWeight.bold)),
                  SizedBox(width: u(12)),
                  T('${room.headCount}', 30, color: K.sub, weight: FontWeight.bold),
                ]),
              ),
              IconCircle(Ic.menu, '대화상대 보기', s.openDrawer),
            ]),
          ),
          Expanded(child: _Messages(s, room)),
          MeasureSize(
            onChange: (sz) {
              if (mounted && sz.height != _barH) setState(() => _barH = sz.height);
            },
            child: _Composer(s, room),
          ),
        ]),
        // + 메뉴: 입력 바 바로 위 왼쪽. 메뉴 밖을 누르면 닫힘.
        if (s.plusOpen) ...[
          Positioned.fill(child: TapQuiet(onTap: s.closePlus, child: const SizedBox.expand())),
          Positioned(left: u(24), bottom: _barH + u(8), child: _PlusMenu(s)),
        ],
      ]),
    );
  }
}

class _Messages extends StatelessWidget {
  const _Messages(this.s, this.room);
  final TokStore s;
  final Room room;

  @override
  Widget build(BuildContext context) {
    final list = s.msgs[room.id] ?? const <Msg>[];
    // 거꾸로 쌓는 목록: 맨 아래(최근 메시지)에서 시작하고, 새 메시지가 오면 아래에 붙는다
    return ListView.separated(
      reverse: true,
      padding: EdgeInsets.fromLTRB(u(Dims.cardInset), u(20), u(Dims.cardInset), u(24)),
      itemCount: list.length,
      separatorBuilder: (_, _) => SizedBox(height: u(18)),
      itemBuilder: (_, i) {
        final m = list[list.length - 1 - i];
        return KeyedSubtree(key: ValueKey(m.id), child: _MsgRow(m, s, room));
      },
    );
  }
}

class _MsgRow extends StatelessWidget {
  const _MsgRow(this.m, this.s, this.room);
  final Msg m;
  final TokStore s;
  final Room room;

  @override
  Widget build(BuildContext context) {
    final m = this.m;
    return switch (m) {
      SysMsg() => Center(
          child: Container(
            padding: EdgeInsets.symmetric(horizontal: u(20), vertical: u(8)),
            decoration: BoxDecoration(border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.rTag))),
            child: T(m.text, 24, color: K.sub, lines: 2, align: TextAlign.center),
          ),
        ),
      TextMsg() => _Other(m.who, s, _Bubble(m.text, mine: false), unread: m.unread),
      LocMsg() => _Other(m.who, s, _LocCard(m, s, room), unread: m.unread),
      MineMsg() => Row(mainAxisAlignment: MainAxisAlignment.end, crossAxisAlignment: CrossAxisAlignment.end, children: [
          if (m.unreadCount > 0)
            Padding(
              padding: EdgeInsets.only(right: u(10), bottom: u(4)),
              child: T('${m.unreadCount}', 22, color: K.blueInk, weight: FontWeight.w800),
            ),
          Flexible(
            child: switch (m) {
              MyMsg() => _Bubble(m.text, mine: true),
              MyLocMsg() => _MineLocCard(m),
            },
          ),
        ]),
    };
  }
}

/// 남이 보낸 것: 얼굴 + 이름 + 내용 + 안 읽은 사람 수(내 메시지와 같은 숫자, 내용 오른쪽 아래).
class _Other extends StatelessWidget {
  const _Other(this.who, this.s, this.child, {this.unread = 0});
  final String who;
  final TokStore s;
  final Widget child;
  final int unread;

  @override
  Widget build(BuildContext context) => Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Avatar(who, s.memberColor(who), 56),
        SizedBox(width: u(14)),
        Flexible(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(padding: EdgeInsets.only(bottom: u(6)), child: T(who, 24, color: K.sub, weight: FontWeight.bold)),
            Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Flexible(child: child),
              if (unread > 0)
                Padding(
                  padding: EdgeInsets.only(left: u(10), bottom: u(4)),
                  child: T('$unread', 22, color: K.blueInk, weight: FontWeight.w800),
                ),
            ]),
          ]),
        ),
        SizedBox(width: u(60)),
      ]);
}

BorderRadius _bubbleShape(bool mine) {
  final big = Radius.circular(u(30)), small = Radius.circular(u(8));
  return BorderRadius.only(topLeft: big, topRight: big, bottomLeft: mine ? big : small, bottomRight: mine ? small : big);
}

/// 말풍선: 남의 것은 연한 면, 내 것은 그라데이션 + 흰 글자.
class _Bubble extends StatelessWidget {
  const _Bubble(this.text, {required this.mine});
  final String text;
  final bool mine;

  @override
  Widget build(BuildContext context) => Container(
        constraints: BoxConstraints(maxWidth: u(560)),
        padding: EdgeInsets.symmetric(horizontal: u(28), vertical: u(20)),
        decoration: BoxDecoration(
          color: mine ? null : K.bubIn,
          gradient: mine ? K.bubGrad : null,
          borderRadius: _bubbleShape(mine),
        ),
        child: T(text, 30, color: mine ? K.onBub : K.ink, lines: 20, height: 1.4),
      );
}

/// 위치·경로 카드 안 글자색 묶음. 상대 카드는 연한 면(상대 말풍선 색), 내 카드는 그라데이션(내 말풍선 색) 위라서 글자·초록이 다르다.
class CardInk {
  CardInk({required bool mine}) {
    fg = mine ? K.onBub : K.ink;
    // 남은 시간 숫자는 낮·밤·내 카드 모두 같은 밝은 초록(낮 상대 카드의 「경로 공유」 태그·진행 막대 색과 같음)
    num = const Color(0xFF2BD4A0);
    sub = mine ? fg : fg.withValues(alpha: .78);
    label = mine ? fg : fg.withValues(alpha: .78 * .75);
    track = fg.withValues(alpha: .28);
  }
  late final Color fg, sub, label, num, track;
}

/// 위치·경로 카드의 면: 상대가 보낸 것은 상대 메시지 색, 내가 보낸 것은 내 메시지 색(그라데이션). 폭은 쓸 수 있는 폭을 채우되 최대 560.
class ChatCard extends StatelessWidget {
  const ChatCard({super.key, required this.mine, required this.children, this.radius});
  final bool mine;
  final List<Widget> children;
  /// 지도 화면의 카드처럼 네 모서리를 같게 할 때.
  final double? radius;

  @override
  Widget build(BuildContext context) => ConstrainedBox(
        constraints: BoxConstraints(maxWidth: radius != null ? double.infinity : u(560)),
        child: Container(
          width: double.infinity,
          padding: EdgeInsets.fromLTRB(u(28), u(24), u(28), u(28)),
          decoration: BoxDecoration(
            color: mine ? null : K.bubIn,
            gradient: mine ? K.bubGrad : null,
            borderRadius: radius != null ? BorderRadius.circular(u(radius!)) : _bubbleShape(mine),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: children),
        ),
      );
}

/// 경로 공유 카드 본문: 이름(3줄까지) + 오른쪽 끝 남은 시간(또는 도착) / 「출발」「도착」 두 줄 / 진행 막대 / 도착 예정 글자.
List<Widget> routeBody(CardInk ink, {required String name, required String from, required String to, required int min, required int pct, required String eta, bool arrived = false, bool stopped = false, List<TrafficSeg>? traffic}) {
  final pc = arrived ? 1.0 : pct.clamp(4, 96) / 100;
  return [
    Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
      Expanded(child: T(name, 38, color: ink.fg, weight: FontWeight.w800, lines: 3)),
      SizedBox(width: u(12)),
      if (arrived || stopped)
        T(arrived ? '도착' : '공유 종료', 40, color: ink.num, weight: FontWeight.w800)
      else
        // 남은 시간은 일·시간·분으로: 숫자는 크게(초록), 단위는 작게(같은 기준선)
        Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          for (final (i, (n, unit)) in leftParts(min).indexed) ...[
            if (i > 0) SizedBox(width: u(8)),
            T('$n', 48, color: ink.num, weight: FontWeight.w800, height: 1.2),
            SizedBox(width: u(2)),
            T(unit, 26, color: ink.sub, weight: FontWeight.bold, height: 1.2),
          ],
        ]),
    ]),
    _fromTo('출발', from, ink),
    _fromTo('도착', to, ink),
    Padding(
      padding: EdgeInsets.only(top: u(16), bottom: u(12)),
      child: SizedBox(
        height: u(24),
        child: LayoutBuilder(builder: (_, c) {
          final w = c.maxWidth;
          return Stack(clipBehavior: Clip.none, alignment: Alignment.centerLeft, children: [
            Container(height: u(12), decoration: BoxDecoration(color: ink.track, borderRadius: BorderRadius.circular(u(6)))),
            // 정체 구간 색(바탕 위, 초록 진행 아래). 정보가 없으면 그리지 않아 회색 바탕만 보인다.
            if (traffic != null && traffic.isNotEmpty)
              SizedBox(
                width: w,
                height: u(12),
                child: ClipRRect(borderRadius: BorderRadius.circular(u(6)), child: CustomPaint(painter: TrafficBarPainter(traffic, seam: u(1.5)))),
              ),
            Container(width: w * pc, height: u(12), decoration: BoxDecoration(color: K.route, borderRadius: BorderRadius.circular(u(6)))),
            Positioned(
              left: w * pc - u(12),
              child: Container(
                width: u(24),
                height: u(24),
                decoration: BoxDecoration(color: K.route, shape: BoxShape.circle, border: Border.all(color: K.route.withValues(alpha: .28), width: u(5))),
              ),
            ),
          ]);
        }),
      ),
    ),
    T(arrived ? '$eta 도착' : stopped ? '위치 공유를 멈췄어요' : eta.isEmpty ? '도착 예정 시각을 받는 중이에요' : '$eta 도착 예정', 24, color: ink.fg, weight: FontWeight.bold, lines: 2),
  ];
}

/// 정체 단계 색(차량 앱 막대와 같음). 낮·밤, 내 카드·상대 카드 어디서나 같다.
extension CongestionColor on Congestion {
  Color get color => switch (this) {
        Congestion.fast => const Color(0xFF4DA3FF),
        Congestion.moderate => const Color(0xFFF2C94C),
        Congestion.slow => const Color(0xFFF2994A),
        Congestion.heavy => const Color(0xFFEB5757),
      };
}

/// 진행 막대 위에 정체 구간을 색 띠로 그린다. 바로 이어지는 구간은 [seam] 만큼 겹쳐 그려 사이에 바탕이 비치는 가는 선을 없앤다.
class TrafficBarPainter extends CustomPainter {
  TrafficBarPainter(this.segs, {required this.seam});
  final List<TrafficSeg> segs;
  final double seam;

  @override
  void paint(Canvas canvas, Size size) {
    final sorted = [...segs]..sort((a, b) => a.from.compareTo(b.from));
    for (var i = 0; i < sorted.length; i++) {
      final s = sorted[i];
      final left = s.from * size.width;
      var right = s.to * size.width;
      final next = i + 1 < sorted.length ? sorted[i + 1] : null;
      if (next != null && (next.from - s.to).abs() < 1e-4) right += seam;
      canvas.drawRect(Rect.fromLTRB(left, 0, right.clamp(left, size.width), size.height), Paint()..color = s.level.color);
    }
  }

  @override
  bool shouldRepaint(TrafficBarPainter old) => old.segs != segs || old.seam != seam;
}

/// 「출발 ○○」「도착 ○○」 한 줄: 라벨은 작고 연하게, 주소는 폭을 채우며 2줄까지 줄 바꿈(첫 줄 기준선을 맞춤).
Widget _fromTo(String label, String text, CardInk ink) => Padding(
      padding: EdgeInsets.only(top: u(4)),
      child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
        SizedBox(width: u(44), child: T(label, 22, color: ink.label, weight: FontWeight.bold)),
        SizedBox(width: u(12)),
        Expanded(child: T(text, 26, color: ink.sub, lines: 2)),
      ]),
    );

/// 가족이 보낸 현재 위치·경로 공유 카드. 둘 다 [지도 보기]가 있다: 현재 위치는 그 자리를, 경로 공유는 보낸 사람의 차가 움직이는 것을 지도에서 봄.
class _LocCard extends StatelessWidget {
  const _LocCard(this.m, this.s, this.room);
  final LocMsg m;
  final TokStore s;
  final Room room;

  @override
  Widget build(BuildContext context) {
    final p = m.place ?? s.place(m.placeId);
    if (p == null) return const SizedBox.shrink();
    final here = m.kind == LocKind.here;
    final ink = CardInk(mine: false);
    return ChatCard(mine: false, children: [
      here ? Tag('위치 공유', K.hereTag, K.hereTagInk, icon: Ic.pin) : Tag('경로 공유', K.route, K.routeInk, icon: Ic.route),
      SizedBox(height: u(12)),
      if (here) ...[
        T(p.title, 38, color: ink.fg, weight: FontWeight.w800),
        T(p.fullAddr, 26, color: ink.sub, lines: 2),
        T('${m.time}에 보낸 위치예요', 24, color: ink.sub),
        SizedBox(height: u(18)),
        _CardBtn('지도 보기', Ic.map, () => s.openMap(room.id, m), label: '${m.who}의 위치를 지도에서 보기'),
      ] else ...[
        ...routeBody(ink, name: p.title, from: m.from, to: p.fullAddr, min: m.min, pct: m.pct, eta: m.eta, traffic: m.traffic, arrived: m.end == RouteEnd.arrived, stopped: m.end == RouteEnd.stopped),
        // 도착 · 공유 종료로 끝난 경로 공유는 지도로 볼 수 없다(차 위치를 더 받지 않음)
        if (m.end == null) ...[
          SizedBox(height: u(18)),
          _CardBtn('지도 보기', Ic.map, () => s.openMap(room.id, m), label: '${m.who}의 차 위치를 지도에서 보기'),
        ],
      ],
    ]);
  }
}

/// 카드 안 주 단추(그라데이션, 폭 전체).
class _CardBtn extends StatelessWidget {
  const _CardBtn(this.text, this.icon, this.onTap, {this.label});
  final String text;
  final Ic icon;
  final VoidCallback onTap;
  final String? label;

  @override
  Widget build(BuildContext context) => Tap(
        label: label ?? text,
        radius: Dims.r,
        onTap: onTap,
        child: Container(
          height: u(80),
          decoration: BoxDecoration(gradient: K.grad, borderRadius: BorderRadius.circular(u(Dims.r))),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            Ico(icon, u(26), K.onGrad),
            SizedBox(width: u(8)),
            T(text, 28, color: K.onGrad, weight: FontWeight.bold),
          ]),
        ),
      );
}

/// 내가 보낸 내 위치: 남이 보낸 현재 위치 카드와 같은 틀(내 위치는 장소 이름이 없어서 구·동이 제목, 전체 주소가 작은 글씨).
class _MineLocCard extends StatelessWidget {
  const _MineLocCard(this.m);
  final MyLocMsg m;

  @override
  Widget build(BuildContext context) {
    final ink = CardInk(mine: true);
    return ChatCard(mine: true, children: [
      Tag('위치 공유', K.hereTag, ink.fg, icon: Ic.pin),
      SizedBox(height: u(12)),
      // 주소를 못 찾았으면(좌표만 보냄) 제목은 「현재 위치」, 작은 글은 좌표
      T(m.title.isNotEmpty ? m.title : m.name.isEmpty ? '위치 공유' : Address.area(m.name), 38, color: ink.fg, weight: FontWeight.w800),
      T(m.name.isNotEmpty ? m.name : (m.lat != null ? '${m.lat!.toStringAsFixed(5)}, ${m.lon!.toStringAsFixed(5)}' : ''), 26, color: ink.sub, lines: 2),
      T('${m.time}에 보낸 위치예요', 24, color: ink.sub),
    ]);
  }
}

/// 입력 바(최소 높이 148, 탭바와 같음): [+ · 입력칸 · 마이크/전송] 한 덩어리.
/// 전송 단추는 글이 있을 때만 보이고(그라데이션), 비어 있으면 그 자리에 마이크. 입력칸은 3줄까지 늘어나고 그 이상은 안에서 스크롤.
class _Composer extends StatefulWidget {
  const _Composer(this.s, this.room);
  final TokStore s;
  final Room room;

  @override
  State<_Composer> createState() => _ComposerState();
}

class _ComposerState extends State<_Composer> {
  late final _c = TextEditingController(text: widget.s.draft[widget.room.id] ?? '');
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _c.addListener(() {
      widget.s.setDraft(widget.room.id, _c.text);
      setState(() {});
    });
  }

  @override
  void didUpdateWidget(_Composer old) {
    super.didUpdateWidget(old);
    if (old.room.id != widget.room.id) _c.text = widget.s.draft[widget.room.id] ?? '';
  }

  @override
  void dispose() {
    _c.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _send() {
    final t = _c.text;
    _c.clear();
    widget.s.send(widget.room.id, t);
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.s;
    final empty = _c.text.trim().isEmpty;
    final style = TextStyle(fontSize: u(32), color: K.ink, height: 1.35);
    Widget circle(Widget child, {Color? color, Gradient? gradient}) => Container(
          width: u(76),
          height: u(76),
          decoration: BoxDecoration(shape: BoxShape.circle, color: color, gradient: gradient),
          alignment: Alignment.center,
          child: child,
        );
    return BarSurface(
      child: Padding(
        padding: EdgeInsets.all(u(24)),
        child: TextFieldTapRegion(
          child: Container(
            constraints: BoxConstraints(minHeight: u(Dims.hL)),
            padding: EdgeInsets.all(u(12)),
            decoration: BoxDecoration(color: K.via, borderRadius: BorderRadius.circular(u(Dims.r))),
            child: Row(children: [
              Tap(
                label: s.plusOpen ? '닫기' : '보내기 메뉴',
                circle: true,
                onTap: () {
                  _focus.unfocus(); // + 를 누르면 키보드는 내려가고 메뉴만 뜬다
                  s.togglePlus();
                },
                child: circle(Ico(s.plusOpen ? Ic.x : Ic.plus, u(40), K.ink)),
              ),
              Expanded(
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: u(12), vertical: u(16)),
                  child: TextField(
                    controller: _c,
                    focusNode: _focus,
                    minLines: 1,
                    maxLines: 3,
                    keyboardType: TextInputType.multiline,
                    textCapitalization: TextCapitalization.sentences,
                    onTapOutside: (_) => _focus.unfocus(),
                    onTap: s.plusOpen ? s.closePlus : null,
                    style: style,
                    cursorColor: K.blue,
                    decoration: InputDecoration.collapsed(
                      hintText: s.listening ? '듣는 중…' : '메시지 입력',
                      hintStyle: style.copyWith(color: K.sub),
                    ),
                  ),
                ),
              ),
              if (empty)
                // 듣는 중에는 빨간 동그라미 + 흰 마이크
                Tap(
                  label: '말하기',
                  circle: true,
                  onTap: () => s.mic(widget.room.id),
                  child: circle(Ico(Ic.mic, u(40), s.listening ? Colors.white : K.ink), color: s.listening ? K.red : null),
                )
              else
                Tap(label: '전송', circle: true, onTap: _send, child: circle(Ico(Ic.send, u(40), K.onGrad), gradient: K.grad)),
            ]),
          ),
        ),
      ),
    );
  }
}

/// + 메뉴(입력 바 위 왼쪽): [내 위치 보내기] 한 줄. 누르면 바로 보낸다.
class _PlusMenu extends StatelessWidget {
  const _PlusMenu(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) => Tap(
        label: '내 위치 보내기',
        radius: Dims.r,
        onTap: s.sendMyLocation,
        child: Container(
          padding: EdgeInsets.symmetric(horizontal: u(24), vertical: u(18)),
          decoration: BoxDecoration(gradient: K.grad, borderRadius: BorderRadius.circular(u(Dims.r))),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Container(
              width: u(64),
              height: u(64),
              decoration: BoxDecoration(color: K.card, shape: BoxShape.circle),
              alignment: Alignment.center,
              child: Ico(Ic.pin, u(34), K.blueInk),
            ),
            SizedBox(width: u(18)),
            T('내 위치 보내기', 30, color: K.onGrad, weight: FontWeight.bold),
          ]),
        ),
      );
}

/// 대화상대 서랍(오른쪽에서 열림): [초대하기] + 나 + 참여자, 왼쪽 아래 나가기 아이콘.
class MembersDrawer extends StatelessWidget {
  const MembersDrawer(this.s, this.room, {super.key});
  final TokStore s;
  final Room room;

  @override
  Widget build(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;
    final pad = MediaQuery.paddingOf(context);
    return TapQuiet(
      child: Container(
        width: (w * .78).clamp(0, u(600)).toDouble(),
        height: double.infinity,
        color: K.bg,
        padding: EdgeInsets.only(top: pad.top, bottom: pad.bottom),
        child: Column(children: [
          Padding(
            padding: EdgeInsets.fromLTRB(u(Dims.gut), u(26), u(24), u(10)),
            child: Row(children: [
              T('대화상대', 40, weight: FontWeight.bold),
              SizedBox(width: u(12)),
              Expanded(child: T('${room.headCount}', 34, color: K.sub, weight: FontWeight.bold)),
              IconCircle(Ic.x, '닫기', s.closeDrawer),
            ]),
          ),
          Expanded(
            child: ListView(
              padding: EdgeInsets.symmetric(horizontal: u(16)),
              children: [
                _drawerRow(
                  divider: false,
                  onTap: s.openInvite,
                  label: '초대하기',
                  children: [
                    Container(
                      width: u(72),
                      height: u(72),
                      decoration: BoxDecoration(color: K.blueSoft, borderRadius: BorderRadius.circular(u(72) * .34)),
                      alignment: Alignment.center,
                      child: Ico(Ic.plus, u(38), K.blue),
                    ),
                    SizedBox(width: u(22)),
                    T('초대하기', 32, weight: FontWeight.bold),
                  ],
                ),
                _drawerRow(children: [
                  Avatar('나', 0, 72, me: true),
                  SizedBox(width: u(22)),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      T(s.myName, 32, weight: FontWeight.bold),
                      T('나', 24, color: K.sub),
                    ]),
                  ),
                ]),
                for (final nm in room.members) _member(nm),
              ],
            ),
          ),
          Padding(
            padding: EdgeInsets.all(u(Dims.gut)),
            child: Align(alignment: Alignment.centerLeft, child: IconCircle(Ic.exit, '채팅방 나가기', s.askLeave)),
          ),
        ]),
      ),
    );
  }

  Widget _member(String nm) {
    final f = s.friends.where((f) => f.name == nm).firstOrNull;
    return _drawerRow(children: [
      Avatar(nm, s.memberColor(nm), 72),
      SizedBox(width: u(22)),
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          T(nm, 32, weight: FontWeight.bold),
          if (f != null && f.status.isNotEmpty) T(f.status, 24, color: K.sub),
        ]),
      ),
    ]);
  }

  Widget _drawerRow({bool divider = true, VoidCallback? onTap, String? label, required List<Widget> children}) {
    final row = Padding(
      padding: EdgeInsets.symmetric(horizontal: u(16), vertical: u(14)),
      child: Row(children: children),
    );
    return Column(children: [
      if (divider) Container(height: u(2), color: K.line), // 둘째 줄부터 위에 2 선
      onTap != null ? Tap(label: label, radius: 24, onTap: onTap, child: row) : row,
    ]);
  }
}
