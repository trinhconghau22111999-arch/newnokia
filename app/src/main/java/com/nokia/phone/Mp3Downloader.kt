package com.nokia.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tải nhạc từ YouTube về mp3 qua trang savemp3.net, chạy trong một WebView ẩn:
 * tự dán đường dẫn video vào ô nhập, bấm nút chuyển đổi, đợi nút/đường dẫn tải về rồi lưu tệp vào [dest].
 */
class Mp3Downloader(private val act: Activity) {
    companion object {
        const val SITE = "https://vi.savemp3.net/ruhuh/youtube-video-to-mp3/"
        private const val SITE_HOST = "savemp3.net"
        private const val MIN_BYTES = 30_000L

        // Chụp tình trạng trang khi bị kẹt (chép vào bộ nhớ tạm để gửi lại cho người sửa app)
        private val DUMP_JS = """
(function(){try{function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0}
var o=[];o.push('url='+location.href);o.push('size='+innerWidth+'x'+innerHeight);
var ins=document.querySelectorAll('input');var a=[];
for(var i=0;i<ins.length;i++)a.push((ins[i].type||'')+(vis(ins[i])?'+':'-')+':'+(ins[i].value||'').slice(0,60));
o.push('inputs='+a.join(' | '));
var bs=document.querySelectorAll('a[href],button');var b=[];
for(var j=0;j<bs.length&&b.length<25;j++){if(vis(bs[j]))b.push((bs[j].textContent||'').trim().slice(0,30)+(bs[j].href?' > '+bs[j].href.slice(0,70):''))}
o.push('buttons='+b.join(' | '));
var fi=null;for(var q=0;q<ins.length;q++){var tt=(ins[q].type||'text').toLowerCase();if((tt==='text'||tt==='url'||tt==='search')&&vis(ins[q])){fi=ins[q];break}}
if(fi){var fc=fi.closest('form')||(fi.parentElement&&fi.parentElement.parentElement&&fi.parentElement.parentElement.parentElement)||fi.parentElement;o.push('form='+(fc?fc.outerHTML.replace(/\s+/g,' ').slice(0,900):''))}
try{var sx=window.__dl;o.push('S='+JSON.stringify({phase:sx.phase,btn:sx.btn,n:sx.n,sel:sx.selDone,q:!!sx.qEl}))}catch(e){o.push('S=none')}
o.push('text='+(document.body.innerText||'').replace(/\s+/g,' ').slice(0,300));
return o.join('\n')}catch(e){return 'dump err '+e}})()
"""

        // Mỗi nhịp: (1) chưa điền -> dán đường dẫn + bấm nút chuyển đổi; (2) đã bấm -> tìm nút/liên kết Tải xuống MỚI xuất hiện.
        private val STEP_JS = """
(function(u){try{
 var S=window.__dl=window.__dl||{phase:0,snap:null,btn:''};
 function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0}
 if(S.phase===0){
  var inp=null,ins=document.querySelectorAll('input');
  for(var i=0;i<ins.length;i++){var t=(ins[i].type||'text').toLowerCase();
   if((t==='text'||t==='url'||t==='search'||t==='')&&vis(ins[i])){inp=ins[i];break}}
  if(!inp)return 'wait';
  S.snap=new Set(document.querySelectorAll('a,button'));
  var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
  inp.focus();setter.call(inp,u);
  inp.dispatchEvent(new Event('input',{bubbles:true}));inp.dispatchEvent(new Event('change',{bubbles:true}));
  var btn=null,node=inp;
  for(var k=0;k<6&&node&&!btn;k++){node=node.parentElement;if(!node)break;
   var bs=node.querySelectorAll('button,input[type=submit],[type=submit]');
   for(var j=0;j<bs.length;j++){if(vis(bs[j])&&!bs[j].disabled){btn=bs[j];break}}}
  if(btn){S.btn=(btn.textContent||btn.value||'').trim().toLowerCase();btn.click()}
  else{var fm=inp.closest('form');
   if(fm&&fm.requestSubmit)fm.requestSubmit();
   else{['keydown','keypress','keyup'].forEach(function(n){inp.dispatchEvent(new KeyboardEvent(n,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}))})}}
  S.phase=1;S.n=0;return 'filled';
 }
 if(S.phase===1){
  S.n=(S.n||0)+1;
  var cap=document.querySelector('iframe[src*=captcha],iframe[src*=turnstile],iframe[src*=challenge],.cf-turnstile,.g-recaptcha,#challenge-form');
  if(cap&&vis(cap)&&!S.capSent){S.capSent=1;return 'captcha'}
  // Có chọn chất lượng: chọn bitrate cao nhất miễn phí (bỏ mục có chữ PRO / Premium / VIP / đăng nhập)
  function kb(e){var m=(e.textContent||e.value||'').match(/(\d{2,3})\s*kbps/i);return m?parseInt(m[1],10):0}
  function locked(e){return /pro\b|premium|vip|đăng nhập|login|trả phí/i.test(e.textContent||'')}
  var sl=document.querySelectorAll('select');
  for(var a=0;a<sl.length;a++){var sel=sl[a];if(S.snap&&S.snap.has(sel))continue;
   var best=null,bk=0;
   for(var o=0;o<sel.options.length;o++){var op=sel.options[o];var v=kb(op);if(v>bk&&!locked(op)){bk=v;best=op}}
   if(best&&sel.value!==best.value&&!S.selDone){
    var ss=Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set;
    ss.call(sel,best.value);sel.dispatchEvent(new Event('change',{bubbles:true}));S.selDone=1}}
  var qs=[],all=document.querySelectorAll('a[href],button,[role=button],[role=option],[role=menuitem],label,li');
  for(var b=0;b<all.length;b++){var qe=all[b];
   if(S.snap&&S.snap.has(qe))continue;if(!vis(qe))continue;
   if((qe.textContent||'').length>60)continue;
   var qv=kb(qe);if(qv>0&&!locked(qe))qs.push({e:qe,k:qv})}
  var many=false;for(var c=1;c<qs.length;c++){if(qs[c].k!==qs[0].k){many=true;break}}
  if(many&&!S.qEl){qs.sort(function(x,y){return y.k-x.k});S.qEl=qs[0].e;
   var qh=S.qEl.href||'';
   if(qh&&qh.indexOf('javascript:')!==0&&qh!==location.href&&qh.charAt(qh.length-1)!=='#'&&/^https?:/.test(qh)&&!/savemp3\.net\/[a-z]{2,}\//.test(qh)){S.phase=2;return 'link:'+qh}
   S.qEl.click();return 'quality'}
  var els=document.querySelectorAll('a[href],button');
  for(var q=0;q<els.length;q++){var e=els[q];
   if(S.snap&&S.snap.has(e))continue;if(!vis(e))continue;
   var tx=(e.textContent||'').trim().toLowerCase();if(!tx&&!e.hasAttribute('download'))continue;
   var h=e.href||'';
   if(/play\.google|chrome\.google|facebook\.com|savevideo\.net|savemp3\.net\/(about|privacy|eula|terms|contact|mobile)/.test(h))continue;
   var dl=e.hasAttribute('download')||/^(tải xuống|tải về|tải|download|save|lưu)/.test(tx)||(kb(e)>0&&(e===S.qEl||(!S.qEl&&qs.length===1&&qs[0].e===e)));
   if(!dl)continue;
   if(tx===S.btn&&!h)continue;
   if(h&&h.indexOf('javascript:')!==0&&h!==location.href&&h.charAt(h.length-1)!=='#'){S.phase=2;return 'link:'+h}
   e.click();S.phase=2;return 'click';
  }
  return 'wait';
 }
 return 'wait';
}catch(err){return 'err:'+err}})(""" + "%URL%" + ")"
    }

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var running = false
    private var startedAt = 0L
    private var fills = 0
    private var fetches = 0
    private var fetching = false
    private var sawCaptcha = false
    private var lastActAt = 0L
    private var stalling = false
    private var phase2At = 0L
    private val netLog = mutableListOf<String>()   // các yêu cầu mạng gần nhất của WebView (bỏ ảnh/css/font) để chẩn đoán
    private val conLog = mutableListOf<String>()   // thông báo console của trang
    private fun logTo(l: MutableList<String>, s: String) { synchronized(l) { l.add(s); if (l.size > 14) l.removeAt(0) } }
    private var clicks = 0              // số lần bấm/nhận link tải; quá giới hạn -> báo lỗi, không lặp mãi
    private var lastByteAt = 0L         // lần cuối có dữ liệu về (hoặc lúc bắt đầu tải tệp)
    private var videoUrl = ""
    private var dest: File? = null
    private var onStatus: (String) -> Unit = {}
    private var onResult: (Boolean, String) -> Unit = { _, _ -> }
    private var onProgress: (Int) -> Unit = {}
    private var pct = 0

    /** Chỉ tăng, tối đa 99 cho tới khi lưu xong. */
    private fun progress(p: Int) {
        val v = p.coerceIn(0, 99)
        if (v > pct) { pct = v; main.post { if (running) onProgress(v) } }
    }

    val busy: Boolean get() = running

    private inner class Bridge {
        @JavascriptInterface fun blob(dataUrl: String) {
            Thread {
                try {
                    val i = dataUrl.indexOf(',')
                    val bytes = Base64.decode(if (i >= 0) dataUrl.substring(i + 1) else dataUrl, Base64.DEFAULT)
                    saveBytes(bytes)
                } catch (e: Exception) { fetchFailed("Không đọc được tệp tải về") }
            }.start()
        }
        @JavascriptInterface fun fail() { fetchFailed("Không lấy được tệp nhạc từ trang") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun start(videoUrl: String, dest: File, onStatus: (String) -> Unit, onResult: (Boolean, String) -> Unit, onProgress: (Int) -> Unit = {}) {
        if (running) return
        running = true; fetching = false; sawCaptcha = false; stalling = false; fills = 0; fetches = 0; phase2At = 0L; clicks = 0; lastByteAt = 0L
        this.videoUrl = videoUrl; this.dest = dest; this.onStatus = onStatus; this.onResult = onResult; this.onProgress = onProgress
        pct = 0; onProgress(0)
        startedAt = System.currentTimeMillis(); lastActAt = startedAt
        onStatus("Đang mở trang tải nhạc...")
        val w = WebView(act)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = true
        w.settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.addJavascriptInterface(Bridge(), "AndroidDl")
        w.setDownloadListener { url, ua, _, _, _ -> onFile(url, ua) }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                cm?.let { logTo(conLog, "${it.messageLevel()}: ${it.message().take(140)}") }
                return true
            }
        }
        synchronized(netLog) { netLog.clear() }; synchronized(conLog) { conLog.clear() }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.let {
                    val u = it.url.toString()
                    if (!Regex("\\.(png|jpe?g|gif|svg|css|woff2?|ttf|ico|webp)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(u))
                        logTo(netLog, "${it.method} ${u.take(120)}")
                }
                return null
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                request?.let { logTo(netLog, "HTTP ${errorResponse?.statusCode} ${it.url.toString().take(100)}") }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val r = request ?: return false
                if (!r.isForMainFrame) return false
                val u = r.url
                val scheme = (u.scheme ?: "").lowercase()
                if (scheme == "blob" || scheme == "data") { onFile(u.toString(), null); return true }
                if (scheme != "http" && scheme != "https") return true      // chặn intent:, market:, ...
                val h = (u.host ?: "").lowercase()
                if (h == SITE_HOST || h.endsWith(".$SITE_HOST")) return false
                onFile(u.toString(), null)                                  // sang tên miền khác = đường dẫn tệp -> tải bằng app
                return true
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { }
        }
        // Gắn vào cửa sổ (rất nhỏ, trong suốt) để WebView chạy script bình thường
        try {
            val deco = act.window.decorView as ViewGroup
            val lp = FrameLayout.LayoutParams(deco.width.coerceAtLeast(360), deco.height.coerceAtLeast(640))
            deco.addView(w, 0, lp)      // nằm dưới giao diện app, kích thước thật để trang hiện đúng bố cục di động
            w.alpha = 0.01f
        } catch (_: Exception) {}
        w.loadUrl(SITE)
        main.postDelayed(tick, 1500)
    }

    private val tick = object : Runnable {
        override fun run() {
            val w = web ?: return
            if (!running) return
            if (fetching) {
                // Đang tải tệp: chỉ coi là còn sống khi có dữ liệu về trong 10 giây, nếu không thì báo lỗi (trước đây treo mãi ở 39%)
                if (System.currentTimeMillis() - lastByteAt > 10_000L) { fetchFailed("Tải lỗi: máy chủ không gửi dữ liệu trong 10 giây"); return }
                main.postDelayed(this, 1000); return
            }
            // Kẹt một bước quá lâu (không thấy ô dán link / không ra nút tải) -> báo lỗi kèm chi tiết, đừng treo mãi
            if (System.currentTimeMillis() - lastActAt > (if (fills == 0) 25_000L else 45_000L)) { stall(w); return }
            // Giới hạn tổng: 3 phút mà chưa bắt đầu tải được tệp thì dừng, dù bước nào cũng "có hoạt động"
            if (System.currentTimeMillis() - startedAt > 180_000L) { stall(w); return }
            val t = (System.currentTimeMillis() - startedAt) / 1000.0
            progress((40 * (1 - Math.exp(-t / 20.0))).toInt())
            w.evaluateJavascript(STEP_JS.replace("%URL%", JSONObject.quote(videoUrl))) { raw ->
                if (!running) return@evaluateJavascript
                val r = try { JSONObject("{\"v\":$raw}").optString("v") } catch (_: Exception) { "" }
                when {
                    r == "filled" -> { fills++; lastActAt = System.currentTimeMillis(); onStatus("Đang chuyển đổi sang mp3...") ; phase2At = 0L
                        if (fills > 3) { finish(false, "Trang tải nhạc không phản hồi"); return@evaluateJavascript } }
                    r.startsWith("link:") -> {
                        if (++clicks > 2) { stall(w); return@evaluateJavascript }
                        phase2At = System.currentTimeMillis(); lastActAt = phase2At; onFile(r.substring(5), null)
                    }
                    r == "click" -> {
                        // Bấm nút Tải xuống mãi mà không ra tệp (trang mở quảng cáo / không phản hồi): bấm tối đa 2 lần rồi báo lỗi
                        if (++clicks > 2) { stall(w); return@evaluateJavascript }
                        phase2At = System.currentTimeMillis(); lastActAt = phase2At; onStatus("Đang tải về...")
                    }
                    r == "quality" -> { lastActAt = System.currentTimeMillis(); onStatus("Đang chọn chất lượng cao nhất...") }
                    r == "captcha" -> { sawCaptcha = true; finish(false, "Tải lỗi: trang yêu cầu xác minh"); return@evaluateJavascript }
                    r.startsWith("err:") -> { /* bỏ qua, thử lại nhịp sau */ }
                }
                // Đã bấm Tải xuống mà 25 giây không thấy tệp -> cho phép tìm nút khác
                if (phase2At != 0L && !fetching && System.currentTimeMillis() - phase2At > 20_000L) {
                    phase2At = 0L
                    w.evaluateJavascript("(function(){if(window.__dl&&window.__dl.phase===2)window.__dl.phase=1})()", null)
                }
            }
            main.postDelayed(this, 1000)
        }
    }

    private fun stall(w: WebView) {
        if (stalling) return
        stalling = true
        val step = if (fills == 0) "dán link vào trang" else "chờ nút tải về"
        val done = Runnable { finish(false, "Tải lỗi: kẹt ở bước $step (đã chép chi tiết vào bộ nhớ tạm, dán gửi lại để sửa)") }
        w.evaluateJavascript(DUMP_JS) { raw ->
            val d = try { JSONObject("{\"v\":$raw}").optString("v") } catch (_: Exception) { "" }
            try {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val net = synchronized(netLog) { netLog.joinToString("\n") }
                val con = synchronized(conLog) { conLog.joinToString("\n") }
                cm.setPrimaryClip(ClipData.newPlainText("savemp3", "bước=$step fills=$fills clicks=$clicks fetches=$fetches\n$d\n--net--\n$net\n--console--\n$con"))
            } catch (_: Exception) {}
            main.removeCallbacks(done); done.run()
        }
        main.postDelayed(done, 3000)   // phòng khi không chụp được trang
    }

    /** Có đường dẫn tệp (từ nút tải / chuyển hướng / DownloadListener): tải bằng app. */
    private fun onFile(url: String, ua: String?) {
        if (!running || fetching) return
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            fetching = true
            progress(90)
            onStatus("Đang lưu nhạc...")
            val js = "(function(){try{fetch(" + JSONObject.quote(url) + ").then(function(r){return r.blob()}).then(function(b){" +
                "var f=new FileReader();f.onload=function(){AndroidDl.blob(f.result)};f.readAsDataURL(b)}).catch(function(){AndroidDl.fail()})}catch(e){AndroidDl.fail()}})()"
            web?.evaluateJavascript(js, null)
            return
        }
        if (fetches >= 2) { fetchFailed("Tải lỗi: thử 2 liên kết đều không được"); return }
        fetches++
        fetching = true
        lastByteAt = System.currentTimeMillis()
        onStatus("Đang tải nhạc về máy...")
        val agent = ua ?: web?.settings?.userAgentString
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                if (agent != null) conn.setRequestProperty("User-Agent", agent)
                conn.setRequestProperty("Referer", SITE)
                CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
                if (conn.responseCode !in 200..299) { conn.disconnect(); fetchFailed("Máy chủ trả lỗi ${conn.responseCode}"); return@Thread }
                val ct = (conn.contentType ?: "").lowercase()
                if (ct.contains("text/html") || ct.contains("json")) { conn.disconnect(); fetchFailed("Liên kết không phải tệp nhạc"); return@Thread }
                val d = dest ?: run { conn.disconnect(); fetchFailed("Tải lỗi: thiếu nơi lưu tệp"); return@Thread }
                val tmp = File(d.parentFile, d.name + ".part")
                val total = conn.contentLengthLong
                lastByteAt = System.currentTimeMillis()
                progress(40)
                conn.inputStream.use { i -> tmp.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024); var done = 0L; var n = 0
                    while (running && i.read(buf).also { n = it } > 0) {
                        lastByteAt = System.currentTimeMillis()
                        o.write(buf, 0, n); done += n
                        // biết dung lượng: 40..95% theo byte; không biết: tăng dần theo từng 100KB
                        progress(if (total > 0) 40 + (55 * done / total).toInt() else 40 + minOf(50L, done / 100_000L).toInt())
                    }
                } }
                conn.disconnect()
                if (!running) { tmp.delete(); return@Thread }     // đã hủy / báo lỗi trong lúc tải
                commit(tmp)
            } catch (e: Exception) { fetchFailed("Tải lỗi: " + (e.message ?: "không rõ")) }
        }.start()
    }

    private fun saveBytes(bytes: ByteArray) {
        val d = dest ?: return
        val tmp = File(d.parentFile, d.name + ".part")
        tmp.writeBytes(bytes)
        commit(tmp)
    }

    private fun commit(tmp: File) {
        val d = dest ?: return
        if (tmp.length() < MIN_BYTES || !looksLikeAudio(tmp)) { tmp.delete(); fetchFailed("Tệp tải về không phải nhạc"); return }
        d.delete()
        if (!tmp.renameTo(d)) { tmp.copyTo(d, true); tmp.delete() }
        main.post { finish(true, "Đã tải xong, lần sau bấm vào bài này sẽ nghe trong máy") }
    }

    private fun looksLikeAudio(f: File): Boolean = try {
        val b = ByteArray(12)
        f.inputStream().use { it.read(b) }
        val id3 = b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()
        val sync = (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xE0) == 0xE0
        val ftyp = b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() && b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte()
        val ogg = b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte()
        val riff = b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte()
        id3 || sync || ftyp || ogg || riff
    } catch (_: Exception) { false }

    /** Tải hỏng: báo lỗi ngay. */
    private fun fetchFailed(msg: String) {
        main.post { finish(false, msg) }
    }

    private fun finish(ok: Boolean, msg: String) {
        if (!running) return
        running = false; fetching = false
        main.removeCallbacks(tick)
        web?.let { w ->
            try { (w.parent as? ViewGroup)?.removeView(w) } catch (_: Exception) {}
            try { w.stopLoading(); w.loadUrl("about:blank"); w.destroy() } catch (_: Exception) {}
        }
        web = null
        if (ok) onProgress(100)
        onResult(ok, if (ok || msg.startsWith("Tải lỗi")) msg else "Tải lỗi: " + msg)
    }

    /** Hủy khi rời màn hình / thoát app. */
    fun cancel() {
        if (!running) return
        running = false; fetching = false
        main.removeCallbacks(tick)
        web?.let { w ->
            try { (w.parent as? ViewGroup)?.removeView(w) } catch (_: Exception) {}
            try { w.stopLoading(); w.loadUrl("about:blank"); w.destroy() } catch (_: Exception) {}
        }
        web = null
    }
}
