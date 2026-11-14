import 'text.dart';

/// 가입·로그인에서 고르는 나라. [example] = 입력 예시, [min]~[max] = 국가번호 뒤 자릿수(맨 앞 0 제외),
/// [trunk] = 나라 안에서 거는 맨 앞 0을 국제 번호에서 빼는 나라.
/// 자릿수 규칙은 시안용 간단 규칙이고 실제 서비스에는 libphonenumber 같은 번호 검사가 필요함 [추정].
class Country {
  const Country(this.code, this.name, this.cc, this.example, this.min, this.max, {this.trunk = false});
  final String code, name, cc, example;
  final int min, max;
  final bool trunk;
}

class Countries {
  static const all = [
    Country('KR', '대한민국', '82', '10-1234-5678', 9, 10, trunk: true),
    Country('US', '미국', '1', '(555) 123-4567', 10, 10),
    Country('JP', '일본', '81', '90-1234-5678', 9, 10, trunk: true),
    Country('CN', '중국', '86', '138 0013 8000', 11, 11),
    Country('GB', '영국', '44', '7400 123456', 10, 10, trunk: true),
    Country('DE', '독일', '49', '1512 3456789', 10, 11, trunk: true),
    Country('FR', '프랑스', '33', '6 12 34 56 78', 9, 9, trunk: true),
    Country('CA', '캐나다', '1', '(555) 123-4567', 10, 10),
    Country('VN', '베트남', '84', '912 345 678', 9, 10, trunk: true),
    Country('TH', '태국', '66', '81 234 5678', 9, 9, trunk: true),
    Country('SG', '싱가포르', '65', '8123 4567', 8, 8),
    Country('IN', '인도', '91', '98765 43210', 10, 10),
  ];

  static Country of(String code) => all.firstWhere((c) => c.code == code, orElse: () => all[0]);
}

/// 국제 전화번호(E.164 기준) 도우미.
class PhoneIntl {
  /// 국가번호 뒤에 오는 번호 숫자만(나라 안 맨 앞 0은 뺌).
  static String national(Country c, String text) {
    final d = digitsOf(text);
    return (c.trunk && d.startsWith('0')) ? d.substring(1) : d;
  }

  static bool isValid(Country c, String text) {
    final n = national(c, text).length;
    return n >= c.min && n <= c.max;
  }

  /// +821012345678
  static String e164(Country c, String text) => '+${c.cc}${national(c, text)}';

  /// +82 10-1234-5678
  static String format(Country c, String text) {
    final d = national(c, text);
    return '+${c.cc} ${(c.code == 'KR' && d.length == 10) ? '${d.substring(0, 2)}-${d.substring(2, 6)}-${d.substring(6)}' : d}';
  }

  /// +82 10-****-5678 / +1 ******4567
  static String mask(Country c, String text) {
    final d = national(c, text);
    if (c.code == 'KR' && d.length == 10) return '+${c.cc} ${d.substring(0, 2)}-****-${d.substring(6)}';
    final b = StringBuffer();
    for (var i = 0; i < d.length; i++) {
      b.write(i < d.length - 4 ? '*' : d[i]);
    }
    return '+${c.cc} $b';
  }
}

/// 입력하는 대로 하이픈·띄어쓰기를 넣는다. 나라마다 입력 예시 모양을 따라가고, 한국처럼 맨 앞 0을 쓰는 나라는 0을 붙인 모양(010-1234-5678)으로.
String formatTyping(Country c, String raw) {
  final d = digitsOf(raw);
  final pat = (c.trunk && d.startsWith('0') ? '0' : '') + c.example;
  final out = StringBuffer();
  var k = 0;
  for (final ch in pat.split('')) {
    if (k >= d.length) break;
    if (RegExp(r'\d').hasMatch(ch)) {
      out.write(d[k++]);
    } else {
      out.write(ch);
    }
  }
  return out.toString() + d.substring(k);
}
