package com.anon.schedule.ui.mine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.anon.schedule.ui.common.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

// ============================ 学习通作业 / 考试 ============================
//
// 链路（逆向自学习通 App，2026-10 验证）：
//   1. WebView 内登录学习通，Cookie 落在本应用自己的 WebView CookieStore 里；
//   2. 顶层加载 https://mooc1-api.chaoxing.com/work/stu-work?userId={uid} —— 跨课程作业聚合页；
//   3. 页面同源执行 JS：解析 li[data] 得到作业，并从 data 里抽出 courseId/clazzId；
//   4. 再对这些课程并行 fetch /mooc-ans/exam/phone/task-list 得到各课考试；
//   5. 结果经 JS Bridge 回传，Compose 用原生卡片渲染。
//
// 注意：接口返回的是服务端渲染好的 HTML，「剩余X小时Y分钟」是抓取那一刻的静态文案，
// 不会自己走秒；点了刷新才会更新。

/** 登录页：登录成功后会话 Cookie 由 WebView 自己保存 */
private const val CX_LOGIN_URL =
    "https://passport2.chaoxing.com/mlogin?refer=https%3A%2F%2Fi.chaoxing.com"

/** 跨课程作业聚合页（userId 由 Cookie 里的 _uid 提供） */
private const val CX_WORK_URL = "https://mooc1-api.chaoxing.com/work/stu-work"

/**
 * 页面内抓取脚本。
 * 解析聚合页 → 提取课程 → 并行请求各课考试 → 一次性回传。
 * 用 location.origin 拼考试地址，保证与当前页面同源（避开 CORS）。
 */
private const val CX_FETCH_JS = """
(function () {
  function push(o) { try { window.cx_obj.push(JSON.stringify(o)); } catch (e) {} }
  function txt(el) { return el ? (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  var lis = document.querySelectorAll('li[data]');
  if (!lis.length) { push({ok:false, needLogin:true}); return; }
  var works = [], courses = {}, order = [];
  for (var i = 0; i < lis.length; i++) {
    var li = lis[i], d = li.getAttribute('data') || '';
    if (d.indexOf('type=work') < 0) continue;
    var ps = li.querySelectorAll('p'), ss = li.querySelectorAll('span');
    var course = '', remain = '', state = '';
    for (var j = 0; j < ss.length; j++) {
      var t = txt(ss[j]);
      if (!t) continue;
      if (t.charAt(0) === '《') course = t;
      else if (t.indexOf('剩余') === 0) remain = t;
      else if (!state) state = t;
    }
    works.push({title: txt(ps[0]), status: state, course: course, remain: remain, url: d, type: 'work'});
    var m1 = /courseId=(\d+)/.exec(d), m2 = /clazzId=(\d+)/.exec(d);
    if (m1 && m2) {
      var k = m1[1] + '_' + m2[1];
      if (!courses[k]) { courses[k] = {cid: m1[1], cls: m2[1], name: course}; order.push(k); }
    }
  }
  if (!order.length) { push({ok:true, works:works, exams:[]}); return; }
  var tasks = order.map(function (k) {
    var c = courses[k];
    var u = location.origin + '/mooc-ans/exam/phone/task-list?courseId=' + c.cid +
            '&classId=' + c.cls + '&cpi=0';
    return fetch(u, {credentials: 'include'}).then(function (r) { return r.text(); }).then(function (h) {
      var doc = new DOMParser().parseFromString(h, 'text/html');
      var out = [], es = doc.querySelectorAll('li[data]');
      for (var i = 0; i < es.length; i++) {
        var li = es[i], d = li.getAttribute('data') || '';
        if (d.indexOf('type=exam') < 0) continue;
        var p = li.querySelector('p'), ss = li.querySelectorAll('span');
        var state = '', remain = '';
        for (var j = 0; j < ss.length; j++) {
          var t = txt(ss[j]);
          if (!t) continue;
          if (t.indexOf('剩余') === 0) remain = t;
          else if (!state) state = t;
        }
        out.push({title: txt(p), status: state, course: c.name, remain: remain, url: d, type: 'exam'});
      }
      return out;
    }).catch(function () { return []; });
  });
  Promise.all(tasks).then(function (groups) {
    var exams = [];
    for (var i = 0; i < groups.length; i++) exams = exams.concat(groups[i]);
    push({ok: true, works: works, exams: exams});
  });
})();
"""

/**
 * 学习通 H5 会把「开始答题 / 开始考试」等跳转交给原生：jsBridge.postNotification('CLIENT_OPEN_URL', ...)。
 * 普通 WebView 没有原生桥，消息会被 jsbridge:// 的隐藏 iframe 静默吞掉，点击就毫无反应。
 * 这里以 androidjsbridge 的名义接管，把它当成本地导航。
 */
private class CxAppBridge(
    private val main: Handler,
    private val getWeb: () -> WebView?,
    private val onChooseImage: () -> Unit,
    private val onDisplayMessage: (String) -> Unit,
    private val onExitLevel: () -> Unit
) {
    @JavascriptInterface
    fun postNotification(name: String, json: String) {
        Log.i(CX_TAG, "bridge.postNotification name=$name json=${json.take(200)}")
        when (name) {
            // 拍照/图片：页面等我们用 CLIENT_CHOOSE_IMAGE_RESULT 把图片交回去
            "CLIENT_CHOOSE_IMAGE" -> {
                main.post { onChooseImage() }
                return
            }
            // 提交成功之类的提示
            "CLIENT_DISPLAY_MESSAGE" -> {
                val message = runCatching { JSONObject(json).optString("message") }.getOrDefault("")
                if (message.isNotEmpty()) main.post { onDisplayMessage(message) }
                return
            }
            // 提交完成后学习通会让原生「退出当前层级」，官方 App 就是退回任务列表
            "CLIENT_EXIT_LEVEL" -> {
                main.post { onExitLevel() }
                return
            }
        }
        if (name != "CLIENT_OPEN_URL") return
        val url = try {
            JSONObject(json).optString("webUrl")
        } catch (e: Exception) {
            ""
        }
        if (url.isEmpty()) return
        main.post { getWeb()?.loadUrl(url.replace("#INNER", "")) }
    }
}

/**
 * WebView 文件选择回调的持有者。
 * onShowFileChooser 必须同步返回 true 并交回 ValueCallback；用户选完（或取消）后
 * 必须且只能回调一次，否则页面上的那个 file input 会永久锁死、再点没反应。
 */
private class CxChooser {
    var callback: ValueCallback<Array<Uri>>? = null
    var multiple = false
    var mime = "*/*"

    /** 接管新的回调；上一轮没交付的先按取消收掉 */
    fun replace(cb: ValueCallback<Array<Uri>>?) {
        callback?.let { runCatching { it.onReceiveValue(null) } }
        callback = cb
    }

    fun finish(uris: List<Uri>?) {
        val cb = callback ?: return
        callback = null
        runCatching { cb.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray()) }
    }
}

/** accept 可能是 MIME 通配（如 image 前缀），也可能是 ".jpg" 这种后缀，统一转成 MIME */
private fun pickMime(types: Array<String>?): String {
    val first = types?.firstOrNull { it.isNotBlank() }?.trim()?.lowercase() ?: return "*/*"
    if (first.contains("/")) return first
    return when (first) {
        ".jpg", ".jpeg" -> "image/jpeg"
        ".png" -> "image/png"
        ".gif" -> "image/gif"
        ".webp" -> "image/webp"
        ".bmp" -> "image/bmp"
        ".pdf" -> "application/pdf"
        ".doc" -> "application/msword"
        ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ".xls" -> "application/vnd.ms-excel"
        ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ".ppt" -> "application/vnd.ms-powerpoint"
        ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ".zip" -> "application/zip"
        ".rar" -> "application/x-rar-compressed"
        ".txt" -> "text/plain"
        else -> "*/*"
    }
}

/** 页面脚本定义完 _jsBridgeReady 后，切到 android 模式并触发它；isReady 保证只触发一次 */
private const val CX_BRIDGE_READY_JS = """
(function () {
  function go() {
    try {
      if (window.jsBridge && window.jsBridge.setDevice && !jsBridge.isReady) {
        jsBridge.setDevice('android');
      }
    } catch (e) {}
  }
  go();
  setTimeout(go, 200);
  setTimeout(go, 800);
  setTimeout(go, 2000);
})();
"""

/**
 * 听课/考试页在某些 WebView 里会算出 html/body 高度为 0（视口信息未就绪时加载），
 * overflow:hidden 会把整块题目裁掉，只剩固定定位的倒计时和水印。
 * 这里把高度撑回视口，只在真的塌成 0 时动手，避免影响正常页面。
 */
private const val CX_LAYOUT_FIX_JS = """
(function () {
  function fix() {
    try {
      var de = document.documentElement, b = document.body;
      if (!b) return;
      // 翻页条写了 top:-59px，会把按钮整个顶到视口外，改成贴底固定（切题会重建，所以每轮都处理）
      var tp = b.querySelectorAll('.turnPage');
      for (var j = 0; j < tp.length; j++) {
        tp[j].style.setProperty('position', 'fixed', 'important');
        tp[j].style.setProperty('top', 'auto', 'important');
        tp[j].style.setProperty('bottom', '0', 'important');
        tp[j].style.setProperty('z-index', '99', 'important');
      }
      if (de.getBoundingClientRect().height < 10) {
        de.style.setProperty('overflow', 'visible', 'important');
        b.style.setProperty('height', 'auto', 'important');
        b.style.setProperty('overflow', 'visible', 'important');
        // 内容容器同样高 0，一并撑开
        var fs = b.querySelectorAll('form');
        for (var i = 0; i < fs.length; i++) {
          fs[i].style.setProperty('height', 'auto', 'important');
        }
      }
    } catch (e) {}
  }
  fix();
  setTimeout(fix, 300);
  setTimeout(fix, 1000);
  setTimeout(fix, 2500);
})();
"""

/** 日志 TAG：拍照 / 附件上传诊断 */
private const val CX_TAG = "CXFile"

/**
 * 图片插进答题框后会按原图像素显示，超出手机宽度导致「只看得见一角」。
 * 用 MutationObserver 只在编辑器内容真的变化时校正一次——不再定时轮询，
 * 轮询会和页面自己的 adjustHeight 抢布局，既卡又容易让整页跳来跳去。
 */
private const val CX_IMAGE_FIX_JS = """
(function () {
  if (window.__cxImgFix) return;
  window.__cxImgFix = true;
  var lastSync = 0;
  function syncHeight() {
    var now = Date.now();
    if (now - lastSync < 800) return;
    lastSync = now;
    var eds = document.querySelectorAll('iframe.edui-iframe, iframe[id^="ueditor_"]');
    for (var k = 0; k < eds.length; k++) {
      try {
        var d = eds[k].contentDocument;
        if (!d || !d.body) continue;
        var ih = Math.max(d.body.scrollHeight || 0, d.documentElement.scrollHeight || 0);
        var cap = Math.round(window.innerHeight * 0.7);
        var target = Math.max(220, Math.min(ih + 24, cap));
        if (Math.abs((eds[k].offsetHeight || 0) - target) > 30) {
          eds[k].style.setProperty('height', target + 'px', 'important');
        }
      } catch (e) {}
    }
  }
  function fitDoc(d) {
    try {
      if (!d || !d.body) return;
      if (!d.getElementById('cx-img-fix')) {
        var s = d.createElement('style');
        s.id = 'cx-img-fix';
        s.innerHTML = 'img{max-width:100%!important;height:auto!important;}';
        (d.head || d.documentElement).appendChild(s);
      }
      var imgs = d.querySelectorAll('img.ans-ued-img');
      var changed = false;
      for (var k = 0; k < imgs.length; k++) {
        var im = imgs[k];
        if (im.getAttribute('data-cx-fit')) continue;
        im.setAttribute('data-cx-fit', '1');
        im.style.setProperty('width', '100%', 'important');
        im.style.setProperty('height', 'auto', 'important');
        changed = true;
      }
      if (changed) syncHeight();
    } catch (e) {}
  }
  function observe() {
    try {
      var ifr = document.querySelectorAll('iframe');
      for (var i = 0; i < ifr.length; i++) {
        try {
          var d = ifr[i].contentDocument;
          if (!d || !d.body || d.body.__cxObs) continue;
          d.body.__cxObs = true;
          fitDoc(d);
          new MutationObserver(function () { fitDoc(d); })
            .observe(d.body, {childList: true, subtree: true});
        } catch (e) {}
      }
    } catch (e) {}
  }
  // 页面自身滚不动时才兜底改样式，避免和页面原来的滚动容器打架
  function fixScroll() {
    try {
      var b = document.body, de = document.documentElement;
      if (!b) return;
      var sh = Math.max(b.scrollHeight, de.scrollHeight);
      if (sh > window.innerHeight + 40 && getComputedStyle(b).overflowY === 'hidden') {
        b.style.setProperty('overflow-y', 'auto', 'important');
        b.style.setProperty('height', 'auto', 'important');
      }
    } catch (e) {}
  }
  fitDoc(document);
  observe();
  syncHeight();
  fixScroll();
  setTimeout(function () { observe(); syncHeight(); fixScroll(); }, 600);
  setTimeout(function () { observe(); syncHeight(); fixScroll(); }, 1800);
  setTimeout(function () { observe(); syncHeight(); fixScroll(); }, 3500);
})();
"""

/**
 * 诊断脚本（定位「拍照 / 附件」到底走哪条路）：
 * 记录被点击的元素、file input 的 click、jsBridge 的调用名、getUserMedia。
 * 只打日志，不改行为；确认结论后可以整段删掉。
 */
private const val CX_DIAG_JS = """
(function () {
  if (window.__cxDiag) return;
  window.__cxDiag = true;
  function tag(el) {
    if (!el) return '?';
    var s = el.tagName || '?';
    if (el.id) s += '#' + el.id;
    if (typeof el.className === 'string' && el.className) s += '.' + el.className.split(/\s+/).slice(0, 3).join('.');
    return s;
  }
  // file input 被程序化点击时，先落一行（这是附件/拍照走 input 的最强证据）
  try {
    var oc = HTMLInputElement.prototype.click;
    HTMLInputElement.prototype.click = function () {
      try {
        console.log('CXD_INPUT_CLICK ' + tag(this) + ' type=' + this.type + ' accept=' + this.accept +
          ' multiple=' + this.multiple + ' capture=' + (this.getAttribute('capture') || ''));
      } catch (e) {}
      return oc.apply(this, arguments);
    };
  } catch (e) {}
  // 所有点击：记下点了谁，方便把「拍照按钮」和它的 DOM 对上
  document.addEventListener('click', function (e) {
    try {
      console.log('CXD_CLICK ' + tag(e.target) + ' text=' + String(e.target.innerText || '').replace(/\s+/g, ' ').slice(0, 30));
    } catch (e) {}
  }, true);
  // jsBridge：记录调用的原生方法名（走桥的话这里一定会有名字）
  function wrapBridge() {
    try {
      if (window.jsBridge && window.jsBridge.postNotification && !window.__cxBridgeWrapped) {
        window.__cxBridgeWrapped = true;
        var p = jsBridge.postNotification.bind(jsBridge);
        jsBridge.postNotification = function (n, j) {
          var s = '';
          try { s = JSON.stringify(j); } catch (e) { s = String(j); }
          try { console.log('CXD_BRIDGE ' + n + ' ' + String(s).slice(0, 300)); } catch (e) {}
          try {
            // 点下拍照的瞬间，页面刚 bind 好回包回调：把它的源码打出来，看期望哪些字段
            if (n === 'CLIENT_CHOOSE_IMAGE') {
              var d = jsBridge.callbackDict && jsBridge.callbackDict['CLIENT_CHOOSE_IMAGE_RESULT'];
              if (d && d.length) console.log('CXDH_CB ' + String(d[0]).slice(0, 2000));
            }
          } catch (e) {}
          return p(n, j);
        };
      }
    } catch (e) {}
  }
  wrapBridge();
  setTimeout(wrapBridge, 500);
  setTimeout(wrapBridge, 1500);
  setTimeout(wrapBridge, 3000);
  // 页内实时相机（getUserMedia）也在诊断范围内
  try {
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      var g = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getUserMedia = function (c) {
        try { console.log('CXD_GETUSERMEDIA ' + JSON.stringify(c)); } catch (e) {}
        return g(c);
      };
    }
  } catch (e) {}
})();
"""

/**
 * 探测脚本：查明「拍照」拿到的图片该怎么交回 H5。
 * 1) openPhoto / 回调函数的源码（看它读哪些字段）；
 * 2) 页面里的隐藏上传地址（#workExamUploadUrl 等）；
 * 3) UEditor 的图片上传 serverUrl。
 */
private const val CX_PROBE_JS = """
(function () {
  if (window.__cxProbe2) return;
  window.__cxProbe2 = true;
  function log(s) { try { console.log(s); } catch (e) {} }
  setTimeout(function () {
    try { log('CXDH_HREF ' + location.href); } catch (e) {}
    try { log('CXDH_OPENPHOTO ' + (typeof openPhoto === 'function' ? String(openPhoto).slice(0, 1500) : 'none')); } catch (e) {}
    try {
      var hs = document.querySelectorAll('input[type=hidden]'), out = [];
      for (var i = 0; i < hs.length; i++) {
        if (hs[i].id) out.push(hs[i].id + '=' + String(hs[i].value).slice(0, 150));
      }
      log('CXDH_HIDDEN ' + out.join(' | '));
    } catch (e) {}
    try {
      var pb = document.querySelector('.pic-btn');
      log('CXDH_PICBTN ' + (pb ? pb.outerHTML.slice(0, 300) : 'none'));
    } catch (e) {}
    try {
      var ss = document.querySelectorAll('script'), found = [];
      for (var j = 0; j < ss.length; j++) {
        if (!ss[j].src && String(ss[j].textContent || '').indexOf('openPhoto') >= 0) found.push(ss[j].textContent);
      }
      for (var k = 0; k < found.length; k++) {
        var t = String(found[k]);
        var idx = t.indexOf('function openPhoto');
        if (idx < 0) idx = t.indexOf('openPhoto');
        log('CXDH_OPENSRC ' + t.slice(Math.max(0, idx - 100), idx + 1800).replace(/\s+/g, ' '));
      }
    } catch (e) {}
    try {
      if (window.UE && UE.instants) {
        var ks = Object.keys(UE.instants);
        if (ks.length) log('CXDH_UE_SERVERURL ' + UE.instants[ks[0]].getOpt('serverUrl'));
      }
    } catch (e) {}
    try { log('CXDH_UPLOADFILE ' + (typeof uploadFile === 'function' ? String(uploadFile).slice(0, 2500) : 'none')); } catch (e) {}
    try { log('CXDH_IMGHANDLER ' + (typeof imgUploadHandler === 'function' ? String(imgUploadHandler).slice(0, 1500) : 'none')); } catch (e) {}
  }, 1500);
})();
"""

/** 一条作业 / 考试 */
private data class CxTask(
    val title: String,
    val course: String,
    val status: String,
    val remain: String,
    val url: String,
    val type: String
)

/**
 * 学习通任务页：
 * - 未登录时展示登录 WebView；
 * - 已登录后 WebView 缩成 1dp 后台加载聚合页抓数据；
 * - 列表用原生卡片渲染，点条目再全屏打开 WebView。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChaoxingScreen(onBack: () -> Unit) {
    val main = remember { Handler(Looper.getMainLooper()) }
    fun ui(block: () -> Unit) = main.post(block)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var needLogin by remember { mutableStateOf(false) }
    var showWeb by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var works by remember { mutableStateOf<List<CxTask>>(emptyList()) }
    var exams by remember { mutableStateOf<List<CxTask>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    // 学习通提交成功后发 CLIENT_EXIT_LEVEL，用来收起作答页
    var exitWork by remember { mutableStateOf(false) }

    // ---------------- 附件 / 拍照上传 ----------------
    val ctx = LocalContext.current
    val chooser = remember { CxChooser() }
    val scope = rememberCoroutineScope()
    var pickDialog by remember { mutableStateOf(false) }
    // true = 页面经原生桥要图（CLIENT_CHOOSE_IMAGE）；false = WebView 的 file input
    var bridgeMode by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    /**
     * 学习通协议：点拍照 → 原生桥 CLIENT_CHOOSE_IMAGE →
     * 原生选图后 jsBridge.trigger('CLIENT_CHOOSE_IMAGE_RESULT', {files:[{data,name}]}) →
     * 页面自己把 base64 传到 /edit/uploadBase64 并插进 UEditor。
     * 所以这里只负责把图片压好、转 base64 交回去，上传和插图都交给页面。
     */
    fun deliverBridgeImages(uris: List<Uri>) {
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { index, uri ->
                    val base64 = imageBase64(ctx, uri) ?: return@mapIndexedNotNull null
                    JSONObject()
                        .put("data", base64)
                        .put("name", "cx_${System.currentTimeMillis()}_$index.jpg")
                }
            }
            if (files.isEmpty()) {
                Log.e(CX_TAG, "bridge 图片读取失败")
                return@launch
            }
            val payload = JSONObject().put("files", JSONArray(files)).toString()
            Log.i(CX_TAG, "trigger CLIENT_CHOOSE_IMAGE_RESULT files=${files.size} len=${payload.length}")
            webView?.evaluateJavascript(
                "window.jsBridge && jsBridge.trigger('CLIENT_CHOOSE_IMAGE_RESULT', $payload);",
                null
            )
            webView?.evaluateJavascript(CX_IMAGE_FIX_JS, null)
        }
    }

    fun handlePicked(uris: List<Uri>?) {
        if (bridgeMode) {
            bridgeMode = false
            if (uris.isNullOrEmpty()) Log.i(CX_TAG, "bridge 选图取消") else deliverBridgeImages(uris)
        } else {
            chooser.finish(uris)
        }
    }

    fun cameraProviderUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }.getOrNull()

    // 拍照：系统相机写进 FileProvider 暴露的 cache 文件（未声明 CAMERA 权限，不需要额外授权）
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = cameraFile
        val uri = cameraUri
        cameraFile = null
        cameraUri = null
        if (ok && file != null && uri != null) {
            Log.i(CX_TAG, "camera ok uri=$uri")
            handlePicked(listOf(uri))
        } else {
            Log.i(CX_TAG, "camera cancelled")
            file?.delete()
            handlePicked(null)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        Log.i(CX_TAG, "pickImage uri=$uri")
        handlePicked(if (uri != null) listOf(uri) else null)
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        Log.i(CX_TAG, "pickImages count=${uris.size}")
        handlePicked(uris)
    }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        Log.i(CX_TAG, "openDoc uri=$uri")
        handlePicked(if (uri != null) listOf(uri) else null)
    }

    val openDocs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        Log.i(CX_TAG, "openDocs count=${uris.size}")
        handlePicked(uris)
    }

    fun launchPick() {
        Log.i(CX_TAG, "launchPick mime=${chooser.mime} multiple=${chooser.multiple}")
        if (chooser.mime.startsWith("image/")) {
            if (chooser.multiple) pickImages.launch(chooser.mime) else pickImage.launch(chooser.mime)
        } else if (chooser.multiple) {
            openDocs.launch(arrayOf(chooser.mime))
        } else {
            openDoc.launch(arrayOf(chooser.mime))
        }
    }

    fun launchCamera() {
        val dir = File(ctx.cacheDir, "cx_camera").apply { mkdirs() }
        val file = File(dir, "cx_${System.currentTimeMillis()}.jpg")
        val uri = cameraProviderUri(file)
        if (uri == null) {
            Log.e(CX_TAG, "camera uri 构造失败，FileProvider 未生效")
            handlePicked(null)
            return
        }
        cameraFile = file
        cameraUri = uri
        Log.i(CX_TAG, "launchCamera uri=$uri")
        cameraLauncher.launch(uri)
    }

    /** WebView 交来文件选择请求：记下回调并交给系统选择器（图片单选时先问拍照/相册/文件） */
    fun onChooserRequest(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        chooser.replace(callback)
        bridgeMode = false
        chooser.multiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        chooser.mime = pickMime(params?.acceptTypes)
        Log.i(
            CX_TAG,
            "onShowFileChooser mime=${chooser.mime} multiple=${chooser.multiple} " +
                "accept=${params?.acceptTypes?.joinToString()} mode=${params?.mode}"
        )
        if (chooser.mime.startsWith("image/") && !chooser.multiple) {
            pickDialog = true
        } else {
            main.post { launchPick() }
        }
        return true
    }

    // JS Bridge：抓取结果回传后解析成卡片数据
    val bridge = remember {
        object {
            @JavascriptInterface
            fun push(json: String) {
                ui {
                    loading = false
                    try {
                        val o = JSONObject(json)
                        if (!o.optBoolean("ok")) {
                            needLogin = true
                        } else {
                            works = parseTasks(o.optJSONArray("works"), "work")
                            exams = parseTasks(o.optJSONArray("exams"), "exam")
                            needLogin = false
                            status = if (works.isEmpty() && exams.isEmpty()) {
                                "没有读到任务，先确认学习通已登录好～"
                            } else ""
                        }
                    } catch (e: Exception) {
                        status = "解析失败：${e.message}"
                    }
                }
            }
        }
    }

    // 学习通 H5 的原生桥：跳转、拍照/选图、提交提示、提交后退出
    val appBridge = remember {
        CxAppBridge(
            main,
            { webView },
            onChooseImage = {
                bridgeMode = true
                pickDialog = true
            },
            onDisplayMessage = { message ->
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            },
            onExitLevel = {
                // 提交成功：收起作答页，回到任务列表并重新拉一次状态（真正的动作在下面按顺序执行）
                exitWork = true
            }
        )
    }

    /** 从 WebView CookieStore 里读 _uid（本应用自己的 Cookie，不读学习通 App 的） */
    fun readUid(): String? {
        for (host in listOf("https://mooc1-api.chaoxing.com", "https://i.chaoxing.com", "https://chaoxing.com")) {
            val c = CookieManager.getInstance().getCookie(host) ?: continue
            Regex("(?:^|;\\s*)_uid=(\\d+)").find(c)?.let { return it.groupValues[1] }
        }
        return null
    }

    /** 加载聚合页；拿不到 uid 就去登录 */
    fun loadTasks(wv: WebView? = webView) {
        val uid = readUid()
        if (uid == null) {
            needLogin = true
            loading = false
            wv?.loadUrl(CX_LOGIN_URL)
        } else {
            needLogin = false
            loading = true
            status = "正在读取学习通任务…"
            wv?.loadUrl("$CX_WORK_URL?userId=$uid")
        }
    }

    LaunchedEffect(Unit) {
        if (readUid() == null) needLogin = true
    }

    // 学习通提交成功（CLIENT_EXIT_LEVEL）：退出作答页，回到列表并刷新状态
    LaunchedEffect(exitWork) {
        if (exitWork) {
            exitWork = false
            showWeb = false
            loading = true
            delay(250)
            loadTasks()
        }
    }

    // 抓取超时兜底：WebView 里 20 门课并行请求，正常几秒完成
    LaunchedEffect(loading, needLogin) {
        if (loading && !needLogin) {
            delay(30_000)
            if (loading) {
                loading = false
                status = "读取超时了，点右上角刷新再试一次～"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 页面销毁时把没交付的文件回调收掉，避免 WebView 侧一直挂着
            chooser.finish(null)
            webView?.let { it.stopLoading(); it.destroy() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习通任务") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showWeb) showWeb = false else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadTasks() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (needLogin) {
                    Text(
                        "请先登录学习通（账号密码只在下面的页面里提交，本应用不保存）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }) {
                            Text("作业 ${works.size}", modifier = Modifier.padding(vertical = 12.dp))
                        }
                        Tab(selected = tab == 1, onClick = { tab = 1 }) {
                            Text("考试 ${exams.size}", modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }

                if (loading && !needLogin) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Text(
                            "正在读取…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                if (status.isNotEmpty() && !needLogin) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (!needLogin) {
                    // 作业 / 考试同一套排序：未完成在前，剩余时间短的在前，已完成沉底
                    val list = sortTasks(if (tab == 0) works else exams)
                    if (list.isEmpty() && !loading) {
                        Text(
                            if (tab == 0) "暂无作业～" else "暂无考试～",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list) { item ->
                            TaskCard(item) {
                                showWeb = true
                                // 先让 WebView 拿到全屏尺寸再加载：视口为 0 时页面会把 html/body 算成 0 高，
                                // 题目区和底部按钮都会被裁到视口外
                                webView?.postDelayed({ webView?.loadUrl(item.url) }, 150)
                            }
                        }
                    }
                }

                if (needLogin) {
                    Button(
                        onClick = { loadTasks() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("登录完成后点这里继续")
                    }
                }
            }

            // WebView：登录时全屏；抓取时缩成 1dp 留在后台执行 JS；点卡片时全屏
            AndroidView(
                modifier = if (needLogin || showWeb) Modifier.fillMaxSize() else Modifier.size(1.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // 去掉 WebView 标记并补上学习通 UA 尾巴，尽量贴近 App 内 H5 的渲染路径
                        settings.userAgentString = settings.userAgentString.replace("; wv", "") + "; chaoxing"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(bridge, "cx_obj")
                        addJavascriptInterface(appBridge, "androidjsbridge")
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                view: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: WebChromeClient.FileChooserParams?
                            ): Boolean = onChooserRequest(filePathCallback, fileChooserParams)

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                // 页内实时相机（getUserMedia）会走到这里；未声明 CAMERA 权限时先如实拒绝并留日志
                                Log.d(
                                    CX_TAG,
                                    "onPermissionRequest ${request?.resources?.joinToString()} origin=${request?.origin}"
                                )
                                runCatching { request?.deny() }
                            }

                            override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                                // 页面自己的 console 很吵（MathJax / JQMigrate 刷屏），只留我们自己的诊断标记
                                val msg = m?.message().orEmpty()
                                if (msg.startsWith("CXD_") || msg.startsWith("CXDH_")) {
                                    Log.i(CX_TAG, "web: $msg")
                                }
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                // 让学习通 H5 认为自己在官方 App 里，跳转/初始化才会继续
                                view.evaluateJavascript(CX_BRIDGE_READY_JS, null)
                                view.evaluateJavascript(CX_LAYOUT_FIX_JS, null)
                                // 已提交的作业在「查看详情」里展示答案，图片同样要限宽
                                view.evaluateJavascript(CX_IMAGE_FIX_JS, null)
                                view.evaluateJavascript(CX_DIAG_JS, null)
                                view.evaluateJavascript(CX_PROBE_JS, null)
                                val u = url.lowercase()
                                when {
                                    // 登录页：显示出来让用户登录
                                    u.contains("passport2") || u.contains("mlogin") -> ui {
                                        needLogin = true
                                        loading = false
                                    }
                                    // 聚合页：注入抓取脚本
                                    u.contains("/work/stu-work") -> {
                                        ui { needLogin = false }
                                        view.evaluateJavascript(CX_FETCH_JS, null)
                                    }
                                    // 登录成功后的落地页：拿着新 Cookie 继续拉任务
                                    u.contains("i.chaoxing.com") || u.contains("passport") -> ui {
                                        if (!showWeb) {
                                            val uid = readUid()
                                            if (uid != null) {
                                                needLogin = false
                                                loading = true
                                                view.loadUrl("$CX_WORK_URL?userId=$uid")
                                            } else {
                                                needLogin = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        webView = this
                        if (!needLogin) loadTasks(this) else loadUrl(CX_LOGIN_URL)
                    }
                }
            )

            // 图片单选时自己给入口：系统选择器在 HyperOS 上不一定带相机
            if (pickDialog) {
                AlertDialog(
                    onDismissRequest = {
                        pickDialog = false
                        if (bridgeMode) bridgeMode = false else chooser.finish(null)
                    },
                    title = { Text(if (bridgeMode) "添加图片" else "上传图片") },
                    text = {
                        Column {
                            PickerRow("拍照") {
                                pickDialog = false
                                launchCamera()
                            }
                            PickerRow("从相册选择") {
                                pickDialog = false
                                if (bridgeMode) {
                                    main.post { pickImages.launch("image/*") }
                                } else {
                                    main.post { pickImage.launch(chooser.mime) }
                                }
                            }
                            if (!bridgeMode) {
                                PickerRow("选择文件") {
                                    pickDialog = false
                                    main.post { openDoc.launch(arrayOf(chooser.mime)) }
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }
}

/** 上传入口的一行 */
@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

/**
 * 把图片压到 maxSide 以内再转 JPEG，返回不带 data: 前缀的 base64。
 * 学习通页面的 uploadFile() 就是这么拼的：'data:image/png;base64,' + file.data。
 * 原图动辄 8MB，不压缩的话 base64 十几 MB，evaluateJavascript 会卡死。
 */
private fun imageBase64(ctx: Context, uri: Uri, maxSide: Int = 1280, quality: Int = 80): String? =
    runCatching {
        val bitmap = decodeScaled(ctx, uri, maxSide) ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

/** 解码并按比例缩小；API 28+ 用 ImageDecoder（会自动处理 EXIF 旋转） */
private fun decodeScaled(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = ctx.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = maxSide.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
    return if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        ).also { bitmap.recycle() }
    } else {
        bitmap
    }
}

/** JSON 数组 → 卡片数据 */
private fun parseTasks(arr: org.json.JSONArray?, type: String): List<CxTask> {
    if (arr == null) return emptyList()
    val out = ArrayList<CxTask>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            CxTask(
                title = o.optString("title").ifBlank { "（未命名）" },
                course = o.optString("course").trim('《', '》'),
                status = o.optString("status"),
                remain = o.optString("remain"),
                url = o.optString("url"),
                type = type
            )
        )
    }
    return out
}

/** 是否已完成 / 已结束：这类任务沉到列表底部 */
private fun CxTask.isFinished(): Boolean =
    status.contains("已完成") || status.contains("已批阅") || status.contains("待批阅") ||
        status.contains("已交") || status.contains("已提交") || status.contains("已结束")

/** 「剩余1天2小时30分钟」→ 总分钟数；没有剩余时间信息时排到未完成区末尾 */
private fun remainMinutes(remain: String): Long {
    if (remain.isEmpty()) return Long.MAX_VALUE
    fun n(pattern: String) = Regex(pattern).find(remain)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val total = n("(\\d+)天") * 1440 + n("(\\d+)小时") * 60 + n("(\\d+)分")
    return when {
        total > 0 -> total
        remain.any { it.isDigit() } -> 0L // 「剩余0分钟」这类马上到期
        else -> Long.MAX_VALUE
    }
}

/** 未完成在前、剩余时间短的在前；已完成 / 已结束沉底 */
private fun sortTasks(list: List<CxTask>): List<CxTask> =
    list.sortedWith(
        compareBy<CxTask> { if (it.isFinished()) 1 else 0 }.thenBy { remainMinutes(it.remain) }
    )

/** 单条任务卡片 */
@Composable
private fun TaskCard(item: CxTask, onClick: () -> Unit) {
    val urgent = remember(item.remain) {
        val h = Regex("剩余(\\d+)小时").find(item.remain)?.groupValues?.get(1)?.toIntOrNull()
        h != null && h <= 24
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.status.isNotEmpty()) {
                    Text(
                        item.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.status) {
                            "已完成", "已批阅" -> MaterialTheme.colorScheme.onSurfaceVariant
                            "待批阅" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.course.isNotEmpty()) {
                    Text(
                        "《${item.course}》",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (item.remain.isNotEmpty()) {
                    Text(
                        item.remain,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                        color = if (urgent) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
