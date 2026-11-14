package com.example.tokcar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.tokcar.core.Dock
import com.example.tokcar.core.Phone
import com.example.tokcar.core.Tab
import com.example.tokcar.core.TokStore
import com.example.tokcar.core.UiState
import com.example.tokcar.data.Room
import com.example.tokcar.ui.theme.Dims
import com.example.tokcar.ui.theme.Ic
import com.example.tokcar.ui.theme.K

/** 홈: 탭 내용 + (공유 카드) + 아래 탭바. */
@Composable
fun HomeScreen(s: UiState, store: TokStore, selectedRoom: String? = null) {
    val dock = s.dock(null)
    // 파란 카드는 목록 위에 떠 있고(뒤에 따로 깔린 바탕 없음) 목록은 카드 뒤로 계속 이어진다. 마지막 줄이 카드에 가리지 않게 아래 여백을 둠.
    val bottomPad = if (dock != null) 136.dp else 0.dp
    Column(Modifier.fillMaxSize().background(K.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (s.tab) {
                Tab.CHAT -> ChatList(s, store, selectedRoom, bottomPad)
                Tab.FRIENDS -> FriendsTab(s, store, bottomPad)
                Tab.SETTINGS -> SettingsTab(s, store, bottomPad)
            }
            dock?.let { DockCard(it, store, Modifier.align(Alignment.BottomCenter).padding(start = Dims.cardInset, end = Dims.cardInset, bottom = 12.dp)) }
        }
        TabBar(s, store)
    }
}

/** 아래로 스크롤하면 제목줄이 숨고, 조금만 위로 올리면 다시 나온다(카톡처럼). */
@Composable
private fun rememberHideOnScroll(): Pair<Boolean, NestedScrollConnection> {
    var shown by remember { mutableStateOf(true) }
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) shown = false else if (available.y > 6f) shown = true
                return Offset.Zero
            }
        }
    }
    return shown to conn
}

@Composable
private fun ScrollPane(title: @Composable () -> Unit, content: @Composable (NestedScrollConnection) -> Unit) {
    val (shown, conn) = rememberHideOnScroll()
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = expandVertically(), exit = shrinkVertically()) { title() }
        Box(Modifier.weight(1f).nestedScroll(conn)) { content(conn) }
    }
}

// ───────────────────────── 채팅 목록 ─────────────────────────

@Composable
private fun ChatList(s: UiState, store: TokStore, selectedRoom: String?, bottomPad: Dp) {
    ScrollPane(title = { TopTitle("채팅") { IconCircle(Ic.Plus, "새 방 만들기", store::openNewRoom) } }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Dims.gut, end = Dims.gut, bottom = 24.dp + bottomPad),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(s.orderedRooms, key = { it.id }) { r -> RoomCard(r, s, store, r.id == selectedRoom) }
        }
    }
}

/** 방 카드: 대화 행(묶음 프로필·이름·인원·시각·최근 대화·안 읽은 수) + 경로 띠(목적지 · [안내]/[안내 종료]/[+ 목적지]). */
@Composable
private fun RoomCard(r: Room, s: UiState, store: TokStore, selected: Boolean) {
    val d = s.destOf(r)
    val un = s.unreadOf(r)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.r)).background(K.card)
            .then(if (selected) Modifier.background(K.blueSoft.copy(alpha = .35f)) else Modifier)
            .border(2.dp, K.line, RoundedCornerShape(Dims.r))
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),   // 경로 띠가 카드 좌·우 끝에서 떨어진 간격(12 + 띠 안쪽 4 = 16)과 아래 간격을 같게
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).tap("${r.name} 열기") { store.openRoom(r.id) }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupAvatar(r, 88.dp, s::memberColor)
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 이름 + 인원수는 왼쪽(남는 폭 전체), 시각은 맨 오른쪽 끝(아래 줄의 안 읽은 수 배지와 오른쪽 끝이 맞음)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        T(r.name, 38, Modifier.weight(1f, fill = false), weight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        T(r.headCount.toString(), 28, color = K.sub, weight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    T(s.timeOf(r), 24, color = K.sub)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(s.lastOf(r), 28, Modifier.weight(1f), weight = FontWeight.Medium)
                    if (un > 0) { Spacer(Modifier.width(12.dp)); Badge(un) }
                }
            }
        }
        RouteStrip(
            name = d?.title ?: "아직 없어요", addr = d?.fullAddr ?: "정하면 안내가 시작돼요",
            onDest = { store.openDest(r.id) }, destLabel = if (d != null) "${d.title} 목적지 바꾸기" else "목적지 정하기",
            modifier = Modifier.padding(horizontal = 4.dp),
        ) { CardButton(r, s, store) }
    }
}

/** 목록 카드 단추: 안내 전 [안내] / 이 방 목적지로 안내·공유 중이면 [안내 종료] / 목적지 없음 [+ 목적지]. */
@Composable
private fun CardButton(r: Room, s: UiState, store: TokStore) {
    val d = s.destId(r)
    when {
        d == null -> ActBtn("목적지", Act.GO, { store.directNav(r.id) }, icon = Ic.Plus, label = "목적지 정하기")
        s.isEndSlot(r) -> ActBtn("안내 종료", Act.DANGER, store::endGuide)
        else -> ActBtn("안내", Act.GO, { store.directNav(r.id) }, icon = Ic.Play, label = "${s.destOf(r)?.title} 바로 안내")
    }
}

// ───────────────────────── 아래 파란 카드 ─────────────────────────

/** 15초 카운트·공유 중·공유 방 묻기. 카운트 중에는 바탕이 왼쪽부터 차오른다. */
@Composable
fun DockCard(d: Dock, store: TokStore, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dims.r)
    // 크기·글자·여백은 위쪽 경로 띠(RouteStrip)와 같다: 높이 112, 제목 36, 작은 글 21
    Box(modifier.fillMaxWidth().height(112.dp).clip(shape).background(K.grad)) {
        if (d is Dock.Counting) {
            val frac = 1f - d.remain.toFloat() / d.total.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(112.dp).background(Color.White.copy(alpha = .18f)))
        }
        Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (title, sub) = when (d) {
                is Dock.Move -> d.title to "${d.fromName}에 공유 중"
                // 공유 중·카운트 중도 묻는 상태처럼 상태 문구가 위(큰 글씨), 「○○로 안내 중」이 아래(작은 글씨)
                is Dock.Counting -> "" to d.title
                is Dock.Sharing -> "${d.roomName}에 공유 중" to d.title
                // 묻는 상태는 질문이 위(큰 글씨), 「○○로 안내 중」이 아래(작은 글씨)
                is Dock.AskRoom -> "이 방에 공유할까요?" to d.title
                is Dock.AskList -> "공유할 방을 고르세요" to d.title
            }
            Column(Modifier.weight(1f)) {
                if (d is Dock.Counting) {
                    // 남은 초 숫자만 빨강, 크게(42) · 아주 굵게(바탕에 묻히지 않게). 나머지는 제목 글씨(36 굵게).
                    // 숫자 칸의 폭을 「가능한 모든 숫자 중 가장 넓은 것」으로 고정해서, 숫자가 바뀌어도(1은 좁고 0은 넓음, 두 자리 → 한 자리)
                    // 뒤쪽 글자가 움직이지 않게 한다(한 자리 숫자는 칸 가운데). 두 글자는 같은 기준선에 맞춘다.
                    val numStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 49.sp))
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val slot = remember(d.total, numStyle, density) {
                        with(density) { (1..d.total.coerceAtLeast(1)).maxOf { measurer.measure("$it", numStyle).size.width }.toDp() + 2.dp }
                    }
                    Row {
                        Text("${d.remain}", Modifier.width(slot).alignByBaseline(), color = K.red, style = numStyle, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                        Text(
                            "초 뒤 ${d.roomName}에 공유돼요", Modifier.weight(1f).alignByBaseline(),
                            color = K.onGrad, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 49.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else T(title, 36, color = K.onGrad, weight = FontWeight.Bold)
                T(sub, 21, color = K.onGradSub)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (d) {
                    is Dock.Move -> ActBtn("이 방으로", Act.GO, { store.shareTripTo(d.roomId) })
                    is Dock.Counting -> ActBtn("공유 취소", Act.DANGER, store::cancelShare)
                    is Dock.Sharing -> ActBtn("공유 취소", Act.DANGER, store::stopShare)
                    is Dock.AskRoom -> {
                        ActBtn("공유", Act.GO, { store.shareTripTo(d.roomId) })
                        ActBtn("닫기", Act.TONAL, { store.declineHere(d.roomId) })
                    }
                    is Dock.AskList -> {
                        ActBtn("방 고르기", Act.GO, store::openPick)
                        ActBtn("닫기", Act.TONAL, store::closeAsk)
                    }
                }
            }
        }
    }
}

// ───────────────────────── 탭바 ─────────────────────────

/** 아래 탭바: 친구 · 채팅 · 설정. 글자 없이 아이콘만, 선택된 탭은 아이콘을 그라데이션으로 채움. 채팅 아이콘에 안 읽은 수. */
@Composable
fun TabBar(s: UiState, store: TokStore) {
    BarSurface(Modifier.fillMaxWidth().height(Dims.barH)) {
        Row(Modifier.fillMaxSize().padding(horizontal = Dims.gut), verticalAlignment = Alignment.CenterVertically) {
            TabItem(Tab.FRIENDS, "친구", Ic.Person, Ic.PERSON, s, store, 0)
            TabItem(Tab.CHAT, "채팅", Ic.Chat, Ic.CHAT, s, store, s.totalUnread)
            TabItem(Tab.SETTINGS, "설정", Ic.Gear, Ic.GEAR, s, store, 0, gear = true)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    tab: Tab, label: String, outline: androidx.compose.ui.graphics.vector.ImageVector, lines: List<String>,
    s: UiState, store: TokStore, badge: Int, gear: Boolean = false,
) {
    val on = s.tab == tab
    val g1 = K.ic1
    val g2 = K.ic2
    val hole = K.card
    Box(
        Modifier.weight(1f).height(Dims.barH).semantics { selected = on; contentDescription = label }.tapQuiet { store.openTab(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            if (on) {
                val v = remember(on, g1, g2, hole) { Ic.filled(lines, g1, g2, if (gear) hole else null) }
                Image(rememberVectorPainter(v), null, Modifier.size(56.dp))
            } else {
                Ico(outline, 56.dp, K.sub)
            }
            if (badge > 0) {   // 배지 둘레에 카드색 4dp 고리를 둘러 아이콘과 겹치는 부분을 끊음(웹의 box-shadow 0 0 0 4px card)
                Box(Modifier.align(Alignment.TopEnd).offset(x = 26.dp, y = (-12).dp).clip(RoundedCornerShape(999.dp)).background(K.card).padding(4.dp)) {
                    Badge(badge, size = 34.dp, fontSize = 20)
                }
            }
        }
    }
}

// ───────────────────────── 친구 ─────────────────────────

@Composable
private fun FriendsTab(s: UiState, store: TokStore, bottomPad: Dp) {
    val viewing = s.sharing?.let { s.room(it)?.members }.orEmpty()
    val list = s.friends.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.KOREAN)) { it.name })
    ScrollPane(title = { TopTitle("친구") { IconCircle(Ic.AddFriend, "친구 추가", store::openAddFriend) } }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Dims.gut, end = Dims.gut, bottom = 24.dp + bottomPad)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.r)).background(K.card).border(2.dp, K.line, RoundedCornerShape(Dims.r)).padding(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar("나", 0, 88.dp, me = true)
                    Spacer(Modifier.width(22.dp))
                    Column {
                        T(s.myName, 38, weight = FontWeight.Bold)
                        T(if (s.navActive) "운전 중" else "대기 중", 28, weight = FontWeight.Medium)
                    }
                }
            }
            item { Section("친구 ${list.size}") }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.r)).background(K.card).border(2.dp, K.line, RoundedCornerShape(Dims.r)).padding(vertical = 8.dp)) {
                    list.forEachIndexed { i, f ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(K.bg))
                        Row(
                            Modifier.fillMaxWidth().tap(f.name) { store.openFriend(f.id) }.padding(horizontal = 28.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(f.ini, f.color, 88.dp)
                            Spacer(Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                T(f.name, 32, weight = FontWeight.Bold)
                                if (f.status.isNotEmpty()) T(f.status, 28, color = K.sub)
                            }
                            if (f.name in viewing) Tag("내 정보 보는 중", K.route.copy(alpha = .2f), K.ink)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── 설정 ─────────────────────────

@Composable
private fun SettingsTab(s: UiState, store: TokStore, bottomPad: Dp) {
    Column(Modifier.fillMaxSize()) {
        TopTitle("설정")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dims.gut).padding(bottom = 24.dp + bottomPad)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(K.card).border(2.dp, K.line, RoundedCornerShape(32.dp)).padding(top = 40.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar("나", 0, 128.dp, me = true)
                Spacer(Modifier.height(18.dp))
                T(s.myName, 44, weight = FontWeight.Bold)
                T((s.phoneMask.ifEmpty { Phone.mask(s.myPhone) }) + " · 인증됨", 24, color = K.sub)
            }
            Section("계정")
            Group {
                KvRow("아이디") { T(s.myId, 32, weight = FontWeight.Bold) }
                KvRow("개인정보 처리방침", onClick = store::privacy) { T("›", 32, color = K.sub) }
                KvRow("로그아웃", danger = true, last = true, onClick = store::logout)
            }
            Section("개발용 · 시험")
            Group {
                KvRow("실제 내비 쓰기") {
                    Switch(
                        s.naviReal, store::setNaviReal,
                        colors = SwitchDefaults.colors(checkedTrackColor = K.blue, checkedThumbColor = Color.White),
                    )
                }
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { T("지금: ${s.naviLabel}", 24, color = K.sub) }
                KvRow("내비 연결 + 데모 이동값 (시연용)") {
                    Switch(
                        s.demoMove, store::setDemoMove,
                        colors = SwitchDefaults.colors(checkedTrackColor = K.blue, checkedThumbColor = Color.White),
                    )
                }
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { T("「실제 내비 쓰기」가 켜져 있을 때 적용: 내비 화면에 경로가 뜨고 남은 시간은 줄어들어요", 22, color = K.sub, lines = 2) }
                KvRow("우리 가족 데이터 정리", danger = true, last = true, onClick = store::resetFamilyData)
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.r)).background(K.card).padding(horizontal = 24.dp, vertical = 8.dp)) { content() }
}

@Composable
private fun KvRow(label: String, danger: Boolean = false, last: Boolean = false, onClick: (() -> Unit)? = null, right: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(104.dp).then(if (onClick != null) Modifier.tap(label, onClick = onClick) else Modifier).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(label, 32, Modifier.weight(1f), color = if (danger) K.red else K.ink, weight = if (danger) FontWeight.Bold else FontWeight.Normal)
            right?.invoke()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(2.dp).background(K.line))   // 행 아래 2dp 선(묶음의 마지막 행은 없음)
    }
}

/** 넓은 창 오른쪽(대화방을 안 열었을 때). */
@Composable
fun EmptyRoomPane() {
    Column(Modifier.fillMaxSize().background(K.bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AppIcon(156.dp)
        Spacer(Modifier.height(24.dp))
        T("방을 고르면 여기에 대화가 열려요", 30, color = K.sub)
    }
}
