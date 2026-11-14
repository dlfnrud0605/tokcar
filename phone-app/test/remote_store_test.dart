import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tokcar_mobile/core/location.dart';
import 'package:tokcar_mobile/core/store.dart';
import 'package:tokcar_mobile/data/models.dart';
import 'package:tokcar_mobile/data/remote.dart';
import 'package:tokcar_mobile/data/seed.dart';
import 'package:tokcar_mobile/data/server.dart';

/// 가족 서버 흉내: 정해 둔 값으로 답하고, 앱이 보낸 프레임을 모은다.
class FakeRemote implements RemoteApi {
  final ctl = StreamController<RemoteEvent>.broadcast(sync: true);
  @override
  Stream<RemoteEvent> get events => ctl.stream;
  @override
  String? token;
  final sent = <Map<String, dynamic>>[];
  bool connected = false, loggedOut = false;
  Object? meFails;

  static final t0 = DateTime(2026, 11, 14, 18, 0);
  static const papa = SUser(1, '아빠'), minji = SUser(2, '민지');
  var msgs = [
    SMessage(5, 1, 1, 'TEXT', t0, '지금 출발', null, null),
    SMessage(6, 1, 2, 'TEXT', t0, '치킨 사와', null, null),
  ];

  @override
  Future<RemoteLogin> login(String loginId, String password) async =>
      password == 'right' ? const RemoteLoginOk('tok', minji) : const RemoteLoginBad(3);
  @override
  Future<void> logout() async => loggedOut = true;
  @override
  Future<SUser> me() async {
    if (meFails != null) throw meFails!;
    return minji;
  }

  @override
  Future<List<SFriend>> friends() async => [const SFriend(1, '아빠', '운전 중'), const SFriend(3, '엄마', '')];
  @override
  Future<List<SRoom>> rooms() async => [SRoom(1, '우리 가족', const [papa, minji], const {1: 5, 2: 5}, msgs.last, 1)];
  @override
  Future<List<SMessage>> messages(int roomId, {int? after}) async => [for (final m in roomId == 9 ? msgs9 : msgs) if (after == null || m.id > after) m];
  final created = <(String, List<int>)>[];
  final invited = <(int, List<int>)>[];
  final left = <int>[];
  Object? reject;
  var msgs9 = <SMessage>[];

  @override
  Future<SRoom> createRoom(String name, List<int> memberIds) async {
    if (reject != null) throw reject!;
    created.add((name, memberIds));
    final others = [for (final id in memberIds) SUser(id, id == 1 ? '아빠' : '엄마')];
    final sys = SMessage(40, 9, null, 'SYSTEM', t0, '민지가 방을 만들었어요 · ${others.map((u) => u.name).join(', ')} 초대', null, null);
    msgs9 = [sys];
    return SRoom(9, name.isEmpty ? others.map((u) => u.name).join(', ') : name, [minji, ...others], const {}, sys, 0);
  }

  @override
  Future<SRoom> invite(int roomId, List<int> userIds) async {
    if (reject != null) throw reject!;
    invited.add((roomId, userIds));
    return SRoom(roomId, '우리 가족', const [papa, minji, SUser(3, '엄마')], const {1: 5, 2: 5}, null, 0);
  }

  @override
  Future<void> leave(int roomId) async => left.add(roomId);

  @override
  void connect() => connected = true;
  @override
  void disconnect() => connected = false;
  @override
  bool send(Map<String, dynamic> frame) {
    sent.add(frame);
    return connected;
  }

  @override
  void dispose() {}

  List<Map<String, dynamic>> sentOf(String type) => [for (final f in sent) if (f['type'] == type) f];
}

class FixedLocation implements LocationProvider {
  @override
  Future<LocationFix> current() async => const LocationFix(37.3671, 127.1086);
}

Seed seed() => Seed.fromJson(File('assets/dummy/seed.json').readAsStringSync());

void main() {
  late FakeRemote remote;
  String? savedToken;
  Session? savedSession;

  TokStore make({Session? session, String? token}) {
    remote = FakeRemote();
    savedToken = token;
    savedSession = session;
    return TokStore(
      server: FakeServer(seed()), location: FixedLocation(), session: session, onSessionChanged: (s) => savedSession = s,
      remote: remote, remoteToken: token, onTokenChanged: (t) => savedToken = t,
    );
  }

  Future<TokStore> loggedIn(WidgetTester tester) async {
    final s = make();
    s.startLogin(LoginMode.login);
    s.setLoginId('minji');
    s.setPw('right');
    s.doLogin();
    await tester.pump();
    await tester.pump();
    return s;
  }

  testWidgets('loginLoadsFriendsRoomsAndMessagesFromServer', (tester) async {
    final s = make();
    expect(s.rooms, isEmpty); // 서버 모드엔 더미 방이 없음
    s.startLogin(LoginMode.login);
    s.setLoginId('minji');
    s.setPw('wrong');
    s.doLogin();
    await tester.pump();
    expect(s.signedIn, isFalse);
    expect(s.login.left, 3);

    s.setPw('right');
    s.doLogin();
    await tester.pump();
    await tester.pump();
    expect(s.signedIn, isTrue);
    expect(savedToken, 'tok');
    expect(remote.connected, isTrue);
    expect(s.friends.map((f) => f.name), ['아빠', '엄마']);
    final r = s.rooms.single;
    expect(r.id, '1');
    expect(r.members, ['아빠']);
    expect(s.unreadOf(r), 1);
    final ms = s.msgs['1']!;
    expect((ms[0] as TextMsg).who, '아빠');
    expect((ms[1] as MyMsg).unreadCount, 1); // 아빠는 5까지만 읽음
    expect(s.lastOf(r), '치킨 사와');
    await tester.pump(const Duration(seconds: 3)); // 토스트가 사라질 때까지
  });

  testWidgets('textGoesOverSocketShowsOnAckAndReadArrives', (tester) async {
    final s = await loggedIn(tester);
    s.openRoom('1');
    expect(remote.sentOf('read').single['msgId'], 6);
    // 남의 메시지에도 안 읽은 수: 이미 읽은 5번은 0, 방에서 새로 받은 아빠 글은 내 읽음이 서버에서 돌아올 때까지 1
    expect((s.msgs['1']![0] as TextMsg).unread, 0);
    remote.ctl.add(RemoteNew(SMessage(8, 1, 1, 'TEXT', FakeRemote.t0, '곧 가', null, null)));
    expect((s.msgs['1']!.last as TextMsg).unread, 1);
    expect(remote.sentOf('read').last['msgId'], 8);
    remote.ctl.add(const RemoteRead(1, 2, 8));
    expect((s.msgs['1']!.last as TextMsg).unread, 0);
    remote.ctl.add(const RemoteRead(1, 2, 8)); // 같은 읽음이 또 와도 그대로
    expect((s.msgs['1']!.last as TextMsg).unread, 0);
    s.send('1', ' 언제 와? ');
    final f = remote.sentOf('text.send').single;
    expect(f['text'], '언제 와?');
    expect(s.msgs['1']!.length, 3); // ack 전엔 아직 없음
    remote.ctl.add(RemoteAck(f['ref'] as String, SMessage(10, 1, 2, 'TEXT', FakeRemote.t0, '언제 와?', null, null)));
    final mine = s.msgs['1']!.last as MyMsg;
    expect(mine.id, 10);
    expect(mine.unreadCount, 1);
    remote.ctl.add(const RemoteRead(1, 1, 10));
    expect((s.msgs['1']!.last as MyMsg).unreadCount, 0);
    await tester.pump(const Duration(seconds: 3)); // 토스트가 사라질 때까지
  });

  testWidgets('myLocationIsSentWithCoordinates', (tester) async {
    final s = await loggedIn(tester);
    s.openRoom('1');
    await s.sendMyLocation();
    final f = remote.sentOf('location.send').single;
    final loc = f['location'] as Map<String, dynamic>;
    expect(loc['lat'], 37.3671);
    expect((loc['addr'] as String).isNotEmpty, isTrue); // 주소를 못 찾으면 좌표 글
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('familyRouteCardFollowsLiveOnCardAndMapThenArrives', (tester) async {
    final s = await loggedIn(tester);
    const route = SRoute('ACTIVE', SPlace('', '성남시 분당구 서현로 210', 37.3836, 127.1232), SPlace('민지 학원', '성남시 분당구 정자일로 95', 37.3671, 127.1086), null);
    final card = SMessage(20, 1, 1, 'ROUTE', FakeRemote.t0, null, null, route);
    remote.ctl.add(RemoteNew(card));
    expect(s.newIn['1'], 1);
    final m = s.msgs['1']!.last as LocMsg;
    expect(m.kind, LocKind.dest);
    expect(m.place?.name, '민지 학원');
    expect(m.start, (37.3836, 127.1232));

    s.openRoom('1');
    s.openMap('1', m);
    expect(s.map!.dest?.name, '민지 학원');
    expect(s.routeStart(s.map!), (37.3836, 127.1232));

    remote.ctl.add(RemoteLive(1, 20, SLive(37.37, 127.11, 300, 70, FakeRemote.t0.add(const Duration(minutes: 5)), [const TrafficSeg(0, 1, Congestion.slow)])));
    final live = s.msgs['1']!.last as LocMsg;
    expect(live.min, 5);
    expect(live.pct, 70);
    expect(live.traffic!.single.level, Congestion.slow);
    expect(s.map!.progress, closeTo(.7, 1e-9));
    expect(s.map!.remain, 5);

    remote.ctl.add(RemoteUpdated(SMessage(20, 1, 1, 'ROUTE', FakeRemote.t0, null, null,
        const SRoute('ARRIVED', SPlace('', '성남시 분당구 서현로 210', 37.3836, 127.1232), SPlace('민지 학원', '성남시 분당구 정자일로 95', 37.3671, 127.1086), null))));
    expect((s.msgs['1']!.last as LocMsg).end, RouteEnd.arrived);
    expect(s.map, isNull); // 보던 경로 공유가 끝나면 지도가 닫힘
    expect(s.toast, '아빠가 도착했어요');
    expect(s.lastOf(s.rooms.single), '아빠가 도착했어요');
    s.openMap('1', s.msgs['1']!.last as LocMsg); // 끝난 카드는 지도로 못 봄
    expect(s.map, isNull);
    await tester.pump(const Duration(seconds: 3)); // 토스트가 사라질 때까지
  });

  testWidgets('signupIsStillBlockedWithANotice', (tester) async {
    final s = await loggedIn(tester);
    s.startLogin(LoginMode.signup);
    expect(s.toast, '이 기능은 다음 단계에서 서버에 붙어요');
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('createInviteLeaveAndDmGoThroughTheServer', (tester) async {
    final s = await loggedIn(tester);
    // 방 만들기: 서버가 만든 방이 목록에 생기고 안내 글이 보임
    s.openNewRoom();
    s.toggleNewMember('3');
    s.createRoom();
    await tester.pump();
    await tester.pump();
    expect(remote.created.single.$1, '');
    expect(remote.created.single.$2, [3]);
    expect(s.roomId, '9');
    expect(s.inChat, isTrue);
    expect(s.room('9')!.name, '엄마');
    expect((s.msgs['9']!.single as SysMsg).text, '민지가 방을 만들었어요 · 엄마 초대');
    remote.ctl.add(RemoteRoomUpdated(SRoom(9, '엄마', const [FakeRemote.minji, SUser(3, '엄마')], const {}, null, 0)));
    expect(s.rooms.where((r) => r.id == '9').length, 1); // 실시간으로 또 와도 하나

    // 1:1: 엄마와 둘만 있는 방(9)이 있으니 새로 안 만들고 그 방을 엶
    s.back();
    s.openDm('3');
    await tester.pump();
    expect(remote.created.length, 1);
    expect(s.roomId, '9');

    // 초대: 우리 가족에 엄마
    s.openRoom('1');
    s.openInvite();
    s.toggleInvite('3');
    s.inviteDone();
    await tester.pump();
    expect(remote.invited.single.$1, 1);
    expect(remote.invited.single.$2, [3]);
    expect(s.room('1')!.members, ['아빠', '엄마']);

    // 거절은 서버 안내 글 그대로
    remote.reject = const RemoteRejected('NOT_FRIEND', '친구만 초대할 수 있어요');
    s.openInvite();
    s.toggleInvite('3');
    s.inviteDone();
    await tester.pump();
    expect(s.toast, '친구만 초대할 수 있어요');
    remote.reject = null;

    // 나가기
    s.askLeave();
    s.leaveRoom();
    await tester.pump();
    expect(remote.left, [1]);
    expect(s.room('1'), isNull);
    expect(s.inChat, isFalse);

    // 다른 기기에서 나간 방(room.removed)도 사라짐
    remote.ctl.add(const RemoteRoomRemoved(9));
    expect(s.room('9'), isNull);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('roomClearedEmptiesTheRoomAndItsUnread', (tester) async {
    final s = await loggedIn(tester);
    final id = s.rooms.single.id;
    expect(s.msgs[id], isNotEmpty);
    expect(s.rooms.single.unread, greaterThan(0));
    // 차 앱의 「우리 가족 데이터 정리」: 서버가 room.cleared 를 보낸다
    remote.ctl.add(RemoteRoomCleared(int.parse(id)));
    await tester.pump();
    expect(s.msgs[id], isEmpty);
    expect(s.lastText[id], '');
    expect(s.rooms.single.unread, 0);
    expect(s.room(id), isNotNull); // 방 자체는 그대로
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('savedTokenRestoresOrSignsOutWhenServerForgotIt', (tester) async {
    const sess = Session('minji', '민지', '', '');
    final ok = make(session: sess, token: 'tok');
    await tester.pump();
    await tester.pump();
    expect(ok.signedIn, isTrue);
    expect(ok.rooms.single.name, '우리 가족');

    remote = FakeRemote()..meFails = const RemoteUnauthorized();
    final gone = TokStore(
      server: FakeServer(seed()), session: sess, onSessionChanged: (s) => savedSession = s,
      remote: remote, remoteToken: 'old', onTokenChanged: (t) => savedToken = t,
    );
    await tester.pump();
    expect(gone.signedIn, isFalse);
    expect(savedToken, isNull);
    expect(savedSession, isNull);
    await tester.pump(const Duration(seconds: 3));
  });

  testWidgets('logoutDropsTokenAndSocket', (tester) async {
    final s = await loggedIn(tester);
    s.logout();
    await tester.pump();
    expect(s.signedIn, isFalse);
    expect(remote.connected, isFalse);
    expect(remote.loggedOut, isTrue);
    expect(savedToken, isNull);
    expect(s.rooms, isEmpty);
    await tester.pump(const Duration(seconds: 3));
  });
}
