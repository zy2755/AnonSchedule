package com.anon.schedule.ui.mine

import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.theme.isDarkMode
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 正方教务的「学业情况查询」页面。
 *
 * 这块内容正方只给整页 HTML，没有可用的 JSON 接口，所以直接内嵌 WebView。
 * 登录态与「成绩查询」共用同一个 CookieManager，WebView 设置也统一走 [configureZfWebView]。
 *
 * 正方会话闲置一段时间就会失效，而这一页每次进来都要真的请求教务，所以比成绩页更容易
 * 撞上登录页。做完自动登录后还有个体验问题：登录页 → 「会话已过期」弹窗 → 首页 →
 * 原始样式的学业页 → 重排后的学业页，中间要闪好几次。
 *
 * 所以这里分两种走法：
 * - 存过账号：主 WebView 只负责显示（缓存 → 最新页面），登录和抓取全交给一个 1dp 的
 *   隐藏 WebView 去跑，中间的登录页、弹窗、跳转页都不露给用户；
 * - 没存账号：还是走原来的单页面加载，不然用户连登录框都看不到。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val saved = remember { ZfCreds.load(ctx) }
    // 有保存账号才走静默同步：省掉中间那几跳
    val silent = saved != null

    // 首帧优先用「渲染后快照」：课程树是脚本画出来的，断网时原始 HTML 只有头部统计
    val snapshot = remember { if (silent) ZfCache.readView(ctx) else null }
    val cachedRaw = remember { if (silent) ZfCache.read(ctx) else null }

    var visibleHtml by remember { mutableStateOf(snapshot ?: cachedRaw) }
    // 标记当前这版是快照还是原始 HTML：快照要先剥脚本，否则原站脚本会再初始化一遍
    var visibleIsView by remember { mutableStateOf(snapshot != null) }
    // 快照要等页面画出来才有东西看，先挂着进度条，别让用户对着一片空白
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showCreds by remember { mutableStateOf(false) }

    var mainView by remember { mutableStateOf<WebView?>(null) }
    var syncView by remember { mutableStateOf<WebView?>(null) }
    // 主 WebView 当前已经画出来的那版 HTML，避免同一个内容重复加载
    var shownHtml by remember { mutableStateOf<String?>(null) }

    // 本次进入是否已经自动登录过：密码错了不要反复提交，免得把账号试锁
    var autoLoginTried by remember { mutableStateOf(false) }
    // 登录提交后页面会跳到登录成功页/首页，这时候要把 WebView 导回学业页
    var awaitingAcademic by remember { mutableStateOf(false) }
    // 用户手动点了刷新：不管内容变没变都要换一版，否则「统计时间」不会动，看着像没刷新
    var forceReload by remember { mutableStateOf(false) }

    val st by Repo.state.collectAsState()
    val dark = isDarkMode(st.settings.themeMode)
    val darkNow = rememberUpdatedState(dark)
    val scope = rememberCoroutineScope()

    fun showHtml(html: String, isView: Boolean) {
        val view = mainView ?: return
        val key = (if (isView) "v:" else "r:") + html
        if (key == shownHtml) return
        shownHtml = key
        val ready = if (isView) zfSnapshotForView(html) else html
        view.loadDataWithBaseURL(
            ZF_ACADEMIC_URL, zfWithStyle(ready, darkNow.value, isView), "text/html", "UTF-8", ZF_ACADEMIC_URL
        )
    }

    /**
     * 非静默模式下主 WebView 本来就是真实页面，这里只顺手存一份服务器原始 HTML，
     * 以后保存了账号就能直接用上。
     */
    fun cacheRaw() {
        val cookie = CookieManager.getInstance().getCookie(ZF_ACADEMIC_URL).orEmpty()
        scope.launch {
            val html = zfFetchHtml(ctx, ZF_ACADEMIC_URL, cookie)
            // 拿回来是登录页，说明会话还是过期的，别把登录页当内容存下来
            if (html.isNullOrEmpty() || html.contains("id=\"yhm\"")) return@launch
            ZfCache.write(ctx, html)
        }
    }

    /**
     * 隐藏页把页面跑完以后，取一份渲染结果。
     *
     * - 树是脚本一段段画出来的，连着两次节点数一样才算画完，否则快照会缺课程；
     * - 存一份 DOM 快照，断网/首帧靠它显示完整课程树；
     * - 跟主页面现在显示的内容比一比签名，一样就不再重载，免得整页白闪。
     */
    fun readRendered(view: WebView) {
        view.evaluateJavascript(ZF_RENDERED_JS) { raw ->
            val obj = runCatching { JSONObject(zfDecodeJsString(raw).orEmpty()) }.getOrNull()
            val html = obj?.optString("html").orEmpty()
            if (html.isEmpty()) {
                loading = false
                return@evaluateJavascript
            }
            ZfCache.writeView(ctx, html)
            val sig = obj?.optString("sig").orEmpty()
            val main = mainView
            if (main == null || shownHtml == null) {
                // 还没画出东西，直接用这份快照顶上
                visibleHtml = html
                visibleIsView = true
                loading = false
                showHtml(html, true)
                return@evaluateJavascript
            }
            main.evaluateJavascript(ZF_SIG_JS) { shown ->
                val shownSig = zfDecodeJsString(shown).orEmpty()
                val force = forceReload
                forceReload = false
                if (shownSig == sig && !force) {
                    // 内容没变：什么都不用做，连刷新动画都不用露
                    loading = false
                } else {
                    // 换一版刚抓到的快照：和首帧同一套排版，不会突然变成原站样式
                    visibleHtml = html
                    visibleIsView = true
                    loading = false
                    showHtml(html, true)
                }
            }
        }
    }

    fun waitTree(view: WebView, attempt: Int = 0, last: Int = -1) {
        view.evaluateJavascript(ZF_TREE_COUNT_JS) { raw ->
            val n = raw?.trim()?.toIntOrNull() ?: 0
            if ((n == 0 || n != last) && attempt < 12) {
                view.postDelayed({ waitTree(view, attempt + 1, n) }, 300)
            } else {
                readRendered(view)
            }
        }
    }

    // 主题切换后重刷一遍：页面已经加载完时不必重新拉数据，直接换色即可
    LaunchedEffect(dark, mainView) {
        val view = mainView ?: return@LaunchedEffect
        view.setBackgroundColor(if (dark) ZF_BG_DARK else ZF_BG_LIGHT)
        applyZfRestyle(view, dark)
    }

    // 后台抓到新内容后替换主页面（内容没变就不折腾，免得闪一下）
    LaunchedEffect(visibleHtml, visibleIsView, mainView) {
        visibleHtml?.let { showHtml(it, visibleIsView) }
    }

    // 页面里点进二级页时，返回键先退网页；退到起点再交给整体导航
    BackHandler(enabled = canGoBack) { mainView?.goBack() }

    // 离开页面时把 Cookie 落盘，下次进来还是登录状态
    DisposableEffect(Unit) {
        onDispose { flushZfCookies() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学业情况查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreds = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "自动登录设置")
                    }
                    IconButton(
                        onClick = {
                            loading = true
                            if (silent) {
                                forceReload = true
                                syncView?.loadUrl(ZF_ACADEMIC_URL)
                            } else {
                                mainView?.reload()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 进度条放在固定高度的容器里：直接 if 增减兄弟节点会让 AndroidView 换位置、
            // WebView 被重建，整页会白闪一下
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Box(Modifier.fillMaxSize()) {
                // 主页面：只负责显示。有账号时它永远只画缓存和抓回来的成品页
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            configureZfWebView(this)
                            setBackgroundColor(if (darkNow.value) ZF_BG_DARK else ZF_BG_LIGHT)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (view == null) return
                                    applyZfRestyle(view, darkNow.value)
                                    loading = false
                                    // 静默模式下这一页只是展示容器，不参与导航
                                    if (silent) return
                                    canGoBack = view.canGoBack()
                                    if (url == null) return
                                    when {
                                        // 会话过期被丢回登录页：登录完记得回到学业页
                                        isZfLoginUrl(url) -> awaitingAcademic = true

                                        url.contains("xsxy") -> {
                                            awaitingAcademic = false
                                            // 主页面已经是真实页面，只顺手更新缓存
                                            cacheRaw()
                                        }

                                        awaitingAcademic -> {
                                            awaitingAcademic = false
                                            view.loadUrl(ZF_ACADEMIC_URL)
                                        }
                                    }
                                }
                            }
                            mainView = this
                            if (silent) {
                                // 有缓存就先顶上，没有就等后台抓回来
                                visibleHtml?.let {
                                    shownHtml = null
                                    showHtml(it, visibleIsView)
                                }
                            } else {
                                loadUrl(ZF_ACADEMIC_URL)
                            }
                        }
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.destroy()
                    }
                )

                // 隐藏的同步页：1dp、不显示，负责静默登录和抓取最新页面
                if (silent) {
                    AndroidView(
                        modifier = Modifier.size(1.dp),
                        factory = { c ->
                            WebView(c).apply {
                                configureZfWebView(this)
                                setBackgroundColor(0)
                                webChromeClient = object : WebChromeClient() {
                                    // 会话过期时正方会弹「请重新登录」的 alert，隐藏页里直接吞掉
                                    override fun onJsAlert(
                                        view: WebView?,
                                        url: String?,
                                        message: String?,
                                        result: JsResult?
                                    ): Boolean {
                                        result?.confirm()
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val u = request.url.toString()
                                        // 登录成功后的中间页没有内容，直接跳回学业页，少两跳
                                        if (u.contains("auth_loginSuccess") ||
                                            u.contains("index_initMenu")
                                        ) {
                                            view.loadUrl(ZF_ACADEMIC_URL)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (view == null || url == null) return
                                        when {
                                            // 会话过期：用保存的账号自动登录一次
                                            isZfLoginUrl(url) -> {
                                                awaitingAcademic = true
                                                val creds = saved
                                                if (creds != null && !autoLoginTried) {
                                                    autoLoginTried = true
                                                    view.evaluateJavascript(
                                                        zfAutoLoginJs(creds.first, creds.second),
                                                        null
                                                    )
                                                }
                                            }

                                            // 已经进到学业页，说明登录态有效：等树画完再取渲染结果
                                            url.contains("xsxy") -> {
                                                awaitingAcademic = false
                                                waitTree(view)
                                            }

                                            // 登录成功后的跳转页：回到学业页
                                            awaitingAcademic -> {
                                                awaitingAcademic = false
                                                view.loadUrl(ZF_ACADEMIC_URL)
                                            }

                                            // 断网之类没走到学业页的，别让进度条一直挂着
                                            else -> loading = false
                                        }
                                    }
                                }
                                // 有缓存就只显示缓存，不自动联网；要看最新的自己点右上角刷新
                                if (visibleHtml == null) loadUrl(ZF_ACADEMIC_URL)
                                syncView = this
                            }
                        },
                        onRelease = { view ->
                            view.stopLoading()
                            view.destroy()
                        }
                    )
                }
            }
        }
    }

    if (showCreds) {
        val stored = remember(showCreds) { ZfCreds.load(ctx) }
        var user by remember(showCreds) { mutableStateOf(stored?.first.orEmpty()) }
        var pwd by remember(showCreds) { mutableStateOf(stored?.second.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showCreds = false },
            title = { Text("教务自动登录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "正方会话闲置一段时间会自动失效。保存学号密码后，进来会用后台页面自动登录，中间不会再闪过登录页。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "账号密码用系统 Keystore 加密后只存在本机，不上传任何第三方；随时可以在这里清除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("学号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "保存后自动重新进入本页；清空后请点「清除」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = user.isNotBlank() && pwd.isNotBlank(),
                    onClick = {
                        ZfCreds.save(ctx, user.trim(), pwd)
                        showCreds = false
                        // 存完直接退出去重进，这次就是静默模式了
                        onBack()
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (stored != null) {
                        TextButton(
                            onClick = {
                                ZfCreds.clear(ctx)
                                ZfCache.clear(ctx)
                                showCreds = false
                                onBack()
                            }
                        ) { Text("清除", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { showCreds = false }) { Text("取消") }
                }
            }
        )
    }
}
