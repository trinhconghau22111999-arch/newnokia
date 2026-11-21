package com.nokia.phone

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** Một bài nhạc. sd = true khi nằm trên thẻ nhớ (không phải bộ nhớ trong của máy). */
data class Track(val id: Long, val title: String, val artist: String, val dur: Long, val sd: Boolean)

/** Thư viện nhạc: đọc từ MediaStore nên có cả bài trong bộ nhớ máy lẫn thẻ nhớ (thẻ phải được hệ thống quét). */
object MusicLib {
    fun uri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    @Suppress("DEPRECATION")
    private fun isSdPath(path: String): Boolean {
        if (path.isEmpty()) return false
        val internal = Environment.getExternalStorageDirectory().absolutePath
        return !(path.startsWith(internal) || path.startsWith("/sdcard") || path.startsWith("/storage/emulated/"))
    }

    @Suppress("DEPRECATION")
    fun load(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val q29 = Build.VERSION.SDK_INT >= 29
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            if (q29) "volume_name" else MediaStore.Audio.Media.DATA     // Android 10+: tên ổ (external_primary = bộ nhớ máy)
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1)?.trim().orEmpty().ifEmpty { "(không tên)" }
                    var artist = c.getString(2)?.trim().orEmpty()
                    if (artist == "<unknown>") artist = ""
                    val src = c.getString(4).orEmpty()
                    val sd = if (q29) src.isNotEmpty() && src != "external_primary" && src != "internal" else isSdPath(src)
                    out.add(Track(c.getLong(0), title, artist, c.getLong(3), sd))
                }
            }
        } catch (_: Exception) {}
        return out.sortedBy { plain(it.title) }
    }
}

/** Bộ phát nhạc dùng chung; tự tạm dừng khi có cuộc gọi / app khác giành âm thanh. */
@Suppress("DEPRECATION")
object Music {
    private var mp: MediaPlayer? = null
    private var am: AudioManager? = null
    private var resumeOnGain = false

    var paused = false
        private set
    val active: Boolean get() = mp != null

    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPlaying()) { resumeOnGain = true; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> try { mp?.setVolume(0.2f, 0.2f) } catch (_: Exception) {}
            AudioManager.AUDIOFOCUS_GAIN -> {
                try { mp?.setVolume(1f, 1f) } catch (_: Exception) {}
                if (resumeOnGain) { resumeOnGain = false; resume() }
            }
            else -> {}
        }
    }

    /** Mở nguồn và phát. onEnd: hát hết bài; onError: lỗi giữa chừng. Trả về false nếu không mở được. title != null: chạy dịch vụ nền để tắt màn hình vẫn nghe. */
    private fun start(ctx: Context, title: String?, onEnd: () -> Unit, onError: () -> Unit, source: (MediaPlayer, Context) -> Unit): Boolean {
        stop()
        val app = ctx.applicationContext
        val a = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am = a
        val m = MediaPlayer()
        return try {
            m.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            if (title != null) m.setWakeMode(app, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            source(m, app)
            m.prepare()
            m.setOnCompletionListener { stop(); onEnd() }
            m.setOnErrorListener { _, _, _ -> stop(); onError(); true }
            a.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            m.start()
            mp = m
            paused = false
            if (title != null) try { MusicService.start(app, title); svcCtx = app } catch (_: Exception) {}
            true
        } catch (_: Exception) {
            try { m.release() } catch (_: Exception) {}
            false
        }
    }

    private var svcCtx: Context? = null

    /** Phát bài trong thư viện máy / thẻ nhớ. */
    fun play(ctx: Context, t: Track, onEnd: () -> Unit, onError: () -> Unit): Boolean =
        start(ctx, null, onEnd, onError) { m, app -> m.setDataSource(app, MusicLib.uri(t.id)) }

    /** Phát tệp mp3 đã tải trong app (có dịch vụ nền + giữ CPU). */
    fun playFile(ctx: Context, f: java.io.File, title: String, onEnd: () -> Unit, onError: () -> Unit): Boolean =
        start(ctx, title, onEnd, onError) { m, _ -> m.setDataSource(f.absolutePath) }

    fun stop() {
        val m = mp
        mp = null
        paused = false
        resumeOnGain = false
        if (m != null) {
            try { m.stop() } catch (_: Exception) {}
            try { m.release() } catch (_: Exception) {}
            try { am?.abandonAudioFocus(focus) } catch (_: Exception) {}
        }
        svcCtx?.let { try { MusicService.stop(it) } catch (_: Exception) {} }
        svcCtx = null
    }

    private fun isPlaying(): Boolean = try { mp?.isPlaying == true } catch (_: Exception) { false }

    private fun pause() {
        try { if (mp?.isPlaying == true) { mp?.pause(); paused = true } } catch (_: Exception) {}
    }

    private fun resume() {
        try { if (mp != null && paused) { mp?.start(); paused = false } } catch (_: Exception) {}
    }

    fun toggle() { if (paused) resume() else pause() }

    /** Tua tới / lui deltaMs (âm = lui). */
    fun seek(deltaMs: Long) {
        val m = mp ?: return
        try {
            val target = (m.currentPosition + deltaMs).coerceIn(0L, maxOf(0L, m.duration - 500L))
            m.seekTo(target.toInt())
        } catch (_: Exception) {}
    }

}
