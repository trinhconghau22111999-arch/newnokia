package com.nokia.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.telephony.PhoneNumberUtils
import android.os.Looper
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val M_NONE = 0     // không còn cuộc gọi (hiện "Kết thúc" rồi đóng)
private const val M_RING = 1     // cuộc gọi đến
private const val M_DIAL = 2     // đang gọi đi / đang kết nối
private const val M_ACTIVE = 3   // đang nói chuyện / đang giữ
private const val M_PICK = 4     // chọn SIM để gọi

/**
 * Giao diện gọi và nhận cuộc gọi theo kiểu Nokia: toàn bộ thông tin nằm trong màn hình LCD,
 * điều khiển bằng phím Gọi (nghe), phím Tắt (cúp/từ chối), phím mềm và D-pad.
 * Chỉ nhận cuộc gọi khi phonecuibap được đặt làm ứng dụng gọi điện mặc định.
 */
class CallActivity : LcdActivity() {
    override val overLock = true

    private var menuOpen by mutableStateOf(false)
    private var sel by mutableIntStateOf(0)
    private var typed by mutableStateOf("")
    private val handler = Handler(Looper.getMainLooper())

    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCallRec() else toast("Cần quyền Micro để ghi âm")
    }

    /** Khi kết thúc cuộc gọi cũng không chạy hiệu ứng trượt, để LCD không bị vẽ lại. */
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLcd(left = { leftLabel() }, right = { rightLabel() }, keyLeft = { keyLeftLabel() }) { CallLcd() }
    }

    // ------------------------------------------------------------ trạng thái

    private fun mode(): Int {
        CallHub.watch()
        val c = CallHub.primary() ?: return M_NONE
        return when (CallHub.stateOf(c)) {
            Call.STATE_RINGING -> M_RING
            Call.STATE_SELECT_PHONE_ACCOUNT -> M_PICK
            Call.STATE_NEW, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> M_DIAL
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> M_NONE
            else -> M_ACTIVE
        }
    }

    private fun spk(): String = if (CallHub.speakerOn()) "Loa trong" else "Loa ngoài"

    private fun leftLabel(): String = when (mode()) {
        M_RING -> "Nghe"
        M_DIAL -> spk()
        M_PICK -> "Chọn"
        M_ACTIVE -> if (menuOpen) "Chọn" else spk()
        else -> ""
    }

    private fun rightLabel(): String = when (mode()) {
        M_RING -> "Từ chối"
        M_DIAL, M_PICK -> "Hủy"
        M_ACTIVE -> if (menuOpen) "Về" else if (CallHub.muted()) "Bật mic" else "Im lặng"
        else -> ""
    }

    /** Nhãn nút mềm trái trên bàn phím: khi đang gọi đây là nút bật/tắt loa (trước là nút "Menu"). */
    private fun keyLeftLabel(): String = when (mode()) {
        M_DIAL -> spk()
        M_ACTIVE -> if (menuOpen) "Chọn" else spk()
        else -> "Menu"
    }

    private fun accounts(): List<PhoneAccountHandle> = try {
        (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).callCapablePhoneAccounts
    } catch (_: Exception) { emptyList() }

    private fun accountLabel(h: PhoneAccountHandle): String = try {
        (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).getPhoneAccount(h)?.label?.toString() ?: "SIM"
    } catch (_: Exception) { "SIM" }

    private fun menuItems(c: Call): List<String> = listOf(
        "Loa ngoài: " + if (CallHub.speakerOn()) "BẬT" else "TẮT",
        "Micro: " + if (CallHub.muted()) "TẮT" else "BẬT",
        if (CallHub.stateOf(c) == Call.STATE_HOLDING) "Tiếp tục cuộc gọi" else "Giữ cuộc gọi",
        if (CallHub.recording) "Dừng ghi âm" else "Ghi âm cuộc gọi",
        "Kết thúc cuộc gọi"
    )

    // ------------------------------------------------------------ phím

    override fun onKey(k: String) {
        val c = CallHub.primary()
        if (c == null) {
            if (k == "END" || k == "SOFTR" || k == "OK") finish()
            return
        }
        when (mode()) {
            M_RING -> when (k) {
                "CALL", "SOFTL" -> CallHub.answer(c)
                "END", "SOFTR" -> CallHub.reject(c)
                "OK" -> CallHub.silence(this)
            }
            M_PICK -> {
                val acc = accounts()
                when (k) {
                    "UP", "LEFT" -> if (acc.isNotEmpty()) sel = maxOf(sel - 1, 0)
                    "DOWN", "RIGHT" -> if (acc.isNotEmpty()) sel = minOf(sel + 1, acc.size - 1)
                    "OK", "SOFTL", "CALL" -> acc.getOrNull(sel)?.let { c.phoneAccountSelected(it, false) }
                    "END", "SOFTR" -> c.disconnect()
                }
            }
            M_DIAL -> when (k) {
                "SOFTL" -> CallHub.toggleSpeaker()
                "END", "SOFTR" -> c.disconnect()
            }
            M_ACTIVE -> if (menuOpen) menuKey(k, c) else when (k) {
                "END" -> c.disconnect()
                "SOFTL" -> CallHub.toggleSpeaker()          // nút "Menu" cũ nay là loa ngoài/trong
                "OK" -> { menuOpen = true; sel = 0 }       // OK mở menu: giữ máy, ghi âm...
                "SOFTR" -> CallHub.toggleMute()
                else -> if (k.length == 1 && (k[0].isDigit() || k == "*" || k == "#")) dtmf(c, k[0])
            }
        }
    }

    private fun menuKey(k: String, c: Call) {
        val n = menuItems(c).size
        when (k) {
            "UP", "LEFT" -> sel = maxOf(sel - 1, 0)
            "DOWN", "RIGHT" -> sel = minOf(sel + 1, n - 1)
            "SOFTR" -> menuOpen = false
            "END" -> c.disconnect()
            "OK", "SOFTL" -> {
                when (sel) {
                    0 -> CallHub.toggleSpeaker()
                    1 -> CallHub.toggleMute()
                    2 -> if (CallHub.stateOf(c) == Call.STATE_HOLDING) c.unhold() else c.hold()
                    3 -> toggleRec()
                    else -> c.disconnect()
                }
                if (sel != 4) CallHub.bump()
            }
            else -> if (k.length == 1 && (k[0].isDigit() || k == "*" || k == "#")) dtmf(c, k[0])
        }
    }

    private fun dtmf(c: Call, ch: Char) {
        if (CallHub.stateOf(c) != Call.STATE_ACTIVE) return
        typed = (typed + ch).takeLast(16)
        c.playDtmfTone(ch)
        handler.postDelayed({ c.stopDtmfTone() }, 150)
    }

    // ------------------------------------------------------------ ghi âm cuộc gọi

    private fun toggleRec() {
        if (CallHub.recording) {
            val f = Rec.stop()
            CallHub.recording = false
            toast(if (f != null) "Đã lưu bản ghi âm" else "Ghi âm lỗi")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startCallRec()
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCallRec() {
        if (Rec.active) { toast("Đang có một bản ghi âm khác"); return }
        if (Rec.start(this)) {
            CallHub.recording = true
            // Android không cho app thường thu trực tiếp tiếng bên kia: chỉ thu qua micro nên cần bật loa ngoài
            if (!CallHub.speakerOn()) toast("Bật loa ngoài để thu cả hai bên")
        } else toast("Không ghi âm được")
    }

    // ------------------------------------------------------------ giao diện LCD

    @Composable
    private fun CallLcd() {
        CallHub.watch()
        val ctx = LocalContext.current
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
        BackHandler { }   // đang gọi thì không thoát bằng nút Back; dùng phím Tắt

        val call = CallHub.primary()
        if (call == null) {
            val end = CallHub.ended
            LaunchedEffect(Unit) {
                delay(if (end != null) 1600L else 0L)
                CallHub.ended = null
                finish()
            }
            EndView(end)
            return
        }

        val number = remember(call) { CallHub.numberOf(call) }
        val name by produceState(initialValue = number.ifEmpty { "Số ẩn" }, call) {
            value = withContext(Dispatchers.IO) {
                call.details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: CallHub.nameOf(ctx, number)
            }
        }
        val others = CallHub.calls.size - 1
        val blink = (now / 500) % 2 == 0L
        val dots = ".".repeat(((now / 500) % 3).toInt() + 1)

        when (mode()) {
            M_RING -> Box(Modifier.fillMaxSize()) {
                CallTop(name, number, "Cuộc gọi đến " + dots)
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (others > 0) Text("(+$others cuộc gọi khác)", color = INK, fontFamily = MONO, fontSize = 12.sp)
                    Text("OK: tắt chuông", color = INK, fontFamily = MONO, fontSize = 12.sp)
                }
            }
            M_PICK -> {
                val acc = remember(call) { accounts() }
                Column(Modifier.fillMaxSize()) {
                    Head("GỌI BẰNG SIM NÀO?")
                    Who(name, number, Modifier.padding(vertical = 6.dp))
                    Box(Modifier.weight(1f)) {
                        if (acc.isEmpty()) Msg("Không tìm thấy SIM")
                        else Lines(acc.map { accountLabel(it) }, sel,
                            { i -> sel = i; onKey("OK") }, { d -> onKey(if (d > 0) "DOWN" else "UP") })
                    }
                }
            }
            M_DIAL -> Box(Modifier.fillMaxSize()) {
                CallTop(name, number, (if (CallHub.stateOf(call) == Call.STATE_CONNECTING) "Đang kết nối " else "Đang gọi ") + dots)
                if (CallHub.speakerOn()) Text("LOA NGOÀI", Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            M_ACTIVE -> if (menuOpen) Column(Modifier.fillMaxSize()) {
                Head(name)
                Box(Modifier.weight(1f)) {
                    Lines(menuItems(call), sel, { i -> sel = i; onKey("OK") }, { d -> onKey(if (d > 0) "DOWN" else "UP") })
                }
            } else {
                val held = CallHub.stateOf(call) == Call.STATE_HOLDING
                val t = call.details?.connectTimeMillis ?: 0L
                val clock = if (t > 0) mmss((now - t).coerceAtLeast(0L)) else "0:00"
                val flags = buildList {
                    if (CallHub.speakerOn()) add("LOA")
                    if (CallHub.muted()) add("MIC TẮT")
                    if (CallHub.recording) add(if (blink) "● GHI" else "○ GHI")
                }.joinToString("  ")
                Box(Modifier.fillMaxSize()) {
                    CallTop(name, number, if (held) "Đang giữ  $clock" else clock)
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (typed.isNotEmpty()) Text(typed, color = Color.White, fontFamily = MONO, fontSize = 18.sp, maxLines = 1)
                        else if (others > 0) Text("(+$others cuộc gọi khác)", color = INK, fontFamily = MONO, fontSize = 12.sp)
                        if (flags.isNotEmpty()) Text(flags, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            else -> EndView(null)
        }
    }

    /** Phần trên màn hình cuộc gọi: tên (lớn) / số điện thoại | quốc gia / trạng thái. Dùng chung cho gọi đi, gọi đến và đang nói. */
    @Composable
    private fun CallTop(name: String, number: String, status: String) {
        val fmt = remember(number) { fmtNumber(number) }
        val hasName = number.isNotEmpty() && name != number
        val sub = listOfNotNull(fmt.takeIf { hasName && it.isNotEmpty() }, regionOf(number).takeIf { it.isNotEmpty() }).joinToString("  |  ")
        Column(Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hasName || fmt.isEmpty()) name else fmt, color = Color.White, fontFamily = MONO, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(sub, color = INK, fontFamily = MONO, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Text(status, color = Color.White, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }

    /** Số điện thoại có khoảng trắng cho dễ đọc, vd 0946 445 752. */
    private fun fmtNumber(n: String): String =
        if (n.isEmpty()) "" else try { PhoneNumberUtils.formatNumber(n, "VN") ?: n } catch (_: Exception) { n }

    /** Quốc gia/vùng suy ra từ đầu số (số trong nước 0xxx = Việt Nam). Số ngắn như 113, 1900... không hiện. */
    private fun regionOf(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        if (d.isEmpty()) return ""
        if (d.startsWith("+")) {
            val m = listOf("+84" to "Việt Nam", "+855" to "Campuchia", "+856" to "Lào", "+66" to "Thái Lan", "+65" to "Singapore",
                "+60" to "Malaysia", "+62" to "Indonesia", "+63" to "Philippines", "+81" to "Nhật Bản", "+82" to "Hàn Quốc",
                "+86" to "Trung Quốc", "+886" to "Đài Loan", "+852" to "Hồng Kông", "+61" to "Úc", "+44" to "Anh",
                "+33" to "Pháp", "+49" to "Đức", "+7" to "Nga", "+1" to "Hoa Kỳ / Canada")
            return m.firstOrNull { d.startsWith(it.first) }?.second.orEmpty()
        }
        return if (d.length >= 9 && d.startsWith("0")) "Việt Nam" else ""
    }

    @Composable
    private fun Who(name: String, number: String, mod: Modifier = Modifier) {
        Column(mod.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (number.isNotEmpty() && number != name)
                Text(number, color = INK, fontFamily = MONO, fontSize = 15.sp, maxLines = 1)
        }
    }

    @Composable
    private fun EndView(end: CallEnd?) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            if (end != null) {
                Text(end.text, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(end.name, color = INK, fontFamily = MONO, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (end.durMs > 0) Text(mmss(end.durMs), color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            }
        }
    }
}
