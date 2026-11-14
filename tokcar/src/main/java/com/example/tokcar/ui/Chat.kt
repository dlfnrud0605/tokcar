package com.example.tokcar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tokcar.core.Josa
import com.example.tokcar.core.TokStore
import com.example.tokcar.core.UiState
import com.example.tokcar.core.guDong
import com.example.tokcar.core.leftParts
import com.example.tokcar.data.LocKind
import com.example.tokcar.data.LocMsg
import com.example.tokcar.data.MineMsg
import com.example.tokcar.data.Msg
import com.example.tokcar.data.MyLocMsg
import com.example.tokcar.data.MyMsg
import com.example.tokcar.data.MyRouteMsg
import com.example.tokcar.data.Room
import com.example.tokcar.data.ShareEnd
import com.example.tokcar.data.SysMsg
import com.example.tokcar.data.TextMsg
import com.example.tokcar.navi.Congestion
import com.example.tokcar.navi.TrafficSegment
import com.example.tokcar.ui.theme.Dims
import com.example.tokcar.ui.theme.Ic
import com.example.tokcar.ui.theme.K

/** 대화방: 제목줄(뒤로 · 이름+인원 · 메뉴) + 경로 띠 + 메시지 + (공유 카드) + 입력 바. */
@Composable
fun ChatRoom(s: UiState, store: TokStore, room: Room, showBack: Boolean = true) {
    var barPx by remember { mutableIntStateOf(0) }   // 입력 바의 실제 높이(여러 줄이면 커짐): + 메뉴가 그 바로 위에 뜨게 함
    Box(Modifier.fillMaxSize().background(K.bg)) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = Dims.cardInset), verticalAlignment = Alignment.CenterVertically) {   // 뒤로·메뉴 단추도 카드와 같은 좌우 끝
            if (showBack) { IconCircle(Ic.Back, "목록으로", store::back); Spacer(Modifier.width(20.dp)) }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                T(room.name, 42, Modifier.weight(1f, fill = false), weight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                T(room.headCount.toString(), 30, color = K.sub, weight = FontWeight.Bold)
            }
            IconCircle(Ic.Menu, "대화상대 보기", store::openDrawer)
        }
        // 위쪽 경로 띠와 아래 파란 카드는 메시지 위에 떠 있다(뒤에 따로 깔린 바탕 없음). 메시지는 카드 뒤로 이어지고, 첫·마지막 메시지가 카드에 가리지 않게 여백을 둠.
        val dock = s.dock(room)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Messages(s, store, room, Modifier.fillMaxSize(), topPad = 112.dp + 20.dp, bottomPad = if (dock != null) 112.dp + 12.dp else 0.dp)
            TripStrip(s, store, room, Modifier.align(Alignment.TopCenter).padding(horizontal = Dims.cardInset))
            dock?.let { DockCard(it, store, Modifier.align(Alignment.BottomCenter).padding(start = Dims.cardInset, end = Dims.cardInset, bottom = 12.dp)) }
        }
        Composer(s, store, room, Modifier.onSizeChanged { barPx = it.height })
    }
    // + 메뉴: 입력 바 바로 위 왼쪽. 메뉴 밖을 누르면 닫힘.
    if (s.plusOpen && !s.driving) {
        Box(Modifier.fillMaxSize().tapQuiet(store::closePlus))
        PlusMenu(store, Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = with(LocalDensity.current) { barPx.toDp() } + 8.dp))
    }
    }
}

/** 대화방 위쪽 경로 띠: 안내 전 [안내] / 이 방 목적지로 안내·공유 중이면 [안내 종료] / 다른 곳으로 안내 중이면 [안내](누르면 이 방 목적지로 바뀜). */
@Composable
private fun TripStrip(s: UiState, store: TokStore, room: Room, modifier: Modifier) {
    val d = s.destOf(room)    // 도착한 뒤에도 방의 최근 목적지를 그대로 보여 준다(「새 목적지」로 바꾸지 않음)
    val name = d?.title ?: "목적지 정하기"
    val addr = d?.fullAddr ?: "눌러서 정해요"
    RouteStrip(name, addr, onDest = { store.openDest(room.id) }, modifier = modifier) {
        when {
            d == null -> Unit
            s.navActive && s.isEndSlot(room) -> ActBtn("안내 종료", Act.DANGER, store::endGuide)
            s.navActive && s.nav.placeId != d.id -> ActBtn("안내", Act.GO, { store.directNav(room.id) }, icon = Ic.Play, label = Josa.ro(d.title) + " 안내 변경")
            else -> ActBtn("안내", Act.GO, { store.directNav(room.id) }, icon = Ic.Play, label = d.title + " 바로 안내")
        }
    }
}

@Composable
private fun Messages(s: UiState, store: TokStore, room: Room, modifier: Modifier, topPad: Dp, bottomPad: Dp) {
    val list = s.msgs[room.id].orEmpty()
    val st = rememberLazyListState()
    LaunchedEffect(room.id, list.size) { if (list.isNotEmpty()) st.animateScrollToItem(list.size - 1) }
    LazyColumn(
        modifier.fillMaxWidth(), state = st,
        contentPadding = PaddingValues(start = Dims.cardInset, end = Dims.cardInset, top = topPad, bottom = 24.dp + bottomPad), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(list, key = { it.id }) { m -> MsgRow(m, s, store, room) }
    }
}

@Composable
private fun MsgRow(m: Msg, s: UiState, store: TokStore, room: Room) {
    when (m) {
        is SysMsg -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(RoundedCornerShape(Dims.rTag)).border(2.dp, K.line, RoundedCornerShape(Dims.rTag)).padding(horizontal = 20.dp, vertical = 8.dp)) {
                T(m.text, 24, color = K.sub, lines = 2, align = TextAlign.Center)
            }
        }
        is TextMsg -> Other(m.who, s, m.unread) { Bubble(m.text, mine = false) }
        is LocMsg -> Other(m.who, s, m.unread) { LocCard(m, s, store, room) }
        is MineMsg -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
            if (m.unreadCount > 0) {
                T(m.unreadCount.toString(), 22, Modifier.padding(end = 10.dp, bottom = 4.dp), color = K.blueInk, weight = FontWeight.ExtraBold)
            }
            when (m) {
                is MyMsg -> Bubble(m.text, mine = true)
                is MyLocMsg -> MineLocCard(m)
                is MyRouteMsg -> MineRouteCard(m, if (m.state == null && s.sharing == room.id) s.traffic else null)    // 정체 구간은 지금 공유 중인 카드에만
            }
        }
    }
}

/** 남이 보낸 것: 얼굴 + 이름 + 내용 + 안 읽은 사람 수(내 메시지와 같은 숫자, 내용 오른쪽 아래). */
@Composable
private fun Other(who: String, s: UiState, unread: Int, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Avatar(who, s.memberColor(who), 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f, fill = false)) {
            T(who, 24, Modifier.padding(bottom = 6.dp), color = K.sub, weight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f, fill = false)) { content() }
                if (unread > 0) T(unread.toString(), 22, Modifier.padding(start = 10.dp, bottom = 4.dp), color = K.blueInk, weight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.width(60.dp))
    }
}

/** 말풍선: 남의 것은 흰색, 내 것은 그라데이션 + 흰 글자. */
@Composable
private fun Bubble(text: String, mine: Boolean) {
    val shape = if (mine) RoundedCornerShape(30.dp, 30.dp, 8.dp, 30.dp) else RoundedCornerShape(30.dp, 30.dp, 30.dp, 8.dp)
    Box(
        Modifier.widthIn(max = 560.dp).clip(shape).background(if (mine) K.bubGrad else SolidColor(K.bubIn)).padding(horizontal = 28.dp, vertical = 20.dp),
    ) { T(text, 30, color = if (mine) K.onBub else K.ink, lines = 20, lineHeight = 42.sp) }
}

private val CardMax = 560.dp

/**
 * 위치·경로 카드 안 글자색 묶음. 상대 카드는 흰 면(상대 말풍선 색), 내 카드는 그라데이션(내 말풍선 색) 위라서 글자·초록이 서로 다르다.
 * 내 카드의 작은 글자(주소·시각·「출발」「도착」·단위)도 큰 글자와 같은 색으로 또렷하게 둔다(상대 카드만 작은 글자를 연하게).
 */
private class CardInk(val fg: Color, val sub: Color, val label: Color, val num: Color, val track: Color)

private val LocalCardInk = compositionLocalOf { CardInk(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified) }

/**
 * 위치·경로 카드의 면: 상대가 보낸 것은 상대 메시지 색(흰 말풍선), 내가 보낸 것은 내 메시지 색(그라데이션 말풍선)과 같다.
 * 남은 시간 숫자의 초록은 바탕에 맞춰 진하기를 바꿈(흰 면 = 진한 초록, 어두운 면 = 밝은 초록).
 */
@Composable
private fun ChatCard(mine: Boolean, content: @Composable () -> Unit) {
    val shape = if (mine) RoundedCornerShape(30.dp, 30.dp, 8.dp, 30.dp) else RoundedCornerShape(30.dp, 30.dp, 30.dp, 8.dp)
    val fg = if (mine) K.onBub else K.ink
    val num = if (mine || K.dark) Color(0xFF2BD4A0) else Color(0xFF0E8F6E)   // 어두운 면(내 카드 낮·밤, 상대 카드 밤)은 밝은 초록, 흰 면은 진한 초록
    val ink = CardInk(
        fg, sub = if (mine) fg else fg.copy(alpha = .78f), label = if (mine) fg else fg.copy(alpha = .78f * .75f),
        num = num, track = fg.copy(alpha = .28f),
    )
    val bgMod = if (mine) Modifier.background(K.bubGrad) else Modifier.background(K.bubIn)
    CompositionLocalProvider(LocalCardInk provides ink) {
        // 폭은 남이 보낸 카드·내 카드 모두 같게: 쓸 수 있는 폭을 채우되 최대 560(버튼이 있든 없든 같은 크기)
        Column(Modifier.widthIn(max = CardMax).fillMaxWidth().clip(shape).then(bgMod).padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 28.dp)) {
            content()
        }
    }
}

/**
 * 경로 공유 카드 본문(웹 시안과 같음): 이름(3줄까지) + 오른쪽 끝 남은 시간(또는 도착·공유 종료) / 「출발」「도착」 두 줄(각각 2줄까지) /
 * 진행 막대 / 도착 예정 글자. 긴 이름·주소가 …로 잘리지 않고 줄을 바꿔 보이게 한다.
 */
@Composable
private fun RouteBody(name: String, from: String, to: String, min: Int, pct: Int, eta: String, end: ShareEnd?, fill: Boolean, traffic: List<TrafficSegment>? = null) {
    val ink = LocalCardInk.current
    val done = end == ShareEnd.ARRIVED
    val off = end == ShareEnd.STOPPED
    Row(verticalAlignment = Alignment.Bottom) {
        T(name, 38, Modifier.weight(1f), color = ink.fg, weight = FontWeight.ExtraBold, lines = 3)
        Spacer(Modifier.width(12.dp))
        when {
            done -> T("도착", 40, color = ink.num, weight = FontWeight.ExtraBold)
            off -> T("공유 종료", 28, color = ink.sub, weight = FontWeight.Bold)
            fill -> T("…", 40, color = ink.num, weight = FontWeight.ExtraBold)
            // 남은 시간은 일·시간·분으로: 숫자는 크게(초록), 단위는 작게(카드의 보조 글자색)
            else -> Row(verticalAlignment = Alignment.Bottom) {
                leftParts(min).forEachIndexed { i, (n, unit) ->
                    if (i > 0) Spacer(Modifier.width(8.dp))
                    T(n.toString(), 48, color = ink.num, weight = FontWeight.ExtraBold)
                    T(unit, 26, Modifier.padding(bottom = 6.dp, start = 2.dp), color = ink.sub, weight = FontWeight.Bold)
                }
            }
        }
    }
    FromTo("출발", from, ink)
    FromTo("도착", to, ink)
    val pc = if (done) 1f else (pct.coerceIn(4, 96) / 100f)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp).height(24.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        // 바탕 막대는 원래 회색. 내비가 준 구간(원활=파랑, 보통·서행·정체=노랑·주황·빨강)을 그 위치에 색으로 얹는다.
        // 구간 정보가 없는 곳(또는 정보가 아예 없을 때)은 회색 그대로. 지나온 부분은 아래 초록 막대가 덮는다.
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(ink.track)) {
            if (!traffic.isNullOrEmpty()) Canvas(Modifier.fillMaxSize()) {
                val segs = traffic.sortedBy { it.from }
                segs.forEachIndexed { i, seg ->
                    val next = segs.getOrNull(i + 1)
                    val left = size.width * seg.from
                    // 바로 이어지는 다음 구간이 있으면 그쪽으로 1.5dp 겹쳐 그린다(다음 구간이 위에 덮음). 소수점 픽셀 반올림 때문에 경계에 회색 바탕이 비치는 틈을 막는다.
                    val contiguous = next != null && next.from - seg.to < 0.002f
                    val right = size.width * seg.to + if (contiguous) 1.5.dp.toPx() else 0f
                    drawRect(seg.level.color(), Offset(left, 0f), Size((right - left).coerceAtLeast(0f), size.height))
                }
            }
        }
        Box(Modifier.width(w * pc).height(12.dp).clip(RoundedCornerShape(6.dp)).background(if (off) ink.fg.copy(alpha = .4f) else K.route))
        if (!off) Box(Modifier.offset(x = w * pc - 12.dp).size(24.dp).clip(CircleShape).background(K.route).border(5.dp, K.route.copy(alpha = .28f), CircleShape))
    }
    val last = when {
        done -> "$eta 도착"
        off -> "경로 공유를 멈췄어요"
        fill -> "도착 예정 시각을 받는 중이에요"
        else -> "$eta 도착 예정"
    }
    T(last, 24, Modifier.fillMaxWidth(), color = ink.fg, weight = FontWeight.Bold, lines = 2)
}

/** 원활한 구간 색(내비 띠와 같은 파랑). */
private val TRAFFIC_FAST = Color(0xFF4DA3FF)    // 원활

/** 정체 단계 색(내비 막대와 같은 노랑·주황·빨강). 낮·밤, 내 카드·상대 카드 어디서나 같게 쓴다. */
private fun Congestion.color(): Color = when (this) {
    Congestion.FAST -> TRAFFIC_FAST
    Congestion.MODERATE -> Color(0xFFF2C94C)
    Congestion.SLOW -> Color(0xFFF2994A)
    Congestion.HEAVY -> Color(0xFFEB5757)
}

/** 「출발 ○○」「도착 ○○」 한 줄: 라벨은 작고 연하게, 주소는 폭을 채우며 2줄까지 줄 바꿈. */
@Composable
private fun FromTo(label: String, text: String, ink: CardInk) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // 라벨(22)과 주소(26)의 글자 크기가 달라서 첫 줄 기준선을 맞춰야 같은 높이로 보인다
        T(label, 22, Modifier.width(44.dp).alignByBaseline(), color = ink.label, weight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        T(text, 26, Modifier.weight(1f).alignByBaseline(), color = ink.sub, lines = 2)
    }
}

/** 가족이 보낸 현재 위치·경로 공유 카드. 현재 위치는 [안내] + [경유지], 경로 공유는 [안내]만. */
@Composable
private fun LocCard(m: LocMsg, s: UiState, store: TokStore, room: Room) {
    val p = s.places[m.placeId] ?: return
    val here = m.kind == LocKind.HERE
    val same = s.sameTrip(m.placeId)
    val viaHere = s.vias.any { it.placeId == m.placeId }
    ChatCard(mine = false) {
        val ink = LocalCardInk.current
        if (here) Tag("위치 공유", K.hereTag, K.hereTagInk, icon = Ic.Pin) else Tag("경로 공유", K.route, K.routeInk, icon = Ic.Route)
        Spacer(Modifier.height(12.dp))
        if (here) {
            T(p.title, 38, color = ink.fg, weight = FontWeight.ExtraBold)
            T(p.fullAddr, 26, color = ink.sub, lines = 2)
            T("${m.time}에 보낸 위치예요", 24, color = ink.sub)
        } else {
            RouteBody(p.title, m.from, p.fullAddr, m.min, m.pct, m.eta, m.end, fill = false)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val goMod = if (here) Modifier.weight(1f) else Modifier.fillMaxWidth()
            CardBtn(if (same) "안내 중" else "안내", on = same, primary = true, modifier = goMod, play = true) { store.goShared(room.id, m.placeId) }
            if (here) CardBtn(if (viaHere) "경유 중" else "경유지", on = viaHere, primary = false, modifier = Modifier.weight(1f)) { store.toggleVia(room.id, m.placeId) }
        }
    }
}

/** 카드 안 단추(상대 카드 면 위): [안내]는 그라데이션, [경유지]는 입력칸과 같은 연한 면(via). 켜진 상태([안내 중]·[경유 중])는 연한 파랑 면(viaOn). */
@Composable
private fun CardBtn(text: String, on: Boolean, primary: Boolean, modifier: Modifier, play: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Dims.r)
    val bgMod = when {
        on -> Modifier.background(K.viaOn)
        primary -> Modifier.background(K.grad)
        else -> Modifier.background(K.via)
    }
    val fg = when {
        on -> K.blueInk
        primary -> K.onGrad
        else -> K.ink
    }
    Row(
        modifier.height(80.dp).clip(shape).then(bgMod).tap(text, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (play) { Ico(Ic.Play, 26.dp, fg); Spacer(Modifier.width(8.dp)) }
        T(text, 28, color = fg, weight = FontWeight.Bold)
        if (!primary && on) { Spacer(Modifier.width(8.dp)); Ico(Ic.X, 24.dp, fg) }
    }
}

@Composable
private fun MineLocCard(m: MyLocMsg) = ChatCard(mine = true) {
    val ink = LocalCardInk.current
    // 남이 보낸 현재 위치 카드와 같은 틀: 태그 / 큰 제목 / 주소 / 시각 줄(내 위치는 장소 이름이 없어서 동 이름이 제목, 구·동이 주소 줄)
    Tag("위치 공유", K.hereTag, ink.fg, icon = Ic.Pin)   // 태그 글자는 카드 글자색을 따른다(낮 흰색, 밤 어두운색)
    Spacer(Modifier.height(12.dp))
    T(com.example.tokcar.core.Address.area(m.name), 38, color = ink.fg, weight = FontWeight.ExtraBold)   // 큰 글씨: 구 동까지
    T(m.name, 26, color = ink.sub, lines = 2)   // 작은 글씨: 전체 주소
    T("${m.time}에 보낸 위치예요", 24, color = ink.sub)
}

@Composable
private fun MineRouteCard(m: MyRouteMsg, traffic: List<TrafficSegment>? = null) = ChatCard(mine = true) {
    val ink = LocalCardInk.current
    Tag("경로 공유", K.route, ink.fg, icon = Ic.Route)
    Spacer(Modifier.height(12.dp))
    RouteBody(m.name, m.from, m.to, m.min, m.pct, m.eta, m.state, m.fill, traffic)
}

/**
 * 입력 바(높이 148, 탭바와 같음). 정차 중: [+ · 입력칸 · 마이크/전송] 한 덩어리. 주행 중: 큰 [말하기] 단추만(+ 없음).
 * 전송 단추는 글이 있을 때만 보이고(그라데이션), 비어 있으면 그 자리에 마이크.
 */
@Composable
private fun Composer(s: UiState, store: TokStore, room: Room, modifier: Modifier = Modifier) {
    // 높이는 최소 148(탭바와 같음)이고, 글이 여러 줄이 되면 입력 상자와 함께 늘어난다
    BarSurface(modifier.fillMaxWidth().heightIn(min = Dims.barH)) {
        Box(Modifier.fillMaxWidth().padding(24.dp)) {
            if (s.driving) {
                Row(
                    Modifier.fillMaxWidth().height(Dims.hL).clip(RoundedCornerShape(Dims.r)).background(if (s.listening) K.red else K.strong).tap("말하기") { store.mic(room.id) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fg = if (s.listening) Color.White else K.onStrong   // 듣는 중에는 빨강 바탕 + 흰 글자
                    Ico(Ic.Mic, 40.dp, fg)
                    Spacer(Modifier.width(16.dp))
                    T(if (s.listening) "듣는 중…" else "말하기", 34, color = fg, weight = FontWeight.ExtraBold)
                }
            } else {
                key(room.id) { ComposerField(s, store, room) }
            }
        }
    }
}

/** 입력칸의 최대 줄 수(3줄). 이보다 길어지면 입력칸 안에서 스크롤되고 오른쪽에 스크롤바가 보인다. */
private const val MAX_LINES = 3

/**
 * 메시지 입력 상자: 엔터로 줄을 바꿀 수 있고(여러 줄), 3줄까지는 상자가 위로 늘어나며 그 이상은 안에서 스크롤된다.
 * + 와 마이크·전송 버튼은 상자가 커져도 상하 가운데에 있다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerField(s: UiState, store: TokStore, room: Room) {
    val text = s.draft[room.id].orEmpty()
    val tf = rememberTextFieldState(text)
    val scroll = rememberScrollState()
    // 저장소의 글이 바뀌면(전송 뒤 비워짐 등) 입력칸에 반영, 입력칸에서 쓴 글은 저장소에 반영
    LaunchedEffect(text) { if (tf.text.toString() != text) tf.setTextAndPlaceCursorAtEnd(text) }
    LaunchedEffect(tf) {
        snapshotFlow { tf.text.toString() }.collect { if (it != store.ui.value.draft[room.id].orEmpty()) store.setDraft(room.id, it) }
    }
    val barColor = K.sub.copy(alpha = .55f)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dims.hL).keepKeyboard().clip(RoundedCornerShape(Dims.r)).background(K.via).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,   // 상자가 커져도 + 와 마이크·전송 버튼은 상하 가운데
    ) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).tap(if (s.plusOpen) "닫기" else "보내기 메뉴") {
                store.togglePlus()
                focus.clearFocus(); keyboard?.hide()   // + 를 누르면 키보드는 내려가고 메뉴만 뜬다
            },
            contentAlignment = Alignment.Center,
        ) {
            Ico(if (s.plusOpen) Ic.X else Ic.Plus, 40.dp, K.ink)
        }
        Box(Modifier.weight(1f).heightIn(min = 76.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) T(if (s.listening) "듣는 중…" else "메시지 입력", 32, color = K.sub)
            BasicTextField(
                tf,
                Modifier.fillMaxWidth().padding(vertical = 16.dp).drawWithContent {
                    drawContent()
                    // 스크롤바: 내용이 3줄을 넘어 스크롤될 때만 오른쪽 끝에 가는 막대
                    if (scroll.maxValue > 0) {
                        val viewport = size.height
                        val total = viewport + scroll.maxValue
                        val thumb = (viewport * viewport / total).coerceAtLeast(24.dp.toPx())
                        val top = (viewport - thumb) * scroll.value / scroll.maxValue
                        drawRoundRect(barColor, Offset(size.width - 4.dp.toPx(), top), Size(4.dp.toPx(), thumb), CornerRadius(2.dp.toPx()))
                    }
                },
                textStyle = TextStyle(fontSize = 32.sp, color = K.ink, lineHeight = (32 * 1.35f).sp),
                cursorBrush = SolidColor(K.blue),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = MAX_LINES),
                scrollState = scroll,
            )
        }
        if (text.isBlank()) {
            // 듣는 중에는 빨간 동그라미 + 흰 마이크(웹 .cinw .cmic.on)
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(if (s.listening) K.red else Color.Transparent).tap("말하기") { store.mic(room.id) },
                contentAlignment = Alignment.Center,
            ) { Ico(Ic.Mic, 40.dp, if (s.listening) Color.White else K.ink) }
        } else {
            // 낮·밤 모두 카드·파란 카드와 같은 그라데이션(밤에는 어두운 파랑 2F5696→1F3761) + 밝은 화살표
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(K.grad).tap("전송") {
                    store.send(room.id, text)
                    if (!imeVisible) { focus.clearFocus(); keyboard?.hide() }   // 키보드가 안 떠 있었다면 전송해도 켜지지 않게(떠 있으면 그대로 유지)
                },
                contentAlignment = Alignment.Center,
            ) {
                Ico(Ic.Send, 40.dp, K.onGrad)
            }
        }
    }
}

/** + 메뉴(입력 바 위 왼쪽): [내 위치 보내기] 한 줄. 누르면 바로 보낸다. */
@Composable
fun PlusMenu(store: TokStore, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(Dims.r)).background(K.grad).tap("내 위치 보내기", onClick = store::sendMyLocation).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(K.card), contentAlignment = Alignment.Center) { Ico(Ic.Pin, 34.dp, K.blueInk) }
        Spacer(Modifier.width(18.dp))
        T("내 위치 보내기", 30, color = K.onGrad, weight = FontWeight.Bold)
    }
}

/** 대화상대 서랍(오른쪽에서 열림): [초대하기] + 나 + 참여자, 왼쪽 아래 나가기 아이콘. */
@Composable
fun MembersDrawer(s: UiState, store: TokStore, room: Room, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().widthIn(max = 600.dp).fillMaxWidth(.78f).background(K.bg).tapQuiet { }) {
        Row(Modifier.fillMaxWidth().padding(start = Dims.gut, end = 24.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("대화상대", 40, weight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            T(room.headCount.toString(), 34, Modifier.weight(1f), color = K.sub, weight = FontWeight.Bold)
            IconCircle(Ic.X, "닫기", store::closeDrawer)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            DrawerRow(divider = false, onClick = store::openInvite) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(34)).background(K.blueSoft), contentAlignment = Alignment.Center) { Ico(Ic.Plus, 38.dp, K.blue) }
                Spacer(Modifier.width(22.dp))
                T("초대하기", 32, weight = FontWeight.Bold)
            }
            DrawerRow {
                Avatar("나", 0, 72.dp, me = true)
                Spacer(Modifier.width(22.dp))
                Column {
                    T(s.myName, 32, weight = FontWeight.Bold)
                    T("나" + if (s.sharing == room.id) " · 이 방에 공유 중" else "", 24, color = K.sub)
                }
            }
            room.members.forEach { nm ->
                val f = s.friends.firstOrNull { it.name == nm }
                DrawerRow {
                    Avatar(nm, s.memberColor(nm), 72.dp)
                    Spacer(Modifier.width(22.dp))
                    Column {
                        T(nm, 32, weight = FontWeight.Bold)
                        if (!f?.status.isNullOrEmpty()) T(f!!.status, 24, color = K.sub)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Dims.gut)) { IconCircle(Ic.Exit, "채팅방 나가기", store::askLeave) }
    }
}

@Composable
private fun DrawerRow(divider: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(2.dp).background(K.line))   // 둘째 줄부터 위에 2dp 선
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}
