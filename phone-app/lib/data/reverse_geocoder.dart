import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../core/location.dart';
import 'naver_gateway.dart';

/// 네이버 Reverse Geocoding(좌표 → 주소). 응답 형식은 네이버클라우드 API 문서 기준.
/// 가족 서버를 거쳐 부른다([NaverGateway]): 네이버 키는 서버에만 있고 앱에는 없다.
///
/// 기본은 **지번 주소**("경기도 성남시 분당구 서현동 123-4")만 쓴다([preferRoad] = false). 지번은 필지마다 있어서 어느 좌표든 거의 항상 나오지만,
/// 도로명은 건물번호가 있는 점에서만 나온다(차량 앱 로그: 판교 제2테크노밸리 한 점은 지번만 오고 도로명이 없었음). 차량 앱과 같게 맞춤.
/// [preferRoad] = true 면 도로명 우선(없으면 지번).
class NaverReverseGeocoder implements AddressResolver {
  NaverReverseGeocoder({required this.gateway, this.preferRoad = false});
  final NaverGateway gateway;
  final bool preferRoad;

  @override
  Future<String?> addressOf(double lat, double lon) async {
    final uri = gateway.uri('reverse-geocode', {
      'coords': '$lon,$lat', // 경도,위도 순서
      'orders': preferRoad ? 'roadaddr,addr' : 'addr',
      'output': 'json',
    });
    final http.Response res;
    try {
      res = await gateway.client.get(uri, headers: gateway.headers).timeout(const Duration(seconds: 8));
    } on TimeoutException {
      throw const AddressException('주소 서버가 응답하지 않아요');
    } catch (e) {
      throw const AddressException('인터넷 연결을 확인해 주세요');
    }
    final problem = gatewayProblem(res);
    if (problem != null) {
      debugPrint('[ReverseGeocode] ${res.statusCode} ${res.body}');
      throw AddressException(problem);
    }
    if (res.statusCode != 200) {
      debugPrint('[ReverseGeocode] ${res.statusCode} ${res.body}');
      throw AddressException('주소 변환 오류(${res.statusCode})');
    }
    final json = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return preferRoad ? parseNaverAddress(json) : parseParcelAddress(json);
  }
}

/// 응답에서 지번 주소만 만든다: "경기도 성남시 분당구 서현동 123-4". 지번(addr) 항목이 없거나 번지가 비면 null.
String? parseParcelAddress(Map<String, dynamic> json) {
  for (final r in ((json['results'] as List?) ?? const []).whereType<Map<String, dynamic>>()) {
    if (r['name'] != 'addr') continue;
    String area(String k) => (((r['region'] as Map?)?[k] as Map?)?['name'] as String?)?.trim() ?? '';
    String land(String k) => ((r['land'] as Map?)?[k] as String?)?.trim() ?? '';
    final n1 = land('number1'), n2 = land('number2');
    if (n1.isEmpty) return null;
    return [area('area1'), area('area2'), area('area3'), area('area4'), n2.isEmpty ? n1 : '$n1-$n2'].where((p) => p.isNotEmpty).join(' ');
  }
  return null;
}

/// 응답에서 사람이 읽는 주소 글을 만든다. 도로명 주소가 있으면 "경기도 성남시 분당구 서현로 210 (서현동)", 없으면 지번 주소.
/// 아무 결과도 없으면(바다·산속 등) null.
String? parseNaverAddress(Map<String, dynamic> json) {
  final results = ((json['results'] as List?) ?? const []).whereType<Map<String, dynamic>>().toList();
  Map<String, dynamic>? pick(String name) {
    for (final r in results) {
      if (r['name'] == name) return r;
    }
    return null;
  }

  String area(Map<String, dynamic> r, String k) => (((r['region'] as Map?)?[k] as Map?)?['name'] as String?)?.trim() ?? '';
  String land(Map<String, dynamic> r, String k) => ((r['land'] as Map?)?[k] as String?)?.trim() ?? '';
  String number(Map<String, dynamic> r) {
    final n1 = land(r, 'number1'), n2 = land(r, 'number2');
    return n2.isEmpty ? n1 : '$n1-$n2';
  }

  String join(Iterable<String> parts) => parts.where((p) => p.isNotEmpty).join(' ');

  final road = pick('roadaddr');
  if (road != null && land(road, 'name').isNotEmpty && land(road, 'number1').isNotEmpty) {
    final base = join([area(road, 'area1'), area(road, 'area2'), land(road, 'name'), number(road)]);
    final dong = area(road, 'area3');
    return RegExp(r'[동읍면리가]$').hasMatch(dong) ? '$base ($dong)' : base;
  }
  final parcel = pick('addr');
  if (parcel != null && land(parcel, 'number1').isNotEmpty) {
    return join([area(parcel, 'area1'), area(parcel, 'area2'), area(parcel, 'area3'), area(parcel, 'area4'), number(parcel)]);
  }
  return null;
}
