import 'package:flutter/material.dart' hide Badge;
import 'package:flutter_svg/flutter_svg.dart';

import '../core/store.dart';
import '../core/text.dart';
import '../data/models.dart';
import 'common.dart';
import 'icons.dart';
import 'theme.dart';

/// 홈: 탭 내용 + 아래 탭바. (차량 앱의 아래 파란 공유 카드는 모바일에 없음: 길안내를 하지 않음)
class HomeScreen extends StatelessWidget {
  const HomeScreen(this.s, {super.key});
  final TokStore s;

  @override
  Widget build(BuildContext context) => ColoredBox(
        color: K.bg,
        child: Column(children: [
          Expanded(
            child: switch (s.tab) {
              HomeTab.chat => _ChatList(s),
              HomeTab.friends => _FriendsTab(s),
              HomeTab.settings => _SettingsTab(s),
            },
          ),
          _TabBar(s),
        ]),
      );
}

/// 아래로 스크롤하면 제목줄이 숨고, 조금만 위로 올리면 다시 나온다(카톡처럼).
class _ScrollPane extends StatefulWidget {
  const _ScrollPane({required this.title, required this.child});
  final Widget title;
  final Widget child;

  @override
  State<_ScrollPane> createState() => _ScrollPaneState();
}

class _ScrollPaneState extends State<_ScrollPane> {
  bool _shown = true;

  bool _onScroll(ScrollUpdateNotification n) {
    final d = n.scrollDelta ?? 0;
    if (n.dragDetails == null) return false;
    if (d > 6 && _shown) {
      setState(() => _shown = false);
    } else if (d < -6 && !_shown) {
      setState(() => _shown = true);
    }
    return false;
  }

  @override
  Widget build(BuildContext context) => Column(children: [
        ClipRect(
          child: AnimatedAlign(
            alignment: Alignment.bottomCenter,
            heightFactor: _shown ? 1 : 0,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut,
            child: widget.title,
          ),
        ),
        Expanded(child: NotificationListener<ScrollUpdateNotification>(onNotification: _onScroll, child: widget.child)),
      ]);
}

// ───────────────────────── 채팅 목록 ─────────────────────────

class _ChatList extends StatelessWidget {
  const _ChatList(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final rooms = s.orderedRooms;
    return _ScrollPane(
      title: TopTitle('채팅', action: IconCircle(Ic.plus, '새 방 만들기', s.openNewRoom)),
      child: ListView.separated(
        padding: EdgeInsets.fromLTRB(u(Dims.gut), 0, u(Dims.gut), u(24)),
        itemCount: rooms.length,
        separatorBuilder: (_, _) => SizedBox(height: u(16)),
        itemBuilder: (_, i) => _RoomCard(rooms[i], s),
      ),
    );
  }
}

/// 방 카드: 묶음 프로필·이름·인원·시각·최근 대화·안 읽은 수. (차량 앱 카드 아래의 목적지 띠 [안내]는 모바일에서 뺌)
class _RoomCard extends StatelessWidget {
  const _RoomCard(this.r, this.s);
  final Room r;
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final un = s.unreadOf(r);
    return Container(
      padding: EdgeInsets.all(u(12)),
      decoration: BoxDecoration(color: K.card, border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.r))),
      child: Tap(
        label: '${r.name} 열기',
        radius: 24,
        onTap: () => s.openRoom(r.id),
        child: Padding(
          padding: EdgeInsets.fromLTRB(u(16), u(12), u(16), u(12)),
          child: Row(children: [
            GroupAvatar(r, 88, s.memberColor),
            SizedBox(width: u(22)),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  // 이름 + 인원수는 왼쪽(남는 폭 전체), 시각은 맨 오른쪽 끝(아래 줄의 안 읽은 수 배지와 오른쪽 끝이 맞음)
                  Expanded(
                    child: Row(children: [
                      Flexible(child: T(r.name, 38, weight: FontWeight.bold)),
                      SizedBox(width: u(10)),
                      T('${r.headCount}', 28, color: K.sub, weight: FontWeight.bold),
                    ]),
                  ),
                  SizedBox(width: u(12)),
                  T(s.timeOf(r), 24, color: K.sub),
                ]),
                SizedBox(height: u(4)),
                Row(children: [
                  Expanded(child: T(s.lastOf(r), 28, weight: FontWeight.w500)),
                  if (un > 0) ...[SizedBox(width: u(12)), Badge(un)],
                ]),
              ]),
            ),
          ]),
        ),
      ),
    );
  }
}

// ───────────────────────── 탭바 ─────────────────────────

/// 아래 탭바: 친구 · 채팅 · 설정. 글자 없이 아이콘만, 선택된 탭은 아이콘을 그라데이션으로 채움. 채팅 아이콘에 안 읽은 수.
class _TabBar extends StatelessWidget {
  const _TabBar(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) => BarSurface(
        child: SizedBox(
          height: u(Dims.barH) - u(2),
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: u(Dims.gut)),
            child: Row(children: [
              _tab(HomeTab.friends, '친구', Ic.person, Ic.personLines, 0),
              _tab(HomeTab.chat, '채팅', Ic.chat, Ic.chatLines, s.totalUnread),
              _tab(HomeTab.settings, '설정', Ic.gear, Ic.gearLines, 0, gear: true),
            ]),
          ),
        ),
      );

  Widget _tab(HomeTab tab, String label, Ic outline, List<String> lines, int badge, {bool gear = false}) {
    final on = s.tab == tab;
    final size = u(56);
    return Expanded(
      child: Semantics(
        selected: on,
        button: true,
        label: label,
        child: TapQuiet(
          onTap: () => s.openTab(tab),
          child: Center(
            child: Stack(clipBehavior: Clip.none, children: [
              on
                  ? SvgPicture.string(Ic.filled(lines, K.ic1, K.ic2, hole: gear ? K.card : null), width: size, height: size)
                  : Ico(outline, size, K.sub),
              if (badge > 0) // 배지 둘레에 카드색 4 고리를 둘러 아이콘과 겹치는 부분을 끊음
                Positioned(
                  top: -u(12),
                  right: -u(26),
                  child: Container(
                    padding: EdgeInsets.all(u(4)),
                    decoration: BoxDecoration(color: K.card, borderRadius: BorderRadius.circular(999)),
                    child: Badge(badge, size: 34, fontSize: 20),
                  ),
                ),
            ]),
          ),
        ),
      ),
    );
  }
}

// ───────────────────────── 친구 ─────────────────────────

class _FriendsTab extends StatelessWidget {
  const _FriendsTab(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final list = [...s.friends]..sort((a, b) => a.name.compareTo(b.name));
    final box = BoxDecoration(color: K.card, border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.r)));
    return _ScrollPane(
      title: TopTitle('친구', action: IconCircle(Ic.addFriend, '친구 추가', s.openAddFriend)),
      child: ListView(
        padding: EdgeInsets.fromLTRB(u(Dims.gut), 0, u(Dims.gut), u(24)),
        children: [
          Container(
            padding: EdgeInsets.all(u(28)),
            decoration: box,
            child: Row(children: [
              Avatar('나', 0, 88, me: true),
              SizedBox(width: u(22)),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  T(s.myName, 38, weight: FontWeight.bold),
                  T('나', 28, weight: FontWeight.w500),
                ]),
              ),
            ]),
          ),
          Section('친구 ${list.length}'),
          Container(
            padding: EdgeInsets.symmetric(vertical: u(8)),
            decoration: box,
            child: Column(children: [
              for (var i = 0; i < list.length; i++) ...[
                if (i > 0) Container(height: u(2), color: K.bg),
                Tap(
                  label: list[i].name,
                  onTap: () => s.openFriend(list[i].id),
                  child: Padding(
                    padding: EdgeInsets.symmetric(horizontal: u(28), vertical: u(16)),
                    child: Row(children: [
                      Avatar(list[i].ini, list[i].color, 88),
                      SizedBox(width: u(24)),
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          T(list[i].name, 32, weight: FontWeight.bold),
                          if (list[i].status.isNotEmpty) T(list[i].status, 28, color: K.sub),
                        ]),
                      ),
                    ]),
                  ),
                ),
              ],
            ]),
          ),
        ],
      ),
    );
  }
}

// ───────────────────────── 설정 ─────────────────────────

class _SettingsTab extends StatelessWidget {
  const _SettingsTab(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) => Column(children: [
        TopTitle('설정'),
        Expanded(
          child: ListView(
            padding: EdgeInsets.fromLTRB(u(Dims.gut), 0, u(Dims.gut), u(24)),
            children: [
              Container(
                padding: EdgeInsets.fromLTRB(u(24), u(40), u(24), u(36)),
                decoration: BoxDecoration(
                  color: K.card,
                  border: Border.all(color: K.line, width: u(2)),
                  borderRadius: BorderRadius.circular(u(32)),
                ),
                child: Column(children: [
                  Avatar('나', 0, 128, me: true),
                  SizedBox(height: u(18)),
                  T(s.myName, 44, weight: FontWeight.bold),
                  T('${s.phoneMask.isEmpty ? Phone.mask(s.myPhone) : s.phoneMask} · 인증됨', 24, color: K.sub),
                ]),
              ),
              Section('계정'),
              _Group([
                _KvRow('아이디', right: T(s.myId, 32, weight: FontWeight.bold)),
                _KvRow('개인정보 처리방침', onTap: s.privacy, right: _chev()),
                _KvRow('로그아웃', danger: true, last: true, onTap: s.logout),
              ]),
            ],
          ),
        ),
      ]);

  static Widget _chev() => T('›', 32, color: K.sub);
}

class _Group extends StatelessWidget {
  const _Group(this.children);
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
        padding: EdgeInsets.symmetric(horizontal: u(24), vertical: u(8)),
        decoration: BoxDecoration(color: K.card, borderRadius: BorderRadius.circular(u(Dims.r))),
        child: Column(children: children),
      );
}

class _KvRow extends StatelessWidget {
  const _KvRow(this.label, {this.danger = false, this.last = false, this.onTap, this.right});
  final String label;
  final bool danger, last;
  final VoidCallback? onTap;
  final Widget? right;

  @override
  Widget build(BuildContext context) {
    final row = Container(
      height: u(104),
      padding: EdgeInsets.symmetric(horizontal: u(8)),
      child: Row(children: [
        Expanded(child: T(label, 32, color: danger ? K.red : K.ink, weight: danger ? FontWeight.bold : FontWeight.normal)),
        ?right,
      ]),
    );
    return Column(children: [
      onTap != null ? Tap(label: label, onTap: onTap, child: row) : row,
      if (!last) Container(height: u(2), color: K.line), // 행 아래 2 선(묶음의 마지막 행은 없음)
    ]);
  }
}
