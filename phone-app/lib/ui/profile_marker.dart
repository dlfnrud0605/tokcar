import 'package:flutter/material.dart';

import 'theme.dart';

/// 지도 위 보낸 사람 프로필 마커 그림(앱의 얼굴과 같은 사각형 라운드 + 흰 테두리, 크기는 뒤로 가기 버튼과 같음).
/// [t] 가 null 이면 프로필만, 0~1 이면 그 위치의 물결(프로필 둘레에서 커지며 옅어지는 사각형 링)을 뒤에 같이 그린다.
/// 물결이 퍼질 자리까지 한 이미지 안에 넣어서, 이미지 가운데(앵커)가 항상 프로필 가운데가 된다.
/// 네이버 마커는 위젯이 아니라 이미지라서, 이 위젯을 이미지로 바꿔([NOverlayImage.fromWidget]) 쓴다.
class ProfileMarker extends StatelessWidget {
  const ProfileMarker({super.key, required this.who, required this.color, this.t});
  final String who;
  final int color;
  final double? t;

  /// 물결이 퍼지는 여유 공간까지 포함한 이미지 크기 배율(프로필 크기 기준).
  static const canvasScale = 1.9;

  /// 테두리·깃대 둘레 색: 낮은 흰색, 밤은 연한 남백(어두운 지도에서 도드라지게).
  static Color get casing => K.dark ? const Color(0xFFEAF0FF) : Colors.white;

  @override
  Widget build(BuildContext context) {
    final size = u(Dims.iconBtn), border = size * .066;
    final (bg, ink) = K.tones[toneIndexOf(color)];
    final r = size * .34;
    final canvas = size * canvasScale;
    final tt = t;
    final e = tt == null ? 0.0 : Curves.easeOut.transform(tt);
    final ringSize = size * (1.1 + .75 * e);
    return Directionality(
      textDirection: TextDirection.ltr,
      child: SizedBox(
        width: canvas,
        height: canvas,
        child: Stack(alignment: Alignment.center, children: [
          if (tt != null)
            Container(
              width: ringSize,
              height: ringSize,
              // 낮은 파랑(g1), 밤은 어두운 지도에서 보이도록 연한 남백. 둘 다 시간이 지날수록 옅어진다.
              decoration: BoxDecoration(color: (K.dark ? casing : K.g1).withValues(alpha: (K.dark ? .38 : .46) * (1 - e)), borderRadius: BorderRadius.circular(ringSize * .34)),
            ),
          SizedBox(
            width: size,
            height: size,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: casing, borderRadius: BorderRadius.circular(r),
                boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: .35), blurRadius: 4, offset: const Offset(0, 2))],
              ),
              child: Padding(
                padding: EdgeInsets.all(border),
                child: DecoratedBox(
                  decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(r - border)),
                  child: Center(
                    child: Text(
                      who.isEmpty ? '' : who.characters.first,
                      style: TextStyle(fontSize: size * .4, fontWeight: FontWeight.w800, color: ink, decoration: TextDecoration.none, height: 1.1),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ]),
      ),
    );
  }
}
