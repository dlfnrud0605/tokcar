package com.example.tokcar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tokcar.R
import com.example.tokcar.data.Room
import com.example.tokcar.ui.theme.AppColors
import com.example.tokcar.ui.theme.Dims
import com.example.tokcar.ui.theme.Ic
import com.example.tokcar.ui.theme.K
import com.example.tokcar.ui.theme.toneIndexOf

/**
 * 입력칸처럼 "눌러도 키보드를 내리지 않을 영역"의 목록. 화면 어디를 눌러도 키보드가 내려가되([TokCarRoot]의 눌림 감시),
 * 여기에 등록된 영역(입력칸·전송 단추가 든 입력 상자 등) 안을 누르면 내려가지 않는다.
 */
class KeepRegistry {
    private val rects = mutableMapOf<Any, Rect>()
    fun put(key: Any, r: Rect) { rects[key] = r }
    fun remove(key: Any) { rects.remove(key) }
    fun contains(p: Offset): Boolean = rects.values.any { it.contains(p) }
}

val LocalKeepRegistry = staticCompositionLocalOf { KeepRegistry() }

/** 이 영역을 누를 때는 키보드를 내리지 않는다(입력 상자에 붙임). */
@Composable
fun Modifier.keepKeyboard(): Modifier {
    val reg = LocalKeepRegistry.current
    val key = remember { Any() }
    DisposableEffect(key) { onDispose { reg.remove(key) } }
    return this.onGloballyPositioned { reg.put(key, it.boundsInRoot()) }
}

/** 글자 한 줄(시안의 font-size 값을 그대로 sp 로). */
@Composable
fun T(
    text: String, size: Int, modifier: Modifier = Modifier, color: Color = K.ink, weight: FontWeight = FontWeight.Normal,
    lines: Int = 1, align: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = lines, overflow = TextOverflow.Ellipsis,
    textAlign = align,
    // 줄 높이를 글자 크기에 맞춰 직접 정함(정하지 않으면 여러 줄 글에서 줄끼리 겹쳐 보이는 기기가 있음)
    lineHeight = if (lineHeight == TextUnit.Unspecified) (size * 1.35f).sp else lineHeight,
)

/** 누를 수 있게(물결 효과 없이 눌린 색만). */
fun Modifier.tap(label: String? = null, enabled: Boolean = true, onClick: () -> Unit): Modifier = this
    .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)

/** 누를 때 아무 표시도 없이(배경을 눌러 닫기 등). */
@Composable
fun Modifier.tapQuiet(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@Composable
fun Ico(v: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) = Icon(v, null, modifier.size(size), tint = tint)

/** 아이콘 단추: 80dp 흰 동그라미 + 2dp 테두리(뒤로·메뉴·닫기·새 방·친구 추가·나가기 모두 같음). */
@Composable
fun IconCircle(v: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 40.dp) {
    Box(
        modifier.size(Dims.iconBtn).clip(CircleShape).background(K.card).border(2.dp, K.line, CircleShape).tap(label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(v, iconSize, K.ink) }
}

enum class Act { GO, DANGER, PRIMARY, TONAL }

/**
 * 작은 동작 단추(161x80, 글자 28): [안내](흰 바탕), [안내 종료]·[공유 취소](빨강), 주 단추(그라데이션), 보조(반투명 흰색).
 */
@Composable
fun ActBtn(text: String, kind: Act, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, label: String? = null) {
    val shape = RoundedCornerShape(Dims.r)
    val (bgMod, fg) = when (kind) {
        Act.GO -> Modifier.background(K.chip, shape) to K.chipInk
        // [안내 종료]·[공유 취소]: 웹 시안과 같은 빨강 바탕 + 흰 글자
        Act.DANGER -> Modifier.background(K.red, shape) to K.onRed
        Act.PRIMARY -> Modifier.background(K.grad, shape) to K.onGrad
        // [닫기]·[아니요]도 [방 고르기]·[공유]와 같은 흰 바탕 + 파란 글자(어느 쪽에서도 잘 보이게)
        Act.TONAL -> Modifier.background(K.chip, shape) to K.chipInk
    }
    Row(
        modifier.defaultMinSize(minWidth = Dims.actW).height(Dims.actH).clip(shape).then(bgMod).tap(label ?: text, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, 26.dp, fg); Spacer(Modifier.width(8.dp)) }
        T(text, 28, color = fg, weight = FontWeight.Bold)
    }
}

/** 큰 단추(폭 전체, 높이 100, 글자 34). 주 단추는 그라데이션, [ghost] 는 연한 파랑, 못 누를 땐 회색. */
@Composable
fun BigBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, ghost: Boolean = false, height: Dp = Dims.hL) {
    val shape = RoundedCornerShape(Dims.r)
    val bg = when {
        !enabled -> Modifier.background(K.block, shape)
        ghost -> Modifier.background(K.blueSoft, shape)
        else -> Modifier.background(K.grad, shape)
    }
    val fg = when {
        !enabled -> K.sub
        ghost -> K.blueInk
        else -> K.onGrad
    }
    Box(
        modifier.fillMaxWidth().height(height).clip(shape).then(bg).tap(text, enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { T(text, 34, color = fg, weight = FontWeight.Bold) }
}

/** 얼굴 색(진한 색)에 맞는 연한 톤(배경, 글자). */
@Composable
fun toneOf(color: Long): Pair<Color, Color> = K.tones[toneIndexOf(color)]

/**
 * 아래 막대(탭바·입력 바) 바탕: 위 모서리만 둥글고, 맨 위에 2dp 선(K.line)이 모양을 따라 이어진다(웹의 inset 0 2px 0 line).
 */
@Composable
fun BarSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = Dims.r, topEnd = Dims.r)
    // 높이는 [modifier] 가 정함(고정이거나 최소값만 주고 안 내용에 따라 늘어남): 바탕은 matchParentSize 로 깔고 내용은 따로 얹는다
    Box(modifier.clip(shape).background(K.line)) {
        Box(Modifier.matchParentSize().padding(top = 2.dp).clip(shape).background(K.card))
        Box(Modifier.padding(top = 2.dp)) { content() }
    }
}

/** 얼굴: 둥근 네모(모서리 34%) + 첫 글자. [me] 는 나(연한 바탕). */
@Composable
fun Avatar(text: String, color: Long, size: Dp, modifier: Modifier = Modifier, me: Boolean = false) {
    val (bg, ink) = if (me) K.meBg to K.meInk else toneOf(color)
    Box(modifier.size(size).clip(RoundedCornerShape(34)).background(bg), contentAlignment = Alignment.Center) {
        T(text.take(1), (size.value * .4f).toInt(), color = ink, weight = FontWeight.ExtraBold)
    }
}

/**
 * 방 사진: 내 얼굴은 빼고 상대 얼굴로(카톡처럼, 웹 시안과 같음). 1명 = 그 사람, 2명 = 대각선으로 겹침(왼쪽 위 + 오른쪽 아래),
 * 3명 = 삼각형(위 1 · 아래 2), 4명 이상 = 앞의 4명을 2x2. 테두리는 없다(겹친 얼굴 사이만 카드색으로 끊음).
 */
@Composable
fun GroupAvatar(room: Room, size: Dp, memberColor: (String) -> Long) {
    val names = room.members.take(4)
    val n = names.size
    val multi = n >= 2
    @Composable
    fun one(name: String, s: Dp, x: Dp, y: Dp) {
        Box(Modifier.offset(x, y).then(if (multi) Modifier.border(3.dp, K.card, RoundedCornerShape(34)) else Modifier)) {
            Avatar(name, memberColor(name), s)
        }
    }
    Box(Modifier.size(size)) {
        when {
            n == 0 -> one(room.name, size, 0.dp, 0.dp)
            n == 1 -> one(names[0], size, 0.dp, 0.dp)
            n == 2 -> { val d = size * .66f; one(names[0], d, 0.dp, 0.dp); one(names[1], d, size - d, size - d) }
            n == 3 -> { val t = size * .56f; one(names[0], t, (size - t) / 2, 0.dp); one(names[1], t, 0.dp, size - t); one(names[2], t, size - t, size - t) }
            else -> { val q = size * .5f; one(names[0], q, 0.dp, 0.dp); one(names[1], q, size - q, 0.dp); one(names[2], q, 0.dp, size - q); one(names[3], q, size - q, size - q) }
        }
    }
}

/** 안 읽은 수: 빨간 알약. */
@Composable
fun Badge(n: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, fontSize: Int = 24) {
    Box(
        modifier.defaultMinSize(minWidth = size).height(size).clip(RoundedCornerShape(999.dp)).background(K.red).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { T(if (n > 99) "99+" else n.toString(), fontSize, color = Color.White, weight = FontWeight.ExtraBold) }
}

/** 작은 태그(모서리 14). */
@Composable
fun Tag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(Dims.rTag)).background(bg).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(text, 22, color = fg, weight = FontWeight.ExtraBold)
        if (icon != null) { Spacer(Modifier.width(6.dp)); Ico(icon, 22.dp, fg) }
    }
}

/**
 * 경로 띠(그라데이션): 흰 둥근 네모 핀 + 목적지 이름 ›  + 주소, 오른쪽에 단추. 목록 카드·대화방 위쪽·목적지 모달이 같은 모양.
 * 크기·글자는 아래 파란 카드(DockCard)와 같다: 높이 112, 이름 36, 주소 21. 핀 아이콘 칸은 [안내] 단추 높이(80)와 같다.
 * [onDest] 를 누르면 목적지 모달.
 */
@Composable
fun RouteStrip(name: String, addr: String, onDest: (() -> Unit)?, modifier: Modifier = Modifier, destLabel: String? = null, button: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(32.dp)).background(K.grad).padding(start = 16.dp, end = 16.dp),   // 아이콘 칸(80)의 위·아래 여백(16)과 왼쪽 여백을 같게
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).then(if (onDest != null) Modifier.tap(destLabel ?: name, onClick = onDest) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 핀 아이콘 칸은 옆 [안내] 단추 높이(80)와 같은 크기
            Box(Modifier.size(Dims.actH).clip(RoundedCornerShape(34)).background(K.chip), contentAlignment = Alignment.Center) { Ico(Ic.Pin, 42.dp, K.chipInk) }
            Spacer(Modifier.width(26.dp))   // 글자 시작을 목록 카드의 방 이름·최근 대화 시작과 같게
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(name, 36, Modifier.weight(1f, fill = false), color = K.onGrad, weight = FontWeight.Bold)
                    if (onDest != null) Ico(Ic.Chev, 28.dp, K.onGrad)
                }
                T(addr, 21, color = K.onGradSub)
            }
        }
        if (button != null) { Spacer(Modifier.width(12.dp)); button() }
    }
}

/** 앱 아이콘(106번, 하늘색). */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.tk_icon), "TokCar", modifier.size(size).clip(RoundedCornerShape(size * .23f)))
}

/** 화면 위 제목줄(채팅·친구·설정): 큰 제목 + 오른쪽 아이콘 단추. */
@Composable
fun TopTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(136.dp).padding(horizontal = Dims.gut),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(title, 54, Modifier.weight(1f), weight = FontWeight.Bold)
        action?.invoke()
    }
}

/** 소제목(회색 작은 글자). */
@Composable
fun Section(text: String, modifier: Modifier = Modifier) =
    T(text, 26, modifier.padding(top = 20.dp, bottom = 12.dp), color = K.sub, weight = FontWeight.Bold)

/** 토스트(화면 아래쪽, 진한 바탕). */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.widthIn(max = 720.dp).clip(RoundedCornerShape(Dims.r)).background(K.strong).padding(horizontal = 36.dp, vertical = 20.dp),
    ) { T(text, 28, color = K.onStrong, weight = FontWeight.Bold, lines = 2, align = TextAlign.Center) }
}

/** 색 체계를 함수 안에서 쓰기 위한 편의(비컴포저블 계산용). */
typealias Colors = AppColors
