import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_naver_map/flutter_naver_map.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'config/map_config.dart';
import 'config/server_config.dart';
import 'core/location.dart';
import 'core/store.dart';
import 'core/voice.dart';
import 'data/directions.dart';
import 'data/place_search.dart';
import 'data/remote.dart';
import 'data/naver_gateway.dart';
import 'data/reverse_geocoder.dart';
import 'data/seed.dart';
import 'data/server.dart';
import 'ui/root.dart';
import 'ui/theme.dart';

/// TokCar 모바일 — 차량 앱(tokcar/, Jetpack Compose)과 같은 화면을 Flutter로 옮긴 폰 앱.
/// 폰에서는 길안내를 하지 않아서 경로 띠·[안내]·공유 카드·목적지 팝업을 뺐고, 가족이 보낸 경로 공유는 [지도 보기]로 본다.
/// 서버는 TOKCAR_SERVER(가족 서버 주소, 기본 http://10.0.2.2:8080)에 항상 연결한다. 서버가 꺼져 있으면 로그인할 수 없다(더미 모드 없음).
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  if (useNaverMap) {
    // 키는 --dart-define-from-file=env.json 으로 들어옴(없으면 그림 지도). 인증에 실패하면 지도 보기가 그림 지도로 바뀌고 이유를 보인다.
    await FlutterNaverMap().init(
      clientId: naverMapClientId,
      onAuthFailed: (ex) {
        debugPrint('[NaverMap] 인증 실패: $ex');
        naverAuthFailed.value = ex.toString();
      },
    );
  }
  final prefs = await SharedPreferences.getInstance();
  final seed = await Seed.load();
  final remote = HttpRemote(tokcarServer);
  final gateway = NaverGateway(base: tokcarServer, token: () => remote.token);
  final store = TokStore(
    server: FakeServer(seed),
    voice: PhoneVoiceInput(),
    location: PhoneLocation(),
    // 좌표 → 주소 · 지도 보기의 도로 경로 · 장소 검색은 가족 서버가 네이버를 대신 불러 준다(서버를 쓸 때만). 서버가 없으면 주소 없이 좌표만 보내고 경로는 곡선으로 그림.
    resolver: NaverReverseGeocoder(gateway: gateway),
    directions: NaverDirections(gateway: gateway),
    session: _loadSession(prefs),
    onSessionChanged: (s) => _saveSession(prefs, s),
    remote: remote,
    // [내 위치 보내기]는 네이버 지도에서 핀을 옮겨 고르기(지도 키와 서버가 있을 때). 검색은 장소명(API HUB) + 주소(Geocoding), 둘 다 서버 경유
    pickOnMap: useNaverMap,
    placeSearch: NaverPlaceSearch(gateway: gateway),
    remoteToken: prefs.getString(_kToken),
    onTokenChanged: (t) => t == null ? prefs.remove(_kToken) : prefs.setString(_kToken, t),
  );
  runApp(TokCarApp(store));
}

const _kId = 'sessionId', _kName = 'sessionName', _kPhone = 'sessionPhone', _kMask = 'sessionMask', _kToken = 'serverToken';

/// 로그인 상태는 기기에 저장해 다시 켜도 유지된다.
Session? _loadSession(SharedPreferences p) {
  final id = p.getString(_kId);
  if (id == null) return null;
  return Session(id, p.getString(_kName) ?? '', p.getString(_kPhone) ?? '', p.getString(_kMask) ?? '');
}

void _saveSession(SharedPreferences p, Session? s) {
  if (s == null) {
    for (final k in [_kId, _kName, _kPhone, _kMask]) {
      p.remove(k);
    }
  } else {
    p
      ..setString(_kId, s.id)
      ..setString(_kName, s.name)
      ..setString(_kPhone, s.phone)
      ..setString(_kMask, s.phoneMask);
  }
}

class TokCarApp extends StatelessWidget {
  const TokCarApp(this.store, {super.key});
  final TokStore store;

  static ThemeData _theme(AppColors c) => ThemeData(
        useMaterial3: true,
        brightness: c.dark ? Brightness.dark : Brightness.light,
        scaffoldBackgroundColor: c.bg,
        colorScheme: ColorScheme.fromSeed(
          seedColor: c.blue,
          brightness: c.dark ? Brightness.dark : Brightness.light,
          primary: c.blue,
          onPrimary: c.onBlue,
          surface: c.card,
          onSurface: c.ink,
        ),
        textSelectionTheme: TextSelectionThemeData(cursorColor: c.blue, selectionColor: c.blue.withValues(alpha: .3), selectionHandleColor: c.blue),
        splashFactory: NoSplash.splashFactory,
      );

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'TokCar',
        debugShowCheckedModeBanner: false,
        theme: _theme(LightColors),
        darkTheme: _theme(DarkColors),
        themeMode: ThemeMode.system,
        home: TokCarRoot(store),
      );
}
