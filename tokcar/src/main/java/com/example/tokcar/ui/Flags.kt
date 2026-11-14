package com.example.tokcar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.example.tokcar.ui.theme.K
import kotlin.math.cos
import kotlin.math.sin

/**
 * 나라 국기(36x24 칸에 그린 단순한 모양). 글자 이모지는 윈도·리눅스 화면에서 안 보이는 경우가 많아서 직접 그린다.
 * 시안(home_variants_taste.html)의 FLAGS 와 같은 모양.
 */
@Composable
fun Flag(code: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val line = K.line
    Canvas(modifier.size(48.dp, 32.dp).clip(shape).border(1.dp, line, shape)) {
        scale(size.width / 36f, Offset.Zero) { drawFlag(code) }
    }
}

private fun c(rgb: Long) = Color(0xFF000000 or rgb)

private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, color: Color) = drawRect(color, Offset(x, y), Size(w, h))

private fun DrawScope.star5(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 5) {
        val a = Math.toRadians((-90 + i * 72).toDouble())
        val b = Math.toRadians((-90 + i * 72 + 36).toDouble())
        val ox = cx + r * cos(a).toFloat()
        val oy = cy + r * sin(a).toFloat()
        val ix = cx + r * 0.382f * cos(b).toFloat()
        val iy = cy + r * 0.382f * sin(b).toFloat()
        if (i == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
        path.lineTo(ix, iy)
    }
    path.close()
    drawPath(path, color)
}

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** 태극기 건괘 등: 가운데를 기준으로 [rot]도 돌린 막대 무늬. 's' = 이은 줄, 'b' = 끊긴 줄. */
private fun DrawScope.trigram(x: Float, y: Float, rot: Float, pattern: String) {
    translate(x, y) {
        rotate(rot, Offset.Zero) {
            pattern.forEachIndexed { i, k ->
                val yy = -1.9f + i * 1.4f
                if (k == 's') rect(-3f, yy, 6f, 1f, Color.Black)
                else { rect(-3f, yy, 2.5f, 1f, Color.Black); rect(0.5f, yy, 2.5f, 1f, Color.Black) }
            }
        }
    }
}

private fun DrawScope.drawFlag(code: String) {
    when (code) {
        "KR" -> {
            rect(0f, 0f, 36f, 24f, Color.White)
            drawPath(svgPath("M12 12a6 6 0 0 1 12 0 3 3 0 0 1-6 0 3 3 0 0 0-6 0z"), c(0xCD2E3A))
            drawPath(svgPath("M24 12a6 6 0 0 1-12 0 3 3 0 0 1 6 0 3 3 0 0 0 6 0z"), c(0x0047A0))
            trigram(6.4f, 5.2f, -33.7f, "sss"); trigram(29.6f, 5.2f, 33.7f, "bsb")
            trigram(6.4f, 18.8f, 33.7f, "sbs"); trigram(29.6f, 18.8f, -33.7f, "bbb")
        }
        "US" -> {
            for (i in 0 until 7) rect(0f, i * 24f / 7f, 36f, 24f / 7f, if (i % 2 == 1) Color.White else c(0xB22234))
            rect(0f, 0f, 15.5f, 13.7f, c(0x3C3B6E))
            for (r in 0 until 4) for (col in 0 until 5) drawCircle(Color.White, 0.6f, Offset(2f + col * 2.9f, 2f + r * 3.1f))
        }
        "JP" -> {
            rect(0f, 0f, 36f, 24f, Color.White)
            drawCircle(c(0xBC002D), 7f, Offset(18f, 12f))
        }
        "CN" -> {
            rect(0f, 0f, 36f, 24f, c(0xDE2910))
            star5(7f, 7f, 4.2f, c(0xFFDE00))
            listOf(14f to 3f, 16.5f to 6f, 16.5f to 10f, 14f to 13f).forEach { (x, y) -> star5(x, y, 1.3f, c(0xFFDE00)) }
        }
        "GB" -> {
            rect(0f, 0f, 36f, 24f, c(0x012169))
            val diag = svgPath("M0 0L36 24M36 0L0 24")
            drawPath(diag, Color.White, style = Stroke(4.5f))
            drawPath(diag, c(0xC8102E), style = Stroke(1.6f))
            val cross = svgPath("M18 0V24M0 12H36")
            drawPath(cross, Color.White, style = Stroke(7f))
            drawPath(cross, c(0xC8102E), style = Stroke(4f))
        }
        "DE" -> {
            rect(0f, 0f, 36f, 8f, Color.Black); rect(0f, 8f, 36f, 8f, c(0xDD0000)); rect(0f, 16f, 36f, 8f, c(0xFFCE00))
        }
        "FR" -> {
            rect(0f, 0f, 12f, 24f, c(0x0055A4)); rect(12f, 0f, 12f, 24f, Color.White); rect(24f, 0f, 12f, 24f, c(0xEF4135))
        }
        "CA" -> {
            rect(0f, 0f, 36f, 24f, Color.White)
            rect(0f, 0f, 9f, 24f, c(0xD52B1E)); rect(27f, 0f, 9f, 24f, c(0xD52B1E))
            val leaf = Path()
            listOf(18f to 4.5f, 20f to 9f, 24.5f to 8f, 22.5f to 12.5f, 25f to 14f, 19f to 15.5f, 19f to 20f, 17f to 20f, 17f to 15.5f, 11f to 14f, 13.5f to 12.5f, 11.5f to 8f, 16f to 9f)
                .forEachIndexed { i, (x, y) -> if (i == 0) leaf.moveTo(x, y) else leaf.lineTo(x, y) }
            leaf.close()
            drawPath(leaf, c(0xD52B1E))
        }
        "VN" -> {
            rect(0f, 0f, 36f, 24f, c(0xDA251D))
            star5(18f, 12.6f, 7f, c(0xFFFF00))
        }
        "TH" -> {
            rect(0f, 0f, 36f, 4f, c(0xA51931)); rect(0f, 4f, 36f, 4f, Color.White); rect(0f, 8f, 36f, 8f, c(0x2D2A4A))
            rect(0f, 16f, 36f, 4f, Color.White); rect(0f, 20f, 36f, 4f, c(0xA51931))
        }
        "SG" -> {
            rect(0f, 0f, 36f, 12f, c(0xED2939)); rect(0f, 12f, 36f, 12f, Color.White)
            drawCircle(Color.White, 4.6f, Offset(9f, 6f)); drawCircle(c(0xED2939), 4.2f, Offset(10.6f, 6f))
            listOf(13.6f to 3f, 16.6f to 5.2f, 15.6f to 8.6f, 11.8f to 8.6f, 10.8f to 5.2f).forEach { (x, y) -> star5(x + 1.2f, y, 1.15f, Color.White) }
        }
        "IN" -> {
            rect(0f, 0f, 36f, 8f, c(0xFF9933)); rect(0f, 8f, 36f, 8f, Color.White); rect(0f, 16f, 36f, 8f, c(0x138808))
            drawCircle(c(0x000080), 3f, Offset(18f, 12f), style = Stroke(0.9f))
            drawCircle(c(0x000080), 0.7f, Offset(18f, 12f))
        }
        else -> rect(0f, 0f, 36f, 24f, Color.LightGray)
    }
}
