package com.nokia.phone

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Bảng màu giao diện HUD (nền navy, viền neon cyan/hồng) theo phong cách Hậu OS. */
val HUD_BG0 = Color(0xFF040D26)
val HUD_BG1 = Color(0xFF0A1F5C)
val HUD_PANEL = Color(0xFF0D2A70)
val HUD_CYAN = Color(0xFF19D3FF)
val HUD_PINK = Color(0xFFFF3D8B)
val HUD_AMBER = Color(0xFFFFB300)

/** Chữ condensed đậm, dùng cho toàn bộ giao diện. */
val HUD_FONT = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/** Hình chữ nhật vát hai góc (trên-trái và dưới-phải) như các khung của Hậu OS. */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val p = Path().apply {
            moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
        }
        return Outline.Generic(p)
    }
}

/** Lưới mờ trên nền. */
fun DrawScope.hudGrid() {
    val step = 22.dp.toPx()
    val col = HUD_CYAN.copy(alpha = 0.07f)
    var x = 0f
    while (x < size.width) { drawLine(col, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(col, Offset(0f, y), Offset(size.width, y), 1f); y += step }
}

/** Nền navy dốc nhẹ + lưới; underline = vẽ vạch neon cyan→hồng ở đáy. */
fun Modifier.hudBackground(underline: Boolean = false): Modifier =
    this.background(Brush.verticalGradient(listOf(HUD_BG1, HUD_BG0))).drawBehind {
        hudGrid()
        if (underline) drawLine(Brush.horizontalGradient(listOf(HUD_CYAN, HUD_PINK)),
            Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
    }

/** Khung vát góc viền neon. */
fun Modifier.neonPanel(color: Color = HUD_CYAN, cut: Dp = 8.dp,
                       fill: Color = HUD_PANEL.copy(alpha = 0.55f), width: Dp = 1.5.dp): Modifier {
    val s = ChamferShape(cut)
    return this.clip(s).background(fill).border(width, color, s)
}

fun ramText(ctx: Context): String = try {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
    val tot = mi.totalMem / 1073741824.0
    val used = tot - mi.availMem / 1073741824.0
    "RAM %.1f/%.1fG".format(used, tot)
} catch (e: Exception) { "" }

/** Nền HUD (navy dốc + lưới mờ) cho các màn hình dựng bằng View truyền thống. */
class HudBgDrawable(private val d: Float) : android.graphics.drawable.Drawable() {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: android.graphics.Canvas) {
        val b = bounds
        p.shader = android.graphics.LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            0xFF0A1F5C.toInt(), 0xFF040D26.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(b, p); p.shader = null
        p.color = 0x1219D3FF; p.strokeWidth = 1f
        val step = 22f * d
        var x = b.left.toFloat()
        while (x < b.right) { c.drawLine(x, b.top.toFloat(), x, b.bottom.toFloat(), p); x += step }
        var y = b.top.toFloat()
        while (y < b.bottom) { c.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, p); y += step }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}

/** Nút vát góc (trên-trái, dưới-phải) viền neon cho các View truyền thống. */
class HudChipDrawable(private val fill: Int, private val edge: Int, private val stroke: Float, private val cut: Float) :
    android.graphics.drawable.Drawable() {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: android.graphics.Canvas) {
        val h = stroke / 2f
        val l = bounds.left + h; val t = bounds.top + h; val r = bounds.right - h; val b = bounds.bottom - h
        val path = android.graphics.Path().apply {
            moveTo(l + cut, t); lineTo(r, t); lineTo(r, b - cut); lineTo(r - cut, b); lineTo(l, b); lineTo(l, t + cut); close()
        }
        p.style = android.graphics.Paint.Style.FILL; p.color = fill; c.drawPath(path, p)
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = stroke; p.color = edge; c.drawPath(path, p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
