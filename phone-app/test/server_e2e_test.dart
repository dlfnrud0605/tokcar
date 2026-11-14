import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tokcar_mobile/data/models.dart';
import 'package:tokcar_mobile/data/remote.dart';

/// 실제 가족 서버(server)에 붙어 보는 연결 확인. 서버를 켜고 주소를 넣었을 때만 돈다:
///   TOKCAR_E2E=http://localhost:8080 flutter test test/server_e2e_test.dart
/// 가계정 papa · minji 의 비밀번호는 서버의 application-local.properties 와 같아야 한다.
void main() {
  final base = Platform.environment['TOKCAR_E2E'] ?? '';

  Future<T> next<T extends RemoteEvent>(RemoteApi api, bool Function(T) ok) =>
      api.events.where((e) => e is T && ok(e)).cast<T>().first.timeout(const Duration(seconds: 5));

  test('minjiReceivesPapasTextAndRouteThroughTheRealServer', () async {
    final papa = HttpRemote(base), minji = HttpRemote(base);
    try {
      final p = await papa.login('papa', 'papa1234!') as RemoteLoginOk;
      final m = await minji.login('minji', 'minji1234!') as RemoteLoginOk;
      papa.token = p.token;
      minji.token = m.token;
      expect((await minji.friends()).map((f) => f.name), containsAll(['아빠', '엄마', '수진']));
      final room = (await minji.rooms()).firstWhere((r) => r.name == '우리 가족');
      expect(room.members.map((u) => u.name).toSet(), {'아빠', '민지', '엄마'});

      final up = [next<RemoteConnected>(papa, (_) => true), next<RemoteConnected>(minji, (_) => true)];
      papa.connect();
      minji.connect();
      await Future.wait(up);

      // 민지(폰)가 글을 보내면 아빠가 받음
      final text = 'E2E ${DateTime.now().millisecondsSinceEpoch}';
      final ack = next<RemoteAck>(minji, (e) => e.ref == 'p-1');
      final got = next<RemoteNew>(papa, (e) => e.message.text == text);
      expect(minji.send({'type': 'text.send', 'ref': 'p-1', 'roomId': room.id, 'text': text}), isTrue);
      final mine = (await ack).message;
      expect(mine.senderId, m.me.id);
      expect((await got).message.id, mine.id);

      // 민지가 위치를 보냄
      final locAck = next<RemoteAck>(minji, (e) => e.ref == 'p-2');
      minji.send({'type': 'location.send', 'ref': 'p-2', 'roomId': room.id, 'location': const SPlace('정자동', '성남시 분당구 정자일로 95', 37.3671, 127.1086).toJson()});
      expect((await locAck).message.location?.lat, 37.3671);

      // 아빠(차)가 경로를 공유하면 민지가 카드 · 실시간 값(정체 포함) · 도착을 받음
      papa.send({'type': 'route.end', 'roomId': room.id, 'reason': 'STOPPED'}); // 지난 시험에서 남은 공유 정리
      final card = next<RemoteNew>(minji, (e) => e.message.kind == 'ROUTE' && e.message.route?.state == 'ACTIVE');
      papa.send({
        'type': 'route.start', 'ref': 'c-1', 'roomId': room.id,
        'from': const SPlace('', '성남시 분당구 판교로 242', 37.40, 127.10).toJson(),
        'dest': const SPlace('민지 학원', '성남시 분당구 정자일로 95', 37.3671, 127.1086).toJson(),
      });
      final route = (await card).message;
      expect(route.route!.dest.name, '민지 학원');
      expect(route.route!.live, isNull);

      final live = next<RemoteLive>(minji, (e) => e.messageId == route.id);
      final eta = DateTime.now().toUtc().add(const Duration(minutes: 10));
      papa.send({
        'type': 'route.live', 'roomId': room.id, 'carLat': 37.39, 'carLon': 127.105, 'remainSec': 600, 'pct': 33,
        'etaAt': eta.toIso8601String(), 'traffic': [[0, 0.4, 'FAST'], [0.4, 1, 'HEAVY']],
      });
      final l = (await live).live;
      expect(l.pct, 33);
      expect(l.traffic![1].level, Congestion.heavy);
      expect(l.etaAt.difference(eta.toLocal()).inSeconds.abs() <= 1, isTrue);

      final end = next<RemoteUpdated>(minji, (e) => e.message.id == route.id);
      papa.send({'type': 'route.end', 'roomId': room.id, 'reason': 'ARRIVED'});
      expect((await end).message.route!.state, 'ARRIVED');

      final again = await minji.messages(room.id, after: route.id - 1);
      expect(again.firstWhere((x) => x.id == route.id).route!.state, 'ARRIVED');

      // 방 만들기 · 초대 · 나가기
      final friends = await minji.friends();
      final mom = friends.firstWhere((f) => f.name == '엄마').id, sujin = friends.firstWhere((f) => f.name == '수진').id;
      final made = await minji.createRoom('', [mom]);
      expect(made.name, '엄마');
      final invited = await minji.invite(made.id, [sujin]);
      expect(invited.members.map((u) => u.name).toSet(), {'민지', '엄마', '수진'});
      await expectLater(minji.invite(made.id, [sujin]), throwsA(isA<RemoteRejected>().having((e) => e.code, 'code', 'ALREADY_MEMBER')));
      await minji.leave(made.id);
      expect((await minji.rooms()).any((r) => r.id == made.id), isFalse);
    } finally {
      papa.dispose();
      minji.dispose();
    }
  }, skip: base.isEmpty ? 'TOKCAR_E2E 가 없으면 건너뜀' : false);
}
