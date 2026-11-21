package com.nokia.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
        private const val TIMEOUT_MS = 100_000L
        private const val MIN_BYTES = 30_000L

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
    private var phase2At = 0L
    private var videoUrl = ""
    private var dest: File? = null
    private var onStatus: (String) -> Unit = {}
    private var onResult: (Boolean, String) -> Unit = { _, _ -> }

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
    fun start(videoUrl: String, dest: File, onStatus: (String) -> Unit, onResult: (Boolean, String) -> Unit) {
        if (running) return
        running = true; fetching = false; sawCaptcha = false; fills = 0; fetches = 0; phase2At = 0L
        this.videoUrl = videoUrl; this.dest = dest; this.onStatus = onStatus; this.onResult = onResult
        startedAt = System.currentTimeMillis()
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
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
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
            val lp = FrameLayout.LayoutParams(2, 2)
            (act.window.decorView as ViewGroup).addView(w, lp)
            w.alpha = 0.01f
        } catch (_: Exception) {}
        w.loadUrl(SITE)
        main.postDelayed(tick, 1500)
    }

    private val tick = object : Runnable {
        override fun run() {
            val w = web ?: return
            if (!running) return
            if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) { finish(false, if (sawCaptcha) "Trang đòi xác minh không phải robot, app không tự vượt qua được" else "Quá thời gian, thử lại sau"); return }
            if (fetching) { main.postDelayed(this, 1000); return }
            w.evaluateJavascript(STEP_JS.replace("%URL%", JSONObject.quote(videoUrl))) { raw ->
                if (!running) return@evaluateJavascript
                val r = try { JSONObject("{\"v\":$raw}").optString("v") } catch (_: Exception) { "" }
                when {
                    r == "filled" -> { fills++; onStatus("Đang chuyển đổi sang mp3...") ; phase2At = 0L
                        if (fills > 3) { finish(false, "Trang tải nhạc không phản hồi"); return@evaluateJavascript } }
                    r.startsWith("link:") -> { phase2At = System.currentTimeMillis(); onFile(r.substring(5), null) }
                    r == "click" -> { phase2At = System.currentTimeMillis(); onStatus("Đang tải về...") }
                    r == "quality" -> onStatus("Đang chọn chất lượng cao nhất...")
                    r == "captcha" -> { sawCaptcha = true; finish(false, "Tải lỗi: trang yêu cầu xác minh"); return@evaluateJavascript }
                    r.startsWith("err:") -> { /* bỏ qua, thử lại nhịp sau */ }
                }
                // Đã bấm Tải xuống mà 25 giây không thấy tệp -> cho phép tìm nút khác
                if (phase2At != 0L && !fetching && System.currentTimeMillis() - phase2At > 25_000L) {
                    phase2At = 0L
                    w.evaluateJavascript("(function(){if(window.__dl&&window.__dl.phase===2)window.__dl.phase=1})()", null)
                }
            }
            main.postDelayed(this, 1000)
        }
    }

    /** Có đường dẫn tệp (từ nút tải / chuyển hướng / DownloadListener): tải bằng app. */
    private fun onFile(url: String, ua: String?) {
        if (!running || fetching) return
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            fetching = true
            onStatus("Đang lưu nhạc...")
            val js = "(function(){try{fetch(" + JSONObject.quote(url) + ").then(function(r){return r.blob()}).then(function(b){" +
                "var f=new FileReader();f.onload=function(){AndroidDl.blob(f.result)};f.readAsDataURL(b)}).catch(function(){AndroidDl.fail()})}catch(e){AndroidDl.fail()}})()"
            web?.evaluateJavascript(js, null)
            return
        }
        if (fetches >= 3) return
        fetches++
        fetching = true
        onStatus("Đang tải nhạc về máy...")
        val agent = ua ?: web?.settings?.userAgentString
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                if (agent != null) conn.setRequestProperty("User-Agent", agent)
                conn.setRequestProperty("Referer", SITE)
                CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
                if (conn.responseCode !in 200..299) { conn.disconnect(); fetchFailed("Máy chủ trả lỗi ${conn.responseCode}"); return@Thread }
                val ct = (conn.contentType ?: "").lowercase()
                if (ct.contains("text/html") || ct.contains("json")) { conn.disconnect(); fetchFailed("Liên kết không phải tệp nhạc"); return@Thread }
                val d = dest ?: return@Thread
                val tmp = File(d.parentFile, d.name + ".part")
                conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                conn.disconnect()
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
