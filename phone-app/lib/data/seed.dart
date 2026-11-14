import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import 'models.dart';

/// 서버 대신 쓰는 더미 데이터(assets/dummy/seed.json, 차량 앱과 같은 파일).
class Seed {
  Seed({
    required this.myName, required this.myPhone, required this.myAddr, required this.places, required this.friends, required this.phonebook,
    required this.rooms, required this.roomMsgs, required this.roomLast, required this.incoming, required this.incomingText, required this.readDelaysMs,
  });

  final String myName, myPhone;
  /// 내 현재 위치 주소(더미). 실제 GPS 연결은 아직 안 함.
  final String myAddr;
  final Map<String, Place> places;
  final List<Friend> friends, phonebook;
  final List<Room> rooms;
  /// 방마다 처음 들어 있는 대화(보낸 사람, 내용).
  final Map<String, List<(String, String)>> roomMsgs;
  final Map<String, String> roomLast;
  final Map<String, IncomingSpec> incoming;
  /// 가족이 보내는 글(개발용 버튼으로 받아 본다).
  final Map<String, IncomingText> incomingText;
  /// 상대가 내 글을 읽는 시각(ms). null이면 계속 안 읽음.
  final Map<String, int?> readDelaysMs;

  static const assetPath = 'assets/dummy/seed.json';

  static Future<Seed> load() async => fromJson(await rootBundle.loadString(assetPath));

  /// "#c2410c" -> 0xFFC2410C
  static int parseColor(String hex) => 0xFF000000 | int.parse(hex.replaceFirst('#', ''), radix: 16);

  static Seed fromJson(String text) {
    final root = jsonDecode(text) as Map<String, dynamic>;
    final me = root['me'] as Map<String, dynamic>;
    List<Map<String, dynamic>> objs(String k) => (root[k] as List).cast<Map<String, dynamic>>();
    String opt(Map<String, dynamic> o, String k) => (o[k] as String?) ?? '';

    final places = {
      for (final o in objs('places'))
        o['id'] as String: Place(id: o['id'], name: o['name'], addr: o['addr'], lat: (o['lat'] as num).toDouble(), lon: (o['lon'] as num).toDouble(), card: opt(o, 'card').isEmpty ? null : o['card']),
    };
    Friend friendOf(Map<String, dynamic> o) =>
        Friend(id: o['id'], tel: o['tel'], name: o['name'], ini: o['ini'], color: parseColor(o['color']), status: opt(o, 'status'));

    final roomObjs = objs('rooms');
    final rooms = [
      for (final o in roomObjs)
        Room(
          id: o['id'], name: o['name'], color: parseColor(o['color']),
          members: (o['members'] as List).cast<String>().toList(), unread: (o['unread'] as int?) ?? 0, time: opt(o, 'time'),
        ),
    ];
    final roomMsgs = {
      for (final o in roomObjs) o['id'] as String: [for (final m in (o['msgs'] as List)) ((m as List)[0] as String, m[1] as String)],
    };
    final incoming = {
      for (final e in (root['incoming'] as Map<String, dynamic>).entries)
        e.key: IncomingSpec(
          roomId: e.value['roomId'], who: e.value['who'], kind: e.value['kind'] == 'here' ? LocKind.here : LocKind.dest, placeId: e.value['place'],
          from: (e.value['from'] as String?) ?? '', min: (e.value['min'] as int?) ?? 0, pct: (e.value['pct'] as int?) ?? 0, eta: (e.value['eta'] as String?) ?? '', traffic: TrafficSeg.parseAll(e.value['traffic']),
        ),
    };
    final incomingText = {
      for (final e in ((root['incomingText'] as Map<String, dynamic>?) ?? {}).entries) e.key: IncomingText(e.value['roomId'], e.value['who'], e.value['text']),
    };
    final delays = {for (final e in (root['readDelaysMs'] as Map<String, dynamic>).entries) e.key: e.value as int?};

    return Seed(
      myName: me['name'], myPhone: me['phone'], myAddr: (me['addr'] as String?) ?? '성남시 분당구 삼평동',
      places: places, friends: objs('friends').map(friendOf).toList(), phonebook: objs('phonebook').map(friendOf).toList(),
      rooms: rooms, roomMsgs: roomMsgs, roomLast: {for (final o in roomObjs) o['id'] as String: opt(o, 'last')},
      incoming: incoming, incomingText: incomingText, readDelaysMs: delays,
    );
  }
}
