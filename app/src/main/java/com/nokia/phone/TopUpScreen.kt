package com.nokia.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tìm mã nạp thẻ (12 đến 16 chữ số) trong chữ đọc được từ ảnh. "" nếu không thấy. */
fun topupFind(text: String): String {
    val runs = Regex("\\d[\\d\\s-]{10,}\\d").findAll(text).map { m -> m.value.filter { it.isDigit() } }
    return runs.firstOrNull { it.length in 12..16 } ?: ""
}

/** Màn hình Nạp thẻ cào: camera mở sẵn ngay trong khung LCD, OK = chụp, đọc mã và nạp. */
@Composable
fun TopUpScreen(camOk: Boolean, cam: CamHolder, msg: String, busy: Boolean, onOk: () -> Unit) {
    if (!camOk) { Msg("Cần quyền Máy ảnh để chụp thẻ"); return }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        CameraView(cam, front = false, flash = false, video = false)
        Box(Modifier.fillMaxSize().clickable { onOk() })
        // Khung căn thẻ: đưa dãy số trên thẻ vào giữa khung
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * 0.88f
            val h = size.height * 0.34f
            drawRoundRect(INK, Offset((size.width - w) / 2f, (size.height - h) / 2f), Size(w, h),
                CornerRadius(10f, 10f), style = Stroke(width = 3f))
        }
        Text("NẠP THẺ CÀO  ·  OK = chụp & nạp", Modifier.align(Alignment.TopCenter).fillMaxWidth().background(LCD)
            .padding(horizontal = 6.dp, vertical = 3.dp),
            color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        val line = if (busy || msg.isNotEmpty()) msg else "Đưa dãy số trên thẻ vào khung rồi bấm OK"
        Text(line, Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(LCD).padding(horizontal = 6.dp, vertical = 4.dp),
            color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
