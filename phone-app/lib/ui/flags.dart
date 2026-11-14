import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'theme.dart';

/// 나라 국기(36x24 칸에 그린 단순한 모양). 글자 이모지는 기기마다 다르게 보여서 직접 그린다(차량 앱 ui/Flags.kt 와 같은 모양).
class Flag extends StatelessWidget {
  const Flag(this.code, this.w, this.h, {super.key});
  final String code;
  /// 차량 앱 dp.
  final double w, h;

  @override
  Widget build(BuildContext context) => Container(
        width: u(w),
        height: u(h),
        foregroundDecoration: BoxDecoration(border: Border.all(color: K.line, width: u(1)), borderRadius: BorderRadius.circular(u(6))),
        child: ClipRRect(borderRadius: BorderRadius.circular(u(6)), child: CustomPaint(painter: _FlagPainter(code))),
      );
}

class _FlagPainter extends CustomPainter {
  _FlagPainter(this.code);
  final String code;

  static Color _c(int rgb) => Color(0xFF000000 | rgb);

  void _rect(Canvas c, double x, double y, double w, double h, Color color) => c.drawRect(Rect.fromLTWH(x, y, w, h), Paint()..color = color);

  void _star(Canvas c, double cx, double cy, double r, Color color) {
    final p = Path();
    for (var i = 0; i < 5; i++) {
      final a = (-90 + i * 72) * math.pi / 180, b = (-90 + i * 72 + 36) * math.pi / 180;
      final ox = cx + r * math.cos(a), oy = cy + r * math.sin(a);
      if (i == 0) {
        p.moveTo(ox, oy);
      } else {
        p.lineTo(ox, oy);
      }
      p.lineTo(cx + r * .382 * math.cos(b), cy + r * .382 * math.sin(b));
    }
    p.close();
    c.drawPath(p, Paint()..color = color);
  }

  /// 태극기 괘: 가운데를 기준으로 [rot]도 돌린 막대 무늬. 's' = 이은 줄, 'b' = 끊긴 줄.
  void _trigram(Canvas c, double x, double y, double rot, String pattern) {
    c.save();
    c.translate(x, y);
    c.rotate(rot * math.pi / 180);
    for (var i = 0; i < pattern.length; i++) {
      final yy = -1.9 + i * 1.4;
      if (pattern[i] == 's') {
        _rect(c, -3, yy, 6, 1, Colors.black);
      } else {
        _rect(c, -3, yy, 2.5, 1, Colors.black);
        _rect(c, .5, yy, 2.5, 1, Colors.black);
      }
    }
    c.restore();
  }

  Paint _stroke(Color color, double w) => Paint()
    ..color = color
    ..style = PaintingStyle.stroke
    ..strokeWidth = w;

  @override
  void paint(Canvas c, Size size) {
    c.scale(size.width / 36, size.height / 24);
    switch (code) {
      case 'KR':
        _rect(c, 0, 0, 36, 24, Colors.white);
        // M12 12a6 6 0 0 1 12 0 3 3 0 0 1-6 0 3 3 0 0 0-6 0z (빨강) / 반대쪽(파랑)
        c.drawPath(
          Path()
            ..moveTo(12, 12)
            ..arcToPoint(const Offset(24, 12), radius: const Radius.circular(6))
            ..arcToPoint(const Offset(18, 12), radius: const Radius.circular(3))
            ..arcToPoint(const Offset(12, 12), radius: const Radius.circular(3), clockwise: false)
            ..close(),
          Paint()..color = _c(0xCD2E3A),
        );
        c.drawPath(
          Path()
            ..moveTo(24, 12)
            ..arcToPoint(const Offset(12, 12), radius: const Radius.circular(6))
            ..arcToPoint(const Offset(18, 12), radius: const Radius.circular(3))
            ..arcToPoint(const Offset(24, 12), radius: const Radius.circular(3), clockwise: false)
            ..close(),
          Paint()..color = _c(0x0047A0),
        );
        _trigram(c, 6.4, 5.2, -33.7, 'sss');
        _trigram(c, 29.6, 5.2, 33.7, 'bsb');
        _trigram(c, 6.4, 18.8, 33.7, 'sbs');
        _trigram(c, 29.6, 18.8, -33.7, 'bbb');
      case 'US':
        for (var i = 0; i < 7; i++) {
          _rect(c, 0, i * 24 / 7, 36, 24 / 7 + .05, i.isOdd ? Colors.white : _c(0xB22234));
        }
        _rect(c, 0, 0, 15.5, 13.7, _c(0x3C3B6E));
        for (var r = 0; r < 4; r++) {
          for (var col = 0; col < 5; col++) {
            c.drawCircle(Offset(2 + col * 2.9, 2 + r * 3.1), .6, Paint()..color = Colors.white);
          }
        }
      case 'JP':
        _rect(c, 0, 0, 36, 24, Colors.white);
        c.drawCircle(const Offset(18, 12), 7, Paint()..color = _c(0xBC002D));
      case 'CN':
        _rect(c, 0, 0, 36, 24, _c(0xDE2910));
        _star(c, 7, 7, 4.2, _c(0xFFDE00));
        for (final (x, y) in const [(14.0, 3.0), (16.5, 6.0), (16.5, 10.0), (14.0, 13.0)]) {
          _star(c, x, y, 1.3, _c(0xFFDE00));
        }
      case 'GB':
        _rect(c, 0, 0, 36, 24, _c(0x012169));
        final diag = Path()
          ..moveTo(0, 0)
          ..lineTo(36, 24)
          ..moveTo(36, 0)
          ..lineTo(0, 24);
        c.drawPath(diag, _stroke(Colors.white, 4.5));
        c.drawPath(diag, _stroke(_c(0xC8102E), 1.6));
        final cross = Path()
          ..moveTo(18, 0)
          ..lineTo(18, 24)
          ..moveTo(0, 12)
          ..lineTo(36, 12);
        c.drawPath(cross, _stroke(Colors.white, 7));
        c.drawPath(cross, _stroke(_c(0xC8102E), 4));
      case 'DE':
        _rect(c, 0, 0, 36, 8, Colors.black);
        _rect(c, 0, 8, 36, 8, _c(0xDD0000));
        _rect(c, 0, 16, 36, 8, _c(0xFFCE00));
      case 'FR':
        _rect(c, 0, 0, 12, 24, _c(0x0055A4));
        _rect(c, 12, 0, 12, 24, Colors.white);
        _rect(c, 24, 0, 12, 24, _c(0xEF4135));
      case 'CA':
        _rect(c, 0, 0, 36, 24, Colors.white);
        _rect(c, 0, 0, 9, 24, _c(0xD52B1E));
        _rect(c, 27, 0, 9, 24, _c(0xD52B1E));
        const pts = [
          (18.0, 4.5), (20.0, 9.0), (24.5, 8.0), (22.5, 12.5), (25.0, 14.0), (19.0, 15.5), (19.0, 20.0), (17.0, 20.0), (17.0, 15.5),
          (11.0, 14.0), (13.5, 12.5), (11.5, 8.0), (16.0, 9.0),
        ];
        final leaf = Path()..moveTo(pts[0].$1, pts[0].$2);
        for (final (x, y) in pts.skip(1)) {
          leaf.lineTo(x, y);
        }
        c.drawPath(leaf..close(), Paint()..color = _c(0xD52B1E));
      case 'VN':
        _rect(c, 0, 0, 36, 24, _c(0xDA251D));
        _star(c, 18, 12.6, 7, _c(0xFFFF00));
      case 'TH':
        _rect(c, 0, 0, 36, 4, _c(0xA51931));
        _rect(c, 0, 4, 36, 4, Colors.white);
        _rect(c, 0, 8, 36, 8, _c(0x2D2A4A));
        _rect(c, 0, 16, 36, 4, Colors.white);
        _rect(c, 0, 20, 36, 4, _c(0xA51931));
      case 'SG':
        _rect(c, 0, 0, 36, 12, _c(0xED2939));
        _rect(c, 0, 12, 36, 12, Colors.white);
        c.drawCircle(const Offset(9, 6), 4.6, Paint()..color = Colors.white);
        c.drawCircle(const Offset(10.6, 6), 4.2, Paint()..color = _c(0xED2939));
        for (final (x, y) in const [(13.6, 3.0), (16.6, 5.2), (15.6, 8.6), (11.8, 8.6), (10.8, 5.2)]) {
          _star(c, x + 1.2, y, 1.15, Colors.white);
        }
      case 'IN':
        _rect(c, 0, 0, 36, 8, _c(0xFF9933));
        _rect(c, 0, 8, 36, 8, Colors.white);
        _rect(c, 0, 16, 36, 8, _c(0x138808));
        c.drawCircle(const Offset(18, 12), 3, _stroke(_c(0x000080), .9));
        c.drawCircle(const Offset(18, 12), .7, Paint()..color = _c(0x000080));
      default:
        _rect(c, 0, 0, 36, 24, Colors.grey.shade300);
    }
  }

  @override
  bool shouldRepaint(_FlagPainter old) => old.code != code;
}
