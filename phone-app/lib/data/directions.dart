import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../core/geo.dart';
import 'naver_gateway.dart';

/// 도로를 따라가는 경로(좌표 목록) + 거리(m) + 시간(ms).
class RouteResult {
  const RouteResult(this.path, {this.distanceM, this.durationMs});
  final List<LL> path;
  final int? distanceM, durationMs;
}

/// 경로를 못 구한 이유. [reason] 은 화면에 그대로 보인다.
class DirectionsException implements Exception {
  const DirectionsException(this.reason);
  final String reason;
}

/// 출발 → 도착 도로 경로를 구하는 곳. 실제는 [NaverDirections], 테스트는 가짜.
abstract class RouteResolver {
  Future<RouteResult> route(LL start, LL goal);
}

/// 네이버 Directions 5(자동차 길찾기). 응답 형식은 네이버클라우드 API 문서 기준.
/// 가족 서버를 거쳐 부른다([NaverGateway]): 네이버 키는 서버에만 있고 앱에는 없다.
/// 경로 결과를 다른 사람에게 전달·저장해도 되는지는 약관 확인 전이라, 이 앱은 받는 폰이 자기 화면에 그리는 용도로만 부른다(저장 안 함).
class NaverDirections implements RouteResolver {
  NaverDirections({required this.gateway});
  final NaverGateway gateway;

  @override
  Future<RouteResult> route(LL start, LL goal) async {
    final uri = gateway.uri('directions', {
      'start': '${start.$2},${start.$1}', // 경도,위도 순서
      'goal': '${goal.$2},${goal.$1}',
      'option': 'traoptimal',
    });
    final http.Response res;
    try {
      res = await gateway.client.get(uri, headers: gateway.headers).timeout(const Duration(seconds: 10));
    } on TimeoutException {
      throw const DirectionsException('경로 서버가 응답하지 않아요');
    } catch (e) {
      throw const DirectionsException('인터넷 연결을 확인해 주세요');
    }
    final problem = gatewayProblem(res);
    if (problem != null) {
      debugPrint('[Directions] ${res.statusCode} ${res.body}');
      throw DirectionsException(problem);
    }
    if (res.statusCode != 200) {
      debugPrint('[Directions] ${res.statusCode} ${res.body}');
      throw DirectionsException('경로 조회 오류(${res.statusCode})');
    }
    return parseNaverRoute(jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>);
  }
}

/// 응답에서 경로를 꺼낸다. code 가 0 이 아니면 이유와 함께 [DirectionsException].
RouteResult parseNaverRoute(Map<String, dynamic> json) {
  final code = (json['code'] as num?)?.toInt() ?? -1;
  if (code != 0) {
    throw DirectionsException(switch (code) {
      1 => '출발지와 도착지가 같아요',
      2 => '출발지나 도착지가 도로 근처가 아니에요',
      3 => '이 구간은 경로를 제공할 수 없어요',
      4 => '경유지 오류',
      5 => '거리가 너무 멀어요(1500km 초과)',
      _ => (json['message'] as String?) ?? '경로를 찾지 못했어요',
    });
  }
  final route = json['route'] as Map?;
  final list = route == null || route.isEmpty ? null : route.values.first as List?;
  final first = list == null || list.isEmpty ? null : list.first as Map?;
  final raw = first?['path'] as List?;
  if (raw == null || raw.length < 2) throw const DirectionsException('경로를 찾지 못했어요');
  final summary = first?['summary'] as Map?;
  return RouteResult(
    [for (final p in raw.cast<List>()) ((p[1] as num).toDouble(), (p[0] as num).toDouble())], // [경도, 위도] → (위도, 경도)
    distanceM: (summary?['distance'] as num?)?.toInt(),
    durationMs: (summary?['duration'] as num?)?.toInt(),
  );
}
