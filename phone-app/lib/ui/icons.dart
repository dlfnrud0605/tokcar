import 'package:flutter/widgets.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// 차량 앱(ui/theme/Icons.kt)의 SVG 아이콘(24x24)을 그대로 옮김. 선 아이콘은 쓸 때 색을 입힌다(currentColor).
class Ic {
  final String svg;
  const Ic._(this.svg);

  static String circle(num cx, num cy, num r) => 'M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0';

  static Ic _lines(List<String> d, [double w = 2]) => Ic._(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="$w" '
        'stroke-linecap="round" stroke-linejoin="round">${d.map((p) => '<path d="$p"/>').join()}</svg>',
      );

  static Ic _mixed(List<String> filled, List<String> lines, [double w = 2]) => Ic._(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '${filled.map((p) => '<path d="$p" fill="currentColor"/>').join()}'
        '${lines.map((p) => '<path d="$p" fill="none" stroke="currentColor" stroke-width="$w" stroke-linecap="round" stroke-linejoin="round"/>').join()}</svg>',
      );

  // ───── 탭 아이콘(선택되면 채움) ─────
  static final personLines = [circle(12, 8, 4), 'M4 21c0-4 3.5-6.5 8-6.5s8 2.5 8 6.5Z'];
  static const chatLines = ['M4 5h16v11H11l-5 4v-4H4z'];
  static const gearLines = [
    'M10.23 4.61 L10.44 1.92 L13.56 1.92 L13.77 4.61 L15.97 5.52 L18.03 3.77 L20.23 5.97 L18.48 8.03 L19.39 10.23 L22.08 10.44 '
        'L22.08 13.56 L19.39 13.77 L18.48 15.97 L20.23 18.03 L18.03 20.23 L15.97 18.48 L13.77 19.39 L13.56 22.08 L10.44 22.08 L10.23 19.39 '
        'L8.03 18.48 L5.97 20.23 L3.77 18.03 L5.52 15.97 L4.61 13.77 L1.92 13.56 L1.92 10.44 L4.61 10.23 L5.52 8.03 L3.77 5.97 L5.97 3.77 L8.03 5.52Z',
  ];
  /// 톱니바퀴 안쪽 동그라미(선택되면 속은 채움 색, 외곽선만 바탕색).
  static final gearHole = circle(12, 12, 3.2);

  static final person = _lines(personLines);
  static final chat = _lines(chatLines);
  static final gear = _lines([...gearLines, gearHole]);

  /// 선택된 탭 아이콘: 면과 외곽선을 같은 그라데이션으로 채운다. 톱니바퀴 안쪽 동그라미는 외곽선만 [hole] 색.
  static String filled(List<String> lines, Color g1, Color g2, {Color? hole}) {
    String hex(Color c) => '#${(c.toARGB32() & 0xFFFFFF).toRadixString(16).padLeft(6, '0')}';
    final b = StringBuffer('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><defs>'
        '<linearGradient id="g" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="24" y2="24">'
        '<stop offset="0" stop-color="${hex(g1)}"/><stop offset="1" stop-color="${hex(g2)}"/></linearGradient></defs>');
    for (final d in lines) {
      b.write('<path d="$d" fill="url(#g)" stroke="url(#g)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>');
    }
    if (hole != null) b.write('<path d="$gearHole" fill="url(#g)" stroke="${hex(hole)}" stroke-width="2"/>');
    b.write('</svg>');
    return b.toString();
  }

  // ───── 그 밖의 선 아이콘 ─────
  static final plus = _lines(['M12 5v14M5 12h14'], 2.4);
  static final chev = _lines(['M9 5l7 7-7 7'], 2.4);
  static final back = _lines(['M15 5l-7 7 7 7'], 2.4);
  static final down = _lines(['M5 9l7 7 7-7'], 2.4);
  static final x = _lines(['M6 6l12 12M18 6L6 18'], 2.4);
  static final send = _lines(['M12 19V5M5.5 11.5 12 5l6.5 6.5'], 2.4);
  /// 돋보기(위치 고르기 화면의 검색).
  static final search = _lines([circle(10.5, 10.5, 6.5), 'M15.5 15.5 20 20'], 2.4);
  static final pin = _lines(['M12 21s6-5.6 6-11a6 6 0 0 0-12 0c0 5.4 6 11 6 11z', circle(12, 10, 2.2)], 2.4);
  static final route = _lines([circle(6, 18, 2.2), circle(18, 6, 2.2), 'M8.2 18H14a3.2 3.2 0 0 0 0-6.4h-4A3.2 3.2 0 0 1 10 5.2h5.8'], 2.4);
  /// 지도(모바일 [지도 보기] 단추, tokcar_mobile.html 의 map 아이콘).
  static final map = _lines(['M9 4 3 6.5v13L9 17l6 3 6-2.5v-13L15 7z', 'M9 4v13M15 7v13'], 2.4);
  static final mic = _mixed(['M12,3 h0 a3,3 0 0 1 3,3 v5 a3,3 0 0 1 -3,3 h0 a3,3 0 0 1 -3,-3 v-5 a3,3 0 0 1 3,-3 z'], ['M5 11a7 7 0 0 0 14 0M12 18v3']);
  static final menu = _lines(['M4 6h16M4 12h16M4 18h16']);
  static final exit = _lines(['M10 4H5v16h5', 'M14 8l5 4-5 4M19 12H9'], 2.2);
  static final addFriend = _lines([circle(10, 8, 4), 'M2 21c0-4 3.5-6.5 8-6.5 1.5 0 2.9.3 4 .9V21Z', 'M19 14v6M16 17h6']);
  static final eye = _lines(['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z', circle(12, 12, 3)], 2.4);
  static final eyeOff = _lines([
    'M3 3l18 18', 'M10.6 5.1A10.6 10.6 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.1',
    'M6.6 6.6A17 17 0 0 0 2 12s3.6 7 10 7a10.3 10.3 0 0 0 4.2-.9', 'M9.9 9.9a3 3 0 0 0 4.2 4.2',
  ], 2.4);
  static final check = _lines(['M5 12.5l4.5 4.5L19 7.5'], 3);
}

/// 아이콘 하나(크기 [size], 색 [color]).
class Ico extends StatelessWidget {
  const Ico(this.icon, this.size, this.color, {super.key});
  final Ic icon;
  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) =>
      SvgPicture.string(icon.svg, width: size, height: size, theme: SvgTheme(currentColor: color), excludeFromSemantics: true);
}
