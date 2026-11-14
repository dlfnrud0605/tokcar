import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/store.dart';
import '../data/models.dart';
import 'chat.dart';
import 'common.dart';
import 'home.dart';
import 'login.dart';
import 'map_view.dart';
import 'pick_place.dart';
import 'sheets.dart';
import 'theme.dart';

/// 앱 전체. 폰은 한 칸: 목록(탭) 또는 대화방. 대화상대 서랍·팝업·지도 보기·토스트는 맨 위에 겹친다.
class TokCarRoot extends StatelessWidget {
  const TokCarRoot(this.store, {super.key});
  final TokStore store;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final dark = MediaQuery.platformBrightnessOf(context) == Brightness.dark;
          K = dark ? DarkColors : LightColors;
          setScaleForWidth(MediaQuery.sizeOf(context).width);
          return PopScope(
            canPop: !store.canHandleBack,
            onPopInvokedWithResult: (didPop, _) {
              if (!didPop) store.handleBack();
            },
            child: AnnotatedRegion<SystemUiOverlayStyle>(
              value: (dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark).copyWith(
                statusBarColor: Colors.transparent,
                systemNavigationBarColor: Colors.transparent,
                systemNavigationBarContrastEnforced: false,
              ),
              child: Scaffold(
                backgroundColor: K.bg,
                resizeToAvoidBottomInset: true,
                body: _body(context),
              ),
            ),
          );
        },
      );

  Widget _body(BuildContext context) {
    final s = store;
    final room = s.inChat ? s.room(s.roomId) : null;
    final top = MediaQuery.paddingOf(context).top;
    return Stack(children: [
      Positioned.fill(
        child: !s.signedIn
            ? LoginScreen(s)
            : Padding(
                padding: EdgeInsets.only(top: top),
                child: room != null ? ChatRoom(s, room, key: ValueKey(room.id)) : HomeScreen(s),
              ),
      ),
      // 대화상대 서랍(오른쪽에서): 화면이 먼저 흐려지고 서랍이 오른쪽에서 밀려 나온다
      if (s.signedIn) Positioned.fill(child: _DrawerLayer(s, s.drawer ? room : null)),
      Positioned.fill(child: ModalLayer(s)),
      if (s.map != null) Positioned.fill(child: MapView(s, s.map!)),
      if (s.pick != null) Positioned.fill(child: PickPlace(s, s.pick!)),
      if (s.toast != null)
        Positioned(
          left: u(Dims.gut),
          right: u(Dims.gut),
          bottom: u(Dims.barH + 140) + MediaQuery.paddingOf(context).bottom,
          child: IgnorePointer(child: Center(child: Toast(s.toast!))),
        ),
    ]);
  }
}

/// 대화상대 서랍 겹: 열고 닫을 때 막은 제자리에서 흐려지고(페이드), 서랍은 오른쪽에서 밀려 나온다.
class _DrawerLayer extends StatefulWidget {
  const _DrawerLayer(this.s, this.room);
  final TokStore s;
  /// 열린 방(닫혔으면 null).
  final Room? room;

  @override
  State<_DrawerLayer> createState() => _DrawerLayerState();
}

class _DrawerLayerState extends State<_DrawerLayer> {
  Room? _last; // 닫히는 동안에도 내용을 그리도록 마지막 방을 기억

  @override
  Widget build(BuildContext context) {
    final open = widget.room != null;
    if (open) _last = widget.room;
    final last = _last;
    return IgnorePointer(
      ignoring: !open,
      child: Stack(children: [
        Positioned.fill(
          child: AnimatedOpacity(
            opacity: open ? 1 : 0,
            duration: Duration(milliseconds: open ? 180 : 200),
            child: GestureDetector(onTap: widget.s.closeDrawer, child: ColoredBox(color: Colors.black.withValues(alpha: .4))),
          ),
        ),
        if (last != null)
          Positioned(
            top: 0,
            bottom: 0,
            right: 0,
            child: AnimatedSlide(
              offset: open ? Offset.zero : const Offset(1, 0),
              duration: Duration(milliseconds: open ? 380 : 220),
              curve: open ? const Interval(120 / 380, 1, curve: Curves.easeOut) : Curves.easeIn,
              child: MembersDrawer(widget.s, last),
            ),
          ),
      ]),
    );
  }
}
