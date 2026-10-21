package com.anon.schedule.ui.mine

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anon.schedule.ui.common.GlassCard
import kotlinx.coroutines.delay
import org.json.JSONObject

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
        var p = li.querySelector('p'), s = li.querySelector('span');
        out.push({title: txt(p), status: txt(s), course: c.name, remain: '', url: d, type: 'exam'});
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
    private val getWeb: () -> WebView?
) {
    @JavascriptInterface
    fun postNotification(name: String, json: String) {
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
      if (de.getBoundingClientRect().height < 10) {
        de.style.setProperty('overflow', 'visible', 'important');
        b.style.setProperty('height', 'auto', 'important');
        b.style.setProperty('overflow', 'visible', 'important');
        // 答题页底部的「上一题 / 下一步（交卷）」挂在 form 里，form 高 0 时会被定位到视口外
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

    // 学习通 H5 的原生桥：接管 CLIENT_OPEN_URL（开始答题 / 开始考试等跳转）
    val appBridge = remember { CxAppBridge(main) { webView } }

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
        onDispose { webView?.let { it.stopLoading(); it.destroy() } }
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
                    val list = if (tab == 0) works else exams
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                // 让学习通 H5 认为自己在官方 App 里，跳转/初始化才会继续
                                view.evaluateJavascript(CX_BRIDGE_READY_JS, null)
                                view.evaluateJavascript(CX_LAYOUT_FIX_JS, null)
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
        }
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
