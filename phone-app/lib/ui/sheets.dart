import 'package:flutter/material.dart';

import '../core/countries.dart';
import '../core/store.dart';
import '../core/text.dart';
import '../data/models.dart';
import 'common.dart';
import 'flags.dart';
import 'icons.dart';
import 'theme.dart';

/// 떠 있는 팝업. 바깥(어두운 면)을 누르면 닫힌다. (차량 앱의 목적지·공유할 방 고르기·경로 바꾸기 팝업은 모바일에 없음)
class ModalLayer extends StatelessWidget {
  const ModalLayer(this.s, {super.key});
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final m = s.modal;
    if (m == null) return const SizedBox.shrink();
    final center = m is LeaveModal || m is CountryModal;
    final Widget child = switch (m) {
      FriendProfileModal() => _ProfileSheet(s, m),
      AddFriendModal() => _AddFriendSheet(s, m),
      NewRoomModal() => _NewRoomSheet(s, m),
      InviteModal() => _InviteSheet(s, m),
      LeaveModal() => _LeaveDialog(s),
      CountryModal() => _CountryDialog(s),
    };
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: s.closeModal,
      child: Container(
        color: Colors.black.withValues(alpha: .45),
        alignment: center ? Alignment.center : Alignment.bottomCenter,
        child: child,
      ),
    );
  }
}

/// 아래에서 올라오는 시트(위 모서리만 둥글게). 바닥의 제스처 막대 자리까지 덮는다.
class _Sheet extends StatelessWidget {
  const _Sheet({this.title, this.onClose, required this.children});
  final String? title;
  final VoidCallback? onClose;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final r = Radius.circular(u(44));
    final mq = MediaQuery.of(context);
    return TapQuiet(
      child: Container(
        constraints: BoxConstraints(maxWidth: u(841), maxHeight: (mq.size.height - mq.padding.top - u(40)).clamp(0, u(1100) + mq.padding.bottom)),
        width: double.infinity,
        padding: EdgeInsets.fromLTRB(u(Dims.gut), u(16), u(Dims.gut), u(28) + mq.padding.bottom),
        decoration: BoxDecoration(color: K.bg, borderRadius: BorderRadius.only(topLeft: r, topRight: r)),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Padding(
            padding: EdgeInsets.only(bottom: u(8)),
            child: Center(
              child: Container(width: u(72), height: u(8), decoration: BoxDecoration(color: K.dash, borderRadius: BorderRadius.circular(u(4)))),
            ),
          ),
          if (title != null) _SheetHead(title!, onClose),
          ...children,
        ]),
      ),
    );
  }
}

class _SheetHead extends StatelessWidget {
  const _SheetHead(this.title, this.onClose);
  final String title;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.symmetric(vertical: u(10)),
        child: Row(children: [
          Expanded(child: T(title, 42, weight: FontWeight.bold)),
          if (onClose != null) IconCircle(Ic.x, '닫기', onClose!),
        ]),
      );
}

/// 가운데 뜨는 작은 창(확인창).
class _Dialog extends StatelessWidget {
  const _Dialog({required this.children, this.padding = 36});
  final List<Widget> children;
  final double padding;

  @override
  Widget build(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;
    return TapQuiet(
      child: Container(
        width: (w * .9).clamp(0, u(720)).toDouble(),
        padding: EdgeInsets.all(u(padding)),
        decoration: BoxDecoration(color: K.bg, borderRadius: BorderRadius.circular(u(44))),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: children),
      ),
    );
  }
}

Widget _note(String text) => Padding(padding: EdgeInsets.only(bottom: u(8)), child: T(text, 26, color: K.sub, lines: 3));

/// 두 단추 줄: 보조(테두리) + 주(그라데이션, [danger]면 빨강).
Widget _twoButtons(String ghost, VoidCallback onGhost, String pri, VoidCallback onPri, {bool danger = false}) => Padding(
      padding: EdgeInsets.only(top: u(24)),
      child: Row(children: [
        Expanded(
          child: Tap(
            label: ghost,
            radius: Dims.r,
            onTap: onGhost,
            child: Container(
              height: u(Dims.hL),
              decoration: BoxDecoration(border: Border.all(color: K.dash, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.r))),
              alignment: Alignment.center,
              child: T(ghost, 34, weight: FontWeight.bold),
            ),
          ),
        ),
        SizedBox(width: u(16)),
        Expanded(
          child: danger
              ? Tap(
                  label: pri,
                  radius: Dims.r,
                  onTap: onPri,
                  child: Container(
                    height: u(Dims.hL),
                    decoration: BoxDecoration(color: K.red, borderRadius: BorderRadius.circular(u(Dims.r))),
                    alignment: Alignment.center,
                    child: T(pri, 34, color: K.onRed, weight: FontWeight.bold),
                  ),
                )
              : BigBtn(pri, onPri),
        ),
      ]),
    );

// ───────────────────────── 새 방·초대 ─────────────────────────

/// 친구 고르기 목록(동그라미 체크).
Widget _friendPick(TokStore s, List<Friend> list, List<String> selected, void Function(String) onToggle) {
  final sorted = [...list]..sort((a, b) => a.name.compareTo(b.name));
  return Column(children: [
    for (final f in sorted)
      Tap(
        label: f.name,
        radius: 24,
        onTap: () => onToggle(f.id),
        child: Padding(
          padding: EdgeInsets.all(u(12)),
          child: Row(children: [
            Avatar(f.ini, f.color, 68),
            SizedBox(width: u(20)),
            Expanded(child: T(f.name, 32, weight: FontWeight.bold)),
            Container(
              width: u(52),
              height: u(52),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: selected.contains(f.id) ? K.blue : Colors.transparent,
                border: Border.all(color: selected.contains(f.id) ? K.blue : K.dash, width: u(3)),
              ),
              alignment: Alignment.center,
              child: selected.contains(f.id) ? Ico(Ic.check, u(30), K.onBlue) : null,
            ),
          ]),
        ),
      ),
  ]);
}

/// 목록 상자(테두리 2): 시트가 화면보다 커지면 이 안이 스크롤된다.
Widget _listBox(Widget child) => Flexible(
      child: Container(
        decoration: BoxDecoration(border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.r))),
        clipBehavior: Clip.antiAlias,
        child: SingleChildScrollView(child: child),
      ),
    );

class _NewRoomSheet extends StatelessWidget {
  const _NewRoomSheet(this.s, this.m);
  final TokStore s;
  final NewRoomModal m;

  @override
  Widget build(BuildContext context) {
    final names = [for (final id in m.selected) ?s.friend(id)?.name];
    final auto = names.isEmpty ? '방 이름' : autoName(names);
    return _Sheet(title: '새 방 만들기', onClose: s.closeModal, children: [
      Section('방 이름', top: 0),
      Field(m.name, s.setNewRoomName, auto),
      Section('함께할 친구${names.isNotEmpty ? ' · ${names.length}명' : ''}'),
      _listBox(_friendPick(s, s.friends, m.selected, s.toggleNewMember)),
      SizedBox(height: u(20)),
      BigBtn(names.isNotEmpty ? '방 만들기 · ${names.length + 1}명' : '친구를 골라 주세요', s.createRoom, enabled: names.isNotEmpty),
    ]);
  }
}

class _InviteSheet extends StatelessWidget {
  const _InviteSheet(this.s, this.m);
  final TokStore s;
  final InviteModal m;

  @override
  Widget build(BuildContext context) {
    final r = s.room(s.roomId);
    if (r == null) return const SizedBox.shrink();
    final cand = s.friends.where((f) => !r.members.contains(f.name)).toList();
    return _Sheet(title: '대화상대 초대', onClose: s.closeModal, children: [
      Section('초대할 친구${m.selected.isNotEmpty ? ' · ${m.selected.length}명' : ''}', top: 0),
      _listBox(cand.isEmpty
          ? Padding(
              padding: EdgeInsets.symmetric(vertical: u(40)),
              child: Center(child: T('초대할 친구가 없어요', 28, color: K.sub, align: TextAlign.center)),
            )
          : _friendPick(s, cand, m.selected, s.toggleInvite)),
      SizedBox(height: u(20)),
      BigBtn(m.selected.isNotEmpty ? '초대하기 · ${m.selected.length}명' : '친구를 골라 주세요', s.inviteDone, enabled: m.selected.isNotEmpty),
    ]);
  }
}

class _LeaveDialog extends StatelessWidget {
  const _LeaveDialog(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final r = s.room(s.roomId);
    if (r == null) return const SizedBox.shrink();
    return _Dialog(children: [
      T('채팅방을 나갈까요?', 42, weight: FontWeight.bold),
      SizedBox(height: u(16)),
      _note('${r.name}에서 나가면 대화 목록에서 사라져요.'),
      _twoButtons('취소', s.closeModal, '나가기', s.leaveRoom, danger: true),
    ]);
  }
}

// ───────────────────────── 친구 ─────────────────────────

class _ProfileSheet extends StatelessWidget {
  const _ProfileSheet(this.s, this.m);
  final TokStore s;
  final FriendProfileModal m;

  @override
  Widget build(BuildContext context) {
    final f = s.friend(m.friendId);
    if (f == null) return const SizedBox.shrink();
    return _Sheet(children: [
      Padding(
        padding: EdgeInsets.only(top: u(24)),
        child: Column(children: [
          Avatar(f.ini, f.color, 168),
          SizedBox(height: u(20)),
          T(f.name, 44, weight: FontWeight.bold),
          T(f.status.isEmpty ? '상태 메시지가 없어요' : f.status, 28, color: K.sub),
        ]),
      ),
      _twoButtons('닫기', s.closeModal, '1:1 채팅', () => s.openDm(f.id)),
    ]);
  }
}

/// 친구 추가: 위쪽에 내 QR·코드, 아래에 전화번호(하이픈 자동).
class _AddFriendSheet extends StatelessWidget {
  const _AddFriendSheet(this.s, this.m);
  final TokStore s;
  final AddFriendModal m;

  @override
  Widget build(BuildContext context) {
    final res = m.res;
    return SingleChildScrollView(
      reverse: true, // 키보드가 올라와 자리가 모자라면 아래(입력칸)가 보이게
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        SizedBox(height: MediaQuery.paddingOf(context).top),
        Padding(
          padding: EdgeInsets.only(bottom: u(28)),
          child: TapQuiet(
            child: Container(
              padding: EdgeInsets.all(u(32)),
              decoration: BoxDecoration(color: K.card, borderRadius: BorderRadius.circular(u(36))),
              child: Column(children: [
                _Qr(252),
                SizedBox(height: u(14)),
                T('내 QR · 코드', 24, color: K.sub, weight: FontWeight.bold),
                T('7K2 · 9QX', 40, weight: FontWeight.w800),
                SizedBox(
                  width: u(520),
                  child: T('친구가 폰 앱에서 QR을 찍거나 코드를 입력하면 친구가 돼요. (예시 코드)', 22, color: K.sub, lines: 2, align: TextAlign.center),
                ),
              ]),
            ),
          ),
        ),
        _Sheet(title: '친구 추가', onClose: s.closeModal, children: [
          Field(m.tel, s.setAddTel, '친구 전화번호', phone: true),
          SizedBox(height: u(16)),
          BigBtn('친구 찾기', s.findFriend, enabled: Phone.isValid(m.tel)),
          ...switch (res) {
            LookupFound() => [
                Container(
                  margin: EdgeInsets.only(top: u(20)),
                  padding: EdgeInsets.all(u(20)),
                  decoration: BoxDecoration(color: K.card, borderRadius: BorderRadius.circular(u(Dims.r))),
                  child: Row(children: [
                    Avatar(res.friend.ini, res.friend.color, 84),
                    SizedBox(width: u(20)),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        T(res.friend.name, 32, weight: FontWeight.bold),
                        T(Phone.mask(res.tel), 26, color: K.sub),
                      ]),
                    ),
                    ActBtn('친구 추가', s.addFoundFriend),
                  ]),
                ),
              ],
            LookupAlready() => [Padding(padding: EdgeInsets.only(top: u(20)), child: T('${res.friend.name}님은 이미 친구예요', 28))],
            LookupSelf() => [Padding(padding: EdgeInsets.only(top: u(20)), child: T('내 번호예요', 28))],
            LookupNotRegistered() => [
                Padding(padding: EdgeInsets.only(top: u(20)), child: T('아직 가입하지 않은 번호예요', 28)),
                SizedBox(height: u(12)),
                BigBtn('초대 문자 보내기', s.inviteSms, ghost: true, height: 80),
              ],
            null => <Widget>[],
          },
          Padding(
            padding: EdgeInsets.only(top: u(16)),
            child: T('시안 예시: 010-2222-3333, 010-4444-5555는 가입자이고 그 밖의 번호는 미가입으로 처리해요.', 22, color: K.sub, lines: 2),
          ),
        ]),
      ]),
    );
  }
}

/// 예시 QR(모양만).
class _Qr extends StatelessWidget {
  const _Qr(this.size);
  final double size;

  @override
  Widget build(BuildContext context) {
    const n = 11;
    var seed = 7;
    final pad = u(14);
    final cell = (u(size) - pad * 2 - u(2) * 2) / n; // 테두리(2)도 안쪽 자리를 차지함
    return Container(
      width: u(size),
      height: u(size),
      padding: EdgeInsets.all(pad),
      decoration: BoxDecoration(color: Colors.white, border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(24))),
      child: Column(children: [
        for (var y = 0; y < n; y++)
          Row(children: [
            for (var x = 0; x < n; x++)
              () {
                final finder = (x < 3 && y < 3) || (x > n - 4 && y < 3) || (x < 3 && y > n - 4);
                seed = (seed * 31 + x * 17 + y * 13) % 97;
                return Container(width: cell, height: cell, color: finder || seed % 3 == 0 ? const Color(0xFF10203F) : Colors.transparent);
              }(),
          ]),
      ]),
    );
  }
}

// ───────────────────────── 나라 고르기 ─────────────────────────

class _CountryDialog extends StatelessWidget {
  const _CountryDialog(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final h = MediaQuery.sizeOf(context).height;
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: (h * .8).clamp(0, u(1000)).toDouble()),
      child: _Dialog(padding: 28, children: [
        _SheetHead('국가 선택', s.closeModal),
        Flexible(
          child: SingleChildScrollView(
            child: Column(children: [
              for (final c in Countries.all)
                Padding(
                  padding: EdgeInsets.symmetric(vertical: u(5)),
                  child: Tap(
                    label: c.name,
                    radius: Dims.r,
                    onTap: () => s.pickCountry(c.code),
                    child: Container(
                      padding: EdgeInsets.symmetric(horizontal: u(22), vertical: u(18)),
                      decoration: BoxDecoration(
                        color: c.code == s.login.country ? K.blueSoft : K.card,
                        borderRadius: BorderRadius.circular(u(Dims.r)),
                      ),
                      child: Row(children: [
                        Flag(c.code, 54, 36),
                        SizedBox(width: u(20)),
                        Expanded(child: T(c.name, 32, weight: FontWeight.bold)),
                        T('+${c.cc}', 28, color: K.sub),
                        SizedBox(width: u(16)),
                        SizedBox(width: u(30), child: T(c.code == s.login.country ? '✓' : '', 30, color: K.blue, weight: FontWeight.bold)),
                      ]),
                    ),
                  ),
                ),
            ]),
          ),
        ),
      ]),
    );
  }
}
