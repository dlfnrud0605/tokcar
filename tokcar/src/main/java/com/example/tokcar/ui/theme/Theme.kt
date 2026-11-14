package com.example.tokcar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 시안(tokcar_app.html)의 --k-* 색. 낮 「남색」(data-c="navy") / 밤 「하늘」(data-c="sky") 테마.
 * 역할: 파랑 = 주 단추·내 것, 흰색 = 남의 것, 진한 남색 = 위치·경로 카드, 빨강 = 종료·안 읽음, 초록 = 진행·남은 시간.
 */
class AppColors(
    val bg: Color, val card: Color, val ink: Color, val sub: Color, val block: Color, val line: Color, val dash: Color,
    /** 상대 말풍선·상대가 보낸 위치·경로 카드의 바탕, 그 위 [경유지] 단추·메시지 입력칸(같은 색), 켜진 단추([안내 중]·[경유 중])의 바탕. 웹 시안 「A 회남색」. */
    val bubIn: Color, val via: Color, val viaOn: Color,
    /** 주색(단색). 내 말풍선·선택된 탭 아이콘. */
    val blue: Color, val onBlue: Color, val blueSoft: Color, val blueInk: Color,
    /** 그라데이션 두 색(버튼·경로 띠·로그인 패널·내 말풍선). */
    val g1: Color, val g2: Color,
    /** 그라데이션 위 글자·아이콘. */
    val onGrad: Color, val onGradSub: Color,
    /** 내 말풍선 그라데이션과 글자. */
    val bub1: Color, val bub2: Color, val onBub: Color,
    /** 위치·경로 카드(진한 면)와 그 위 글자. "말하기" 단추도 같은 색. */
    val strong: Color, val onStrong: Color,
    /** 현재 위치 태그(밝은 테마색)·경로 공유 태그와 남은 시간(초록). */
    val hereTag: Color, val hereTagInk: Color, val route: Color, val routeInk: Color,
    val red: Color, val onRed: Color,
    /** 경로 띠·파란 카드 위의 흰 단추([안내] 등)와 그 글자. 낮에는 흰색, 밤에는 연한 남백색. */
    val chip: Color, val chipInk: Color,
    /** 선택된 탭 아이콘 그라데이션(밝은 쪽 → 진한 쪽). */
    val ic1: Color, val ic2: Color,
    /** 얼굴 톤 t0..t5 (배경, 글자). 나는 [meBg]/[meInk]. */
    val tones: List<Pair<Color, Color>>,
    val meBg: Color, val meInk: Color,
    val dark: Boolean,
) {
    // 웹 시안의 기본 방향 ↘: 왼쪽 위가 밝은 쪽(g1), 오른쪽 아래가 진한 쪽(g2). 남색 팔레트에서는 밝은 쪽에도 흰 글자가 읽힘.
    val grad: Brush get() = Brush.linearGradient(listOf(g1, g2), start = Offset.Zero, end = Offset.Infinite)
    val bubGrad: Brush get() = Brush.linearGradient(listOf(bub1, bub2), start = Offset.Zero, end = Offset.Infinite)
}

val LightColors = AppColors(
    bg = Color(0xFFEEF1F7), card = Color.White, ink = Color(0xFF10203F), sub = Color(0xFF5A6782),
    block = Color(0xFFE2E7F1), line = Color(0xFFD3DBEA), dash = Color(0xFFA9B6CF),
    // 상대 말풍선·카드 E2E7F1 / 경유지·입력칸 = 바탕에 글자색 12% 섞은 C9CFDC / 켜진 단추 = 바탕에 파랑 22% 섞은 B7C1D7
    bubIn = Color(0xFFE2E7F1), via = Color(0xFFC9CFDC), viaOn = Color(0xFFB7C1D7),
    blue = Color(0xFF1F3A7A), onBlue = Color.White, blueSoft = Color(0xFFDDE3F0), blueInk = Color(0xFF16295A),
    g1 = Color(0xFF3A5FB0), g2 = Color(0xFF14254F), onGrad = Color.White, onGradSub = Color.White,
    bub1 = Color(0xFF3A5FB0), bub2 = Color(0xFF14254F), onBub = Color.White,
    strong = Color(0xFF0E1A38), onStrong = Color.White,
    // 현재 위치 태그만 웹(#3A5FB0 + 진한 글자)보다 밝게: 웹 값은 글자 대비가 약함
    hereTag = Color(0xFF9DB4EE), hereTagInk = Color.White, route = Color(0xFF2BD4A0), routeInk = Color.White,
    red = Color(0xFFE5482D), onRed = Color.White,
    chip = Color.White, chipInk = Color(0xFF16295A), ic1 = Color(0xFF3A5FB0), ic2 = Color(0xFF14254F),
    tones = listOf(
        Color(0xFFDDE3F0) to Color(0xFF16295A), Color(0xFFD4EEFF) to Color(0xFF0B67A8), Color(0xFFD6F1F4) to Color(0xFF0B6470),
        Color(0xFFE2E5FF) to Color(0xFF3A43C2), Color(0xFFE6EDF7) to Color(0xFF2E4F7E), Color(0xFFDDE7FA) to Color(0xFF3558A8),
    ),
    meBg = Color(0xFFE2E7F1), meInk = Color(0xFF10203F),
    dark = false,
)

// 밤: 웹 시안 「하늘」 테마 야간의 값을 그대로 옮김(낮은 남색, 밤은 하늘색 계열)
val DarkColors = AppColors(
    bg = Color(0xFF0B1222), card = Color(0xFF141E36), ink = Color(0xFFEAF0FF), sub = Color(0xFF93A3C4),
    block = Color(0xFF1C2945), line = Color(0xFF263457), dash = Color(0xFF3A4C78),
    // 상대 말풍선·카드 1C2945 / 경유지·입력칸 35415B / 켜진 단추 384F6E (낮과 같은 방식으로 섞은 값)
    bubIn = Color(0xFF1C2945), via = Color(0xFF35415B), viaOn = Color(0xFF384F6E),
    blue = Color(0xFF9AD7FF), onBlue = Color(0xFF0B1222), blueSoft = Color(0xFF1D345D), blueInk = Color(0xFFBDE5FF),
    g1 = Color(0xFF2F5696), g2 = Color(0xFF1F3761), onGrad = Color(0xFFEAF0FF), onGradSub = Color(0xFFEAF0FF),
    // 내 말풍선·내 카드는 밤에도 파란 카드·경로 띠와 같은 그라데이션(g1·g2)과 글자(밝은 남백색)
    bub1 = Color(0xFF2F5696), bub2 = Color(0xFF1F3761), onBub = Color(0xFFEAF0FF),
    strong = Color(0xFFEAF0FF), onStrong = Color(0xFF0B1222),
    hereTag = Color(0xFF2A5FE0), hereTagInk = Color.White, route = Color(0xFF0E8F6E), routeInk = Color.White,
    red = Color(0xFFF0604A), onRed = Color.White,
    chip = Color(0xFFEAF0FF), chipInk = Color(0xFF1F3761), ic1 = Color(0xFFB8E3FF), ic2 = Color(0xFF9AD7FF),
    tones = listOf(
        Color(0xFF1D345D) to Color(0xFFBDE5FF), Color(0xFF163A55) to Color(0xFF8FD0FF), Color(0xFF15393F) to Color(0xFF86D9E2),
        Color(0xFF24295C) to Color(0xFFAEB5FF), Color(0xFF1F2D45) to Color(0xFFA9C0E2), Color(0xFF1E2C4E) to Color(0xFF9FB6EA),
    ),
    meBg = Color(0xFF1C2945), meInk = Color(0xFFEAF0FF),
    dark = true,
)

/** 크기(dp). 시안의 반폭 창 841dp 기준 값 그대로. */
object Dims {
    /** 화면 좌우 여백. */
    val gut: Dp = 32.dp
    /**
     * 경로 띠·아래 파란 카드의 좌우 끝 간격(화면 가장자리부터). 목록 방 카드 안의 경로 띠 끝 위치(화면 여백 32 + 카드 안쪽 12 + 띠 4)와 같아서,
     * 목록의 아래 카드와 대화방 위·아래 카드가 모두 이 폭으로 맞는다.
     */
    val cardInset: Dp = 48.dp
    /** 큰 단추·입력칸 높이. */
    val hL: Dp = 100.dp
    /** 아래 탭바·대화방 입력 바 높이(같음). */
    val barH: Dp = 148.dp
    /** 아이콘 단추(동그라미) 지름. */
    val iconBtn: Dp = 80.dp
    /** [안내]·[안내 종료]·[공유 취소] 단추. */
    val actW: Dp = 161.dp
    val actH: Dp = 80.dp
    /** 큰 것(단추·입력칸·카드) 모서리. */
    val r: Dp = 30.dp
    /** 작은 태그 모서리. */
    val rTag: Dp = 14.dp
}

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** 시스템 주간/야간을 따른다. */
@Composable
fun TokTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(primary = c.blue, onPrimary = c.onBlue, background = c.bg, surface = c.card, onSurface = c.ink)
    else lightColorScheme(primary = c.blue, onPrimary = c.onBlue, background = c.bg, surface = c.card, onSurface = c.ink)
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme) {
            Surface(color = c.bg, contentColor = c.ink, content = content)
        }
    }
}

/** 화면 코드에서 K.card, K.ink 처럼 쓴다. */
val K: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/** 방·친구 얼굴 색(진한 색) -> 연한 톤 번호. 모르는 색은 0(파랑 톤). */
fun toneIndexOf(color: Long): Int = when (color and 0xFFFFFFL) {
    0xC2410CL -> 0
    0x92600AL, 0xA16207L -> 1
    0x0F766EL -> 2
    0x0E7490L, 0x1D4ED8L, 0x0369A1L -> 3
    0x5B3FD0L, 0x6D28D9L, 0x4338CAL -> 4
    0xBE185DL, 0x9D174DL -> 5
    else -> 0
}
