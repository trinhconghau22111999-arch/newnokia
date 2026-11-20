package com.nokia.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Cập nhật: tải APK mới nhất từ GitHub Releases rồi mở trình cài đặt (cài đè lên app hiện tại). */
object Updater {
    /** Đường dẫn cố định: luôn trỏ tới Release mới nhất (file tên phonecuibap.apk). */
    const val APK_URL = "https://github.com/trinhconghau22111999-arch/newnokia/releases/latest/download/phonecuibap.apk"

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    private fun say(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    fun start(ctx: Context) {
        if (busy) { say(ctx, "Đang tải bản cập nhật..."); return }
        busy = true
        say(ctx, "Đang tải bản cập nhật...")
        Thread {
            try {
                val file = File(ctx.cacheDir, "update.apk")
                val conn = (URL(APK_URL).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code")
                    conn.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                if (file.length() < 100_000) throw IOException("file tải về không hợp lệ")
                main.post { install(ctx, file) }
            } catch (e: Exception) {
                say(ctx, "Không tải được bản cập nhật: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }.start()
    }

    private fun install(ctx: Context, file: File) {
        // Android 8+: phải được phép "cài ứng dụng không rõ nguồn" cho app này
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            say(ctx, "Bật \"Cho phép từ nguồn này\", rồi bấm Cập nhật lần nữa")
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
            return
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            say(ctx, "Không mở được trình cài đặt")
        }
    }
}
