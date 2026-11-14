/// 가족 서버(server) 주소. 실행할 때 넣는다:
///   flutter run -d <기기ID> --dart-define-from-file=env.json --dart-define=TOKCAR_SERVER=http://10.0.2.2:8080
/// 에뮬레이터는 10.0.2.2(이 PC), USB 폰은 adb reverse tcp:8080 tcp:8080 뒤 http://localhost:8080.
/// 앱은 서버가 있어야 동작한다(더미 모드 없음). 안 넣으면 에뮬레이터에서 이 PC 의 서버(10.0.2.2:8080)를 본다.
const tokcarServer = String.fromEnvironment('TOKCAR_SERVER', defaultValue: 'http://10.0.2.2:8080');
