package com.example.tokcar.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 시안의 SVG 아이콘을 그대로 옮긴 벡터(24x24). 선(stroke) 아이콘은 쓸 때 tint 로 색을 입힌다.
 * circle/rect 는 path 로 바꿔 넣었다.
 */
object Ic {
    fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 ${-r},$r h${-(w - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)} a$r,$r 0 0 1 $r,${-r} z"

    private fun build(name: String, stroke: Float = 2f, filled: List<String> = emptyList(), lines: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            filled.forEach { d -> addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)) }
            lines.forEach { d ->
                addPath(
                    PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // ───── 탭 아이콘(선택되면 채움) ─────
    val PERSON = listOf(circle(12f, 8f, 4f), "M4 21c0-4 3.5-6.5 8-6.5s8 2.5 8 6.5Z")
    val CHAT = listOf("M4 5h16v11H11l-5 4v-4H4z")
    val GEAR = listOf(
        "M10.23 4.61 L10.44 1.92 L13.56 1.92 L13.77 4.61 L15.97 5.52 L18.03 3.77 L20.23 5.97 L18.48 8.03 L19.39 10.23 L22.08 10.44 " +
            "L22.08 13.56 L19.39 13.77 L18.48 15.97 L20.23 18.03 L18.03 20.23 L15.97 18.48 L13.77 19.39 L13.56 22.08 L10.44 22.08 L10.23 19.39 " +
            "L8.03 18.48 L5.97 20.23 L3.77 18.03 L5.52 15.97 L4.61 13.77 L1.92 13.56 L1.92 10.44 L4.61 10.23 L5.52 8.03 L3.77 5.97 L5.97 3.77 L8.03 5.52Z",
    )
    /** 톱니바퀴 안쪽 동그라미(선택되면 속은 채움 색, 외곽선만 흰색). */
    val GEAR_HOLE = circle(12f, 12f, 3.2f)

    val Person by lazy { build("person", lines = PERSON) }
    val Chat by lazy { build("chat", lines = CHAT) }
    val Gear by lazy { build("gear", lines = GEAR + GEAR_HOLE) }

    /**
     * 선택된 탭 아이콘: 면과 외곽선을 같은 그라데이션으로 채운다(카톡·인스타처럼). 톱니바퀴 안쪽 동그라미는 외곽선만 [hole] 색.
     * [g1]→[g2] 는 왼쪽 위 → 오른쪽 아래(웹 시안의 기본 ↘ 방향).
     */
    fun filled(lines: List<String>, g1: Color, g2: Color, hole: Color? = null): ImageVector {
        val brush = Brush.linearGradient(listOf(g1, g2), start = Offset(0f, 0f), end = Offset(24f, 24f))
        return ImageVector.Builder("filled", 24.dp, 24.dp, 24f, 24f).apply {
            lines.forEach { d ->
                addPath(
                    PathParser().parsePathString(d).toNodes(), fill = brush, stroke = brush, strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
            if (hole != null) addPath(PathParser().parsePathString(GEAR_HOLE).toNodes(), fill = brush, stroke = SolidColor(hole), strokeLineWidth = 2f)
        }.build()
    }

    // ───── 그 밖의 선 아이콘 ─────
    val Plus by lazy { build("plus", 2.4f, lines = listOf("M12 5v14M5 12h14")) }
    val Chev by lazy { build("chev", 2.4f, lines = listOf("M9 5l7 7-7 7")) }
    val Back by lazy { build("back", 2.4f, lines = listOf("M15 5l-7 7 7 7")) }
    val Down by lazy { build("down", 2.4f, lines = listOf("M5 9l7 7 7-7")) }
    val X by lazy { build("x", 2.4f, lines = listOf("M6 6l12 12M18 6L6 18")) }
    val Send by lazy { build("send", 2.4f, lines = listOf("M12 19V5M5.5 11.5 12 5l6.5 6.5")) }
    val Pin by lazy { build("pin", 2.4f, lines = listOf("M12 21s6-5.6 6-11a6 6 0 0 0-12 0c0 5.4 6 11 6 11z", circle(12f, 10f, 2.2f))) }
    val Route by lazy {
        build("route", 2.4f, lines = listOf(circle(6f, 18f, 2.2f), circle(18f, 6f, 2.2f), "M8.2 18H14a3.2 3.2 0 0 0 0-6.4h-4A3.2 3.2 0 0 1 10 5.2h5.8"))
    }
    val Play by lazy { build("play", filled = listOf("M8 5.2v13.6a.6.6 0 0 0 .9.5l10.6-6.8a.6.6 0 0 0 0-1L8.9 4.7a.6.6 0 0 0-.9.5z")) }
    val Mic by lazy { build("mic", filled = listOf(rrect(9f, 3f, 6f, 11f, 3f)), lines = listOf("M5 11a7 7 0 0 0 14 0M12 18v3")) }
    val Menu by lazy { build("list", lines = listOf("M4 6h16M4 12h16M4 18h16")) }
    val Exit by lazy { build("exit", 2.2f, lines = listOf("M10 4H5v16h5", "M14 8l5 4-5 4M19 12H9")) }
    val AddFriend by lazy {
        build("addfriend", lines = listOf(circle(10f, 8f, 4f), "M2 21c0-4 3.5-6.5 8-6.5 1.5 0 2.9.3 4 .9V21Z", "M19 14v6M16 17h6"))
    }
    val Eye by lazy { build("eye", 2.4f, lines = listOf("M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))) }
    val EyeOff by lazy {
        build(
            "eyeoff", 2.4f,
            lines = listOf(
                "M3 3l18 18", "M10.6 5.1A10.6 10.6 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.1",
                "M6.6 6.6A17 17 0 0 0 2 12s3.6 7 10 7a10.3 10.3 0 0 0 4.2-.9", "M9.9 9.9a3 3 0 0 0 4.2 4.2",
            ),
        )
    }
    val Check by lazy { build("check", 3f, lines = listOf("M5 12.5l4.5 4.5L19 7.5")) }
}
