import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'naver_gateway.dart';

/// 검색 결과 한 줄. [name] 이 비면 주소 검색 결과(이름 없는 곳).
class PlaceHit {
  const PlaceHit({required this.name, required this.addr, required this.lat, required this.lon, this.category = ''});
  final String name, addr, category;
  final double lat, lon;
}

class PlaceSearchException implements Exception {
  const PlaceSearchException(this.reason);
  final String reason;
}

/// 위치 보내기 화면의 검색.
abstract class PlaceSearch {
  /// [lat]·[lon] 은 지금 핀 자리: 결과를 가까운 순으로 정렬하는 데 쓴다.
  Future<List<PlaceHit>> search(String query, {double? lat, double? lon});
}

/// 네이버 검색 두 가지를 합친다.
/// - 장소명: NAVER API HUB 「지역 검색 결과 조회」(최대 5개, 검색 중심 좌표가 없어 앱에서 핀과 가까운 순으로 다시 정렬)
/// - 주소: Maps 「Geocoding」(검색 중심 좌표를 주면 가까운 순)
/// 한쪽이 실패해도 다른 쪽 결과는 보인다. 둘 다 가족 서버를 거쳐 부른다([NaverGateway]): 네이버 키는 서버에만 있고 앱에는 없다.
class NaverPlaceSearch implements PlaceSearch {
  NaverPlaceSearch({required this.gateway});
  final NaverGateway gateway;

  @override
  Future<List<PlaceHit>> search(String query, {double? lat, double? lon}) async {
    final q = query.trim();
    if (q.isEmpty) return const [];
    final errors = <String>[];
    Future<List<PlaceHit>> safe(Future<List<PlaceHit>> Function() f) async {
      try {
        return await f();
      } on PlaceSearchException catch (e) {
        errors.add(e.reason);
        return const [];
      }
    }

    final both = await Future.wait([
      safe(() => _places(q)),
      safe(() => _addresses(q, lat, lon)),
    ]);
    var places = both[0];
    final addrs = both[1];
    if (lat != null && lon != null) {
      places = [...places]..sort((a, b) => distM(lat, lon, a.lat, a.lon).compareTo(distM(lat, lon, b.lat, b.lon)));
    }
    // 같은 곳이 두 번 나오지 않게(장소 검색 결과 주소와 같은 주소 검색 결과는 뺌)
    final seen = {for (final p in places) p.addr};
    final out = [...places, for (final a in addrs) if (seen.add(a.addr)) a];
    if (out.isEmpty && errors.isNotEmpty) throw PlaceSearchException(errors.first);
    return out;
  }

  Future<http.Response> _get(Uri uri, String what) async {
    try {
      final res = await gateway.client.get(uri, headers: gateway.headers).timeout(const Duration(seconds: 8));
      final problem = gatewayProblem(res);
      if (problem != null) {
        debugPrint('[PlaceSearch] $what ${res.statusCode} ${res.body}');
        throw PlaceSearchException('$what: $problem');
      }
      if (res.statusCode != 200) {
        debugPrint('[PlaceSearch] $what ${res.statusCode} ${res.body}');
        throw PlaceSearchException('$what 오류(${res.statusCode})');
      }
      return res;
    } on TimeoutException {
      throw const PlaceSearchException('검색 서버가 응답하지 않아요');
    } on PlaceSearchException {
      rethrow;
    } catch (e) {
      throw const PlaceSearchException('인터넷 연결을 확인해 주세요');
    }
  }

  Future<List<PlaceHit>> _places(String q) async {
    final res = await _get(gateway.uri('local-search', {'query': q, 'display': '5'}), '장소 검색');
    return parseLocalSearch(jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>);
  }

  Future<List<PlaceHit>> _addresses(String q, double? lat, double? lon) async {
    final res = await _get(gateway.uri('geocode', {
      'query': q,
      if (lat != null && lon != null) 'coordinate': '$lon,$lat',
      'count': '10',
    }), '주소 검색');
    return parseGeocode(jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>);
  }
}

/// 지역 검색 응답 → 결과. 제목의 <b> 강조 태그와 HTML 글자(&amp; 등)는 지운다.
/// 좌표(mapx 경도 · mapy 위도)는 WGS84. 정수로 오면(위경도 × 10,000,000) 나눠서 쓴다.
List<PlaceHit> parseLocalSearch(Map<String, dynamic> json) {
  double coord(Object? v) {
    final d = v is num ? v.toDouble() : double.tryParse('$v') ?? 0;
    return d.abs() > 1000 ? d / 1e7 : d;
  }

  return [
    for (final i in ((json['items'] as List?) ?? const []).whereType<Map<String, dynamic>>())
      if (coord(i['mapx']) != 0 && coord(i['mapy']) != 0)
        PlaceHit(
          name: cleanTitle('${i['title'] ?? ''}'),
          addr: '${(i['roadAddress'] as String?)?.isNotEmpty == true ? i['roadAddress'] : i['address'] ?? ''}',
          lat: coord(i['mapy']),
          lon: coord(i['mapx']),
          category: '${i['category'] ?? ''}',
        ),
  ];
}

/// Geocoding 응답 → 결과(이름 없음). x 경도 · y 위도는 글자로 온다.
List<PlaceHit> parseGeocode(Map<String, dynamic> json) => [
      for (final a in ((json['addresses'] as List?) ?? const []).whereType<Map<String, dynamic>>())
        if (double.tryParse('${a['y']}') != null && double.tryParse('${a['x']}') != null)
          PlaceHit(
            name: '',
            addr: '${(a['roadAddress'] as String?)?.isNotEmpty == true ? a['roadAddress'] : a['jibunAddress'] ?? ''}',
            lat: double.parse('${a['y']}'),
            lon: double.parse('${a['x']}'),
          ),
    ];

String cleanTitle(String s) => s
    .replaceAll(RegExp(r'<[^>]*>'), '')
    .replaceAll('&amp;', '&')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .trim();

/// 두 점 사이 거리(m). 정렬용이라 근사로 충분.
double distM(double lat1, double lon1, double lat2, double lon2) {
  const r = 6371000.0;
  final dLat = (lat2 - lat1) * math.pi / 180, dLon = (lon2 - lon1) * math.pi / 180;
  final x = dLon * math.cos((lat1 + lat2) / 2 * math.pi / 180);
  return r * math.sqrt(dLat * dLat + x * x);
}
