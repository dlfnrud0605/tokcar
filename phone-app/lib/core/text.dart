/// 한글 받침에 맞춰 조사를 붙이는 도우미. 한글이 아닌 글자로 끝나면 받침이 없는 것으로 본다.
class Josa {
  /// 마지막 글자의 받침 번호(0 = 받침 없음, 8 = ㄹ).
  static int _jong(String s) {
    if (s.isEmpty) return 0;
    final c = s.codeUnitAt(s.length - 1);
    return (c >= 0xAC00 && c <= 0xD7A3) ? (c - 0xAC00) % 28 : 0;
  }

  static String eul(String s) => s + (_jong(s) == 0 ? '를' : '을');
  static String ga(String s) => s + (_jong(s) == 0 ? '가' : '이');
  static String ro(String s) => s + ((_jong(s) == 0 || _jong(s) == 8) ? '로' : '으로');
}

String digitsOf(String t) => t.replaceAll(RegExp(r'\D'), '');

class Phone {
  static final _ok = RegExp(r'^010\d{8}$');

  static String digits(String t) => digitsOf(t);
  static bool isValid(String t) => _ok.hasMatch(digits(t));

  /// 010-****-5678
  static String mask(String t) {
    final d = digits(t);
    return d.length == 11 ? '${d.substring(0, 3)}-****-${d.substring(7)}' : d;
  }
}

/// 친구 이름으로 방 이름을 자동으로 짓는다.
String autoName(List<String> names) {
  switch (names.length) {
    case 0:
      return '';
    case 1:
      return names[0];
    case 2:
      return names.join(', ');
    default:
      return '${names.take(2).join(', ')} 외 ${names.length - 2}명';
  }
}

/// 시각을 "오후 6:05" 로.
String clockText(DateTime t) {
  final h = t.hour;
  final hh = h % 12 == 0 ? 12 : h % 12;
  return '${h >= 12 ? '오후' : '오전'} $hh:${t.minute.toString().padLeft(2, '0')}';
}

/// 남은 시간을 일·시간·분으로 나눈다: 203분 = 3시간 23분, 1500분 = 1일 1시간. 0인 칸은 빼고, 모두 0이면 "0분".
List<(int, String)> leftParts(int minutes) {
  final m = minutes < 0 ? 0 : minutes;
  final d = m ~/ 1440, h = m % 1440 ~/ 60, mm = m % 60;
  final out = <(int, String)>[];
  if (d > 0) out.add((d, '일'));
  if (h > 0) out.add((h, '시간'));
  if (mm > 0 || out.isEmpty) out.add((mm, '분'));
  return out;
}

/// "3시간 23분" 같은 글(목록 카드의 최근 대화).
String leftText(int minutes) => leftParts(minutes).map((p) => '${p.$1}${p.$2}').join(' ');

/// 주소 도우미. 큰 글씨 이름이 없거나 주소처럼 생긴 경우 [area] 로 "구(시) 동"까지만 줄여 쓴다.
class Address {
  static final _province = RegExp(r'(특별시|광역시|특별자치시|특별자치도|도)$');
  static final _dong = RegExp(r'[동읍면리가]$');
  static final _district = RegExp(r'(시|군|구)$');
  static final _road = RegExp(r'(로|길)$|[로길]\d');
  static final _ws = RegExp(r'\s+');

  /// 맨 앞의 도·특별시·광역시 이름은 뺀다: "경기도 광주시 오포로 215" -> "광주시 오포로 215".
  static String withoutProvince(String addr) {
    final t = addr.trim().split(_ws);
    return (t.length > 1 && _province.hasMatch(t[0])) ? t.skip(1).join(' ') : addr.trim();
  }

  static bool looksLikeAddress(String s) =>
      RegExp(r'[로길]\s*\d').hasMatch(s) || (s.contains('(') && s.contains(')')) || RegExp(r'^\S+(시|도|군)\s+\S+(시|군|구)\s').hasMatch(s);

  /// 위치 보내기 카드 제목용으로 「구(시) + 도로명」까지만: "성남시 분당구 정자일로 95 (정자동)" -> "분당구 정자일로".
  /// 도로명이 없으면 [area](구 동).
  static String street(String addr) {
    final a = addr.trim();
    if (a.isEmpty) return a;
    final tokens = a.replaceAll(RegExp(r'\([^)]*\)'), ' ').trim().split(_ws).where((e) => e.isNotEmpty).toList();
    if (tokens.length > 1 && _province.hasMatch(tokens[0])) tokens.removeAt(0);
    final roadIdx = tokens.indexWhere((t) => _road.hasMatch(t));
    if (roadIdx < 0) return area(a);
    final head = tokens.take(roadIdx).where((t) => _district.hasMatch(t)).toList();
    return [if (head.isNotEmpty) head.last, tokens[roadIdx]].join(' ');
  }

  /// 주소를 "구(시) 동"까지만: "성남시 분당구 삼평동 123-4" -> "분당구 삼평동", "서울특별시 강남구 테헤란로 152" -> "강남구 테헤란로".
  static String area(String addr) {
    final a = addr.trim();
    if (a.isEmpty) return a;
    final paren = RegExp(r'\(([^)]*[동읍면리가])\)').firstMatch(a)?.group(1);
    final tokens = a.replaceAll(RegExp(r'\([^)]*\)'), ' ').trim().split(_ws).where((e) => e.isNotEmpty).toList();
    if (tokens.length > 1 && _province.hasMatch(tokens[0])) tokens.removeAt(0);
    final dongIdx = tokens.indexWhere((t) => t.length >= 2 && _dong.hasMatch(t));
    final head = tokens.take(dongIdx >= 0 ? dongIdx : tokens.length).where((t) => _district.hasMatch(t)).toList();
    final district = head.isEmpty ? null : head.last;
    final dong = paren ?? (dongIdx >= 0 ? tokens[dongIdx] : null);
    final roads = tokens.where((t) => _road.hasMatch(t));
    final road = roads.isEmpty ? null : roads.first;
    final out = [district, dong ?? road].whereType<String>().join(' ');
    return out.isEmpty ? a : out;
  }
}
