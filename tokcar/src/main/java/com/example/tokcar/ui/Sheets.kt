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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tokcar.core.Countries
import com.example.tokcar.core.Josa
import com.example.tokcar.core.Modal
import com.example.tokcar.core.NavState
import com.example.tokcar.core.Phone
import com.example.tokcar.core.TokStore
import com.example.tokcar.core.UiState
import com.example.tokcar.core.autoName
import com.example.tokcar.data.PhoneLookup
import com.example.tokcar.ui.theme.Dims
import com.example.tokcar.ui.theme.Ic
import com.example.tokcar.ui.theme.K

/** 떠 있는 팝업. 바깥(어두운 면)을 누르면 닫힌다. */
@Composable
fun ModalLayer(s: UiState, store: TokStore) {
    val m = s.modal ?: return
    val center = m is Modal.Reroute || m is Modal.Leave || m is Modal.Country
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).tapQuiet(store::closeModal),
        contentAlignment = if (center) Alignment.Center else Alignment.BottomCenter,
    ) {
        when (m) {
            is Modal.Dest -> DestSheet(s, store, m)
            Modal.Pick -> PickSheet(s, store)
            is Modal.Reroute -> RerouteDialog(s, store, m)
            is Modal.NewRoom -> NewRoomSheet(s, store, m)
            is Modal.Invite -> InviteSheet(s, store, m)
            Modal.Leave -> LeaveDialog(s, store)
            is Modal.FriendProfile -> ProfileSheet(s, store, m)
            is Modal.AddFriend -> AddFriendSheet(s, store, m)
            Modal.Country -> CountryDialog(s, store)
        }
    }
}

/** 아래에서 올라오는 시트(위 모서리만 둥글게, 폭은 반폭 창 841 이하). */
@Composable
private fun Sheet(title: String?, onClose: (() -> Unit)?, maxH: Dp = 1100.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.widthIn(max = 841.dp).fillMaxWidth().heightIn(max = maxH)
            .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)).background(K.bg).tapQuiet { }
            .padding(start = Dims.gut, end = Dims.gut, top = 16.dp, bottom = 28.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(72.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(K.dash))
        }
        if (title != null) SheetHead(title, onClose)
        content()
    }
}

@Composable
private fun SheetHead(title: String, onClose: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, 42, Modifier.weight(1f), weight = FontWeight.Bold)
        if (onClose != null) IconCircle(Ic.X, "닫기", onClose)
    }
}

/** 가운데 뜨는 작은 창(확인창). */
@Composable
private fun Dialog(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(.9f).clip(RoundedCornerShape(44.dp)).background(K.bg).tapQuiet { }.padding(36.dp),
        content = content,
    )
}

@Composable
private fun Note(text: String) = T(text, 26, Modifier.padding(bottom = 8.dp), color = K.sub, lines = 3)

/** 두 단추 줄: 보조(연한) + 주(그라데이션). */
@Composable
private fun TwoButtons(ghost: String, onGhost: () -> Unit, pri: String, onPri: () -> Unit, danger: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.weight(1f).height(Dims.hL).clip(RoundedCornerShape(Dims.r)).border(2.dp, K.dash, RoundedCornerShape(Dims.r)).tap(ghost, onClick = onGhost),
            contentAlignment = Alignment.Center,
        ) { T(ghost, 34, color = K.ink, weight = FontWeight.Bold) }
        if (danger) {
            Box(
                Modifier.weight(1f).height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(K.red).tap(pri, onClick = onPri),
                contentAlignment = Alignment.Center,
            ) { T(pri, 34, color = K.onRed, weight = FontWeight.Bold) }
        } else BigBtn(pri, onPri, Modifier.weight(1f))
    }
}

/** 목록 한 줄: 둥근 네모 아이콘 + 이름·작은 글 + 오른쪽 표시. */
@Composable
private fun OptRow(title: String, sub: String?, onClick: () -> Unit, lead: @Composable () -> Unit, trail: String = "›") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(Dims.r)).border(2.dp, K.line, RoundedCornerShape(Dims.r)).tap(title, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            T(title, 32, weight = FontWeight.Bold)
            if (!sub.isNullOrEmpty()) T(sub, 24, color = K.sub)
        }
        T(trail, 32, color = K.sub)
    }
}

@Composable
private fun PinBox() = Box(Modifier.size(64.dp).clip(RoundedCornerShape(34)).background(K.blueSoft), contentAlignment = Alignment.Center) {
    Ico(Ic.Pin, 34.dp, K.blueInk)
}

// ───────────────────────── 목적지 ─────────────────────────

/**
 * 목적지 모달: 최근 목적지(누르면 안내) + 이 방에서 간 곳 + 내비 최근 목적지 + 새 목적지 검색([말하기]).
 * 데모 내비에서는 말한 뒤 가짜 검색 결과 목록이 보인다.
 */
@Composable
private fun DestSheet(s: UiState, store: TokStore, m: Modal.Dest) {
    val r = s.room(m.roomId) ?: return
    val cur = s.destId(r)
    val d = s.place(cur)
    val active = s.navActive && s.nav.placeId == cur
    val arrived = s.nav.state == NavState.ARRIVED && s.nav.placeId == cur
    val tap = d != null && !active && !arrived
    val note = if (s.navActive) {
        if (s.sharing != null) "안내 중에 목적지를 바꾸면 공유 중인 방에 알려요" else "안내 중에 목적지를 바꿔도 공유는 시작되지 않아요"
    } else "고르면 안내가 시작되고 15초 뒤 ${r.name}에 공유돼요 (취소 가능)"
    val none = s.histOf(r).isEmpty()
    val mine = if (none) s.favs.filter { it != cur } else s.histOf(r).filter { it != cur }.take(3)
    val recents = s.recent.filter { it !in s.favs && it != cur && it !in mine }.take(2)
    Sheet("목적지", store::closeModal) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Note(note)
            Section(if (active) "안내 중" else if (arrived) "도착함" else "최근 목적지", Modifier.padding(top = 0.dp))
            RouteStrip(
                d?.title ?: "아직 정하지 않았어요", d?.fullAddr ?: "아래에서 고르면 안내가 시작돼요",
                onDest = if (tap) store::startDest else null,
                destLabel = if (s.navActive) "이 목적지로 안내 시작" else "안내 시작, 15초 뒤 ${r.name}에 공유",
            )
            if (mine.isNotEmpty()) {
                Section(if (none) "내비 즐겨찾기" else "이 방에서 간 곳")
                mine.forEach { pid -> s.place(pid)?.let { p -> OptRow(p.title, p.fullAddr, { store.setDest(r.id, pid) }, { PinBox() }) } }
            }
            if (recents.isNotEmpty()) {
                Section("내비 최근 목적지")
                recents.forEach { pid -> s.place(pid)?.let { p -> OptRow(p.title, p.fullAddr, { store.setDest(r.id, pid) }, { PinBox() }) } }
            }
            Section("새 목적지 검색")
            Row(
                Modifier.fillMaxWidth().height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(if (s.listening) K.red else K.strong).tap("말해서 내비에서 새 목적지 검색", onClick = store::destMic),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (s.listening) Color.White else K.onStrong   // 듣는 중에는 빨강
                Ico(Ic.Mic, 40.dp, fg)
                Spacer(Modifier.width(16.dp))
                T(if (s.listening) "듣는 중…" else "말하기", 34, color = fg, weight = FontWeight.ExtraBold)
            }
            if (m.results) {
                Section("내비 검색 결과 · \"${m.query}\"")
                s.navResults.forEach { pid -> s.place(pid)?.let { p -> OptRow(p.title, p.fullAddr, { store.setDest(r.id, pid) }, { PinBox() }) } }
            }
        }
    }
}

// ───────────────────────── 공유할 방 고르기 ─────────────────────────

@Composable
private fun PickSheet(s: UiState, store: TokStore) {
    val pid = s.nav.placeId?.takeIf { s.navActive } ?: return
    val p = s.place(pid) ?: return
    val ord = s.orderedRooms
    val rooms = ord.filter { s.destId(it) == pid } + ord.filter { s.destId(it) != pid }
    Sheet("공유할 방 고르기", store::closeModal) {
        Note("${Josa.ro(p.title)} 안내 중이에요. 방을 누르면 그 방의 목적지가 ${Josa.ro(p.title)} 바뀌고 15초 뒤 공유돼요 (취소 가능)")
        Column(Modifier.verticalScroll(rememberScrollState())) {
            rooms.forEach { r ->
                OptRow(r.name, r.members.joinToString(", "), { store.shareTripTo(r.id) }, { GroupAvatar(r, 64.dp, s::memberColor) })
            }
        }
    }
}

// ───────────────────────── 경로 바꾸기 확인 ─────────────────────────

@Composable
private fun RerouteDialog(s: UiState, store: TokStore, m: Modal.Reroute) {
    val cur = s.place(s.nav.placeId) ?: return
    val nw = s.place(m.placeId) ?: return
    Dialog {
        T("경로를 바꿀까요?", 42, weight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(K.card).padding(20.dp)) {
                T("지금", 22, color = K.sub); T(cur.short, 32, weight = FontWeight.Bold)
            }
            T("  →  ", 32, color = K.sub)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(K.blueSoft).padding(20.dp)) {
                T("바꿀 곳", 22, color = K.blueInk); T(nw.short, 32, color = K.blueInk, weight = FontWeight.Bold)
            }
        }
        Note("지금 안내는 끝나고 새 경로로 안내해요." + if (s.sharing != null) " 공유 중인 방의 경로 카드도 새 목적지로 바뀌어요." else "")
        TwoButtons("취소", store::rerouteNo, "바꾸기", store::rerouteOk)
    }
}

// ───────────────────────── 새 방·초대 ─────────────────────────

@Composable
private fun FriendPick(s: UiState, ids: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    val coll = java.text.Collator.getInstance(java.util.Locale.KOREAN)
    s.friends.filter { it.id in ids }.sortedWith(compareBy(coll) { it.name }).forEach { f ->
        val on = f.id in selected
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).tap(f.name) { onToggle(f.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(f.ini, f.color, 68.dp)
            Spacer(Modifier.width(20.dp))
            T(f.name, 32, Modifier.weight(1f), weight = FontWeight.Bold)
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (on) K.blue else Color.Transparent).border(3.dp, if (on) K.blue else K.dash, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (on) Ico(Ic.Check, 30.dp, K.onBlue) }
        }
    }
}

@Composable
private fun NewRoomSheet(s: UiState, store: TokStore, m: Modal.NewRoom) {
    val names = m.selected.mapNotNull { s.friend(it)?.name }
    val auto = if (names.isEmpty()) "방 이름" else autoName(names)
    Sheet("새 방 만들기", store::closeModal) {
        Section("방 이름", Modifier.padding(top = 0.dp))
        if (s.driving) {
            Box(Modifier.fillMaxWidth().height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(K.block).padding(horizontal = 32.dp), contentAlignment = Alignment.CenterStart) {
                T(auto, 32, color = K.sub)
            }
        } else Field(m.name, store::setNewRoomName, auto)
        Section("함께할 친구" + if (names.isNotEmpty()) " · ${names.size}명" else "")
        Column(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(Dims.r)).border(2.dp, K.line, RoundedCornerShape(Dims.r)).verticalScroll(rememberScrollState())) {
            FriendPick(s, s.friends.map { it.id }, m.selected, store::toggleNewMember)
        }
        Spacer(Modifier.height(20.dp))
        BigBtn(if (names.isNotEmpty()) "방 만들기 · ${names.size + 1}명" else "친구를 골라 주세요", store::createRoom, enabled = names.isNotEmpty())
    }
}

@Composable
private fun InviteSheet(s: UiState, store: TokStore, m: Modal.Invite) {
    val r = s.room(s.roomId) ?: return
    val cand = s.friends.filter { it.name !in r.members }.map { it.id }
    Sheet("대화상대 초대", store::closeModal) {
        Section("초대할 친구" + if (m.selected.isNotEmpty()) " · ${m.selected.size}명" else "", Modifier.padding(top = 0.dp))
        Column(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(Dims.r)).border(2.dp, K.line, RoundedCornerShape(Dims.r)).verticalScroll(rememberScrollState())) {
            if (cand.isEmpty()) T("초대할 친구가 없어요", 28, Modifier.fillMaxWidth().padding(vertical = 40.dp), color = K.sub, align = TextAlign.Center)
            else FriendPick(s, cand, m.selected, store::toggleInvite)
        }
        Spacer(Modifier.height(20.dp))
        BigBtn(if (m.selected.isNotEmpty()) "초대하기 · ${m.selected.size}명" else "친구를 골라 주세요", store::inviteDone, enabled = m.selected.isNotEmpty())
    }
}

@Composable
private fun LeaveDialog(s: UiState, store: TokStore) {
    val r = s.room(s.roomId) ?: return
    Dialog {
        T("채팅방을 나갈까요?", 42, weight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Note("${r.name}에서 나가면 대화 목록에서 사라져요.")
        TwoButtons("취소", store::closeModal, "나가기", store::leaveRoom, danger = true)
    }
}

// ───────────────────────── 친구 ─────────────────────────

@Composable
private fun ProfileSheet(s: UiState, store: TokStore, m: Modal.FriendProfile) {
    val f = s.friend(m.friendId) ?: return
    Sheet(null, null) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(f.ini, f.color, 168.dp)
            Spacer(Modifier.height(20.dp))
            T(f.name, 44, weight = FontWeight.Bold)
            T(f.status.ifEmpty { "상태 메시지가 없어요" }, 28, color = K.sub)
        }
        TwoButtons("닫기", store::closeModal, "1:1 채팅", { store.openDm(f.id) })
    }
}

/** 친구 추가: 위쪽에 내 QR·코드, 아래에 전화번호(하이픈 자동). 주행 중에는 번호 입력만 막힌다. */
@Composable
private fun AddFriendSheet(s: UiState, store: TokStore, m: Modal.AddFriend) {
    Column(Modifier.widthIn(max = 841.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.padding(bottom = 28.dp).clip(RoundedCornerShape(36.dp)).background(K.card).tapQuiet { }.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Qr(252.dp)
            Spacer(Modifier.height(14.dp))
            T("내 QR · 코드", 24, color = K.sub, weight = FontWeight.Bold)
            T("7K2 · 9QX", 40, weight = FontWeight.ExtraBold)
            T("친구가 폰 앱에서 QR을 찍거나 코드를 입력하면 친구가 돼요. (예시 코드)", 22, Modifier.widthIn(max = 520.dp), color = K.sub, lines = 2, align = TextAlign.Center)
        }
        Sheet("친구 추가", store::closeModal) {
            if (s.driving) {
                Box(Modifier.fillMaxWidth().height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(K.block).padding(horizontal = 32.dp), contentAlignment = Alignment.CenterStart) {
                    T("주행 중에는 번호를 입력할 수 없어요", 30, color = K.sub)
                }
            } else Field(m.tel, store::setAddTel, "친구 전화번호", phone = true)
            Spacer(Modifier.height(16.dp))
            BigBtn("친구 찾기", store::findFriend, enabled = !s.driving && Phone.isValid(m.tel))
            when (val res = m.res) {
                is PhoneLookup.Found -> Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(Dims.r)).background(K.card).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(res.friend.ini, res.friend.color, 84.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) { T(res.friend.name, 32, weight = FontWeight.Bold); T(Phone.mask(res.tel), 26, color = K.sub) }
                    ActBtn("친구 추가", Act.PRIMARY, store::addFoundFriend)
                }
                is PhoneLookup.Already -> T("${res.friend.name}님은 이미 친구예요", 28, Modifier.padding(top = 20.dp))
                PhoneLookup.Self -> T("내 번호예요", 28, Modifier.padding(top = 20.dp))
                is PhoneLookup.NotRegistered -> Column(Modifier.padding(top = 20.dp)) {
                    T("아직 가입하지 않은 번호예요", 28)
                    Spacer(Modifier.height(12.dp))
                    BigBtn("초대 문자 보내기", store::inviteSms, ghost = true, height = 80.dp)
                }
                null -> Unit
            }
            T("시안 예시: 010-2222-3333, 010-4444-5555는 가입자이고 그 밖의 번호는 미가입으로 처리해요.", 22, Modifier.padding(top = 16.dp), color = K.sub, lines = 2)
        }
    }
}

/** 예시 QR(모양만). */
@Composable
private fun Qr(size: Dp) {
    val n = 11
    var seed = 7
    val pad = 14.dp
    val cell = (size - pad * 2) / n
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.size(size).clip(shape).background(Color.White).border(2.dp, K.line, shape).padding(pad)) {
        for (y in 0 until n) Row {
            for (x in 0 until n) {
                val finder = (x < 3 && y < 3) || (x > n - 4 && y < 3) || (x < 3 && y > n - 4)
                seed = (seed * 31 + x * 17 + y * 13) % 97
                Box(Modifier.size(cell).background(if (finder || seed % 3 == 0) Color(0xFF10203F) else Color.Transparent))
            }
        }
    }
}

// ───────────────────────── 나라 고르기 ─────────────────────────

@Composable
private fun CountryDialog(s: UiState, store: TokStore) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(.9f).heightIn(max = 1000.dp).clip(RoundedCornerShape(44.dp)).background(K.bg).tapQuiet { }.padding(28.dp),
    ) {
        SheetHead("국가 선택", store::closeModal)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Countries.all.forEach { c ->
                val on = c.code == s.login.country
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(Dims.r)).background(if (on) K.blueSoft else K.card)
                        .tap(c.name) { store.pickCountry(c.code) }.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Flag(c.code, Modifier.size(54.dp, 36.dp))
                    Spacer(Modifier.width(20.dp))
                    T(c.name, 32, Modifier.weight(1f), weight = FontWeight.Bold)
                    T("+${c.cc}", 28, color = K.sub)
                    Spacer(Modifier.width(16.dp))
                    T(if (on) "✓" else "", 30, color = K.blue, weight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────── 입력칸 ─────────────────────────

/** 입력칸(높이 100, 연한 바탕, 모서리 30). */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, phone: Boolean = false, password: Boolean = false,
    center: Boolean = false, numeric: Boolean = false, trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(Dims.hL).keepKeyboard().clip(RoundedCornerShape(Dims.r)).background(K.bg).border(2.dp, K.line, RoundedCornerShape(Dims.r)).padding(start = 32.dp, end = if (trailing != null) 12.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
            if (value.isEmpty()) T(hint, 32, color = K.sub)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(fontSize = 32.sp, color = K.ink, textAlign = if (center) TextAlign.Center else TextAlign.Start),
                cursorBrush = SolidColor(K.blue),
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        phone -> KeyboardType.Phone
                        numeric -> KeyboardType.Number
                        password -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                ),
            )
        }
        trailing?.invoke()
    }
}
