import 'dart:math' as math;

/// (위도, 경도)
typedef LL = (double, double);

/// 출발에서 도착까지 살짝 휜 선(실제 도로가 아님: 도로 경로는 Directions API가 있어야 함). [n]+1 개의 점.
List<LL> routePoints(LL a, LL b, {int n = 48}) {
  final dLat = b.$1 - a.$1, dLon = b.$2 - a.$2;
  // 가운데를 진행 방향의 수직으로 거리의 12% 만큼 밀어 휘게 함
  final c = (a.$1 + dLat / 2 + dLon * .12, a.$2 + dLon / 2 - dLat * .12);
  return [
    for (var i = 0; i <= n; i++)
      () {
        final t = i / n, u = 1 - t;
        return (u * u * a.$1 + 2 * u * t * c.$1 + t * t * b.$1, u * u * a.$2 + 2 * u * t * c.$2 + t * t * b.$2);
      }(),
  ];
}

/// 두 점 사이 거리(m, 가까운 거리용 근사).
double distM(LL a, LL b) {
  final dLat = (b.$1 - a.$1) * 111320.0;
  final dLon = (b.$2 - a.$2) * 111320.0 * math.cos((a.$1 + b.$1) / 2 * math.pi / 180);
  return math.sqrt(dLat * dLat + dLon * dLon);
}

/// 점마다 처음부터의 누적 거리(m).
List<double> _cumulative(List<LL> pts) {
  final c = <double>[0];
  for (var i = 1; i < pts.length; i++) {
    c.add(c.last + distM(pts[i - 1], pts[i]));
  }
  return c;
}

/// [f](0~1)가 가리키는 구간 번호와 그 구간 안 비율. 도로 경로는 점 간격이 고르지 않아서 점 개수가 아니라 **거리**로 계산한다.
(int, double) _locate(List<LL> pts, List<double> cum, double f) {
  final total = cum.last;
  if (total <= 0) return (0, 0);
  final target = f.clamp(0.0, 1.0) * total;
  var i = 0;
  while (i < pts.length - 2 && cum[i + 1] < target) {
    i++;
  }
  final seg = cum[i + 1] - cum[i];
  return (i, seg <= 0 ? 0 : ((target - cum[i]) / seg).clamp(0.0, 1.0));
}

LL _lerp(LL a, LL b, double t) => (a.$1 + (b.$1 - a.$1) * t, a.$2 + (b.$2 - a.$2) * t);

/// 선 위 [f](0~1) 지점(거리 기준). 점과 점 사이는 직선으로 이음.
LL pointAt(List<LL> pts, double f) {
  final (i, t) = _locate(pts, _cumulative(pts), f);
  return _lerp(pts[i], pts[i + 1], t);
}

/// 지나온 부분의 점들(처음~[f] 지점, 거리 기준). 항상 2개 이상.
List<LL> passed(List<LL> pts, double f) {
  final (i, t) = _locate(pts, _cumulative(pts), f);
  return [...pts.sublist(0, i + 1), _lerp(pts[i], pts[i + 1], t)];
}

/// 남은 길: [f] 지점(지금 위치)부터 끝까지의 점들(거리 기준). 항상 2개 이상.
List<LL> remaining(List<LL> pts, double f) {
  final (i, t) = _locate(pts, _cumulative(pts), f);
  return [_lerp(pts[i], pts[i + 1], t), ...pts.sublist(i + 1)];
}

/// 선의 전체 길이(m).
double lengthM(List<LL> pts) => _cumulative(pts).last;

/// a 에서 b 로 가는 방향(도, 북쪽 0 · 시계 방향).
double bearing(LL a, LL b) {
  final dLat = b.$1 - a.$1, dLon = (b.$2 - a.$2) * math.cos(a.$1 * math.pi / 180);
  if (dLat == 0 && dLon == 0) return 0;
  return (math.atan2(dLon, dLat) * 180 / math.pi + 360) % 360;
}
