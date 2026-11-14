import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tokcar_mobile/core/countries.dart';
import 'package:tokcar_mobile/core/geo.dart';
import 'package:tokcar_mobile/core/location.dart';
import 'package:tokcar_mobile/data/directions.dart';
import 'package:tokcar_mobile/data/reverse_geocoder.dart';
import 'package:tokcar_mobile/core/store.dart';
import 'package:tokcar_mobile/core/text.dart';
import 'package:tokcar_mobile/core/voice.dart';
import 'package:tokcar_mobile/data/models.dart';
import 'package:tokcar_mobile/data/seed.dart';
import 'package:tokcar_mobile/data/server.dart';

Seed seed() => Seed.fromJson(File('assets/dummy/seed.json').readAsStringSync());

const papa = Session('papa', '아빠', '01012345678', '+82 10-****-5678');

TokStore store({Session? session = papa}) => TokStore(server: FakeServer(seed()), session: session);

void main() {
  test('loginWithIdPasswordLocksAfterFiveFails', () {
    final s = store(session: null);
    s.startLogin(LoginMode.login);
    s.setLoginId('papa');
    for (var i = 0; i < 4; i++) {
      s.setPw('wrong1234');
      s.doLogin();
      expect(s.login.err, LoginErr.bad);
      expect(s.login.left, 4 - i);
    }
    s.setPw('wrong1234');
    s.doLogin();
    expect(s.login.left, 0); // 잠김
    s.setPw('papa1234!');
    s.doLogin();
    expect(s.signedIn, isFalse); // 잠기면 맞는 비밀번호도 안 됨
  });

  test('loginWithRightPasswordSignsIn', () {
    Session? saved;
    final s = TokStore(server: FakeServer(seed()), onSessionChanged: (v) => saved = v);
    s.startLogin(LoginMode.login);
    s.setLoginId('papa');
    s.setPw('papa1234!');
    s.doLogin();
    expect(s.signedIn, isTrue);
    expect(saved?.id, 'papa');
    expect(s.myPhone, '01012345678');
  });

  testWidgets('signupCreatesAccountWithIdCheck', (tester) async {
    final s = store(session: null);
    s.startLogin(LoginMode.signup);
    s.setLoginPhone('01012345678');
    s.sendCode();
    expect(s.login.err, LoginErr.known); // 이미 가입된 번호
    s.setLoginPhone('01099998888');
    expect(s.login.phone, '010-9999-8888');
    s.sendCode();
    await tester.pump(const Duration(milliseconds: 400));
    expect(s.login.step, LoginStep.code);
    s.setLoginCode('123456');
    s.verify();
    await tester.pump(const Duration(milliseconds: 400));
    expect(s.login.step, LoginStep.account);
    s.setNewId('admin');
    s.checkId();
    expect(s.idOk, isFalse); // 예약어
    s.setNewId('newdad');
    s.checkId();
    expect(s.idOk, isTrue);
    s.setPw('abcd1234');
    s.setPw2('abcd1234');
    s.accountNext();
    expect(s.login.step, LoginStep.profile);
    s.setLoginName('새아빠');
    s.profileNext();
    s.agreeAll();
    s.join();
    expect(s.signedIn, isTrue);
    expect(s.myName, '새아빠');
    expect(s.phoneMask, '+82 10-****-8888');
    await tester.pump(const Duration(seconds: 3)); // 토스트 타이머 정리
  });

  testWidgets('newRoomIsNotSavedUntilFirstMessage', (tester) async {
    final s = store();
    final before = s.rooms.length;
    s.openNewRoom();
    s.toggleNewMember('mom1');
    s.toggleNewMember('minji');
    s.createRoom();
    expect(s.inChat, isTrue);
    expect(s.rooms.length, before); // 아직 목록에 없음
    final id = s.roomId!;
    s.send(id, '안녕');
    expect(s.rooms.length, before + 1);
    expect(s.orderedRooms.first.id, id); // 최근 대화가 맨 위
    expect(s.msgs[id]!.first, isA<SysMsg>()); // 방을 만든 안내가 맨 앞
    // 글을 안 보내고 나온 새 방은 버려짐
    s.back();
    s.openNewRoom();
    s.toggleNewMember('chul');
    s.createRoom();
    s.back();
    expect(s.rooms.length, before + 1);
    await tester.pump(const Duration(seconds: 10));
  });

  testWidgets('readReceiptsCountDown', (tester) async {
    final s = store();
    s.openRoom('family'); // 엄마(1.5초), 민지(5초)
    s.send('family', '곧 가');
    final m = s.msgs['family']!.last as MyMsg;
    expect(m.unreadCount, 2);
    await tester.pump(const Duration(milliseconds: 1600));
    expect(m.unreadCount, 1);
    await tester.pump(const Duration(seconds: 4));
    expect(m.unreadCount, 0);
  });

  testWidgets('incomingRouteOpensMapAndCountsDown', (tester) async {
    final s = store();
    s.simulateIncoming('dest');
    await tester.pump();
    expect(s.newIn['family'], 1);
    expect(s.lastOf(s.room('family')!), '민지가 경로를 공유했어요 · 18분 남음');
    final card = s.msgs['family']!.last as LocMsg;
    s.openRoom('family');
    s.openMap('family', card);
    expect(s.map!.remain, 18);
    expect(s.map!.total, 43); // 18분 남고 58% 왔으면 전체 약 43분
    await tester.pump(const Duration(milliseconds: MapState.stepMs));
    expect(s.map!.remain, 17);
    await tester.pump(const Duration(milliseconds: MapState.stepMs * 20));
    expect(s.map!.remain, 0); // 도착에서 멈춤
    expect(s.handleBack(), isTrue); // 뒤로 = 지도 닫기
    expect(s.map, isNull);
    expect(s.inChat, isTrue);
  });

  testWidgets('routeShareCarriesTrafficToCardAndMap', (tester) async {
    final s = store();
    s.simulateIncoming('dest');
    await tester.pump();
    final card = s.msgs['family']!.last as LocMsg;
    expect(card.traffic, isNotNull);
    expect(card.traffic!.length, 7);
    expect(card.traffic![3].level, Congestion.slow);
    s.openRoom('family');
    s.openMap('family', card);
    expect(s.map!.traffic, same(card.traffic));
    s.simulateIncoming('here');
    await tester.pump();
    expect((s.msgs['family']!.last as LocMsg).traffic, isNull); // 현재 위치 카드엔 정체 정보 없음
    s.closeMap();
  });

  test('trafficParseSkipsBadEntriesAndKeepsNullVsEmpty', () {
    expect(TrafficSeg.parseAll(null), isNull);
    expect(TrafficSeg.parseAll([]), isEmpty);
    final r = TrafficSeg.parseAll([
      [0, 0.5, 'fast'],
      [0.5, 0.4, 'slow'], // 거꾸로 → 버림
      [0.5, 0.7, 'weird'], // 모르는 단계 → 버림
      'x',
      [0.7, 1.5, 'heavy'], // 1 로 맞춤
    ])!;
    expect(r.length, 2);
    expect(r.last.to, 1.0);
    expect(r.last.level, Congestion.heavy);
  });

  testWidgets('hereMapIsStaticAndBackClosesIt', (tester) async {
    final s = store();
    s.openRoom('family');
    s.simulateIncoming('here');
    await tester.pump();
    final card = s.msgs['family']!.last as LocMsg;
    s.openMap('family', card);
    expect(s.map!.here, isTrue);
    expect(s.map!.time, card.time);
    await tester.pump(const Duration(seconds: 10));
    expect(s.map!.remain, 0); // 움직이는 타이머 없음
    expect(s.handleBack(), isTrue);
    expect(s.map, isNull);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('inviteAddsMembersAndLeaveRemovesRoom', (tester) async {
    final s = store();
    s.openRoom('mom');
    s.openInvite();
    s.toggleInvite('minji');
    s.inviteDone();
    expect(s.room('mom')!.members, contains('민지'));
    expect(s.msgs['mom']!.last, isA<SysMsg>());
    s.askLeave();
    s.leaveRoom();
    expect(s.room('mom'), isNull);
    expect(s.inChat, isFalse);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('addFriendByPhone', (tester) async {
    final s = store();
    s.openAddFriend();
    s.setAddTel('01022223333');
    expect((s.modal as AddFriendModal).tel, '010-2222-3333');
    s.findFriend();
    expect((s.modal as AddFriendModal).res, isA<LookupFound>());
    s.addFoundFriend();
    expect(s.friends.any((f) => f.name == '이모'), isTrue);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('micSendsHeardTextAndCancelIsQuiet', (tester) async {
    final v = _FakeVoice();
    final s = TokStore(server: FakeServer(seed()), session: papa, voice: v);
    s.openRoom('family');
    final n = s.msgs['family']!.length;
    s.mic('family');
    expect(s.listening, isTrue);
    v.c!.complete('곧 도착해요');
    await tester.pump();
    expect(s.listening, isFalse);
    expect((s.msgs['family']!.last as MyMsg).text, '곧 도착해요');
    s.mic('family');
    s.mic('family'); // 듣는 중에 다시 누르면 멈춤
    await tester.pump();
    expect(s.listening, isFalse);
    expect(s.toast, isNull); // 멈출 때는 "잘 못 들었어요"를 띄우지 않음
    expect(s.msgs['family']!.length, n + 1);
    await tester.pump(const Duration(seconds: 10));
  });

  test('routeGeometry', () {
    const a = (37.3846, 127.1233), b = (37.367, 127.1088); // 서현역 앞 -> 민지 학원
    final r = routePoints(a, b);
    expect(r.first, a);
    expect(r.last.$1, closeTo(b.$1, 1e-9));
    expect(r.last.$2, closeTo(b.$2, 1e-9));
    expect(pointAt(r, 0), a);
    final mid = pointAt(r, .5);
    expect(mid.$1, inInclusiveRange(b.$1 - .01, a.$1 + .01));
    expect(passed(r, 0).length, 2); // 처음에도 선은 점 2개 이상
    expect(passed(r, 1).length, greaterThan(r.length - 1));
    expect(bearing((0, 0), (1, 0)), closeTo(0, 1e-9)); // 북
    expect(bearing((0, 0), (0, 1)), closeTo(90, 1e-9)); // 동
    expect(bearing((0, 0), (-1, 0)), closeTo(180, 1e-9)); // 남
    expect(bearing((0, 0), (0, -1)), closeTo(270, 1e-9)); // 서
  });

  testWidgets('routeStartUsesPlaceWithSameAddress', (tester) async {
    final s = store();
    s.simulateIncoming('dest');
    await tester.pump();
    final card = s.msgs['family']!.last as LocMsg;
    s.openMap('family', card);
    final st = s.routeStart(s.map!);
    final mom = s.seed.places['momHere']!; // 카드의 출발 주소 = 엄마 위치(서현역 앞)와 같은 주소
    expect(st, (mom.lat, mom.lon));
    s.closeMap();
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('sendMyLocationUsesGpsAndAddress', (tester) async {
    final loc = _FakeLoc(() async => const LocationFix(37.3846, 127.1233));
    final s = TokStore(
      server: FakeServer(seed()), session: papa, location: loc,
      resolver: _FakeAddr(() async => '경기도 성남시 분당구 서현로 210 (서현동)'),
    );
    s.openRoom('family');
    final n = s.msgs['family']!.length;
    s.togglePlus();
    final f = s.sendMyLocation();
    expect(s.locating, isTrue);
    await f;
    await tester.pump();
    final m = s.msgs['family']!.last as MyLocMsg;
    expect(s.msgs['family']!.length, n + 1);
    expect(m.name, '성남시 분당구 서현로 210 (서현동)'); // 도 이름은 뺌
    expect((m.lat, m.lon), (37.3846, 127.1233));
    expect(s.locating, isFalse);
    expect(s.plusOpen, isFalse);
    await tester.pump(const Duration(seconds: 10)); // 읽음 표시 타이머 정리
  });

  testWidgets('sendMyLocationBlocksDoubleTapAndReportsPermission', (tester) async {
    final gate = Completer<LocationFix>();
    final loc = _FakeLoc(() => gate.future);
    final s = TokStore(server: FakeServer(seed()), session: papa, location: loc);
    s.openRoom('family');
    final first = s.sendMyLocation();
    await s.sendMyLocation(); // 재는 중에 또 누르면 무시
    expect(loc.calls, 1);
    gate.completeError(const LocationException('위치 권한을 허용해야 내 위치를 보낼 수 있어요'));
    await first;
    expect(s.toast, '위치 권한을 허용해야 내 위치를 보낼 수 있어요');
    expect(s.msgs['family']!.whereType<MyLocMsg>(), isEmpty); // 못 얻으면 보내지 않음
    expect(s.locating, isFalse);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('sendMyLocationWithoutAddressSendsCoordinatesOnly', (tester) async {
    final s = TokStore(
      server: FakeServer(seed()), session: papa, location: _FakeLoc(() async => const LocationFix(37.1, 127.2)),
      resolver: _FakeAddr(() async => throw const AddressException('주소 변환 인증 실패(401): 콘솔에서 Reverse Geocoding 선택과 키를 확인해 주세요')),
    );
    s.openRoom('family');
    await s.sendMyLocation();
    final m = s.msgs['family']!.last as MyLocMsg;
    expect(m.name, '');
    expect((m.lat, m.lon), (37.1, 127.2));
    expect(s.toast, contains('좌표만 보냈어요'));
    await tester.pump(const Duration(seconds: 10)); // 읽음 표시 타이머 정리
  });

  testWidgets('sendMyLocationDropsIfRoomLeftMeanwhile', (tester) async {
    final gate = Completer<LocationFix>();
    final s = TokStore(server: FakeServer(seed()), session: papa, location: _FakeLoc(() => gate.future));
    s.openRoom('mom');
    final f = s.sendMyLocation();
    s.askLeave();
    s.leaveRoom();
    gate.complete(const LocationFix(37, 127));
    await f;
    expect(s.room('mom'), isNull);
    expect(s.msgs['mom']!.whereType<MyLocMsg>(), isEmpty);
    await tester.pump(const Duration(seconds: 3));
  });

  test('parseNaverRoute', () {
    final ok = parseNaverRoute({
      'code': 0,
      'route': {
        'traoptimal': [
          {'summary': {'distance': 1850, 'duration': 420000}, 'path': [[127.1233, 37.3846], [127.1200, 37.3800], [127.1088, 37.3670]]},
        ],
      },
    });
    expect(ok.path.first, (37.3846, 127.1233)); // [경도, 위도] -> (위도, 경도)
    expect(ok.path.length, 3);
    expect((ok.distanceM, ok.durationMs), (1850, 420000));
    // 옵션 이름이 달라도(첫 번째 항목) 읽음
    expect(parseNaverRoute({'code': 0, 'route': {'trafast': [{'path': [[1, 2], [3, 4]]}]}}).path.length, 2);
    // 오류 코드는 이유와 함께 알림
    expect(() => parseNaverRoute({'code': 2, 'message': 'x'}), throwsA(isA<DirectionsException>().having((e) => e.reason, 'reason', contains('도로 근처'))));
    expect(() => parseNaverRoute({'code': 0, 'route': {}}), throwsA(isA<DirectionsException>()));
    expect(() => parseNaverRoute({'code': 0, 'route': {'traoptimal': [{'path': [[1, 2]]}]}}), throwsA(isA<DirectionsException>()));
  });

  test('remainingPathIsFromCurrentPositionToEnd', () {
    final pts = <LL>[(37.0, 127.0), (37.0, 127.1), (37.1, 127.1)]; // 동쪽으로 ㄱ자
    final all = remaining(pts, 0);
    expect(all.first, pts.first);
    expect(all.last, pts.last);
    final half = remaining(pts, .5); // 전체 길이의 절반 지점부터
    expect(half.last, pts.last);
    expect(distM(half.first, pts.last), closeTo(lengthM(pts) / 2, 5));
    expect(remaining(pts, 1).length, 2); // 끝에서도 선은 점 2개 이상
    expect(remaining(pts, 1).first, pts.last);
    final seg2 = remaining(pts, .9); // 두 번째 구간 안: 지난 점(127.1,37.0)은 빠짐
    expect(seg2.length, 2);
    expect(seg2.first.$2, closeTo(127.1, 1e-9));
  });

  test('pathProgressIsByDistanceNotPointCount', () {
    // 점이 몰려 있는 구간(앞쪽 0.001도 안에 점 10개)과 긴 구간 하나: 점 개수 기준이면 진행률 50%가 앞쪽 구간에 걸림
    final pts = <LL>[for (var i = 0; i <= 10; i++) (37.0, 127.0 + i * 0.0001), (37.0, 127.1)];
    final mid = pointAt(pts, .5);
    expect(mid.$2, greaterThan(127.04)); // 거리 기준이면 전체의 절반쯤(경도 127.05 근처)
    expect(mid.$2, lessThan(127.06));
    expect(passed(pts, 1).last.$2, closeTo(127.1, 1e-9));
    expect(lengthM(pts), closeTo(distM((37.0, 127.0), (37.0, 127.1)), 1));
    expect(pointAt(pts, 0), pts.first);
    expect(passed(pts, 0).length, 2);
  });

  test('parseParcelAddress: 지번만, 도로명이 같이 와도 지번', () {
    Map<String, dynamic> region() => {'area1': {'name': '경기도'}, 'area2': {'name': '성남시 수정구'}, 'area3': {'name': '시흥동'}, 'area4': {'name': ''}};
    final j = {
      'results': [
        {'name': 'roadaddr', 'region': region(), 'land': {'name': '창업로40번길', 'number1': '20', 'number2': ''}},
        {'name': 'addr', 'region': region(), 'land': {'number1': '335', 'number2': ''}},
      ],
    };
    expect(parseParcelAddress(j), '경기도 성남시 수정구 시흥동 335');
    expect(parseParcelAddress({'results': [{'name': 'addr', 'region': region(), 'land': {'number1': '12', 'number2': '3'}}]}), '경기도 성남시 수정구 시흥동 12-3');
    expect(parseParcelAddress({'results': [{'name': 'roadaddr', 'region': region(), 'land': {'name': '창업로', 'number1': '42'}}]}), isNull);
    expect(parseParcelAddress({'results': []}), isNull);
  });

  test('parseNaverAddress', () {
    Map<String, dynamic> road() => {
          'status': {'code': 0, 'name': 'ok'},
          'results': [
            {'name': 'addr', 'region': {'area1': {'name': '경기도'}, 'area2': {'name': '성남시 분당구'}, 'area3': {'name': '서현동'}, 'area4': {'name': ''}}, 'land': {'number1': '123', 'number2': '4'}},
            {'name': 'roadaddr', 'region': {'area1': {'name': '경기도'}, 'area2': {'name': '성남시 분당구'}, 'area3': {'name': '서현동'}, 'area4': {'name': ''}}, 'land': {'name': '서현로', 'number1': '210', 'number2': ''}},
          ],
        };
    expect(parseNaverAddress(road()), '경기도 성남시 분당구 서현로 210 (서현동)');
    // 도로명 주소가 비면 지번 주소(번지-호)
    final j = road();
    ((j['results'] as List)[1] as Map)['land'] = {'name': '', 'number1': ''};
    expect(parseNaverAddress(j), '경기도 성남시 분당구 서현동 123-4');
    expect(parseNaverAddress({'results': []}), isNull);
    expect(parseNaverAddress({'status': {'code': 3}}), isNull);
  });

  test('textHelpers', () {
    expect(leftText(203), '3시간 23분');
    expect(leftText(1500), '1일 1시간');
    expect(leftText(0), '0분');
    expect(Josa.ro('집'), '집으로');
    expect(Josa.ro('서울'), '서울로');
    expect(Josa.ga('아빠'), '아빠가');
    expect(Address.area('성남시 분당구 삼평동 123-4'), '분당구 삼평동');
    expect(Address.area('서울특별시 강남구 테헤란로 152'), '강남구 테헤란로');
    expect(Address.withoutProvince('경기도 광주시 오포로 215'), '광주시 오포로 215');
    expect(formatTyping(Countries.of('KR'), '01012345678'), '010-1234-5678');
    expect(formatTyping(Countries.of('US'), '5551234567'), '(555) 123-4567');
    expect(PhoneIntl.mask(Countries.of('KR'), '010-1234-5678'), '+82 10-****-5678');
    expect(clockText(DateTime(2026, 1, 1, 18, 5)), '오후 6:05');
    expect(clockText(DateTime(2026, 1, 1, 0, 30)), '오전 12:30');
  });
}

class _FakeLoc implements LocationProvider {
  _FakeLoc(this.f);
  final Future<LocationFix> Function() f;
  int calls = 0;
  @override
  Future<LocationFix> current() {
    calls++;
    return f();
  }
}

class _FakeAddr implements AddressResolver {
  _FakeAddr(this.f);
  final Future<String?> Function() f;
  @override
  Future<String?> addressOf(double lat, double lon) => f();
}

class _FakeVoice implements VoiceInput {
  Completer<String?>? c;
  @override
  Future<String?> listen() => (c = Completer<String?>()).future;
  @override
  void cancel() => c?.complete(null);
}
