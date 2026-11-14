import 'dart:async';

import 'package:flutter/foundation.dart';

import '../data/models.dart';
import '../data/place_search.dart';
import '../data/remote.dart';
import '../data/seed.dart';
import '../data/server.dart';
import '../data/directions.dart';
import 'countries.dart';
import 'location.dart';
import 'text.dart';
import 'voice.dart';

/// 아래 탭바 순서: 친구 · 채팅 · 설정.
enum HomeTab { friends, chat, settings }

// ───────────────────────── 회원가입·로그인 ─────────────────────────

/// 처음 화면 -> (회원가입) 번호 -> 인증번호 -> 아이디·비밀번호 -> 이름 -> 약관
///          -> (로그인) 아이디·비밀번호
///          -> (아이디 찾기) 번호 -> 인증번호 -> 찾은 아이디
///          -> (비밀번호 찾기) 아이디 -> 인증번호 -> 새 비밀번호
enum LoginStep { welcome, idpw, phone, rid, code, account, newpw, fdone, profile, terms }

enum LoginMode { signup, login, findid, resetpw }

/// 이미 가입된 번호 / 가입 안 된 번호 / 아이디·비밀번호가 틀림 / 없는 아이디.
enum LoginErr { known, unknown, bad, noid }

class Agree {
  bool tos = false, priv = false, loc = false, mkt = false;
  bool get all => tos && priv && loc && mkt;
  bool get required => tos && priv && loc;
}

class LoginState {
  LoginState({this.mode, this.step = LoginStep.welcome, this.id = '', this.rid = '', this.left = 0});
  LoginMode? mode;
  LoginStep step;
  String country = 'KR';
  /// 입력한 번호(하이픈 포함, 입력 모양 그대로).
  String phone = '', code = '', name = '';
  String id;
  String pw = '', pw2 = '';
  bool showPw = false;
  /// 아이디 중복 확인 결과(확인한 아이디, 쓸 수 있는가). 그 뒤에 아이디를 고치면 다시 확인해야 함.
  (String, bool)? idCheck;
  /// 비밀번호 찾기에서 입력한 아이디.
  String rid;
  List<String> found = [];
  final agree = Agree();
  LoginErr? err;
  /// 로그인에서 틀렸을 때 남은 시도 횟수.
  int left;
  bool busy = false;
}

/// 아이디·비밀번호 규칙.
class Rules {
  static final id = RegExp(r'^[a-z][a-z0-9]{3,15}$');

  static ({bool len, bool mix, bool notId, bool ok}) pw(String pw, String id) {
    final len = pw.length >= 8 && pw.length <= 20;
    final mix = RegExp(r'[A-Za-z]').hasMatch(pw) && RegExp(r'\d').hasMatch(pw);
    final notId = id.isEmpty || !pw.toLowerCase().contains(id.toLowerCase());
    return (len: len, mix: mix, notId: notId, ok: len && mix && notId);
  }

  /// 찾은 아이디를 가려서 보인다: papa -> pa**
  static String maskId(String id) => id.length <= 2 ? id : id.substring(0, 2) + '*' * (id.length - 2);
}

// ───────────────────────── 팝업 ─────────────────────────

sealed class Modal {}

class FriendProfileModal extends Modal {
  FriendProfileModal(this.friendId);
  final String friendId;
}

/// 친구 추가. [tel] = 입력 중인 번호(하이픈 포함), [res] = 번호 검색 결과.
class AddFriendModal extends Modal {
  String tel = '';
  PhoneLookup? res;
}

class NewRoomModal extends Modal {
  final selected = <String>[];
  String name = '';
}

/// 대화상대 초대(대화방 서랍의 [초대하기]).
class InviteModal extends Modal {
  final selected = <String>[];
}

/// 채팅방 나가기 확인.
class LeaveModal extends Modal {}

/// 국가 선택(회원가입·아이디 찾기).
class CountryModal extends Modal {}

/// 아직 글을 안 보낸 새 방(초안). 목록에 나오지 않고 첫 메시지를 보내야 저장된다. [sysText] = 저장될 때 맨 앞에 남는 안내 글.
class Draft {
  Draft(this.room, this.sysText);
  final Room room;
  final String? sysText;
}

/// 지도 보기(모바일에만 있음): 가족이 보낸 경로 공유 카드의 [지도 보기]. 보낸 사람의 차가 경로 위를 움직이는 것을 본다.
/// 서버 모드에서는 차가 보내는 실시간 값(남은 분 · 진행률 · 정체)으로 바뀐다([msgId] 카드의 route.live).
/// 더미 모드는 받은 카드의 값(남은 분·진행률)에서 시작해 [MapState.stepMs]마다 1분씩 줄여 흉내 낸다.
class MapState {
  MapState({
    required this.roomId, required this.who, required this.placeId, required this.from, required this.eta, required this.remain, required this.total,
    this.kind = LocKind.dest, this.time = '', this.traffic, this.msgId, this.dest, this.start, this.pct, this.end,
  });
  final String roomId, who, placeId, from;
  String eta;
  /// 경로 위 정체 구간(경로 공유 카드와 같은 막대에 그림).
  List<TrafficSeg>? traffic;
  /// 서버 모드: 지도에 띄운 카드(실시간 값을 받을 메시지 id) · 장소 · 출발 좌표 · 진행률(0~100) · 끝난 이유.
  final int? msgId;
  final Place? dest;
  final (double, double)? start;
  int? pct;
  RouteEnd? end;
  /// here = 한 번 보낸 현재 위치(그 자리 핀만 보임, 움직이지 않음), dest = 경로 공유(차가 경로를 따라 움직임).
  final LocKind kind;
  /// 현재 위치를 보낸 시각(here 일 때).
  final String time;
  bool get here => kind == LocKind.here;
  int remain;
  final int total;
  double get progress => pct != null ? (pct! / 100).clamp(0.0, 1.0) : total <= 0 ? 1 : (1 - remain / total).clamp(0.0, 1.0);

  /// 시안: 1분을 2초로 흘림.
  static const stepMs = 2000;
}

/// 지도에서 위치 고르기: 가운데 핀 자리([lat]·[lon])와 그 주소, 검색, 장소 이름.
class PickState {
  PickState(this.roomId, this.lat, this.lon);
  final String roomId;
  double lat, lon;
  /// 핀 자리 주소(못 찾으면 빈 글, [addrNote] 에 이유).
  String addr = '';
  String? addrNote;
  bool addrBusy = true;
  String query = '';
  /// 검색 결과. null = 검색 전(목록 안 보임).
  List<PlaceHit>? hits;
  String? searchNote;
  bool searching = false;
  /// 장소 검색으로 고른 곳의 이름. 지도를 직접 옮기면 비워짐.
  String name = '';
  /// 보낼 카드 제목: 장소 이름이 있으면 그 이름, 없으면 주소를 줄여 「분당구 정자일로」까지(사용자가 고치지 않음).
  String get title => name.trim().isNotEmpty ? name.trim() : Address.street(addr);
  /// 검색 결과를 고를 때마다 1씩: 지도가 그 자리로 움직인다.
  int moveSeq = 0;
}

/// 로그인 상태 유지에 저장하는 값(앱을 다시 켜도 로그인된 채로). 서버 모드면 토큰은 따로 저장한다.
class Session {
  const Session(this.id, this.name, this.phone, this.phoneMask);
  final String id, name, phone, phoneMask;
}

/// TokCar 모바일의 모든 상태와 동작. 차량 앱(tokcar/ core/Store.kt)의 TokStore 와 같은 규칙에서 길안내·경로 공유·주행 모드를 뺐다.
/// 화면은 이 객체를 보고 그리며(바뀌면 [notifyListeners]), 동작은 이 클래스의 함수를 부른다.
class TokStore extends ChangeNotifier {
  TokStore({
    required this.server, this.voice, this.location, this.resolver, this.directions, Session? session, this.onSessionChanged,
    this.remote, String? remoteToken, this.onTokenChanged, this.placeSearch, this.pickOnMap = false,
  }) : seed = server.seed {
    signedIn = session != null;
    myName = session?.name ?? seed.myName;
    myId = session?.id ?? 'papa';
    myPhone = session?.phone ?? seed.myPhone;
    phoneMask = session?.phoneMask ?? '';
    myAddr = Address.withoutProvince(seed.myAddr);
    _sub = server.events.listen(_onServerEvent);
    final api = remote;
    if (api != null) {
      // 서버 모드: 더미 방 · 친구 · 대화 없이 시작해서 로그인한 뒤 서버에서 받는다. 앱 안에서만 쓰는 id 는 음수(서버 id 와 안 겹치게).
      _msgSeq = -1000000000;
      rooms = [];
      friends = [];
      api.token = remoteToken;
      _remoteSub = api.events.listen(_onRemote);
      if (session != null) {
        if (remoteToken != null) {
          _restore();
        } else {
          _signOutLocal();
        }
      }
      return;
    }
    rooms = [...seed.rooms];
    friends = [...seed.friends];
    for (final r in seed.rooms) {
      msgs[r.id] = [for (final m in seed.roomMsgs[r.id] ?? const <(String, String)>[]) TextMsg(++_msgSeq, m.$1, m.$2)];
      lastText[r.id] = (seed.roomLast[r.id] ?? '').replaceFirst(_lastRe, '');
    }
    for (var i = 0; i < rooms.length; i++) {
      act[rooms[i].id] = rooms.length - i;
    }
  }

  final FamilyServer server;
  final Seed seed;
  /// 말하기(음성 인식). null 이면 말하기를 쓸 수 없음.
  final VoiceInput? voice;
  /// 폰 GPS. null 이면 더미 위치(seed.json 의 me.addr)를 보냄.
  final LocationProvider? location;
  /// 좌표 → 주소. null 이면 주소 없이 좌표만 보냄.
  final AddressResolver? resolver;
  /// 지도 보기의 도로 경로. null 이면 출발~도착을 이은 곡선으로 그림.
  final RouteResolver? directions;
  final void Function(Session?)? onSessionChanged;
  /// 가족 서버(server). 있으면 로그인 · 친구 · 방 · 대화 · 위치 · 경로 공유를 서버로 주고받는다.
  /// 가입 · 방 만들기 · 친구 추가 같은 2단계 기능은 서버에 아직 없어서 안내만 띄운다.
  final RemoteApi? remote;
  /// 위치 보내기 화면의 장소 · 주소 검색. null 이면 검색 없이 핀만 옮김.
  final PlaceSearch? placeSearch;
  /// [내 위치 보내기]를 지도에서 핀을 옮겨 고르는 화면으로 할지(네이버 지도가 있을 때). 아니면 GPS 자리를 바로 보냄.
  final bool pickOnMap;
  final void Function(String?)? onTokenChanged;
  late final StreamSubscription<ServerEvent> _sub;
  StreamSubscription<RemoteEvent>? _remoteSub;
  static final _lastRe = RegExp(r'^[^:]{1,12}: ');
  static const toastMs = 2200;
  static const roomColors = [0xFFBE185D, 0xFF0E7490, 0xFF4338CA, 0xFFC2410C, 0xFF0F766E, 0xFF5B3FD0, 0xFF92600A];

  // ───── 상태 ─────
  late bool signedIn;
  LoginState login = LoginState();
  late String myName, myId, myPhone, phoneMask, myAddr;
  HomeTab tab = HomeTab.chat;
  bool inChat = false;
  String? roomId;
  Modal? modal;
  /// 대화방 오른쪽 위 메뉴의 대화상대 서랍.
  bool drawer = false;
  bool plusOpen = false;
  bool listening = false;
  /// 내 위치를 재는 중(+ 메뉴의 [내 위치 보내기]를 누른 뒤 카드가 올라갈 때까지).
  bool locating = false;
  String? toast;
  MapState? map;
  /// 지도에서 위치 고르기([내 위치 보내기]). null 이면 닫힘.
  PickState? pick;
  int _pickTok = 0;

  late List<Room> rooms;
  final drafts = <String, Draft>{};
  late List<Friend> friends;
  final msgs = <String, List<Msg>>{};
  final lastText = <String, String>{};
  final readRooms = <String>{};
  final newIn = <String, int>{};
  final at = <String, String>{};
  /// 방마다 입력 중인 글.
  final draft = <String, String>{};
  /// 최근 대화 순서(클수록 위).
  final act = <String, int>{};
  int _actN = 100;

  int _msgSeq = 1000;
  bool _micCancelled = false;
  int _roomSeq = 0;
  Timer? _toastTimer, _mapTimer;
  bool _disposed = false;

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  // ───── 읽기 도우미 ─────

  /// 저장된 방과 초안 방 모두에서 찾는다.
  Room? room(String? id) {
    for (final r in rooms) {
      if (r.id == id) return r;
    }
    return drafts[id]?.room;
  }

  bool isDraft(String? id) => id != null && drafts.containsKey(id);

  Friend? friend(String id) {
    for (final f in friends) {
      if (f.id == id) return f;
    }
    return null;
  }

  Place? place(String? id) => id == null ? null : seed.places[id];

  // ───── 가족 서버 상태(서버 모드) ─────
  int _meId = 0;
  final _names = <int, String>{};
  /// 방(서버 id) → 나를 뺀 멤버 id.
  final _roomOthers = <int, List<int>>{};
  /// 방 → 사람마다 읽은 곳.
  final _readUpTo = <int, Map<int, int>>{};
  /// 방 → 받은 마지막 메시지 id(다시 연결했을 때 빠진 것 채우기).
  final _lastSeen = <int, int>{};
  final _readSent = <int, int>{};
  int _refSeq = 0;

  int unreadOf(Room r) => (readRooms.contains(r.id) ? 0 : r.unread) + (newIn[r.id] ?? 0);
  int get totalUnread => rooms.fold(0, (s, r) => s + unreadOf(r));
  String timeOf(Room r) => at[r.id] ?? r.time;
  String lastOf(Room r) {
    final t = lastText[r.id] ?? '';
    return t.isEmpty ? '대화를 시작해 보세요' : t;
  }

  int memberColor(String name) {
    for (final f in friends) {
      if (f.name == name) return f.color;
    }
    return 0xFF8B8F98;
  }

  /// 최근 대화가 있는 방이 위.
  List<Room> get orderedRooms {
    final idx = {for (var i = 0; i < rooms.length; i++) rooms[i].id: i};
    return [...rooms]..sort((a, b) {
        final c = (act[b.id] ?? 0).compareTo(act[a.id] ?? 0);
        return c != 0 ? c : idx[a.id]!.compareTo(idx[b.id]!);
      });
  }

  // ═══════════════ 대화 ═══════════════
  int _nextId() => ++_msgSeq;

  void _bump(String roomId) {
    _actN++;
    act[roomId] = _actN;
  }

  /// 초안 방 저장: 첫 메시지(글·위치)를 보내는 순간 목록에 들어가고, 만든 안내 글이 맨 앞에 남는다.
  void _commitDraft(String roomId) {
    final d = drafts.remove(roomId);
    if (d == null) return;
    rooms.insert(0, d.room);
    msgs[roomId] = [if (d.sysText != null) SysMsg(_nextId(), d.sysText!), ...?msgs[roomId]];
  }

  void _leaveDraft(String roomId) {
    if (!isDraft(roomId)) return;
    drafts.remove(roomId);
    msgs.remove(roomId);
    draft.remove(roomId);
  }

  void _addMsg(String roomId, Msg m) {
    _commitDraft(roomId);
    (msgs[roomId] ??= []).add(m);
    lastText[roomId] = describeMsg(m);
    at[roomId] = '방금';
    _bump(roomId);
  }

  void openRoom(String id) {
    if (room(id) == null) return;
    roomId = id;
    inChat = true;
    readRooms.add(id);
    newIn[id] = 0;
    modal = null;
    plusOpen = false;
    drawer = false;
    _markRead(id);
    _changed();
  }

  /// 대화방 뒤로: 글을 안 보낸 새 방은 버린다.
  void back() {
    final id = roomId;
    if (id != null) _leaveDraft(id);
    inChat = false;
    modal = null;
    plusOpen = false;
    drawer = false;
    _changed();
  }

  /// 입력칸의 글을 기억한다(화면을 다시 그릴 필요는 없음: 전송 단추가 보이는지는 입력칸이 스스로 정함).
  void setDraft(String roomId, String text) => draft[roomId] = text;

  void _sendMine(Room r, MineMsg Function(int id, int total) build) {
    if (remote != null) return; // 서버 모드에서는 ack 가 오면 넣는다
    final m = build(_nextId(), r.members.length);
    _addMsg(r.id, m);
    server.messageSent(r.id, m.id, r.members);
  }

  void send(String roomId, String text) {
    final t = text.trim();
    final r = room(roomId);
    if (r == null || t.isEmpty) return;
    if (remote != null) {
      final rid = _serverRoomId(r);
      if (rid == null) return;
      draft[roomId] = '';
      _sendFrame({'type': 'text.send', 'ref': _nextRef(), 'roomId': rid, 'text': t});
      _changed();
      return;
    }
    draft[roomId] = '';
    _sendMine(r, (id, n) => MyMsg(id, t, n));
    _changed();
  }

  /// + 메뉴 열기·닫기.
  void togglePlus() {
    plusOpen = !plusOpen;
    _changed();
  }

  void closePlus() {
    plusOpen = false;
    _changed();
  }

  /// + 메뉴의 [내 위치 보내기]: 폰 GPS로 위치를 재고 주소로 바꾼다.
  /// [pickOnMap] 이면 그 자리에서 시작하는 지도 화면([pick])을 열어 핀을 옮기거나 검색해서 고른 뒤 보내고, 아니면 바로 보낸다.
  /// 위치를 못 얻으면 이유를 토스트로 알리고 보내지 않는다. 주소만 못 찾으면 좌표만 담아 보낸다.
  Future<void> sendMyLocation() async {
    final r = room(roomId);
    if (r == null || !inChat || locating) return;
    plusOpen = false;
    final provider = location;
    if (remote != null && _serverRoomId(r) == null) return _changed();
    if (provider == null) {
      if (remote != null) return showToast('이 폰에서는 위치를 쓸 수 없어요');
      _sendMine(r, (id, n) => MyLocMsg(id, myAddr, clockText(DateTime.now()), n));
      _changed();
      return;
    }
    locating = true;
    showToast('내 위치를 확인하고 있어요…');
    LocationFix fix;
    try {
      fix = await provider.current();
    } on LocationException catch (e) {
      locating = false;
      return showToast(e.reason);
    } catch (e) {
      locating = false;
      return showToast('위치를 가져오지 못했어요');
    }
    var addr = '';
    String? addrNote;
    try {
      final found = await resolver?.addressOf(fix.lat, fix.lon);
      if (found != null) {
        addr = Address.withoutProvince(found);
      } else {
        addrNote = '주소를 찾지 못해 좌표만 보냈어요';
      }
    } on AddressException catch (e) {
      addrNote = '${e.reason} · 좌표만 보냈어요';
    } catch (e) {
      addrNote = '주소를 찾지 못해 좌표만 보냈어요';
    }
    locating = false;
    if (room(r.id) == null) return; // 위치를 재는 사이에 방을 나갔으면 보내지 않음
    if (pickOnMap) {
      // 지도에서 핀을 옮겨 고르는 화면으로(GPS 자리에서 시작)
      _toastTimer?.cancel();
      toast = null;
      pick = PickState(r.id, fix.lat, fix.lon)
        ..addr = addr
        ..addrBusy = false
        ..addrNote = addrNote;
      return _changed();
    }
    if (remote != null) {
      final where = addr.isEmpty ? '${fix.lat.toStringAsFixed(5)}, ${fix.lon.toStringAsFixed(5)}' : addr;
      final name = addr.isEmpty ? '' : Address.area(addr);
      if (!_sendFrame({'type': 'location.send', 'ref': _nextRef(), 'roomId': int.parse(r.id), 'location': SPlace(name, where, fix.lat, fix.lon).toJson()})) {
        return _changed();
      }
    }
    _sendMine(r, (id, n) => MyLocMsg(id, addr, clockText(DateTime.now()), n, lat: fix.lat, lon: fix.lon));
    if (resolver == null) addrNote = null;
    if (addrNote != null) {
      showToast(addrNote);
    } else {
      _toastTimer?.cancel();
      toast = null;
    }
    _changed();
  }

  // ───── 지도에서 위치 고르기 ─────

  /// 지도를 움직이다 멈췄다: 가운데 좌표의 주소를 다시 찾는다(마지막으로 멈춘 자리 것만 반영).
  Future<void> pickMoved(double lat, double lon) async {
    final p = pick;
    if (p == null) return;
    // 검색 결과로 옮긴 뒤 멈춘 것(같은 자리)이면 그대로: 고른 장소 이름을 지키려고
    if ((p.lat - lat).abs() < 1e-5 && (p.lon - lon).abs() < 1e-5) return;
    p
      ..lat = lat
      ..lon = lon
      ..name = '' // 직접 옮긴 자리는 장소 이름이 없음 → 줄인 주소가 제목
      ..addrBusy = true
      ..addrNote = null;
    final tok = ++_pickTok;
    _changed();
    String addr = '';
    String? note;
    try {
      final found = await resolver?.addressOf(lat, lon);
      if (found != null) {
        addr = Address.withoutProvince(found);
      } else {
        note = '이 자리의 주소를 찾지 못했어요';
      }
    } on AddressException catch (e) {
      note = e.reason;
    } catch (_) {
      note = '주소를 찾지 못했어요';
    }
    if (tok != _pickTok || pick != p) return;
    p
      ..addr = addr
      ..addrNote = note
      ..addrBusy = false;
    _changed();
  }

  void setPickQuery(String text) {
    final p = pick;
    if (p == null) return;
    p.query = text;
    if (text.trim().isEmpty) {
      p
        ..hits = null
        ..searchNote = null;
      _changed();
    }
  }

  /// 검색: 장소명(API HUB 지역 검색) + 주소(Geocoding). 핀과 가까운 순.
  Future<void> pickSearch() async {
    final p = pick;
    final search = placeSearch;
    if (p == null || p.query.trim().isEmpty || p.searching) return;
    if (search == null) {
      p.searchNote = '검색 키가 없어요(env.json)';
      return _changed();
    }
    p
      ..searching = true
      ..searchNote = null;
    _changed();
    try {
      final hits = await search.search(p.query, lat: p.lat, lon: p.lon);
      if (pick != p) return;
      p
        ..hits = hits
        ..searchNote = hits.isEmpty ? '검색 결과가 없어요' : null;
    } on PlaceSearchException catch (e) {
      if (pick != p) return;
      p
        ..hits = const []
        ..searchNote = e.reason;
    } catch (_) {
      if (pick != p) return;
      p
        ..hits = const []
        ..searchNote = '검색하지 못했어요';
    }
    p.searching = false;
    _changed();
  }

  /// 검색 결과를 골랐다: 지도가 그곳으로 가고([PickState.moveSeq]), 장소 이름이 있으면 이름 칸에 넣는다.
  void pickHit(PlaceHit h) {
    final p = pick;
    if (p == null) return;
    final tok = ++_pickTok; // 이동 중에 끝나는 이전 주소 찾기는 버림
    // 검색 결과 주소는 도로명이라, 핀 주소(지번)와 맞추려고 그 좌표의 지번을 다시 찾는다. 찾는 동안은 검색 결과 주소를 보이고, 못 찾으면 그대로 둔다.
    p
      ..lat = h.lat
      ..lon = h.lon
      ..addr = Address.withoutProvince(h.addr)
      ..addrBusy = resolver != null
      ..addrNote = null
      ..hits = null
      ..searchNote = null
      ..moveSeq = p.moveSeq + 1;
    if (h.name.isNotEmpty) p.name = h.name.length > 30 ? h.name.substring(0, 30) : h.name;
    _changed();
    final r = resolver;
    if (r == null) return;
    () async {
      String? found;
      try {
        found = await r.addressOf(h.lat, h.lon);
      } catch (_) {}
      if (tok != _pickTok || pick != p) return;
      if (found != null && found.isNotEmpty) p.addr = Address.withoutProvince(found);
      p.addrBusy = false;
      _changed();
    }();
  }

  void closePick() {
    pick = null;
    _pickTok++;
    _changed();
  }

  /// [이 위치 보내기]: 고른 자리(이름을 넣었으면 그 이름)로 위치 카드를 보낸다.
  void sendPicked() {
    final p = pick;
    if (p == null || p.addrBusy) return;
    final r = room(p.roomId);
    if (r == null) return closePick();
    final name = p.title;
    final addr = p.addr;
    if (remote != null) {
      final rid = _serverRoomId(r);
      if (rid == null) return;
      final where = addr.isEmpty ? '${p.lat.toStringAsFixed(5)}, ${p.lon.toStringAsFixed(5)}' : addr;
      final title = name;
      if (!_sendFrame({'type': 'location.send', 'ref': _nextRef(), 'roomId': rid, 'location': SPlace(title, where, p.lat, p.lon).toJson()})) {
        return _changed();
      }
    } else {
      _sendMine(r, (id, n) => MyLocMsg(id, addr, clockText(DateTime.now()), n, lat: p.lat, lon: p.lon, title: name));
    }
    pick = null;
    _changed();
  }

  /// [말하기]: 알아들은 말은 바로 내 메시지로 보낸다. 듣는 중에 다시 누르면 멈춘다.
  Future<void> mic(String roomId) async {
    final v = voice;
    if (v == null) return showToast('이 폰에서는 말하기를 쓸 수 없어요');
    if (listening) {
      _micCancelled = true; // 다시 누르면 멈춤: 들은 말은 버리고 안내도 띄우지 않음
      v.cancel();
      return;
    }
    _micCancelled = false;
    listening = true;
    plusOpen = false;
    _changed();
    String? text;
    String? failure;
    try {
      text = await v.listen();
    } on VoiceException catch (e) {
      failure = e.reason;
    } catch (e) {
      failure = '말하기를 쓸 수 없어요';
    } finally {
      listening = false;
      _changed();
    }
    if (_micCancelled) {
      return;
    } else if (failure != null) {
      showToast(failure);
    } else if (text == null || text.trim().isEmpty) {
      showToast('잘 못 들었어요. 다시 말해 주세요');
    } else {
      send(roomId, text);
    }
  }

  void _onServerEvent(ServerEvent e) {
    if (remote != null) return; // 서버 모드에서는 더미 서버의 흉내(읽음 · 가족 메시지)를 쓰지 않는다
    switch (e) {
      case ReadEvent():
        for (final m in msgs[e.roomId] ?? const <Msg>[]) {
          if (m is MineMsg && m.id == e.msgId) m.readBy.add(e.who);
        }
        _changed();
      case LocationEvent():
        _receive(e.spec);
      case MessageEvent():
        _receiveText(e);
    }
  }

  bool _known(String roomId) => signedIn && rooms.any((r) => r.id == roomId);

  /// 나간 방(목록에 없는 방)으로 오는 것은 무시한다.
  void _receiveText(MessageEvent e) {
    if (!_known(e.roomId)) return;
    final inRoom = inChat && roomId == e.roomId;
    _addMsg(e.roomId, TextMsg(_nextId(), e.who, e.text));
    if (!inRoom) newIn[e.roomId] = (newIn[e.roomId] ?? 0) + 1;
    _changed();
  }

  void _receive(IncomingSpec s) {
    if (!_known(s.roomId)) return;
    final inRoom = inChat && roomId == s.roomId;
    _addMsg(
      s.roomId,
      LocMsg(_nextId(), who: s.who, kind: s.kind, placeId: s.placeId, from: s.from, min: s.min, pct: s.pct, eta: s.eta, time: clockText(DateTime.now()), traffic: s.traffic),
    );
    if (!inRoom) newIn[s.roomId] = (newIn[s.roomId] ?? 0) + 1;
    _changed();
  }

  /// (개발용) 가족 폰에서 위치·글을 보낸 것처럼 만든다.
  void simulateIncoming(String key) => server.simulateIncoming(key);

  // ═══════════════ 지도 보기 ═══════════════

  /// 경로 공유의 출발 좌표. 카드에는 출발 주소 글만 있어서, 같은 주소의 장소가 있으면 그 좌표를 쓰고 없으면 도착지 근처 더미 좌표로 둔다.
  (double, double) routeStart(MapState mp) {
    if (mp.start != null) return mp.start!;
    final dest = mp.dest ?? place(mp.placeId);
    final from = Address.withoutProvince(mp.from);
    for (final p in seed.places.values) {
      if (p.id != mp.placeId && Address.withoutProvince(p.addr) == from) return (p.lat, p.lon);
    }
    return dest == null ? (37.5665, 126.9780) : (dest.lat + .035, dest.lon - .03);
  }

  /// 가족이 보낸 위치 카드의 [지도 보기]. 현재 위치는 그 자리를 보이고, 경로 공유는 차가 움직이는 것을 보인다.
  void openMap(String roomId, LocMsg m) {
    if (m.kind == LocKind.dest && m.end != null) return showToast('경로 공유가 끝났어요');
    if (m.kind == LocKind.here) {
      _mapTimer?.cancel();
      map = MapState(roomId: roomId, who: m.who, placeId: m.placeId, from: '', eta: '', remain: 0, total: 0, kind: LocKind.here, time: m.time, dest: m.place);
      plusOpen = false;
      _changed();
      return;
    }
    final pct = m.pct.clamp(0, 99);
    final total = [m.min + 1, (m.min / (1 - pct / 100).clamp(0.05, 1.0)).round()].reduce((a, b) => a > b ? a : b);
    if (remote != null) {
      // 서버 모드: 흉내 없이 차가 보내는 값으로만 바뀐다(_onRemote 의 RemoteLive)
      _mapTimer?.cancel();
      map = MapState(
        roomId: roomId, who: m.who, placeId: m.placeId, from: m.from, eta: m.eta, remain: m.min, total: total, traffic: m.traffic,
        msgId: m.id, dest: m.place, start: m.start, pct: m.end == RouteEnd.arrived ? 100 : m.pct, end: m.end,
      );
      plusOpen = false;
      _changed();
      return;
    }
    map = MapState(roomId: roomId, who: m.who, placeId: m.placeId, from: m.from, eta: m.eta, remain: m.min, total: total, traffic: m.traffic);
    plusOpen = false;
    _mapTimer?.cancel();
    _mapTimer = Timer.periodic(const Duration(milliseconds: MapState.stepMs), (t) {
      final mp = map;
      if (mp == null) return t.cancel();
      mp.remain = mp.remain > 0 ? mp.remain - 1 : 0;
      if (mp.remain <= 0) t.cancel();
      _changed();
    });
    _changed();
  }

  void closeMap() {
    _mapTimer?.cancel();
    map = null;
    _changed();
  }

  // ═══════════════ 친구·새 방·대화상대 ═══════════════
  void openTab(HomeTab t) {
    tab = t;
    modal = null;
    plusOpen = false;
    _changed();
  }

  void closeModal() {
    modal = null;
    _changed();
  }

  void openFriend(String id) {
    modal = FriendProfileModal(id);
    _changed();
  }

  /// 친구 프로필의 [1:1 채팅]. 1:1 방도 첫 메시지를 보내야 목록에 저장된다.
  void openDm(String friendId) {
    if (remote != null) {
      _remoteDm(friendId);
      return;
    }
    final f = friend(friendId);
    if (f == null) return;
    final rid = 'dm-${f.id}';
    if (room(rid) == null) {
      drafts[rid] = Draft(Room(id: rid, name: f.name, color: f.color, members: [f.name], unread: 0, time: '방금'), null);
      msgs[rid] = [];
    }
    openRoom(rid);
  }

  void openNewRoom() {
    modal = NewRoomModal();
    _changed();
  }

  void _toggle(List<String> list, String id) => list.contains(id) ? list.remove(id) : list.add(id);

  void toggleNewMember(String friendId) {
    final m = modal;
    if (m is! NewRoomModal) return;
    _toggle(m.selected, friendId);
    _changed();
  }

  void setNewRoomName(String text) {
    final m = modal;
    if (m is! NewRoomModal) return;
    m.name = text.length > 20 ? text.substring(0, 20) : text;
    _changed();
  }

  /// [방 만들기]: 목록에는 아직 넣지 않는다(첫 메시지를 보내야 저장).
  void createRoom() {
    final m = modal;
    if (m is! NewRoomModal || m.selected.isEmpty) return;
    if (remote != null) {
      _remoteCreateRoom(m.name.trim(), m.selected);
      return;
    }
    final names = [for (final id in m.selected) ?friend(id)?.name];
    final name = m.name.trim().isEmpty ? autoName(names) : m.name.trim();
    final id = 'room-${++_roomSeq}';
    final color = roomColors[(rooms.length + drafts.length) % roomColors.length];
    final r = Room(id: id, name: name, color: color, members: names, unread: 0, time: '방금');
    drafts[id] = Draft(r, '${Josa.ga(myName)} 방을 만들었어요 · ${names.join(', ')} 초대');
    msgs[id] = [];
    modal = null;
    openRoom(id);
  }

  void openDrawer() {
    drawer = true;
    plusOpen = false;
    _changed();
  }

  void closeDrawer() {
    drawer = false;
    _changed();
  }

  void openInvite() {
    drawer = false;
    modal = InviteModal();
    _changed();
  }

  void toggleInvite(String friendId) {
    final m = modal;
    if (m is! InviteModal) return;
    _toggle(m.selected, friendId);
    _changed();
  }

  /// [초대하기]: 고른 친구를 방에 넣는다. 저장된 방이면 안내 글을 남기고, 초안 방이면 인원만 바꾼다.
  void inviteDone() {
    final m = modal;
    final rid = roomId;
    if (m is! InviteModal || rid == null) return;
    if (remote != null) {
      _remoteInvite(rid, m.selected);
      return;
    }
    final names = [for (final id in m.selected) ?friend(id)?.name];
    if (names.isEmpty) return;
    final r = room(rid);
    if (r == null) return;
    r.members = [...r.members, ...names];
    modal = null;
    if (!isDraft(rid)) _addMsg(rid, SysMsg(_nextId(), '${Josa.ga(myName)} 대화상대를 초대했어요 · ${names.join(', ')}'));
    showToast('${names.length}명을 초대했어요');
  }

  void askLeave() {
    drawer = false;
    modal = LeaveModal();
    _changed();
  }

  /// [나가기]: 방이 목록에서 사라진다.
  void leaveRoom() {
    final id = roomId;
    if (id == null) return;
    if (remote != null) {
      _remoteLeave(id);
      return;
    }
    rooms.removeWhere((r) => r.id == id);
    drafts.remove(id);
    if (map?.roomId == id) closeMap();
    modal = null;
    drawer = false;
    inChat = false;
    roomId = null;
    showToast('채팅방을 나갔어요');
  }

  // ───── 친구 추가 ─────
  void openAddFriend() {
    modal = AddFriendModal();
    _changed();
  }

  /// 번호 입력: 하이픈을 자동으로 넣고, 번호를 고치면 이전 검색 결과는 지운다.
  void setAddTel(String text) {
    final m = modal;
    if (m is! AddFriendModal) return;
    final t = formatTyping(Countries.of('KR'), text);
    m.tel = t.length > 13 ? t.substring(0, 13) : t;
    m.res = null;
    _changed();
  }

  void findFriend() {
    if (_notYet()) return;
    final m = modal;
    if (m is! AddFriendModal) return;
    final d = Phone.digits(m.tel);
    if (!Phone.isValid(d)) return;
    m.res = server.lookup(d, Phone.digits(myPhone), friends);
    _changed();
  }

  void addFoundFriend() {
    final m = modal;
    if (m is! AddFriendModal) return;
    final res = m.res;
    if (res is! LookupFound) return;
    final f = res.friend;
    friends = [...friends, Friend(id: f.id, tel: res.tel, name: f.name, ini: f.ini, color: f.color, status: f.status)];
    modal = null;
    showToast('${Josa.eul(f.name)} 친구로 추가했어요');
  }

  void inviteSms() {
    modal = null;
    showToast('앱 설치 링크가 담긴 초대 문자를 보냈어요 (더미)');
  }

  // ═══════════════ 회원가입·로그인 ═══════════════
  void startLogin(LoginMode mode) {
    if (mode != LoginMode.login && _notYet()) return;
    final old = login;
    login = switch (mode) {
      LoginMode.login => LoginState(mode: mode, step: LoginStep.idpw, left: FamilyServer.lockN),
      LoginMode.signup || LoginMode.findid => LoginState(mode: mode, step: LoginStep.phone),
      LoginMode.resetpw => LoginState(mode: mode, step: LoginStep.rid, rid: old.id.isEmpty ? old.rid : old.id),
    };
    _changed();
  }

  LoginState _toIdpw() => LoginState(mode: LoginMode.login, step: LoginStep.idpw, left: FamilyServer.lockN);

  void loginBack() {
    final l = login;
    l.err = null;
    switch (l.step) {
      case LoginStep.idpw:
        login = LoginState();
      case LoginStep.phone:
        login = l.mode == LoginMode.findid ? _toIdpw() : LoginState();
      case LoginStep.rid || LoginStep.fdone:
        login = _toIdpw();
      case LoginStep.code:
        if (l.mode == LoginMode.resetpw) {
          l.step = LoginStep.rid;
        } else {
          l.step = LoginStep.phone;
          l.code = '';
        }
      case LoginStep.account || LoginStep.newpw:
        l.step = LoginStep.code;
      case LoginStep.profile:
        l.step = LoginStep.account;
      case LoginStep.terms:
        l.step = LoginStep.profile;
      case LoginStep.welcome:
        break;
    }
    _changed();
  }

  void openCountry() {
    modal = CountryModal();
    _changed();
  }

  void pickCountry(String code) {
    login.country = code;
    login.phone = formatTyping(Countries.of(code), login.phone);
    login.err = null;
    modal = null;
    _changed();
  }

  String _cut(String s, int n) => s.length > n ? s.substring(0, n) : s;

  /// 번호 입력: 나라별 모양으로 하이픈·띄어쓰기를 자동으로 넣는다.
  void setLoginPhone(String text) {
    login.phone = _cut(formatTyping(Countries.of(login.country), text), 18);
    login.err = null;
    _changed();
  }

  void setLoginCode(String text) {
    login.code = _cut(digitsOf(text), 6);
    _changed();
  }

  void setLoginName(String text) {
    login.name = _cut(text, 12);
    _changed();
  }

  /// 로그인 아이디 입력. 잠긴 아이디면 바로 잠김 안내가 보이게 남은 횟수를 같이 읽는다.
  void setLoginId(String text) {
    login.id = _cut(text, 16);
    login.err = null;
    login.left = remote != null ? FamilyServer.lockN : server.triesLeft(login.id.trim().toLowerCase());
    _changed();
  }

  /// 회원가입 아이디 입력(영문 소문자로 바꿈).
  void setNewId(String text) {
    login.id = _cut(text.toLowerCase().replaceAll(RegExp(r'[^\x21-\x7E]'), ''), 16);
    _changed();
  }

  void setRid(String text) {
    login.rid = _cut(text, 16);
    login.err = null;
    _changed();
  }

  void setPw(String text) {
    login.pw = _cut(text, 20);
    login.err = null;
    _changed();
  }

  void setPw2(String text) {
    login.pw2 = _cut(text, 20);
    _changed();
  }

  void toggleShowPw() {
    login.showPw = !login.showPw;
    _changed();
  }

  String _loginKey() => PhoneIntl.e164(Countries.of(login.country), login.phone);

  /// [인증번호 받기]: 회원가입은 이미 가입된 번호, 아이디 찾기는 가입 안 된 번호면 안내를 띄운다.
  void sendCode() {
    final l = login;
    if (!PhoneIntl.isValid(Countries.of(l.country), l.phone) || l.busy) return;
    final key = _loginKey();
    final known = server.registeredName(key) != null;
    if (l.mode == LoginMode.signup && known) {
      l.err = LoginErr.known;
      return _changed();
    }
    if (l.mode == LoginMode.findid && !known) {
      l.err = LoginErr.unknown;
      return _changed();
    }
    _requestCode(key);
  }

  Future<void> _requestCode(String key) async {
    final l = login;
    l.busy = true;
    _changed();
    await server.sendCode(key);
    l
      ..step = LoginStep.code
      ..code = ''
      ..err = null
      ..busy = false;
    _changed();
  }

  Future<void> verify() async {
    final l = login;
    if (!RegExp(r'^\d{6}$').hasMatch(l.code) || l.busy) return;
    final key = _loginKey();
    l.busy = true;
    _changed();
    final ok = await server.verify(key, l.code);
    l.busy = false;
    if (!ok) return showToast('인증번호가 맞지 않아요');
    switch (l.mode) {
      case LoginMode.signup:
        l
          ..step = LoginStep.account
          ..id = ''
          ..idCheck = null
          ..pw = ''
          ..pw2 = '';
      case LoginMode.resetpw:
        l
          ..step = LoginStep.newpw
          ..pw = ''
          ..pw2 = '';
      case LoginMode.findid:
        l
          ..step = LoginStep.fdone
          ..found = server.findIds(key);
      default:
        break;
    }
    _changed();
  }

  void resendCode() {
    server.sendCode(_loginKey());
    showToast('인증번호를 다시 보냈어요');
  }

  void editPhone() {
    login
      ..step = LoginStep.phone
      ..code = '';
    _changed();
  }

  /// [중복 확인].
  void checkId() {
    final id = login.id;
    if (!Rules.id.hasMatch(id)) return;
    login.idCheck = (id, !server.idTaken(id));
    _changed();
  }

  bool get idOk {
    final c = login.idCheck;
    return c != null && c.$1 == login.id && c.$2;
  }

  /// 계정 만들기 [다음].
  void accountNext() {
    final l = login;
    if (!(idOk && Rules.pw(l.pw, l.id).ok && l.pw == l.pw2)) return;
    l.step = LoginStep.profile;
    _changed();
  }

  /// [로그인]: 연속 5번 틀리면 잠긴다.
  void doLogin() {
    final l = login;
    final id = l.id.trim().toLowerCase();
    if (id.isEmpty || l.pw.isEmpty) return;
    if (remote != null) {
      _remoteLogin(id, l.pw);
      return;
    }
    switch (server.login(id, l.pw)) {
      case LoginOk(:final account):
        _finishAuth(account, joined: false);
      case LoginBad(:final left):
        l
          ..pw = ''
          ..err = LoginErr.bad
          ..left = left;
        _changed();
      case LoginLocked():
        l
          ..pw = ''
          ..err = LoginErr.bad
          ..left = 0;
        _changed();
    }
  }

  /// 비밀번호 찾기: 아이디 -> 가입한 번호로 인증번호.
  void resetSend() {
    final id = login.rid.trim().toLowerCase();
    if (id.isEmpty) return;
    final a = server.account(id);
    if (a == null) {
      login.err = LoginErr.noid;
      return _changed();
    }
    login
      ..rid = id
      ..country = a.country
      ..phone = formatTyping(Countries.of(a.country), a.phone)
      ..err = null;
    _requestCode(a.key);
  }

  /// 새 비밀번호 [비밀번호 바꾸기]: 잠금도 풀리고 로그인 화면으로.
  void newPwNext() {
    final l = login;
    if (!(Rules.pw(l.pw, l.rid).ok && l.pw == l.pw2)) return;
    server.resetPassword(l.rid, l.pw);
    login = LoginState(mode: LoginMode.login, step: LoginStep.idpw, id: l.rid, left: FamilyServer.lockN);
    showToast('비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요');
  }

  void profileNext() {
    if (login.name.trim().isEmpty) return;
    login.step = LoginStep.terms;
    _changed();
  }

  void toggleAgree(String key) {
    final a = login.agree;
    switch (key) {
      case 'tos':
        a.tos = !a.tos;
      case 'priv':
        a.priv = !a.priv;
      case 'loc':
        a.loc = !a.loc;
      default:
        a.mkt = !a.mkt;
    }
    _changed();
  }

  void agreeAll() {
    final a = login.agree;
    final v = !a.all;
    a
      ..tos = v
      ..priv = v
      ..loc = v
      ..mkt = v;
    _changed();
  }

  /// [가입하기]: 필수 약관 3개에 동의해야 한다.
  void join() {
    final l = login;
    if (!l.agree.required || l.name.trim().isEmpty) return;
    final c = Countries.of(l.country);
    final a = Account(l.id, l.pw, _loginKey(), c.code, digitsOf(l.phone), l.name.trim());
    server.createAccount(a);
    _finishAuth(a, joined: true);
  }

  void _finishAuth(Account a, {required bool joined}) {
    final c = Countries.of(a.country);
    final phone = c.code == 'KR' ? '0${PhoneIntl.national(c, a.phone)}' : digitsOf(a.key);
    final s = Session(a.id, a.name, phone, PhoneIntl.mask(c, a.phone));
    myName = s.name;
    myId = s.id;
    myPhone = s.phone;
    phoneMask = s.phoneMask;
    signedIn = true;
    tab = HomeTab.chat;
    login = LoginState();
    onSessionChanged?.call(s);
    showToast(a.name + (joined ? '님, 가입을 환영해요' : '님, 연결됐어요'));
  }

  void logout() {
    closeMap();
    final api = remote;
    if (api != null) {
      api.logout();
      _signOutLocal();
      return;
    }
    signedIn = false;
    login = LoginState();
    inChat = false;
    modal = null;
    plusOpen = false;
    drawer = false;
    onSessionChanged?.call(null);
    _changed();
  }

  void privacy() => showToast('개인정보 처리방침은 다음 단계에서 다뤄요');

  // ═══════════════ 가족 서버(서버 모드) ═══════════════

  /// 2단계 기능(가입 · 방 만들기 · 친구 추가 …)은 서버 모드에서 아직 막는다. 막았으면 true.
  bool _notYet() {
    if (remote == null) return false;
    modal = null;
    showToast('이 기능은 다음 단계에서 서버에 붙어요');
    return true;
  }

  String _nextRef() => 'c-${++_refSeq}';

  int? _serverRoomId(Room r) {
    final id = int.tryParse(r.id);
    if (id == null) showToast('이 기능은 다음 단계에서 서버에 붙어요');
    return id;
  }

  bool _sendFrame(Map<String, dynamic> frame) {
    final ok = remote?.send(frame) ?? false;
    if (!ok) showToast('서버에 연결되지 않았어요. 잠시 뒤 다시 해 주세요');
    return ok;
  }

  int _colorOf(int id) => roomColors[id % roomColors.length];

  Place _placeOf(SPlace p) => Place(id: 'ext:${p.lat},${p.lon}', name: p.name.isEmpty ? p.addr : p.name, addr: p.addr, lat: p.lat, lon: p.lon);

  Future<void> _remoteLogin(String id, String pw) async {
    final api = remote!;
    final l = login;
    if (l.busy) return;
    l.busy = true;
    _changed();
    final r = await api.login(id, pw);
    l.busy = false;
    switch (r) {
      case RemoteLoginOk(:final token, :final me):
        api.token = token;
        onTokenChanged?.call(token);
        final s = Session(id, me.name, '', '');
        myName = s.name;
        myId = s.id;
        myPhone = '';
        phoneMask = '';
        signedIn = true;
        tab = HomeTab.chat;
        login = LoginState();
        onSessionChanged?.call(s);
        showToast('${me.name}님, 연결됐어요');
        await _afterAuth(me);
      case RemoteLoginBad(:final left):
        l
          ..pw = ''
          ..err = LoginErr.bad
          ..left = left ?? FamilyServer.lockN;
        _changed();
      case RemoteLoginLocked():
        l
          ..pw = ''
          ..err = LoginErr.bad
          ..left = 0;
        _changed();
      case RemoteLoginFailed(:final reason):
        showToast(reason);
    }
  }

  /// 앱을 다시 켰을 때: 저장된 토큰으로 이어 붙는다. 서버가 모르는 토큰이면 로그인 화면으로, 못 붙으면 5초마다 다시.
  Future<void> _restore() async {
    final api = remote!;
    while (!_disposed && signedIn) {
      try {
        await _afterAuth(await api.me());
        return;
      } on RemoteUnauthorized {
        _signOutLocal();
        showToast('다시 로그인해 주세요');
        return;
      } catch (e) {
        toast = '서버에 연결하지 못했어요. 다시 시도하는 중…';
        _changed();
        await Future<void>.delayed(const Duration(seconds: 5));
      }
    }
  }

  Future<void> _afterAuth(SUser me) async {
    final api = remote!;
    _meId = me.id;
    _names[me.id] = me.name;
    try {
      await _loadAll();
    } on RemoteUnauthorized {
      _signOutLocal();
      return showToast('다시 로그인해 주세요');
    } catch (e) {
      showToast('서버에서 대화를 받아 오지 못했어요');
    }
    if (signedIn && !_disposed) api.connect();
  }

  /// 친구 · 방 · 방마다 최근 대화를 받아 화면 상태로.
  Future<void> _loadAll() async {
    final api = remote!;
    final fs = await api.friends();
    final rs = await api.rooms();
    for (final f in fs) {
      _names[f.id] = f.name;
    }
    for (final r in rs) {
      for (final m in r.members) {
        _names[m.id] = m.name;
      }
    }
    final loaded = <String, List<Msg>>{};
    for (final r in rs) {
      _remember(r);
      final list = await api.messages(r.id);
      _lastSeen[r.id] = list.fold(0, (a, m) => m.id > a ? m.id : a);
      loaded['${r.id}'] = [for (final m in list) _toMsg(m)];
    }
    if (_disposed || !signedIn) return;
    friends = [
      for (final f in fs) Friend(id: '${f.id}', tel: '', name: f.name, ini: f.name.isEmpty ? '' : f.name.substring(0, 1), color: _colorOf(f.id), status: f.status),
    ];
    rooms = [for (final r in rs) _appRoom(r)];
    msgs
      ..clear()
      ..addAll(loaded);
    lastText
      ..clear()
      ..addAll({for (final e in loaded.entries) e.key: e.value.isEmpty ? '' : describeMsg(e.value.last)});
    final ordered = [...rs]..sort((a, b) => (b.lastMessage?.id ?? 0).compareTo(a.lastMessage?.id ?? 0));
    act.clear();
    for (var i = 0; i < ordered.length; i++) {
      act['${ordered[i].id}'] = ordered.length - i;
    }
    _actN = 100 + ordered.length;
    readRooms.clear();
    newIn.clear();
    at.clear();
    final open = roomId;
    if (inChat && open != null) _markRead(open);
    _changed();
  }

  /// 서버 방의 멤버 · 읽은 곳을 기억한다(메시지 바꾸기 · 읽음에 씀).
  void _remember(SRoom r) {
    for (final m in r.members) {
      _names[m.id] = m.name;
    }
    _roomOthers[r.id] = [for (final m in r.members) if (m.id != _meId) m.id];
    _readUpTo[r.id] = {...r.readUpTo};
    final mine = r.readUpTo[_meId] ?? 0;
    if (mine > (_readSent[r.id] ?? 0)) _readSent[r.id] = mine;
  }

  Room _appRoom(SRoom r) => Room(
        id: '${r.id}', name: r.name, color: _colorOf(r.id), members: [for (final m in r.members) if (m.id != _meId) m.name],
        unread: r.unread, time: r.lastMessage == null ? '' : clockText(r.lastMessage!.createdAt),
      );

  /// 서버 방을 목록에 넣거나 바꾼다(방 만들기 · 초대 · room.updated). 처음 보는 방이면 메시지를 받아 온다.
  /// 받는 사이에 실시간으로 온 메시지와 겹치지 않게 id 로 합친다.
  void _upsertRoom(SRoom r) {
    _remember(r);
    final id = '${r.id}';
    final i = rooms.indexWhere((x) => x.id == id);
    if (i >= 0) {
      final old = rooms[i];
      old.members = _appRoom(r).members;
      rooms[i] = Room(id: id, name: r.name, color: old.color, members: old.members, unread: old.unread, time: old.time);
      _changed();
      return;
    }
    rooms = [_appRoom(r), ...rooms];
    msgs[id] ??= [];
    _bump(id);
    _changed();
    _fetchRoom(r.id);
  }

  Future<void> _fetchRoom(int rid) async {
    final api = remote;
    if (api == null) return;
    try {
      for (final m in await api.messages(rid)) {
        _upsert(m, incoming: false);
      }
    } catch (_) {
      return;
    }
    final id = '$rid';
    final list = msgs[id] ?? [];
    list.sort((a, b) => (a.id > 0 ? a.id : 1 << 62).compareTo(b.id > 0 ? b.id : 1 << 62));
    lastText[id] = list.isEmpty ? '' : describeMsg(list.last);
    if (inChat && roomId == id) _markRead(id);
    _changed();
  }

  /// 방에서 빠짐(내가 나감 · room.removed).
  void _dropRoom(String id) {
    final rid = int.tryParse(id);
    if (rid == null) return;
    for (final m in [_roomOthers, _readUpTo, _lastSeen, _readSent]) {
      m.remove(rid);
    }
    rooms = [for (final r in rooms) if (r.id != id) r];
    msgs.remove(id);
    lastText.remove(id);
    if (map?.roomId == id) closeMap();
    if (roomId == id) {
      inChat = false;
      roomId = null;
      modal = null;
      drawer = false;
    }
    _changed();
  }

  /// (개발용) 서버가 방을 처음 상태로 돌렸다(room.cleared): 그 방의 대화 · 읽음 · 안 읽은 수를 비운다. 열어 둔 지도는 닫는다.
  void _clearRoom(int rid) {
    final id = '$rid';
    final i = rooms.indexWhere((r) => r.id == id);
    if (i < 0) return;
    _lastSeen[rid] = 0;
    _readUpTo[rid] = {};
    _readSent.remove(rid);
    final old = rooms[i];
    rooms[i] = Room(id: id, name: old.name, color: old.color, members: old.members, unread: 0, time: '');
    msgs[id] = [];
    lastText[id] = '';
    newIn.remove(id);
    readRooms.remove(id);
    if (map?.roomId == id) closeMap();
    _changed();
  }

  List<int> _ids(List<String> friendIds) => [for (final f in friendIds) ?int.tryParse(f)];

  String _failure(Object e) => switch (e) {
        RemoteRejected(:final message) => message,
        RemoteUnauthorized() => '다시 로그인해 주세요',
        _ => '서버에 연결하지 못했어요',
      };

  Future<void> _remoteCreateRoom(String name, List<String> selected) async {
    try {
      final r = await remote!.createRoom(name, _ids(selected));
      modal = null;
      _upsertRoom(r);
      openRoom('${r.id}');
    } catch (e) {
      showToast(_failure(e));
    }
  }

  /// 친구 프로필의 [1:1 채팅]: 그 친구와 둘만 있는 방이 있으면 열고, 없으면 만든다.
  Future<void> _remoteDm(String friendId) async {
    final f = friend(friendId);
    final uid = int.tryParse(friendId);
    if (f == null || uid == null) return;
    for (final r in rooms) {
      final others = _roomOthers[int.tryParse(r.id)];
      if (others != null && others.length == 1 && others.single == uid) {
        modal = null;
        return openRoom(r.id);
      }
    }
    try {
      final r = await remote!.createRoom(f.name, [uid]);
      modal = null;
      _upsertRoom(r);
      openRoom('${r.id}');
    } catch (e) {
      showToast(_failure(e));
    }
  }

  Future<void> _remoteInvite(String roomId, List<String> selected) async {
    final rid = int.tryParse(roomId);
    final ids = _ids(selected);
    if (rid == null || ids.isEmpty) return;
    try {
      _upsertRoom(await remote!.invite(rid, ids));
      modal = null;
      showToast('${ids.length}명을 초대했어요');
    } catch (e) {
      showToast(_failure(e));
    }
  }

  Future<void> _remoteLeave(String roomId) async {
    final rid = int.tryParse(roomId);
    if (rid == null) return;
    try {
      await remote!.leave(rid);
      _dropRoom(roomId);
      showToast('채팅방을 나갔어요');
    } catch (e) {
      modal = null;
      showToast(_failure(e));
    }
  }

  /// 서버 메시지 → 화면 메시지. 내 경로 공유 카드(차에서 보낸 것)도 폰에서는 상대 카드 모양으로 보인다.
  Msg _toMsg(SMessage m) {
    final mine = m.senderId == _meId;
    final who = _names[m.senderId] ?? '가족';
    final time = clockText(m.createdAt);
    final others = _roomOthers[m.roomId] ?? const <int>[];
    MineMsg reads(MineMsg x) {
      for (final o in others) {
        final n = _names[o];
        if (n != null && (_readUpTo[m.roomId]?[o] ?? 0) >= m.id) x.readBy.add(n);
      }
      return x;
    }

    // 남이 보낸 것: 보낸 사람을 뺀 방 사람(나 포함) 중 아직 안 읽은 수
    final unread = mine ? 0 : [...others, _meId].where((u) => u != m.senderId && (_readUpTo[m.roomId]?[u] ?? 0) < m.id).length;
    switch (m.kind) {
      case 'TEXT':
        return mine ? reads(MyMsg(m.id, m.text ?? '', others.length)) : TextMsg(m.id, who, m.text ?? '', unread: unread);
      case 'LOCATION':
        final loc = m.location;
        if (mine) return reads(MyLocMsg(m.id, loc?.addr ?? '', time, others.length, lat: loc?.lat, lon: loc?.lon, title: loc?.name ?? ''));
        // 보낸 지 1년이 지나 좌표가 지워진 위치
        if (loc == null) return TextMsg(m.id, who, '위치를 보냈어요', unread: unread);
        return LocMsg(m.id, who: who, kind: LocKind.here, placeId: 'msg:${m.id}', time: time, place: _placeOf(loc), unread: unread);
      case 'ROUTE':
        final r = m.route;
        if (r == null) return SysMsg(m.id, '경로 공유');
        final live = r.live;
        final end = switch (r.state) { 'ARRIVED' => RouteEnd.arrived, 'STOPPED' => RouteEnd.stopped, _ => null };
        return LocMsg(
          m.id, who: mine ? myName : who, kind: LocKind.dest, placeId: 'msg:${m.id}', from: r.from.addr,
          min: live == null ? 0 : (live.remainSec + 59) ~/ 60, pct: end == RouteEnd.arrived ? 100 : live?.pct ?? 0,
          eta: live == null ? '' : clockText(live.etaAt), time: time, traffic: live?.traffic,
          place: _placeOf(r.dest), start: (r.from.lat, r.from.lon), end: end, unread: unread,
        );
      default:
        return SysMsg(m.id, m.text ?? '');
    }
  }

  void _onRemote(RemoteEvent e) {
    if (_meId == 0 || _disposed) return;
    switch (e) {
      case RemoteConnected():
        _catchUp();
      case RemoteAck(:final message):
        _upsert(message, incoming: false);
      case RemoteNew(:final message):
        _upsert(message, incoming: true);
      case RemoteUpdated(:final message):
        _upsert(message, incoming: false);
      case RemoteLive():
        _live(e.roomId, e.messageId, e.live);
      case RemoteRead():
        _read(e.roomId, e.userId, e.msgId);
      case RemoteRoomUpdated(:final room):
        _upsertRoom(room);
      case RemoteRoomRemoved(:final roomId):
        _dropRoom('$roomId');
      case RemoteRoomCleared(:final roomId):
        _clearRoom(roomId);
      case RemoteError(:final code, :final message):
        if (code != 'NO_ACTIVE_ROUTE') showToast(message);
    }
  }

  /// 다시 연결됐을 때 끊긴 사이에 온 메시지를 채운다.
  Future<void> _catchUp() async {
    final api = remote!;
    for (final rid in [..._roomOthers.keys]) {
      try {
        for (final m in await api.messages(rid, after: _lastSeen[rid] ?? 0)) {
          _upsert(m, incoming: true);
        }
      } catch (_) {}
    }
  }

  /// 서버 메시지를 넣거나(새것) 같은 id 를 바꾼다(경로 공유 카드 변경 · 내 ack).
  void _upsert(SMessage m, {required bool incoming}) {
    final rid = '${m.roomId}';
    if (!rooms.any((r) => r.id == rid)) return;
    if (m.id > (_lastSeen[m.roomId] ?? 0)) _lastSeen[m.roomId] = m.id;
    final msg = _toMsg(m);
    final list = msgs[rid] ??= [];
    final i = list.indexWhere((x) => x.id == m.id);
    if (i >= 0) {
      list[i] = msg;
      if (i == list.length - 1) lastText[rid] = describeMsg(msg);
      final mp = map;
      if (mp != null && mp.msgId == m.id && msg is LocMsg && msg.end != null) {
        // 보고 있던 경로 공유가 끝나면(도착 · 공유 종료) 지도를 닫는다
        closeMap();
        showToast(msg.end == RouteEnd.arrived ? '${Josa.ga(msg.who)} 도착했어요' : '경로 공유가 끝났어요');
      } else if (mp != null && mp.msgId == m.id && msg is LocMsg) {
        mp
          ..end = msg.end
          ..pct = msg.end == RouteEnd.arrived ? 100 : msg.pct
          ..remain = msg.min
          ..eta = msg.eta
          ..traffic = msg.traffic;
      }
      return _changed();
    }
    _addMsg(rid, msg);
    if (incoming && m.senderId != _meId) {
      if (inChat && roomId == rid) {
        _markRead(rid);
      } else {
        newIn[rid] = (newIn[rid] ?? 0) + 1;
      }
    }
    _changed();
  }

  void _live(int roomId, int msgId, SLive live) {
    final rid = '$roomId';
    final list = msgs[rid];
    final i = list?.indexWhere((x) => x.id == msgId) ?? -1;
    if (list == null || i < 0) return;
    final old = list[i];
    if (old is! LocMsg) return;
    final min = (live.remainSec + 59) ~/ 60;
    final eta = clockText(live.etaAt);
    list[i] = old.withLive(min: min, pct: live.pct, eta: eta, traffic: live.traffic);
    if (i == list.length - 1) lastText[rid] = describeMsg(list[i]);
    final mp = map;
    if (mp != null && mp.msgId == msgId) {
      mp
        ..remain = min
        ..pct = live.pct
        ..eta = eta
        ..traffic = live.traffic;
    }
    _changed();
  }

  /// [userId] 가 [msgId] 까지 읽었다. 내 메시지는 읽은 사람에 넣고, 남의 메시지(그 사람이 보낸 것 빼고)는 안 읽은 수를 하나 줄인다.
  /// 처음 읽는 구간(이전 읽은 곳 뒤 ~ msgId)만 바꿔서 같은 읽음이 두 번 와도 두 번 줄지 않는다.
  void _read(int roomId, int userId, int msgId) {
    final reads = _readUpTo[roomId] ??= {};
    final prev = reads[userId] ?? 0;
    if (msgId <= prev) return;
    reads[userId] = msgId;
    final who = _names[userId];
    if (who == null) return;
    for (final m in msgs['$roomId'] ?? const <Msg>[]) {
      if (m.id <= prev || m.id > msgId) continue;
      switch (m) {
        case MineMsg():
          if (userId != _meId) m.readBy.add(who);
        case TextMsg():
          if (m.who != who && m.unread > 0) m.unread--;
        case LocMsg():
          if (m.who != who && m.unread > 0) m.unread--;
        default:
          break;
      }
    }
    _changed();
  }

  /// 이 방을 보고 있으면 「여기까지 읽음」을 서버에 알린다(늘었을 때만).
  void _markRead(String rid) {
    final api = remote;
    final id = int.tryParse(rid);
    if (api == null || id == null) return;
    final top = (msgs[rid] ?? const <Msg>[]).fold(0, (a, m) => m.id > a ? m.id : a);
    if (top <= 0 || top <= (_readSent[id] ?? 0)) return;
    if (api.send({'type': 'read', 'roomId': id, 'msgId': top})) _readSent[id] = top;
  }

  void _signOutLocal() {
    remote?.disconnect();
    remote?.token = null;
    onTokenChanged?.call(null);
    _meId = 0;
    for (final m in [_names, _roomOthers, _readUpTo, _lastSeen, _readSent]) {
      m.clear();
    }
    rooms = [];
    friends = [];
    for (final m in [msgs, lastText, newIn, act, at]) {
      m.clear();
    }
    readRooms.clear();
    signedIn = false;
    login = LoginState();
    inChat = false;
    roomId = null;
    modal = null;
    plusOpen = false;
    drawer = false;
    onSessionChanged?.call(null);
    _changed();
  }

  // ═══════════════ 토스트 ═══════════════
  void showToast(String text) {
    _toastTimer?.cancel();
    toast = text;
    _changed();
    _toastTimer = Timer(const Duration(milliseconds: toastMs), () {
      toast = null;
      _changed();
    });
  }

  /// 시스템 뒤로 단추: 처리했으면 true(앱을 닫지 않음).
  bool handleBack() {
    if (pick != null) {
      closePick();
    } else if (map != null) {
      closeMap();
    } else if (modal != null) {
      closeModal();
    } else if (drawer) {
      closeDrawer();
    } else if (plusOpen) {
      closePlus();
    } else if (!signedIn && login.step != LoginStep.welcome) {
      loginBack();
    } else if (signedIn && inChat) {
      back();
    } else {
      return false;
    }
    return true;
  }

  bool get canHandleBack => pick != null || map != null || modal != null || drawer || plusOpen || (signedIn && inChat) || (!signedIn && login.step != LoginStep.welcome);

  @override
  void dispose() {
    _disposed = true;
    _toastTimer?.cancel();
    _mapTimer?.cancel();
    _sub.cancel();
    _remoteSub?.cancel();
    remote?.dispose();
    voice?.cancel();
    server.dispose();
    super.dispose();
  }
}
