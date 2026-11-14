import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'models.dart';

// 가족 서버(server)와 주고받는 JSON 모양. 칸 이름은 서버 API 명세(docs/api.html)와 같다.
// 시각은 서버가 ISO-8601 UTC 로 주고, 앱 안에서는 DateTime(로컬)으로 다룬다.

class SUser {
  const SUser(this.id, this.name);
  final int id;
  final String name;
  factory SUser.fromJson(Map<String, dynamic> j) => SUser(j['id'] as int, j['name'] as String);
}

class SFriend {
  const SFriend(this.id, this.name, this.status);
  final int id;
  final String name, status;
  factory SFriend.fromJson(Map<String, dynamic> j) => SFriend(j['id'] as int, j['name'] as String, (j['statusMsg'] as String?) ?? '');
}

class SPlace {
  const SPlace(this.name, this.addr, this.lat, this.lon);
  final String name, addr;
  final double lat, lon;
  factory SPlace.fromJson(Map<String, dynamic> j) =>
      SPlace((j['name'] as String?) ?? '', (j['addr'] as String?) ?? '', (j['lat'] as num).toDouble(), (j['lon'] as num).toDouble());
  Map<String, dynamic> toJson() => {'name': name, 'addr': addr, 'lat': lat, 'lon': lon};
}

/// 경로 공유의 실시간 값(서버 메모리). traffic 이 null 이면 정보 없음.
class SLive {
  const SLive(this.carLat, this.carLon, this.remainSec, this.pct, this.etaAt, this.traffic);
  final double carLat, carLon;
  final int remainSec, pct;
  final DateTime etaAt;
  final List<TrafficSeg>? traffic;
  factory SLive.fromJson(Map<String, dynamic> j) => SLive(
        (j['carLat'] as num).toDouble(),
        (j['carLon'] as num).toDouble(),
        j['remainSec'] as int,
        j['pct'] as int,
        DateTime.parse(j['etaAt'] as String).toLocal(),
        // 서버는 단계를 대문자("FAST")로 줌 → 앱의 TrafficSeg 는 소문자로 읽음
        TrafficSeg.parseAll((j['traffic'] as List?)?.map((e) => e is List && e.length >= 3 ? [e[0], e[1], '${e[2]}'.toLowerCase()] : e).toList()),
      );
}

class SRoute {
  const SRoute(this.state, this.from, this.dest, this.live);
  final String state;
  final SPlace from, dest;
  final SLive? live;
  factory SRoute.fromJson(Map<String, dynamic> j) => SRoute(
        j['state'] as String,
        SPlace.fromJson(j['from'] as Map<String, dynamic>),
        SPlace.fromJson(j['dest'] as Map<String, dynamic>),
        j['live'] == null ? null : SLive.fromJson(j['live'] as Map<String, dynamic>),
      );
}

class SMessage {
  const SMessage(this.id, this.roomId, this.senderId, this.kind, this.createdAt, this.text, this.location, this.route);
  final int id, roomId;
  final int? senderId;
  final String kind;
  final DateTime createdAt;
  final String? text;
  final SPlace? location;
  final SRoute? route;
  factory SMessage.fromJson(Map<String, dynamic> j) => SMessage(
        j['id'] as int,
        j['roomId'] as int,
        j['senderId'] as int?,
        j['kind'] as String,
        DateTime.parse(j['createdAt'] as String).toLocal(),
        j['text'] as String?,
        j['location'] == null ? null : SPlace.fromJson(j['location'] as Map<String, dynamic>),
        j['route'] == null ? null : SRoute.fromJson(j['route'] as Map<String, dynamic>),
      );
}

class SRoom {
  const SRoom(this.id, this.name, this.members, this.readUpTo, this.lastMessage, this.unread);
  final int id;
  final String name;
  final List<SUser> members;
  /// 사람마다 어디까지 읽었는지(사용자 id → 메시지 id).
  final Map<int, int> readUpTo;
  final SMessage? lastMessage;
  final int unread;
  factory SRoom.fromJson(Map<String, dynamic> j) => SRoom(
        j['id'] as int,
        j['name'] as String,
        [for (final m in j['members'] as List) SUser.fromJson(m as Map<String, dynamic>)],
        {for (final r in j['readUpTo'] as List) (r as Map<String, dynamic>)['userId'] as int: r['msgId'] as int},
        j['lastMessage'] == null ? null : SMessage.fromJson(j['lastMessage'] as Map<String, dynamic>),
        (j['unread'] as int?) ?? 0,
      );
}

// ───── 결과 · 실시간 이벤트 ─────

sealed class RemoteLogin {
  const RemoteLogin();
}

class RemoteLoginOk extends RemoteLogin {
  const RemoteLoginOk(this.token, this.me);
  final String token;
  final SUser me;
}

class RemoteLoginBad extends RemoteLogin {
  const RemoteLoginBad(this.left);
  final int? left;
}

class RemoteLoginLocked extends RemoteLogin {
  const RemoteLoginLocked();
}

class RemoteLoginFailed extends RemoteLogin {
  const RemoteLoginFailed(this.reason);
  final String reason;
}

/// 서버가 WebSocket 으로 먼저 알려 주는 일(API 명세 4절 「서버 → 앱」).
sealed class RemoteEvent {
  const RemoteEvent();
}

class RemoteConnected extends RemoteEvent {
  const RemoteConnected();
}

class RemoteAck extends RemoteEvent {
  const RemoteAck(this.ref, this.message);
  final String? ref;
  final SMessage message;
}

class RemoteNew extends RemoteEvent {
  const RemoteNew(this.message);
  final SMessage message;
}

class RemoteUpdated extends RemoteEvent {
  const RemoteUpdated(this.message);
  final SMessage message;
}

class RemoteLive extends RemoteEvent {
  const RemoteLive(this.roomId, this.messageId, this.live);
  final int roomId, messageId;
  final SLive live;
}

class RemoteRead extends RemoteEvent {
  const RemoteRead(this.roomId, this.userId, this.msgId);
  final int roomId, userId, msgId;
}

class RemoteError extends RemoteEvent {
  const RemoteError(this.ref, this.code, this.message);
  final String? ref;
  final String code, message;
}

/// 방이 생겼거나 멤버 · 이름이 바뀜(방 만들기 · 초대 · 누가 나감). 모르는 방이면 목록에 넣는다.
class RemoteRoomUpdated extends RemoteEvent {
  const RemoteRoomUpdated(this.room);
  final SRoom room;
}

/// 그 방에서 빠짐(내가 나감, 다른 기기에서 나간 것 포함).
class RemoteRoomRemoved extends RemoteEvent {
  const RemoteRoomRemoved(this.roomId);
  final int roomId;
}

/// (개발용) 서버가 그 방을 처음 상태로 돌렸다(대화 · 경로 공유 삭제). 차 앱의 「우리 가족 데이터 정리」.
class RemoteRoomCleared extends RemoteEvent {
  const RemoteRoomCleared(this.roomId);
  final int roomId;
}

/// 서버가 거절한 요청(친구가 아님 · 이미 방에 있음 등). [message] 는 그대로 보여 줄 안내 글.
class RemoteRejected implements Exception {
  const RemoteRejected(this.code, this.message);
  final String code, message;
}

/// 토큰이 없거나 서버가 모르는 토큰(서버를 다시 켰을 때 등). 다시 로그인해야 함.
class RemoteUnauthorized implements Exception {
  const RemoteUnauthorized();
}

/// 가족 서버. REST 조회와 WebSocket 실시간. 테스트에서는 가짜로 바꿔 끼운다.
abstract class RemoteApi {
  Stream<RemoteEvent> get events;
  String? token;

  Future<RemoteLogin> login(String loginId, String password);
  Future<void> logout();
  Future<SUser> me();
  Future<List<SFriend>> friends();
  Future<List<SRoom>> rooms();
  /// [after] 가 있으면 그 뒤 것 전부, 없으면 최신 50개. 오래된 것 → 새것 순.
  Future<List<SMessage>> messages(int roomId, {int? after});
  /// 방 만들기: 나 + [memberIds]. 이름이 비면 서버가 친구 이름으로 짓는다.
  Future<SRoom> createRoom(String name, List<int> memberIds);
  Future<SRoom> invite(int roomId, List<int> userIds);
  Future<void> leave(int roomId);

  /// WebSocket 을 열고, 끊기면 1·2·4…30초 간격으로 다시 붙는다.
  void connect();
  void disconnect();
  /// 열려 있지 않으면 false.
  bool send(Map<String, dynamic> frame);
  void dispose();
}

/// 실제 구현(http + dart:io WebSocket). [base] 예: http://10.0.2.2:8080
class HttpRemote implements RemoteApi {
  HttpRemote(String base) : _base = base.endsWith('/') ? base.substring(0, base.length - 1) : base;

  final String _base;
  final _events = StreamController<RemoteEvent>.broadcast();
  @override
  String? token;
  WebSocket? _ws;
  bool _wanted = false;
  Timer? _retry;
  int _backoffMs = 1000;

  @override
  Stream<RemoteEvent> get events => _events.stream;

  Map<String, String> get _headers => {'Content-Type': 'application/json', if (token != null) 'Authorization': 'Bearer $token'};

  @override
  Future<RemoteLogin> login(String loginId, String password) async {
    try {
      final r = await http
          .post(Uri.parse('$_base/api/auth/login'), headers: {'Content-Type': 'application/json'}, body: jsonEncode({'loginId': loginId, 'password': password}))
          .timeout(const Duration(seconds: 10));
      final j = r.body.isEmpty ? null : jsonDecode(utf8.decode(r.bodyBytes)) as Map<String, dynamic>;
      return switch (r.statusCode) {
        200 => RemoteLoginOk(j!['token'] as String, SUser.fromJson(j['me'] as Map<String, dynamic>)),
        401 => RemoteLoginBad((j?['error'] as Map<String, dynamic>?)?['left'] as int?),
        423 => const RemoteLoginLocked(),
        _ => RemoteLoginFailed('서버 오류(${r.statusCode})'),
      };
    } catch (e) {
      return const RemoteLoginFailed('서버에 연결하지 못했어요');
    }
  }

  @override
  Future<void> logout() async {
    try {
      await http.post(Uri.parse('$_base/api/auth/logout'), headers: _headers).timeout(const Duration(seconds: 5));
    } catch (_) {}
  }

  Future<dynamic> _get(String path) async {
    final r = await http.get(Uri.parse('$_base$path'), headers: _headers).timeout(const Duration(seconds: 10));
    if (r.statusCode == 401) throw const RemoteUnauthorized();
    if (r.statusCode != 200) throw HttpException('서버 오류(${r.statusCode}) $path');
    return jsonDecode(utf8.decode(r.bodyBytes));
  }

  /// POST · DELETE. 4xx 에 서버 오류 글이 있으면 [RemoteRejected].
  Future<dynamic> _send(String method, String path, [Object? body]) async {
    final uri = Uri.parse('$_base$path');
    final r = await (method == 'DELETE'
            ? http.delete(uri, headers: _headers)
            : http.post(uri, headers: _headers, body: body == null ? null : jsonEncode(body)))
        .timeout(const Duration(seconds: 10));
    if (r.statusCode == 401) throw const RemoteUnauthorized();
    final text = utf8.decode(r.bodyBytes);
    if (r.statusCode >= 400 && r.statusCode < 500 && text.isNotEmpty) {
      final e = (jsonDecode(text) as Map<String, dynamic>)['error'] as Map<String, dynamic>?;
      if (e != null) throw RemoteRejected((e['code'] as String?) ?? '', (e['message'] as String?) ?? '');
    }
    if (r.statusCode < 200 || r.statusCode > 299) throw HttpException('서버 오류(${r.statusCode}) $path');
    return text.isEmpty ? null : jsonDecode(text);
  }

  @override
  Future<SRoom> createRoom(String name, List<int> memberIds) async =>
      SRoom.fromJson(await _send('POST', '/api/rooms', {'name': name, 'memberIds': memberIds}) as Map<String, dynamic>);

  @override
  Future<SRoom> invite(int roomId, List<int> userIds) async =>
      SRoom.fromJson(await _send('POST', '/api/rooms/$roomId/members', {'userIds': userIds}) as Map<String, dynamic>);

  @override
  Future<void> leave(int roomId) async => _send('DELETE', '/api/rooms/$roomId/members/me');

  @override
  Future<SUser> me() async => SUser.fromJson(await _get('/api/me') as Map<String, dynamic>);

  @override
  Future<List<SFriend>> friends() async => [for (final f in await _get('/api/friends') as List) SFriend.fromJson(f as Map<String, dynamic>)];

  @override
  Future<List<SRoom>> rooms() async => [for (final r in await _get('/api/rooms') as List) SRoom.fromJson(r as Map<String, dynamic>)];

  @override
  Future<List<SMessage>> messages(int roomId, {int? after}) async {
    final j = await _get('/api/rooms/$roomId/messages${after == null ? '' : '?after=$after'}') as Map<String, dynamic>;
    return [for (final m in j['messages'] as List) SMessage.fromJson(m as Map<String, dynamic>)];
  }

  // ───── WebSocket ─────

  @override
  void connect() {
    _wanted = true;
    if (_ws == null) _open();
  }

  @override
  void disconnect() {
    _wanted = false;
    _retry?.cancel();
    _ws?.close(1000, 'bye');
    _ws = null;
  }

  @override
  bool send(Map<String, dynamic> frame) {
    final ws = _ws;
    if (ws == null || ws.readyState != WebSocket.open) return false;
    ws.add(jsonEncode(frame));
    return true;
  }

  Future<void> _open() async {
    final t = token;
    if (t == null) return;
    final url = '${_base.replaceFirst('http', 'ws')}/ws';
    try {
      final ws = await WebSocket.connect(url, headers: {'Authorization': 'Bearer $t'}).timeout(const Duration(seconds: 10));
      if (!_wanted) {
        ws.close(1000, 'bye');
        return;
      }
      ws.pingInterval = const Duration(seconds: 25);
      _ws = ws;
      _backoffMs = 1000;
      _events.add(const RemoteConnected());
      ws.listen(
        (data) {
          try {
            final e = _parse(jsonDecode(data as String) as Map<String, dynamic>);
            if (e != null) _events.add(e);
          } catch (e) {
            debugPrint('[TokRemote] 프레임 읽기 실패: $e');
          }
        },
        onDone: () => _dropped(ws),
        onError: (_) => _dropped(ws),
        cancelOnError: true,
      );
    } catch (e) {
      debugPrint('[TokRemote] 연결 실패: $e');
      _scheduleRetry();
    }
  }

  void _dropped(WebSocket ws) {
    if (!identical(ws, _ws)) return;
    _ws = null;
    _scheduleRetry();
  }

  void _scheduleRetry() {
    if (!_wanted) return;
    _retry?.cancel();
    _retry = Timer(Duration(milliseconds: _backoffMs), () {
      if (_wanted && _ws == null) _open();
    });
    _backoffMs = (_backoffMs * 2).clamp(1000, 30000);
  }

  RemoteEvent? _parse(Map<String, dynamic> j) {
    final ref = j['ref'] as String?;
    SMessage msg() => SMessage.fromJson(j['message'] as Map<String, dynamic>);
    return switch (j['type']) {
      'ack' => RemoteAck(ref, msg()),
      'message.new' => RemoteNew(msg()),
      'message.updated' => RemoteUpdated(msg()),
      'route.live' => RemoteLive(j['roomId'] as int, j['messageId'] as int, SLive.fromJson(j['live'] as Map<String, dynamic>)),
      'read' => RemoteRead(j['roomId'] as int, j['userId'] as int, j['msgId'] as int),
      'error' => RemoteError(ref, (j['code'] as String?) ?? '', (j['message'] as String?) ?? ''),
      'room.updated' => RemoteRoomUpdated(SRoom.fromJson(j['room'] as Map<String, dynamic>)),
      'room.removed' => RemoteRoomRemoved(j['roomId'] as int),
      'room.cleared' => RemoteRoomCleared(j['roomId'] as int),
      _ => null,
    };
  }

  @override
  void dispose() {
    disconnect();
    _events.close();
  }
}
