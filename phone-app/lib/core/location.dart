import 'dart:async';

import 'package:geolocator/geolocator.dart';

/// 폰 GPS로 잰 내 위치.
class LocationFix {
  const LocationFix(this.lat, this.lon, {this.accuracyM});
  final double lat, lon;
  /// 오차 반경(미터). 모르면 null.
  final double? accuracyM;
}

/// 위치를 못 얻은 이유. [reason] 은 토스트로 그대로 보인다.
class LocationException implements Exception {
  const LocationException(this.reason);
  final String reason;
}

/// 내 현재 위치를 재는 곳. 실제는 [PhoneLocation], 테스트는 가짜.
abstract class LocationProvider {
  Future<LocationFix> current();
}

/// 좌표를 주소 글로 바꾸는 곳. 못 찾으면 null, 서비스 문제는 [AddressException].
abstract class AddressResolver {
  Future<String?> addressOf(double lat, double lon);
}

class AddressException implements Exception {
  const AddressException(this.reason);
  final String reason;
}

/// 폰의 위치 서비스(geolocator 패키지: 안드로이드 Fused Location · iOS Core Location).
/// 위치 스위치가 꺼져 있거나 권한이 없으면 이유를 [LocationException]으로 알린다.
class PhoneLocation implements LocationProvider {
  @override
  Future<LocationFix> current() async {
    if (!await Geolocator.isLocationServiceEnabled()) throw const LocationException('폰의 위치(GPS)를 켜 주세요');
    var perm = await Geolocator.checkPermission();
    if (perm == LocationPermission.denied) perm = await Geolocator.requestPermission();
    if (perm == LocationPermission.denied) throw const LocationException('위치 권한을 허용해야 내 위치를 보낼 수 있어요');
    if (perm == LocationPermission.deniedForever) throw const LocationException('폰 설정에서 이 앱의 위치 권한을 허용해 주세요');
    try {
      final p = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 12)),
      );
      return LocationFix(p.latitude, p.longitude, accuracyM: p.accuracy);
    } on TimeoutException {
      throw const LocationException('위치를 찾지 못했어요. 하늘이 보이는 곳에서 다시 눌러 주세요');
    } on LocationServiceDisabledException {
      throw const LocationException('폰의 위치(GPS)를 켜 주세요');
    }
  }
}
