import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../data/models.dart';
import 'icons.dart';
import 'theme.dart';

/// 글자(차량 앱의 font-size 값을 그대로 [u]로 줄임). 줄 높이는 글자 크기의 1.35배.
class T extends StatelessWidget {
  const T(this.text, this.size, {super.key, this.color, this.weight = FontWeight.normal, this.lines = 1, this.align, this.height = 1.35});
  final String text;
  final double size;
  final Color? color;
  final FontWeight weight;
  final int lines;
  final TextAlign? align;
  final double height;

  @override
  Widget build(BuildContext context) => Text(
        text,
        maxLines: lines,
        overflow: TextOverflow.ellipsis,
        textAlign: align,
        style: TextStyle(fontSize: u(size), color: color ?? K.ink, fontWeight: weight, height: height),
      );
}

/// 누를 수 있게: 누르는 동안 살짝 어두워진다(모양은 [radius] 또는 [circle]).
class Tap extends StatefulWidget {
  const Tap({super.key, required this.child, this.onTap, this.label, this.radius, this.circle = false, this.enabled = true});
  final Widget child;
  final VoidCallback? onTap;
  final String? label;
  final double? radius;
  final bool circle;
  final bool enabled;

  @override
  State<Tap> createState() => _TapState();
}

class _TapState extends State<Tap> {
  bool _down = false;

  void _set(bool v) {
    if (_down != v && mounted) setState(() => _down = v);
  }

  @override
  Widget build(BuildContext context) {
    final on = widget.enabled && widget.onTap != null;
    return Semantics(
      button: true,
      enabled: on,
      label: widget.label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: on ? (_) => _set(true) : null,
        onTapUp: on ? (_) => _set(false) : null,
        onTapCancel: on ? () => _set(false) : null,
        onTap: on ? widget.onTap : null,
        child: DecoratedBox(
          position: DecorationPosition.foreground,
          decoration: BoxDecoration(
            color: _down ? K.ink.withValues(alpha: .08) : Colors.transparent,
            shape: widget.circle ? BoxShape.circle : BoxShape.rectangle,
            borderRadius: widget.circle || widget.radius == null ? null : BorderRadius.circular(u(widget.radius!)),
          ),
          child: widget.child,
        ),
      ),
    );
  }
}

/// 누를 때 아무 표시도 없이(배경을 눌러 닫기, 눌러도 닫히지 않게 막기 등).
class TapQuiet extends StatelessWidget {
  const TapQuiet({super.key, required this.child, this.onTap});
  final Widget child;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => GestureDetector(behavior: HitTestBehavior.opaque, onTap: onTap ?? () {}, child: child);
}

/// 아이콘 단추: 80 흰 동그라미 + 2 테두리(뒤로·메뉴·닫기·새 방·친구 추가·나가기 모두 같음).
class IconCircle extends StatelessWidget {
  const IconCircle(this.icon, this.label, this.onTap, {super.key, this.iconSize = 40});
  final Ic icon;
  final String label;
  final VoidCallback onTap;
  final double iconSize;

  @override
  Widget build(BuildContext context) => Tap(
        label: label,
        circle: true,
        onTap: onTap,
        child: Container(
          width: u(Dims.iconBtn),
          height: u(Dims.iconBtn),
          decoration: BoxDecoration(color: K.card, shape: BoxShape.circle, border: Border.all(color: K.line, width: u(2))),
          alignment: Alignment.center,
          child: Ico(icon, u(iconSize), K.ink),
        ),
      );
}

/// 작은 동작 단추(161x80, 글자 28). 여기서는 주 단추(그라데이션)만 쓴다.
class ActBtn extends StatelessWidget {
  const ActBtn(this.text, this.onTap, {super.key});
  final String text;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Tap(
        label: text,
        radius: Dims.r,
        onTap: onTap,
        child: Container(
          constraints: BoxConstraints(minWidth: u(Dims.actW)),
          height: u(Dims.actH),
          padding: EdgeInsets.symmetric(horizontal: u(12)),
          decoration: BoxDecoration(gradient: K.grad, borderRadius: BorderRadius.circular(u(Dims.r))),
          alignment: Alignment.center,
          child: T(text, 28, color: K.onGrad, weight: FontWeight.bold),
        ),
      );
}

/// 큰 단추(폭 전체, 높이 100, 글자 34). 주 단추는 그라데이션, [ghost] 는 연한 파랑, 못 누를 땐 회색.
class BigBtn extends StatelessWidget {
  const BigBtn(this.text, this.onTap, {super.key, this.enabled = true, this.ghost = false, this.height = Dims.hL});
  final String text;
  final VoidCallback onTap;
  final bool enabled, ghost;
  final double height;

  @override
  Widget build(BuildContext context) {
    final fg = !enabled ? K.sub : (ghost ? K.blueInk : K.onGrad);
    return Tap(
      label: text,
      radius: Dims.r,
      enabled: enabled,
      onTap: onTap,
      child: Container(
        width: double.infinity,
        height: u(height),
        decoration: BoxDecoration(
          color: !enabled ? K.block : (ghost ? K.blueSoft : null),
          gradient: enabled && !ghost ? K.grad : null,
          borderRadius: BorderRadius.circular(u(Dims.r)),
        ),
        alignment: Alignment.center,
        child: T(text, 34, color: fg, weight: FontWeight.bold),
      ),
    );
  }
}

/// 얼굴: 둥근 네모(모서리 34%) + 첫 글자. [me] 는 나(연한 바탕).
class Avatar extends StatelessWidget {
  const Avatar(this.text, this.color, this.size, {super.key, this.me = false});
  final String text;
  final int color;
  /// 차량 앱 dp.
  final double size;
  final bool me;

  @override
  Widget build(BuildContext context) {
    final (bg, ink) = me ? (K.meBg, K.meInk) : K.tones[toneIndexOf(color)];
    return Container(
      width: u(size),
      height: u(size),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(u(size) * .34)),
      alignment: Alignment.center,
      child: T(text.isEmpty ? '' : text.characters.first, size * .4, color: ink, weight: FontWeight.w800),
    );
  }
}

/// 방 사진: 내 얼굴은 빼고 상대 얼굴로. 1명 = 그 사람, 2명 = 대각선으로 겹침, 3명 = 삼각형, 4명 이상 = 앞의 4명을 2x2.
/// 겹친 얼굴 사이만 카드색 테두리로 끊는다.
class GroupAvatar extends StatelessWidget {
  const GroupAvatar(this.room, this.size, this.memberColor, {super.key});
  final Room room;
  final double size;
  final int Function(String) memberColor;

  @override
  Widget build(BuildContext context) {
    final names = room.members.take(4).toList();
    final n = names.length;
    final multi = n >= 2;
    Widget one(String name, double s, double x, double y) => Positioned(
          left: u(x),
          top: u(y),
          child: Container(
            foregroundDecoration: multi
                ? BoxDecoration(border: Border.all(color: K.card, width: u(3)), borderRadius: BorderRadius.circular(u(s) * .34))
                : null,
            child: Avatar(name, memberColor(name), s),
          ),
        );
    final s = size;
    final List<Widget> kids;
    if (n == 0) {
      kids = [one(room.name, s, 0, 0)];
    } else if (n == 1) {
      kids = [one(names[0], s, 0, 0)];
    } else if (n == 2) {
      final d = s * .66;
      kids = [one(names[0], d, 0, 0), one(names[1], d, s - d, s - d)];
    } else if (n == 3) {
      final t = s * .56;
      kids = [one(names[0], t, (s - t) / 2, 0), one(names[1], t, 0, s - t), one(names[2], t, s - t, s - t)];
    } else {
      final q = s * .5;
      kids = [one(names[0], q, 0, 0), one(names[1], q, s - q, 0), one(names[2], q, 0, s - q), one(names[3], q, s - q, s - q)];
    }
    return SizedBox(width: u(s), height: u(s), child: Stack(children: kids));
  }
}

/// 안 읽은 수: 빨간 알약.
class Badge extends StatelessWidget {
  const Badge(this.n, {super.key, this.size = 44, this.fontSize = 24});
  final int n;
  final double size, fontSize;

  @override
  Widget build(BuildContext context) => Container(
        constraints: BoxConstraints(minWidth: u(size)),
        height: u(size),
        padding: EdgeInsets.symmetric(horizontal: u(10)),
        decoration: BoxDecoration(color: K.red, borderRadius: BorderRadius.circular(999)),
        alignment: Alignment.center,
        child: T(n > 99 ? '99+' : '$n', fontSize, color: Colors.white, weight: FontWeight.w800, height: 1.15),
      );
}

/// 작은 태그(모서리 14).
class Tag extends StatelessWidget {
  const Tag(this.text, this.bg, this.fg, {super.key, this.icon});
  final String text;
  final Color bg, fg;
  final Ic? icon;

  @override
  Widget build(BuildContext context) => Container(
        padding: EdgeInsets.symmetric(horizontal: u(14), vertical: u(4)),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(u(Dims.rTag))),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          T(text, 22, color: fg, weight: FontWeight.w800),
          if (icon != null) ...[SizedBox(width: u(6)), Ico(icon!, u(22), fg)],
        ]),
      );
}

/// 아래 막대(탭바·입력 바) 바탕: 위 모서리만 둥글고, 맨 위에 2 선(K.line)이 모양을 따라 이어진다.
/// 폰 아래 제스처 막대 자리까지 같은 색으로 채운다(안쪽 내용은 그 위에).
class BarSurface extends StatelessWidget {
  const BarSurface({super.key, required this.child, this.minHeight = Dims.barH});
  final Widget child;
  final double minHeight;

  @override
  Widget build(BuildContext context) {
    final r = Radius.circular(u(Dims.r));
    final bottom = MediaQuery.paddingOf(context).bottom;
    return Container(
      decoration: BoxDecoration(color: K.line, borderRadius: BorderRadius.only(topLeft: r, topRight: r)),
      padding: EdgeInsets.only(top: u(2)),
      child: Container(
        constraints: BoxConstraints(minHeight: u(minHeight) - u(2) + bottom),
        decoration: BoxDecoration(color: K.card, borderRadius: BorderRadius.only(topLeft: r, topRight: r)),
        padding: EdgeInsets.only(bottom: bottom),
        child: child,
      ),
    );
  }
}

/// 앱 아이콘(106번, 남색).
class AppIcon extends StatelessWidget {
  const AppIcon(this.size, {super.key});
  final double size;

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(u(size) * .23),
        child: SvgPicture.asset('assets/tk_icon.svg', width: u(size), height: u(size), semanticsLabel: 'TokCar'),
      );
}

/// 화면 위 제목줄(채팅·친구·설정): 큰 제목 + 오른쪽 아이콘 단추.
class TopTitle extends StatelessWidget {
  const TopTitle(this.title, {super.key, this.action});
  final String title;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Container(
        height: u(136),
        padding: EdgeInsets.symmetric(horizontal: u(Dims.gut)),
        child: Row(children: [
          Expanded(child: T(title, 54, weight: FontWeight.bold)),
          ?action,
        ]),
      );
}

/// 소제목(회색 작은 글자).
class Section extends StatelessWidget {
  const Section(this.text, {super.key, this.top = 20});
  final String text;
  final double top;

  @override
  Widget build(BuildContext context) =>
      Padding(padding: EdgeInsets.only(top: u(top), bottom: u(12)), child: T(text, 26, color: K.sub, weight: FontWeight.bold));
}

/// 토스트(화면 아래쪽, 진한 바탕).
class Toast extends StatelessWidget {
  const Toast(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Container(
        constraints: BoxConstraints(maxWidth: u(720)),
        padding: EdgeInsets.symmetric(horizontal: u(36), vertical: u(20)),
        decoration: BoxDecoration(color: K.strong, borderRadius: BorderRadius.circular(u(Dims.r))),
        child: T(text, 28, color: K.onStrong, weight: FontWeight.bold, lines: 2, align: TextAlign.center),
      );
}

/// 입력칸(높이 100, 연한 바탕, 모서리 30). 값은 저장소가 들고 있고(하이픈 자동 등으로 바뀔 수 있음), 바뀌면 입력칸에 반영한다.
/// 입력칸 밖을 누르면 키보드가 내려간다(입력칸 안의 단추는 [TextFieldTapRegion]으로 묶여서 눌러도 유지).
class Field extends StatefulWidget {
  const Field(this.value, this.onChanged, this.hint,
      {super.key, this.phone = false, this.password = false, this.center = false, this.numeric = false, this.trailing});
  final String value;
  final ValueChanged<String> onChanged;
  final String hint;
  final bool phone, password, center, numeric;
  final Widget? trailing;

  @override
  State<Field> createState() => _FieldState();
}

class _FieldState extends State<Field> {
  late final _c = TextEditingController(text: widget.value);

  @override
  void didUpdateWidget(Field old) {
    super.didUpdateWidget(old);
    if (widget.value != _c.text) {
      _c.value = TextEditingValue(text: widget.value, selection: TextSelection.collapsed(offset: widget.value.length));
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(fontSize: u(32), color: K.ink, height: 1.35);
    return TextFieldTapRegion(
      child: Container(
        height: u(Dims.hL),
        padding: EdgeInsets.only(left: u(32), right: u(widget.trailing != null ? 12 : 32)),
        decoration: BoxDecoration(
          color: K.bg,
          border: Border.all(color: K.line, width: u(2)),
          borderRadius: BorderRadius.circular(u(Dims.r)),
        ),
        child: Row(children: [
          Expanded(
            child: TextField(
              controller: _c,
              onChanged: widget.onChanged,
              onTapOutside: (_) => FocusManager.instance.primaryFocus?.unfocus(),
              style: style,
              cursorColor: K.blue,
              textAlign: widget.center ? TextAlign.center : TextAlign.start,
              obscureText: widget.password,
              enableSuggestions: !widget.password,
              autocorrect: !widget.password,
              keyboardType: widget.phone
                  ? TextInputType.phone
                  : widget.numeric
                      ? TextInputType.number
                      : widget.password
                          ? TextInputType.visiblePassword
                          : TextInputType.text,
              inputFormatters: widget.numeric ? [FilteringTextInputFormatter.digitsOnly] : null,
              decoration: InputDecoration.collapsed(hintText: widget.hint, hintStyle: style.copyWith(color: K.sub)),
            ),
          ),
          ?widget.trailing,
        ]),
      ),
    );
  }
}

/// 자식의 실제 크기를 알려 준다(입력 바 높이에 맞춰 + 메뉴를 띄울 때 씀).
class MeasureSize extends SingleChildRenderObjectWidget {
  const MeasureSize({super.key, required this.onChange, super.child});
  final ValueChanged<Size> onChange;

  @override
  RenderObject createRenderObject(BuildContext context) => RenderMeasureSize(onChange);

  @override
  void updateRenderObject(BuildContext context, RenderMeasureSize renderObject) => renderObject.onChange = onChange;
}

class RenderMeasureSize extends RenderProxyBox {
  RenderMeasureSize(this.onChange);
  ValueChanged<Size> onChange;
  Size? _old;

  @override
  void performLayout() {
    super.performLayout();
    final s = size;
    if (s != _old) {
      _old = s;
      WidgetsBinding.instance.addPostFrameCallback((_) => onChange(s));
    }
  }
}
