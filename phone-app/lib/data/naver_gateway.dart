import 'package:http/http.dart' as http;

/// 네이버 지도 API 를 **가족 서버를 거쳐** 부르는 길. 서버가 네이버 키를 붙이므로 앱에는 비밀 키가 없고, 앱은 로그인 토큰만 보낸다.
/// 서비스 이름: reverse-geocode(좌표 → 주소) · geocode(주소 → 좌표) · directions(경로) · local-search(장소명 검색).
/// 응답은 네이버 응답 그대로라 아래 해석 코드는 그대로 쓴다. 서버가 없거나 로그인 전이면 쓸 수 없다(호출하는 쪽이 null 로 둠).
class NaverGateway {
  NaverGateway({required String base, required this.token, http.Client? client})
      : base = base.replaceAll(RegExp(r'/+$'), ''),
        client = client ?? http.Client();
  final String base;
  final String? Function() token;
  final http.Client client;

  Uri uri(String service, Map<String, String> query) => Uri.parse('$base/api/naver/$service').replace(queryParameters: query);

  Map<String, String> get headers => {'Authorization': 'Bearer ${token() ?? ''}'};
}

/// 서버가 거절·실패로 답했을 때 화면에 보일 글. 해당이 없으면 null(호출하는 쪽의 기본 처리로 넘어감).
String? gatewayProblem(http.Response res) => switch (res.statusCode) {
      401 => '서버에 로그인한 뒤에 쓸 수 있어요',
      429 => '잠시 뒤에 다시 해 주세요',
      502 => '서버가 네이버에 연결하지 못했어요',
      503 => '서버에 네이버 키가 설정돼 있지 않아요',
      _ => null,
    };
