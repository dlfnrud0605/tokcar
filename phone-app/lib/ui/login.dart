import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/countries.dart';
import '../core/store.dart';
import 'common.dart';
import 'flags.dart';
import 'icons.dart';
import 'theme.dart';

/// 회원가입·로그인. 처음 화면은 위쪽 그라데이션 패널(아이콘·TokCar), 그 밖의 단계는 제목 + 입력.
class LoginScreen extends StatelessWidget {
  const LoginScreen(this.s, {super.key});
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final l = s.login;
    if (l.step == LoginStep.welcome) return _Welcome(s);
    final pad = MediaQuery.paddingOf(context);
    return ColoredBox(
      color: K.bg,
      child: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: u(841)),
          child: SingleChildScrollView(
            padding: EdgeInsets.fromLTRB(u(Dims.gut), pad.top, u(Dims.gut), pad.bottom + u(40)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Padding(
                padding: EdgeInsets.only(top: u(28), bottom: u(20)),
                child: Align(alignment: Alignment.centerLeft, child: IconCircle(Ic.back, '뒤로', s.loginBack)),
              ),
              ..._StepBody(s, l).build(),
            ]),
          ),
        ),
      ),
    );
  }
}

/// 처음 화면: 위쪽 그라데이션 패널은 상태 표시줄 뒤까지 채운다.
class _Welcome extends StatelessWidget {
  const _Welcome(this.s);
  final TokStore s;

  @override
  Widget build(BuildContext context) {
    final pad = MediaQuery.paddingOf(context);
    final r = Radius.circular(u(56));
    return ColoredBox(
      color: K.bg,
      child: Column(children: [
        Expanded(
          child: AnnotatedRegion<SystemUiOverlayStyle>(
            value: SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent),
            child: Container(
              width: double.infinity,
              padding: EdgeInsets.fromLTRB(u(56), pad.top + u(64), u(56), u(64)),
              decoration: BoxDecoration(gradient: K.grad, borderRadius: BorderRadius.only(bottomLeft: r, bottomRight: r)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.end, children: [
                AppIcon(156),
                SizedBox(height: u(28)),
                T('TokCar', 92, color: K.onGrad, weight: FontWeight.bold),
                T('집에서 기다리는 가족에게\n도착 정보를 알려 줘요', 32, color: K.onGrad.withValues(alpha: .85), lines: 2),
              ]),
            ),
          ),
        ),
        Padding(
          padding: EdgeInsets.fromLTRB(u(Dims.gut), u(Dims.gut), u(Dims.gut), u(Dims.gut) + pad.bottom),
          child: Column(children: [
            BigBtn('회원가입', () => s.startLogin(LoginMode.signup)),
            SizedBox(height: u(16)),
            BigBtn('로그인', () => s.startLogin(LoginMode.login), ghost: true),
            SizedBox(height: u(16)),
            T('가입은 전화번호 인증으로 시작해요 · 해외 번호도 괜찮아요', 22, color: K.sub, align: TextAlign.center),
          ]),
        ),
      ]),
    );
  }
}

/// 단계마다 보이는 것(제목 + 입력 + 단추).
class _StepBody {
  _StepBody(this.s, this.l);
  final TokStore s;
  final LoginState l;

  Widget _head(String title, String sub) => Padding(
        padding: EdgeInsets.only(bottom: u(36)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(width: u(10), height: u(130), decoration: BoxDecoration(color: K.blue, borderRadius: BorderRadius.circular(u(5)))),
          SizedBox(width: u(28)),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              T(title, 54, weight: FontWeight.bold, lines: 2),
              SizedBox(height: u(8)),
              T(sub, 30, color: K.sub, lines: 3),
            ]),
          ),
        ]),
      );

  /// 빨간 안내 + (있으면) 바로 가는 글자 단추.
  Widget _err(String text, [String? action, VoidCallback? onAction]) => Padding(
        padding: EdgeInsets.only(top: u(12)),
        child: Row(children: [
          Flexible(child: T(text, 26, color: K.red, weight: FontWeight.bold, lines: 2)),
          if (action != null && onAction != null) ...[SizedBox(width: u(16)), _linkBtn(action, onAction)],
        ]),
      );

  Widget _linkBtn(String text, VoidCallback onTap) => Tap(
        label: text,
        radius: 12,
        onTap: onTap,
        child: Padding(
          padding: EdgeInsets.symmetric(horizontal: u(8), vertical: u(12)),
          child: T(text, 28, color: K.blueInk, weight: FontWeight.bold),
        ),
      );

  Widget _links(List<(String, VoidCallback)> items) => Padding(
        padding: EdgeInsets.only(top: u(12)),
        child: Wrap(alignment: WrapAlignment.center, spacing: u(36), children: [for (final (t, f) in items) _linkBtn(t, f)]),
      );

  Widget _demo(String text) => Padding(
        padding: EdgeInsets.only(top: u(20)),
        child: T(text, 22, color: K.sub, align: TextAlign.center, lines: 2),
      );

  Widget _hint(String text, bool? ok) => Padding(
        padding: EdgeInsets.only(top: u(10), bottom: u(6), left: u(8)),
        child: T(text, 24, color: ok == null ? K.sub : (ok ? K.blueInk : K.red), lines: 2),
      );

  Widget _pwField(String value, ValueChanged<String> onChanged, String hint) => Field(
        value,
        onChanged,
        hint,
        password: !l.showPw,
        trailing: Tap(
          label: l.showPw ? '비밀번호 가리기' : '비밀번호 보기',
          circle: true,
          onTap: s.toggleShowPw,
          child: SizedBox(width: u(76), height: u(76), child: Center(child: Ico(l.showPw ? Ic.eyeOff : Ic.eye, u(36), K.sub))),
        ),
      );

  /// 비밀번호 규칙 칩: 8~20자 · 영문과 숫자 함께 · 아이디 미포함.
  Widget _ruleChips(String pw, String id) {
    final r = Rules.pw(pw, id);
    return Padding(
      padding: EdgeInsets.only(top: u(12)),
      child: Wrap(spacing: u(10), runSpacing: u(10), children: [
        for (final (on, t) in [(r.len, '8~20자'), (r.mix, '영문과 숫자 함께'), (r.notId && pw.isNotEmpty, '아이디 미포함')])
          Tag((on ? '✓ ' : '') + t, on ? K.blueSoft : K.block, on ? K.blueInk : K.sub),
      ]),
    );
  }

  Widget _pwMatch() => l.pw2.isEmpty
      ? _hint('비밀번호를 한 번 더 입력해 주세요', null)
      : (l.pw == l.pw2 ? _hint('비밀번호가 같아요', true) : _hint('비밀번호가 서로 달라요', false));

  Widget _agreeRow(String label, bool? required, bool on, VoidCallback onTap, {bool bold = false}) => Padding(
        padding: EdgeInsets.only(bottom: u(10)),
        child: Tap(
          label: label,
          radius: Dims.r,
          onTap: onTap,
          child: Container(
            padding: EdgeInsets.symmetric(horizontal: u(20), vertical: u(18)),
            decoration: BoxDecoration(border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.r))),
            child: Row(children: [
              Container(
                width: u(48),
                height: u(48),
                decoration: BoxDecoration(
                  color: on ? K.blue : K.card,
                  border: Border.all(color: on ? K.blue : K.dash, width: u(3)),
                  borderRadius: BorderRadius.circular(u(14)),
                ),
                alignment: Alignment.center,
                child: on ? Ico(Ic.check, u(30), Colors.white) : null,
              ),
              SizedBox(width: u(20)),
              if (required != null) ...[
                T(required ? '필수' : '선택', 24, color: required ? K.blueInk : K.sub, weight: FontWeight.bold),
                SizedBox(width: u(12)),
              ],
              Expanded(child: T(label, 30, weight: bold ? FontWeight.bold : FontWeight.normal)),
              if (required == true) T('›', 30, color: K.sub),
            ]),
          ),
        ),
      );

  Widget _gap(double h) => SizedBox(height: u(h));

  List<Widget> build() {
    final c = Countries.of(l.country);
    switch (l.step) {
      case LoginStep.idpw:
        final locked = l.left <= 0;
        return [
          _head('로그인', '아이디와 비밀번호를 입력해 주세요'),
          Field(l.id, s.setLoginId, '아이디'),
          _gap(16),
          _pwField(l.pw, s.setPw, '비밀번호'),
          if (locked)
            _err('5번 틀려서 잠겼어요', '비밀번호 찾기', () => s.startLogin(LoginMode.resetpw))
          else if (l.err == LoginErr.bad)
            _err('아이디 또는 비밀번호가 맞지 않아요 (남은 시도 ${l.left}번)'),
          _gap(24),
          BigBtn('로그인', s.doLogin, enabled: l.id.trim().isNotEmpty && l.pw.isNotEmpty && !locked),
          _links([
            ('회원가입', () => s.startLogin(LoginMode.signup)),
            ('아이디 찾기', () => s.startLogin(LoginMode.findid)),
            ('비밀번호 찾기', () => s.startLogin(LoginMode.resetpw)),
          ]),
          _demo('시안 예시: 아이디 minji · 비밀번호 minji1234!'),
        ];
      case LoginStep.phone:
        final find = l.mode == LoginMode.findid;
        return [
          _head(find ? '아이디 찾기' : '회원가입', find ? '가입할 때 인증한 번호로\n인증번호를 보내 드려요' : '전화번호로 인증번호를 보내 드려요\n해외 번호는 나라를 골라 주세요'),
          Row(children: [
            Tap(
              label: '국가 선택: ${c.name} +${c.cc}',
              radius: Dims.r,
              onTap: s.openCountry,
              child: Container(
                height: u(Dims.hL),
                padding: EdgeInsets.symmetric(horizontal: u(22)),
                decoration: BoxDecoration(color: K.bg, border: Border.all(color: K.line, width: u(2)), borderRadius: BorderRadius.circular(u(Dims.r))),
                child: Row(children: [
                  Flag(c.code, 48, 32),
                  SizedBox(width: u(12)),
                  T('+${c.cc}', 32, weight: FontWeight.bold),
                  Ico(Ic.down, u(28), K.sub),
                ]),
              ),
            ),
            SizedBox(width: u(16)),
            Expanded(child: Field(l.phone, s.setLoginPhone, c.example, phone: true)),
          ]),
          if (l.err == LoginErr.known) _err('이미 가입된 번호예요', '로그인하기', () => s.startLogin(LoginMode.login)),
          if (l.err == LoginErr.unknown) _err('가입되지 않은 번호예요', '회원가입하기', () => s.startLogin(LoginMode.signup)),
          _gap(24),
          BigBtn('인증번호 받기', s.sendCode, enabled: PhoneIntl.isValid(c, l.phone) && !l.busy),
          _demo('시안 예시: +82 10-1234-5678은 가입된 번호이고 그 밖의 번호는 새 번호로 처리해요'),
        ];
      case LoginStep.rid:
        return [
          _head('비밀번호 찾기', '아이디를 입력하면 가입할 때 인증한\n번호로 인증번호를 보내 드려요'),
          Field(l.rid, s.setRid, '아이디'),
          if (l.err == LoginErr.noid) _err('가입된 아이디가 아니에요', '회원가입하기', () => s.startLogin(LoginMode.signup)),
          _gap(24),
          BigBtn('인증번호 받기', s.resetSend, enabled: l.rid.trim().isNotEmpty && !l.busy),
          _demo('시안 예시: 아이디 papa'),
        ];
      case LoginStep.code:
        final to = l.mode == LoginMode.resetpw ? PhoneIntl.mask(c, l.phone) : PhoneIntl.format(c, l.phone);
        return [
          _head('인증번호를 입력해 주세요', '$to로\n인증번호를 보냈어요'),
          Field(l.code, s.setLoginCode, '인증번호 6자리', center: true, numeric: true),
          _gap(24),
          BigBtn('확인', s.verify, enabled: l.code.length == 6 && !l.busy),
          if (l.mode == LoginMode.resetpw)
            _links([('인증번호 다시 받기', s.resendCode)])
          else
            _links([('번호 바꾸기', s.editPhone), ('인증번호 다시 받기', s.resendCode)]),
          _demo('시안에서는 아무 숫자 6자리나 통과해요'),
        ];
      case LoginStep.account:
        final checked = l.idCheck?.$1 == l.id;
        final can = Rules.id.hasMatch(l.id) && !checked;
        return [
          _head('계정을 만들어 주세요', '로그인할 때 쓰는\n아이디와 비밀번호예요'),
          Row(children: [
            Expanded(child: Field(l.id, s.setNewId, '아이디')),
            SizedBox(width: u(16)),
            Tap(
              label: '중복 확인',
              radius: Dims.r,
              enabled: can,
              onTap: s.checkId,
              child: Container(
                height: u(Dims.hL),
                padding: EdgeInsets.symmetric(horizontal: u(28)),
                decoration: BoxDecoration(
                  gradient: can ? K.grad : null,
                  color: can ? null : K.block,
                  borderRadius: BorderRadius.circular(u(Dims.r)),
                ),
                alignment: Alignment.center,
                child: T('중복 확인', 30, color: can ? K.onGrad : K.sub, weight: FontWeight.bold),
              ),
            ),
          ]),
          if (l.id.isEmpty)
            _hint('영문 소문자로 시작하고, 영문 소문자·숫자 4~16자예요', null)
          else if (!Rules.id.hasMatch(l.id))
            _hint('영문 소문자로 시작하고 영문 소문자·숫자 4~16자로 써 주세요', false)
          else if (checked)
            s.idOk ? _hint('사용할 수 있는 아이디예요', true) : _hint('이미 사용 중인 아이디예요', false)
          else
            _hint('중복 확인을 눌러 주세요', null),
          _gap(12),
          _pwField(l.pw, s.setPw, '비밀번호'),
          _gap(16),
          _pwField(l.pw2, s.setPw2, '비밀번호 확인'),
          _ruleChips(l.pw, l.id),
          _pwMatch(),
          _gap(24),
          BigBtn('다음', s.accountNext, enabled: s.idOk && Rules.pw(l.pw, l.id).ok && l.pw == l.pw2),
        ];
      case LoginStep.newpw:
        return [
          _head('새 비밀번호를 만들어 주세요', '8자 이상, 영문과 숫자를\n함께 써 주세요'),
          _pwField(l.pw, s.setPw, '비밀번호'),
          _gap(16),
          _pwField(l.pw2, s.setPw2, '비밀번호 확인'),
          _ruleChips(l.pw, l.rid),
          _pwMatch(),
          _gap(24),
          BigBtn('비밀번호 바꾸기', s.newPwNext, enabled: Rules.pw(l.pw, l.rid).ok && l.pw == l.pw2),
        ];
      case LoginStep.fdone:
        final any = l.found.isNotEmpty;
        return [
          _head(any ? '가입한 아이디예요' : '찾은 아이디가 없어요', any ? '보안을 위해 일부를 가려서 보여 줘요' : '이 번호로 만든 아이디가 없어요'),
          for (final id in l.found)
            Container(
              margin: EdgeInsets.only(bottom: u(12)),
              height: u(Dims.hL),
              decoration: BoxDecoration(color: K.card, border: Border.all(color: K.line, width: u(3)), borderRadius: BorderRadius.circular(u(Dims.r))),
              alignment: Alignment.center,
              child: T(Rules.maskId(id), 40, weight: FontWeight.w800),
            ),
          _gap(12),
          BigBtn('로그인하기', () => s.startLogin(LoginMode.login)),
          _gap(16),
          BigBtn('비밀번호 찾기', () => s.startLogin(LoginMode.resetpw), ghost: true),
        ];
      case LoginStep.profile:
        return [
          _head('이름을 알려 주세요', '가족에게 이 이름으로 보여요'),
          Field(l.name, s.setLoginName, '이름 (예: 아빠, 김민수)'),
          _gap(24),
          BigBtn('다음', s.profileNext, enabled: l.name.trim().isNotEmpty),
        ];
      case LoginStep.terms:
        final a = l.agree;
        return [
          _head('약관에 동의해 주세요', '서비스를 쓰려면 필수 항목이 필요해요'),
          _agreeRow('모두 동의', null, a.all, s.agreeAll, bold: true),
          _agreeRow('서비스 이용약관', true, a.tos, () => s.toggleAgree('tos')),
          _agreeRow('개인정보 수집·이용', true, a.priv, () => s.toggleAgree('priv')),
          _agreeRow('위치 정보 이용', true, a.loc, () => s.toggleAgree('loc')),
          _agreeRow('마케팅 정보 수신', false, a.mkt, () => s.toggleAgree('mkt')),
          _gap(14),
          BigBtn('가입하기', s.join, enabled: a.required),
        ];
      case LoginStep.welcome:
        return [];
    }
  }
}
