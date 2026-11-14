package com.example.tokcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tokcar.core.Countries
import com.example.tokcar.core.LoginErr
import com.example.tokcar.core.LoginMode
import com.example.tokcar.core.LoginState
import com.example.tokcar.core.LoginStep
import com.example.tokcar.core.PhoneIntl
import com.example.tokcar.core.Rules
import com.example.tokcar.core.TokStore
import com.example.tokcar.core.UiState
import com.example.tokcar.ui.theme.Dims
import com.example.tokcar.ui.theme.Ic
import com.example.tokcar.ui.theme.K

/** 회원가입·로그인. 처음 화면은 위쪽 그라데이션 패널(아이콘·TokCar), 그 밖의 단계는 제목 + 입력. */
@Composable
fun LoginScreen(s: UiState, store: TokStore) {
    val l = s.login
    Box(Modifier.fillMaxSize().background(K.bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 841.dp).fillMaxSize().imePadding()) {
            if (l.step == LoginStep.WELCOME) Welcome(store)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dims.gut)) {
                Box(Modifier.padding(top = 28.dp, bottom = 20.dp)) { IconCircle(Ic.Back, "뒤로", store::loginBack) }
                StepBody(s, l, store)
            }
        }
    }
}

@Composable
private fun Welcome(store: TokStore) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(bottomStart = 56.dp, bottomEnd = 56.dp)).background(K.grad).padding(horizontal = 56.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            AppIcon(156.dp)
            Spacer(Modifier.height(28.dp))
            T("TokCar", 92, color = K.onGrad, weight = FontWeight.Bold)
            T("집에서 기다리는 가족에게\n도착 정보를 알려 줘요", 32, color = K.onGrad.copy(alpha = .85f), lines = 2)
        }
        Column(Modifier.fillMaxWidth().padding(Dims.gut), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BigBtn("회원가입", { store.startLogin(LoginMode.SIGNUP) })
            BigBtn("로그인", { store.startLogin(LoginMode.LOGIN) }, ghost = true)
            T("가입은 전화번호 인증으로 시작해요 · 해외 번호도 괜찮아요", 22, Modifier.fillMaxWidth(), color = K.sub, align = TextAlign.Center)
        }
    }
}

@Composable
private fun Head(title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
        Box(Modifier.width(10.dp).height(130.dp).clip(RoundedCornerShape(5.dp)).background(K.blue))
        Spacer(Modifier.width(28.dp))
        Column {
            T(title, 54, weight = FontWeight.Bold, lines = 2)
            Spacer(Modifier.height(8.dp))
            T(sub, 30, color = K.sub, lines = 3)
        }
    }
}

/** 빨간 안내 + (있으면) 바로 가는 글자 단추. */
@Composable
private fun Err(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        T(text, 26, color = K.red, weight = FontWeight.Bold, lines = 2)
        if (action != null && onAction != null) { Spacer(Modifier.width(16.dp)); LinkBtn(action, onAction) }
    }
}

@Composable
private fun LinkBtn(text: String, onClick: () -> Unit) =
    T(text, 28, Modifier.clip(RoundedCornerShape(12.dp)).tap(text, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp), color = K.blueInk, weight = FontWeight.Bold)

@Composable
private fun Links(vararg items: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally)) {
        items.forEach { (t, f) -> LinkBtn(t, f) }
    }
}

@Composable
private fun DemoNote(text: String) = T(text, 22, Modifier.fillMaxWidth().padding(top = 20.dp), color = K.sub, align = TextAlign.Center, lines = 2)

@Composable
private fun Hint(text: String, ok: Boolean?) =
    T(text, 24, Modifier.padding(top = 10.dp, bottom = 6.dp, start = 8.dp), color = when (ok) { true -> K.blueInk; false -> K.red; null -> K.sub }, lines = 2)

@Composable
private fun PwField(value: String, onChange: (String) -> Unit, hint: String, l: LoginState, store: TokStore) {
    Field(value, onChange, hint, password = !l.showPw, trailing = {
        Box(Modifier.size(76.dp).clip(CircleShape).tap(if (l.showPw) "비밀번호 가리기" else "비밀번호 보기", onClick = store::toggleShowPw), contentAlignment = Alignment.Center) {
            Ico(if (l.showPw) Ic.EyeOff else Ic.Eye, 36.dp, K.sub)
        }
    })
}

/** 비밀번호 규칙 칩: 8~20자 · 영문과 숫자 함께 · 아이디 미포함. */
@Composable
private fun RuleChips(pw: String, id: String) {
    val r = Rules.pw(pw, id)
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(r.len to "8~20자", r.mix to "영문과 숫자 함께", (r.notId && pw.isNotEmpty()) to "아이디 미포함").forEach { (on, t) ->
            Tag((if (on) "✓ " else "") + t, if (on) K.blueSoft else K.block, if (on) K.blueInk else K.sub)
        }
    }
}

@Composable
private fun ColumnScope.StepBody(s: UiState, l: LoginState, store: TokStore) {
    val c = Countries.of(l.country)
    when (l.step) {
        LoginStep.IDPW -> {
            val locked = l.left <= 0
            Head("로그인", "아이디와 비밀번호를 입력해 주세요")
            Field(l.id, store::setLoginId, "아이디")
            Spacer(Modifier.height(16.dp))
            PwField(l.pw, store::setPw, "비밀번호", l, store)
            when {
                locked -> Err("5번 틀려서 잠겼어요", "비밀번호 찾기") { store.startLogin(LoginMode.RESETPW) }
                l.err == LoginErr.BAD -> Err("아이디 또는 비밀번호가 맞지 않아요 (남은 시도 ${l.left}번)")
            }
            Spacer(Modifier.height(24.dp))
            BigBtn("로그인", store::doLogin, enabled = l.id.isNotBlank() && l.pw.isNotEmpty() && !locked)
            Links("회원가입" to { store.startLogin(LoginMode.SIGNUP) }, "아이디 찾기" to { store.startLogin(LoginMode.FINDID) }, "비밀번호 찾기" to { store.startLogin(LoginMode.RESETPW) })
            DemoNote("시안 예시: 아이디 papa · 비밀번호 papa1234!")
        }
        LoginStep.PHONE -> {
            val find = l.mode == LoginMode.FINDID
            Head(if (find) "아이디 찾기" else "회원가입", if (find) "가입할 때 인증한 번호로\n인증번호를 보내 드려요" else "전화번호로 인증번호를 보내 드려요\n해외 번호는 나라를 골라 주세요")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(K.bg).border(2.dp, K.line, RoundedCornerShape(Dims.r)).tap("국가 선택: ${c.name} +${c.cc}", onClick = store::openCountry).padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Flag(c.code, Modifier.size(48.dp, 32.dp))
                    Spacer(Modifier.width(12.dp))
                    T("+${c.cc}", 32, weight = FontWeight.Bold)
                    Ico(Ic.Down, 28.dp, K.sub)
                }
                Spacer(Modifier.width(16.dp))
                Field(l.phone, store::setLoginPhone, c.example, Modifier.weight(1f), phone = true)
            }
            when (l.err) {
                LoginErr.KNOWN -> Err("이미 가입된 번호예요", "로그인하기") { store.startLogin(LoginMode.LOGIN) }
                LoginErr.UNKNOWN -> Err("가입되지 않은 번호예요", "회원가입하기") { store.startLogin(LoginMode.SIGNUP) }
                else -> Unit
            }
            Spacer(Modifier.height(24.dp))
            BigBtn("인증번호 받기", store::sendCode, enabled = PhoneIntl.isValid(c, l.phone) && !l.busy)
            DemoNote("시안 예시: +82 10-1234-5678은 가입된 번호이고 그 밖의 번호는 새 번호로 처리해요")
        }
        LoginStep.RID -> {
            Head("비밀번호 찾기", "아이디를 입력하면 가입할 때 인증한\n번호로 인증번호를 보내 드려요")
            Field(l.rid, store::setRid, "아이디")
            if (l.err == LoginErr.NOID) Err("가입된 아이디가 아니에요", "회원가입하기") { store.startLogin(LoginMode.SIGNUP) }
            Spacer(Modifier.height(24.dp))
            BigBtn("인증번호 받기", store::resetSend, enabled = l.rid.isNotBlank() && !l.busy)
            DemoNote("시안 예시: 아이디 papa")
        }
        LoginStep.CODE -> {
            val to = if (l.mode == LoginMode.RESETPW) PhoneIntl.mask(c, l.phone) else PhoneIntl.format(c, l.phone)
            Head("인증번호를 입력해 주세요", "${to}로\n인증번호를 보냈어요")
            Field(l.code, store::setLoginCode, "인증번호 6자리", center = true, numeric = true)
            Spacer(Modifier.height(24.dp))
            BigBtn("확인", store::verify, enabled = l.code.length == 6 && !l.busy)
            if (l.mode == LoginMode.RESETPW) Links("인증번호 다시 받기" to store::resendCode)
            else Links("번호 바꾸기" to store::editPhone, "인증번호 다시 받기" to store::resendCode)
            DemoNote("시안에서는 아무 숫자 6자리나 통과해요")
        }
        LoginStep.ACCOUNT -> {
            val checked = l.idCheck?.value == l.id
            val idOk = checked && l.idCheck?.ok == true
            Head("계정을 만들어 주세요", "로그인할 때 쓰는\n아이디와 비밀번호예요")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(l.id, store::setNewId, "아이디", Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                val can = Rules.ID.matches(l.id) && !checked
                Box(
                    Modifier.height(Dims.hL).clip(RoundedCornerShape(Dims.r)).then(if (can) Modifier.background(K.grad) else Modifier.background(K.block))
                        .tap("중복 확인", can, store::checkId).padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center,
                ) { T("중복 확인", 30, color = if (can) K.onGrad else K.sub, weight = FontWeight.Bold) }
            }
            when {
                l.id.isEmpty() -> Hint("영문 소문자로 시작하고, 영문 소문자·숫자 4~16자예요", null)
                !Rules.ID.matches(l.id) -> Hint("영문 소문자로 시작하고 영문 소문자·숫자 4~16자로 써 주세요", false)
                checked -> if (idOk) Hint("사용할 수 있는 아이디예요", true) else Hint("이미 사용 중인 아이디예요", false)
                else -> Hint("중복 확인을 눌러 주세요", null)
            }
            Spacer(Modifier.height(12.dp))
            PwField(l.pw, store::setPw, "비밀번호", l, store)
            Spacer(Modifier.height(16.dp))
            PwField(l.pw2, store::setPw2, "비밀번호 확인", l, store)
            RuleChips(l.pw, l.id)
            PwMatch(l)
            Spacer(Modifier.height(24.dp))
            BigBtn("다음", store::accountNext, enabled = idOk && Rules.pw(l.pw, l.id).ok && l.pw == l.pw2)
        }
        LoginStep.NEWPW -> {
            Head("새 비밀번호를 만들어 주세요", "8자 이상, 영문과 숫자를\n함께 써 주세요")
            PwField(l.pw, store::setPw, "비밀번호", l, store)
            Spacer(Modifier.height(16.dp))
            PwField(l.pw2, store::setPw2, "비밀번호 확인", l, store)
            RuleChips(l.pw, l.rid)
            PwMatch(l)
            Spacer(Modifier.height(24.dp))
            BigBtn("비밀번호 바꾸기", store::newPwNext, enabled = Rules.pw(l.pw, l.rid).ok && l.pw == l.pw2)
        }
        LoginStep.FDONE -> {
            val any = l.found.isNotEmpty()
            Head(if (any) "가입한 아이디예요" else "찾은 아이디가 없어요", if (any) "보안을 위해 일부를 가려서 보여 줘요" else "이 번호로 만든 아이디가 없어요")
            l.found.forEach { id ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(K.card).border(3.dp, K.line, RoundedCornerShape(Dims.r)),
                    contentAlignment = Alignment.Center,
                ) { T(Rules.maskId(id), 40, weight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.height(12.dp))
            BigBtn("로그인하기", { store.startLogin(LoginMode.LOGIN) })
            Spacer(Modifier.height(16.dp))
            BigBtn("비밀번호 찾기", { store.startLogin(LoginMode.RESETPW) }, ghost = true)
        }
        LoginStep.PROFILE -> {
            Head("이름을 알려 주세요", "가족에게 이 이름으로 보여요")
            Field(l.name, store::setLoginName, "이름 (예: 아빠, 김민수)")
            Spacer(Modifier.height(24.dp))
            BigBtn("다음", store::profileNext, enabled = l.name.isNotBlank())
        }
        LoginStep.TERMS -> {
            val a = l.agree
            Head("약관에 동의해 주세요", "서비스를 쓰려면 필수 항목이 필요해요")
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AgreeRow("모두 동의", null, a.all, bold = true, onClick = store::agreeAll)
                AgreeRow("서비스 이용약관", true, a.tos) { store.toggleAgree("tos") }
                AgreeRow("개인정보 수집·이용", true, a.priv) { store.toggleAgree("priv") }
                AgreeRow("위치 정보 이용", true, a.loc) { store.toggleAgree("loc") }
                AgreeRow("마케팅 정보 수신", false, a.mkt) { store.toggleAgree("mkt") }
            }
            Spacer(Modifier.height(24.dp))
            BigBtn("가입하기", store::join, enabled = a.required)
        }
        LoginStep.WELCOME -> Unit
    }
    Spacer(Modifier.height(40.dp))
}

@Composable
private fun PwMatch(l: LoginState) = when {
    l.pw2.isEmpty() -> Hint("비밀번호를 한 번 더 입력해 주세요", null)
    l.pw == l.pw2 -> Hint("비밀번호가 같아요", true)
    else -> Hint("비밀번호가 서로 달라요", false)
}

@Composable
private fun AgreeRow(label: String, required: Boolean?, on: Boolean, bold: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.r)).border(2.dp, K.line, RoundedCornerShape(Dims.r)).tap(label, onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (on) K.blue else K.card).border(3.dp, if (on) K.blue else K.dash, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { if (on) Ico(Ic.Check, 30.dp, Color.White) }
        Spacer(Modifier.width(20.dp))
        if (required != null) {
            T(if (required) "필수" else "선택", 24, color = if (required) K.blueInk else K.sub, weight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
        }
        T(label, 30, Modifier.weight(1f), weight = if (bold) FontWeight.Bold else FontWeight.Normal)
        if (required == true) T("›", 30, color = K.sub)
    }
}
