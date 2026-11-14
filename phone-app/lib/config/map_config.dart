import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';

/// 네이버 지도 Client ID. 앱 코드에 직접 쓰지 않고 실행할 때 넣는다:
///   flutter run -d <기기ID> --dart-define-from-file=env.json
/// (env.json 은 env.example.json 을 복사해 만들고 깃에 올리지 않음). 비어 있으면 그림 지도로 동작한다.
const naverMapClientId = String.fromEnvironment('NAVER_MAP_CLIENT_ID');

/// Client Secret(주소 변환·경로·장소 검색)은 **앱에 없다.** 가족 서버가 대신 네이버를 불러 준다(lib/data/naver_gateway.dart).
/// 서버의 secrets.properties 또는 환경 변수에 두는 키: Maps(Client ID · Secret), API HUB 지역 검색(Client ID · Secret).

/// 시안용 지도 사진을 찍을 때: --dart-define=MAP_BARE=true 로 실행하면 [지도 보기]에서 칩·카드·마커·경로선을 모두 숨기고 지도만 보인다.
const mapBare = bool.fromEnvironment('MAP_BARE');

/// 진짜 네이버 지도를 쓰는가: 키가 있고 안드로이드일 때만(iOS는 아직 안 붙임).
bool get useNaverMap => naverMapClientId.isNotEmpty && !kIsWeb && Platform.isAndroid;

/// 네이버 인증에 실패했는가(401 등). 실패하면 지도 보기는 그림 지도로 바뀌고 안내 글이 보인다.
final naverAuthFailed = ValueNotifier<String?>(null);
