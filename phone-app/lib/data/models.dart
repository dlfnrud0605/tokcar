import '../core/text.dart';

/// 장소. 좌표는 예시(더미)이고 실제 도로·건물 위치가 아니다.
class Place {
  const Place({required this.id, required this.name, required this.addr, required this.lat, required this.lon, this.card});
  final String id, name, addr;
  /// 지도에 찍는 좌표(더미 데이터라 실제 건물 위치와 다를 수 있음).
  final double lat, lon;
  /// 카드에 쓰는 짧은 이름(없으면 name).
  final String? card;

  String get short => card ?? name;

  /// 작은 글씨로 보이는 전체 주소. 이름이 주소처럼 생겼고 주소 칸보다 길면 이름을 전체 주소로 본다(도·특별시 이름은 뺌).
  String get fullAddr {
    final base = short;
    final a = (Address.looksLikeAddress(base) && base.length > addr.length) ? base : (addr.trim().isEmpty ? base : addr);
    return Address.withoutProvince(a);
  }

  /// 큰 글씨 이름. 이름이 주소처럼 생겼으면 "구(시) 동"까지만.
  String get title => (short.trim().isEmpty || Address.looksLikeAddress(short)) ? Address.area(fullAddr) : short;
}

class Friend {
  const Friend({required this.id, required this.tel, required this.name, required this.ini, required this.color, required this.status});
  final String id, tel, name, ini;
  /// 얼굴 색(ARGB). 연한 톤으로 바꿔 쓴다.
  final int color;
  final String status;
}

class Room {
  Room({required this.id, required this.name, required this.color, required this.members, required this.unread, required this.time});
  final String id, name;
  final int color;
  List<String> members;
  /// 서버가 알려 준 안 읽은 수(방을 열면 0).
  final int unread;
  final String time;

  /// 나를 포함한 인원수.
  int get headCount => members.length + 1;
}

enum LocKind { here, dest }

/// 경로 위 정체 단계(차량 앱·내비 SDK 와 같음): 원활 · 보통 · 서행 · 정체.
enum Congestion {
  fast, moderate, slow, heavy;

  /// 서버가 주는 글자("fast"…)를 단계로. 모르는 글자는 null(막대에 안 그림).
  static Congestion? parse(Object? v) => switch (v) { 'fast' => fast, 'moderate' => moderate, 'slow' => slow, 'heavy' => heavy, _ => null };
}

/// 경로 위 정체 구간. [from]·[to] 는 경로 전체를 0~1로 본 위치(출발 0, 도착 1).
class TrafficSeg {
  const TrafficSeg(this.from, this.to, this.level);
  final double from, to;
  final Congestion level;

  /// 서버 형식 `[from, to, "fast"]` 의 목록. 모양이 틀린 칸·모르는 단계는 건너뛴다. null 이면 정보 없음(null).
  static List<TrafficSeg>? parseAll(Object? v) {
    if (v is! List) return null;
    final out = <TrafficSeg>[];
    for (final e in v) {
      if (e is! List || e.length < 3 || e[0] is! num || e[1] is! num) continue;
      final lv = Congestion.parse(e[2]);
      if (lv == null) continue;
      final a = (e[0] as num).toDouble().clamp(0.0, 1.0), b = (e[1] as num).toDouble().clamp(0.0, 1.0);
      if (b > a) out.add(TrafficSeg(a, b, lv));
    }
    return out;
  }
}

/// 대화방의 한 줄.
sealed class Msg {
  Msg(this.id);
  final int id;
}

/// 다른 사람이 보낸 글. [unread] = 이 글을 아직 안 읽은 사람 수(보낸 사람 빼고, 카카오톡처럼 옆에 보임).
class TextMsg extends Msg {
  TextMsg(super.id, this.who, this.text, {this.unread = 0});
  final String who, text;
  int unread;
}

/// 시스템 안내(방 만들기, 초대).
class SysMsg extends Msg {
  SysMsg(super.id, this.text);
  final String text;
}

/// 경로 공유가 끝난 이유(서버가 알려 줌).
enum RouteEnd { arrived, stopped }

/// 가족이 보낸 위치 카드. here = 한 번 보낸 현재 위치, dest = 경로 공유(목적지로 가는 중).
/// 경로 공유는 출발([from]), 남은 분([min]), 진행률([pct], %), 도착 예정([eta])을 함께 받는다.
/// 서버에서 온 카드는 장소를 [place](현재 위치 · 목적지)로, 출발 좌표를 [start]로 직접 갖는다(더미는 [placeId]로 seed 장소를 찾음).
class LocMsg extends Msg {
  LocMsg(super.id, {required this.who, required this.kind, required this.placeId, this.from = '', this.min = 0, this.pct = 0, this.eta = '', this.time = '', this.traffic, this.place, this.start, this.end, this.unread = 0});
  final String who;
  final LocKind kind;
  final String placeId, from;
  final int min, pct;
  final String eta, time;
  /// 경로 위 정체 구간(보낸 차가 알려 줌). null = 정보 없음, 빈 목록 = 정체 없음.
  final List<TrafficSeg>? traffic;
  final Place? place;
  /// 경로 공유의 출발 좌표(또는 차의 지금 위치에 가까운 곳). null 이면 store.routeStart 가 정함.
  final (double, double)? start;
  /// 경로 공유가 끝났으면 도착 · 공유 종료. 가는 중이면 null.
  final RouteEnd? end;
  /// 이 카드를 아직 안 읽은 사람 수(보낸 사람 빼고).
  int unread;

  /// 실시간 값이 바뀐 같은 카드.
  LocMsg withLive({required int min, required int pct, required String eta, List<TrafficSeg>? traffic}) => LocMsg(id,
      who: who, kind: kind, placeId: placeId, from: from, min: min, pct: pct, eta: eta, time: time, traffic: traffic, place: place, start: start, end: end, unread: unread);
}

/// 내가 보낸 것. 아직 안 읽은 사람 수를 옆에 보인다(카카오톡 방식).
sealed class MineMsg extends Msg {
  MineMsg(super.id, this.total);
  final int total;
  final Set<String> readBy = {};
  int get unreadCount => total - readBy.length;
}

class MyMsg extends MineMsg {
  MyMsg(super.id, this.text, super.total);
  final String text;
}

/// 내가 보낸 내 위치. name = 전체 주소(주소를 못 찾았으면 빈 글), lat/lon = 폰 GPS 좌표(더미 위치면 null).
class MyLocMsg extends MineMsg {
  MyLocMsg(super.id, this.name, this.time, super.total, {this.lat, this.lon, this.title = ''});
  final String name, time;
  final double? lat, lon;
  /// 지도에서 고를 때 넣은 장소 이름(예: 민지 학원). 비면 주소로 제목을 만든다.
  final String title;
}

/// 목록 카드에 보이는 마지막 대화 문구. 보낸 사람 이름 없이 내용만.
String describeMsg(Msg m) => switch (m) {
      TextMsg() => m.text,
      MyMsg() => m.text,
      SysMsg() => m.text,
      LocMsg() => m.kind == LocKind.here
          ? '${Josa.ga(m.who)} 위치를 공유했어요'
          : switch (m.end) {
              RouteEnd.arrived => '${Josa.ga(m.who)} 도착했어요',
              RouteEnd.stopped => '${Josa.ga(m.who)} 경로 공유를 멈췄어요',
              null => '${Josa.ga(m.who)} 경로를 공유했어요${m.min > 0 ? ' · ${leftText(m.min)} 남음' : ''}',
            },
      MyLocMsg() => '위치를 공유했어요',
    };

/// 가족 폰에서 위치를 보내는 상황을 흉내 내는 데이터.
class IncomingSpec {
  const IncomingSpec({required this.roomId, required this.who, required this.kind, required this.placeId, this.from = '', this.min = 0, this.pct = 0, this.eta = '', this.traffic});
  final String roomId, who;
  final LocKind kind;
  final String placeId, from;
  final int min, pct;
  final String eta;
  final List<TrafficSeg>? traffic;
}

/// 가족이 보낸 글을 흉내 내는 데이터.
class IncomingText {
  const IncomingText(this.roomId, this.who, this.text);
  final String roomId, who, text;
}

/// 친구 추가: 전화번호 검색 결과.
sealed class PhoneLookup {
  const PhoneLookup();
}

class LookupSelf extends PhoneLookup {
  const LookupSelf();
}

class LookupAlready extends PhoneLookup {
  const LookupAlready(this.friend);
  final Friend friend;
}

class LookupFound extends PhoneLookup {
  const LookupFound(this.friend, this.tel);
  final Friend friend;
  final String tel;
}

class LookupNotRegistered extends PhoneLookup {
  const LookupNotRegistered(this.tel);
  final String tel;
}

/// 가입한 계정. [phone] 은 입력한 그대로의 번호(나라 안 표기), [key] 는 E.164.
class Account {
  const Account(this.id, this.pw, this.key, this.country, this.phone, this.name);
  final String id, pw, key, country, phone, name;
  Account withPw(String p) => Account(id, p, key, country, phone, name);
}

sealed class LoginResult {
  const LoginResult();
}

class LoginOk extends LoginResult {
  const LoginOk(this.account);
  final Account account;
}

class LoginBad extends LoginResult {
  const LoginBad(this.left);
  final int left;
}

class LoginLocked extends LoginResult {
  const LoginLocked();
}
