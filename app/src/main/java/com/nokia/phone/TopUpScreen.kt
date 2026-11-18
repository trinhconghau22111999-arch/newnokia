package com.nokia.phone

import androidx.compose.foundation.layout.*
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

/** Màn hình Nạp thẻ cào: hướng dẫn / kết quả đọc mã. */
@Composable
fun TopUpScreen(msg: String) {
    Column(Modifier.fillMaxSize()) {
        Head("NẠP THẺ CÀO")
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(msg.ifEmpty { "Bấm OK để chụp ảnh thẻ cào" }, color = INK, fontFamily = MONO,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
