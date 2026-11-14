import 'dart:async';

import 'models.dart';
import 'seed.dart';

/// 서버가 앱에 먼저 알려 주는 일.
sealed class ServerEvent {
  const ServerEvent();
}

/// 상대가 내 글을 읽었다.
class ReadEvent extends ServerEvent {
  const ReadEvent(this.roomId, this.msgId, this.who);
  final String roomId;
  final int msgId;
  final String who;
}

/// 가족이 위치를 보냈다.
class LocationEvent extends ServerEvent {
  const LocationEvent(this.spec);
  final IncomingSpec spec;
}

/// 가족이 글을 보냈다.
class MessageEvent extends ServerEvent {
  const MessageEvent(this.roomId, this.who, this.text);
  final String roomId, who, text;
}

/// 가족 서버와 주고받는 부분. 진짜 서버가 생기면 이 인터페이스를 구현해 끼우면 된다.
abstract class FamilyServer {
  static const lockN = 5;

  Seed get seed;
  Stream<ServerEvent> get events;

  /// 번호(E.164)가 가입돼 있으면 가입한 이름, 아니면 null.
  String? registeredName(String e164);
  Future<bool> sendCode(String phone);
  /// 인증번호 확인. 더미 서버는 숫자 6자리면 통과시킨다.
  Future<bool> verify(String phone, String code);
  PhoneLookup lookup(String tel, String myPhone, List<Friend> friends);
  /// 내가 방에 글을 보냈다. 상대가 읽는 일을 서버가 알려 준다.
  void messageSent(String roomId, int msgId, List<String> members);
  /// (개발용) 가족 폰에서 위치·글을 보낸 것처럼 만든다. key: "here"·"dest"(위치) 또는 "text"(글).
  void simulateIncoming(String key);

  // ───── 아이디·비밀번호 계정 ─────
  // 실제 서버는 비밀번호를 해시로만 저장해야 함. 더미 서버는 화면용이라 메모리에 그대로 둔다.
  bool idTaken(String id);
  Account? account(String id);
  LoginResult login(String id, String pw);
  int triesLeft(String id);
  List<String> findIds(String e164);
  void createAccount(Account a);
  void resetPassword(String id, String pw);
  void dispose();
}

/// assets/dummy/seed.json 의 값으로 응답하는 가짜 서버(차량 앱의 FakeServer 와 같은 규칙). 값은 메모리에만 있다.
class FakeServer implements FamilyServer {
  FakeServer(this.seed) {
    if (seed.myPhone.startsWith('0')) _registered['+82${seed.myPhone.substring(1)}'] = seed.myName;
    _accounts['papa'] = Account('papa', 'papa1234!', '+821012345678', 'KR', '01012345678', seed.myName); // 시안 가입자: 010-1234-5678
  }

  @override
  final Seed seed;
  final _events = StreamController<ServerEvent>.broadcast();
  final _timers = <Timer>[];
  final _registered = <String, String>{};
  final _accounts = <String, Account>{};
  final _fails = <String, int>{};

  static const _reserved = {'admin', 'test', 'mama', 'minji', 'warak', 'root'};
  static const _delay = Duration(milliseconds: 300);
  static const _defaultReadMs = 3000;

  @override
  Stream<ServerEvent> get events => _events.stream;

  void _later(Duration d, void Function() f) => _timers.add(Timer(d, () {
        if (!_events.isClosed) f();
      }));

  @override
  String? registeredName(String e164) => _registered[e164];

  @override
  Future<bool> sendCode(String phone) async {
    await Future<void>.delayed(_delay);
    return true;
  }

  @override
  Future<bool> verify(String phone, String code) async {
    await Future<void>.delayed(_delay);
    return RegExp(r'^\d{6}$').hasMatch(code);
  }

  @override
  PhoneLookup lookup(String tel, String myPhone, List<Friend> friends) {
    if (tel == myPhone) return const LookupSelf();
    for (final f in friends) {
      if (f.tel == tel) return LookupAlready(f);
    }
    for (final f in seed.phonebook) {
      if (f.tel == tel) return LookupFound(f, tel);
    }
    return LookupNotRegistered(tel);
  }

  @override
  void messageSent(String roomId, int msgId, List<String> members) {
    for (final name in members) {
      final ms = seed.readDelaysMs.containsKey(name) ? seed.readDelaysMs[name] : _defaultReadMs;
      if (ms == null) continue; // 계속 안 읽는 사람
      _later(Duration(milliseconds: ms), () => _events.add(ReadEvent(roomId, msgId, name)));
    }
  }

  @override
  void simulateIncoming(String key) {
    final spec = seed.incoming[key];
    if (spec != null) {
      _events.add(LocationEvent(spec));
      return;
    }
    final t = seed.incomingText[key];
    if (t != null) _events.add(MessageEvent(t.roomId, t.who, t.text));
  }

  @override
  bool idTaken(String id) => _accounts.containsKey(id) || _reserved.contains(id);

  @override
  Account? account(String id) => _accounts[id];

  @override
  LoginResult login(String id, String pw) {
    if (triesLeft(id) <= 0) return const LoginLocked();
    final a = _accounts[id];
    if (a == null || a.pw != pw) {
      _fails[id] = (_fails[id] ?? 0) + 1;
      return triesLeft(id) <= 0 ? const LoginLocked() : LoginBad(triesLeft(id));
    }
    _fails[id] = 0;
    return LoginOk(a);
  }

  @override
  int triesLeft(String id) => (FamilyServer.lockN - (_fails[id] ?? 0)).clamp(0, FamilyServer.lockN);

  @override
  List<String> findIds(String e164) => _accounts.values.where((a) => a.key == e164).map((a) => a.id).toList();

  @override
  void createAccount(Account a) {
    _accounts[a.id] = a;
    _registered[a.key] = a.name;
  }

  @override
  void resetPassword(String id, String pw) {
    final a = _accounts[id];
    if (a != null) _accounts[id] = a.withPw(pw);
    _fails[id] = 0;
  }

  @override
  void dispose() {
    for (final t in _timers) {
      t.cancel();
    }
    _events.close();
  }
}
