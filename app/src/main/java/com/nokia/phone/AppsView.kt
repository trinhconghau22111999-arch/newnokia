package com.nokia.phone

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Số icon trên một hàng ở chế độ bảng icon. */
const val APP_COLS = 4

/** Số icon ghim tối đa ở màn hình chờ. */
const val MAX_PINS = 8

/** Icon app nạp theo nhu cầu (chỉ ô đang hiện), giữ tối đa 48 icon nhỏ; xóa sạch khi rời màn hình Ứng dụng. */
object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun get(pkg: String): ImageBitmap? = cache.get(pkg)

    fun load(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return try {
            val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96, Bitmap.Config.ARGB_8888)
            bmp.asImageBitmap().also { cache.put(pkg, it) }
        } catch (_: Exception) { null }
    }

    fun clear() = cache.evictAll()
}

/** Màn hình Ứng dụng: hai thẻ ở trên "Chữ" (danh sách tên) và "Icon" (bảng icon vuông, nhiều icon một hàng). */
@Composable
fun AppsScreen(
    apps: List<AppInfo>, mode: Int, sel: Int,
    onMode: (Int) -> Unit, onTap: (Int) -> Unit, onScroll: (Int) -> Unit,
    onLong: (Int) -> Unit = {}, pinApp: AppInfo? = null, pinned: Boolean = false,
    onPinConfirm: () -> Unit = {}, onPinCancel: () -> Unit = {}
) {
  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Chữ", "Icon").forEachIndexed { i, t ->
                Box(Modifier.weight(1f).border(1.dp, INK).background(if (mode == i) INK else LCD)
                    .clickable { onMode(i) }.padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (mode == i) LCD else INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (mode == 0) Box(Modifier.weight(1f)) {
            Lines(apps.map { it.label }.ifEmpty { listOf("(trống)") }, sel, onTap, onScroll)
        } else IconGrid(apps, sel, onTap, onLong)
    }
    if (pinApp != null) Box(Modifier.fillMaxSize().background(INK.copy(alpha = 0.45f)).clickable { onPinCancel() }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).border(2.dp, INK).background(LCD).clickable(enabled = false) {}.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pinApp.label, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().background(INK).clickable { onPinConfirm() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(if (pinned) "Bỏ ghim khỏi màn hình chính" else "Ghim vào màn hình chính", color = LCD, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().border(1.dp, INK).clickable { onPinCancel() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text("Hủy", color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
  }
}

@Composable
private fun ColumnScope.IconGrid(apps: List<AppInfo>, sel: Int, onTap: (Int) -> Unit, onLong: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberLazyGridState()
    // Cuộn theo ô đang chọn khi di chuyển bằng phím
    LaunchedEffect(sel) {
        val info = state.layoutInfo
        val vis = info.visibleItemsInfo
        if (vis.isNotEmpty()) {
            val cur = vis.firstOrNull { it.index == sel }
            if (cur == null) {
                // ô đang chọn nằm ngoài vùng nhìn thấy -> nhảy tới hàng chứa nó
                val rows = maxOf(1, vis.size / APP_COLS)
                if (sel < vis.first().index) state.scrollToItem(sel / APP_COLS * APP_COLS)
                else state.scrollToItem(maxOf(0, sel / APP_COLS - rows + 1) * APP_COLS)
            } else {
                // ô hiện một phần (bị cắt ở mép trên/dưới) -> cuộn thêm cho thấy đủ cả ô
                val top = cur.offset.y
                val bottom = top + cur.size.height
                val viewTop = info.viewportStartOffset
                val viewBottom = info.viewportEndOffset
                if (top < viewTop) state.animateScrollBy((top - viewTop).toFloat())
                else if (bottom > viewBottom) state.animateScrollBy((bottom - viewBottom).toFloat())
            }
        }
    }
    if (apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) { Msg("(trống)") }
    else LazyVerticalGrid(GridCells.Fixed(APP_COLS), Modifier.weight(1f).fillMaxWidth(), state = state) {
        itemsIndexed(apps, key = { _, a -> a.pkg }) { i, a ->
            val icon by produceState<ImageBitmap?>(AppIcons.get(a.pkg), a.pkg) {
                if (value == null) value = withContext(Dispatchers.IO) { AppIcons.load(ctx, a.pkg) }
            }
            Box(Modifier.aspectRatio(1f).padding(3.dp)
                .then(if (i == sel) Modifier.background(INK) else Modifier.border(1.dp, INK))
                .pointerInput(a.pkg) {   // chạm = mở, giữ 2 giây = hỏi ghim ra màn hình chính
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var long = false
                        val up = try { withTimeout(2000L) { waitForUpOrCancellation() } }
                            catch (_: PointerEventTimeoutCancellationException) { long = true; null }
                        if (long) { onLong(i); waitForUpOrCancellation() } else if (up != null) onTap(i)
                    }
                }.padding(6.dp), contentAlignment = Alignment.Center) {
                icon?.let { Image(it, null, Modifier.fillMaxSize()) }
            }
        }
    }
    // Tên app đang chọn
    Text(apps.getOrNull(sel)?.label ?: "", Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
}

/** Icon app đã ghim ở màn hình chờ (dưới khung ngày giờ): tối đa 8 icon, 4 icon một hàng, chạm để mở. */
@Composable
fun PinnedIcons(pkgs: List<String>, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    if (pkgs.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        pkgs.take(MAX_PINS).chunked(APP_COLS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { pkg ->
                    val icon by produceState<ImageBitmap?>(AppIcons.get(pkg), pkg) {
                        if (value == null) value = withContext(Dispatchers.IO) { AppIcons.load(ctx, pkg) }
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(4.dp).clickable { onOpen(pkg) }, contentAlignment = Alignment.Center) {
                        icon?.let { Image(it, null, Modifier.fillMaxSize()) }
                    }
                }
                repeat(APP_COLS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
