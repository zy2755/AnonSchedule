package com.anon.schedule.ui.mine

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.theme.isDarkMode
import kotlinx.coroutines.delay
import org.json.JSONTokener

/** 缓存页顶上去之后，留一点时间让它画出来，再去拉真正的内容 */
private const val CACHE_HOLD_MS = 450L

/**
 * 正方教务的「学业情况查询」页面。
 *
 * 这块内容正方只给整页 HTML，没有可用的 JSON 接口，所以直接内嵌 WebView。
 * 登录态与「成绩查询」共用同一个 CookieManager，WebView 设置也统一走 [configureZfWebView]。
 *
 * 正方会话闲置一段时间就会失效，而这一页每次进来都要真的请求教务，所以比成绩页更容易
 * 撞上登录页。这里做了两件事：
 * 1. 把上一版整页 HTML 缓存下来，进来先把缓存画上去，离线也能看；
 * 2. 会话过期时用本机保存的账号密码自动登录，登录完再自动回到学业页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showCreds by remember { mutableStateOf(false) }

    // 本次进入是否已经自动登录过：密码错了不要反复提交，免得把账号试锁
    var autoLoginTried by remember { mutableStateOf(false) }
    // 自动登录提交后页面会跳到登录成功页/首页，这时候要把 WebView 导回学业页
    var awaitingAcademic by remember { mutableStateOf(false) }

    // 跟 App 主题走：成绩页是本地数据会跟着深浅切换，这页也不该写死成浅色
    val st by Repo.state.collectAsState()
    val dark = isDarkMode(st.settings.themeMode)
    val darkNow = rememberUpdatedState(dark)

    val cacheHtml = remember { ZfCache.read(ctx) }

    // 主题切换后重刷一遍：页面已经加载完时不必重新拉数据，直接换色即可
    LaunchedEffect(dark, webView) {
        val view = webView ?: return@LaunchedEffect
        view.setBackgroundColor(if (dark) ZF_BG_DARK else ZF_BG_LIGHT)
        applyZfRestyle(view, dark)
    }

    // 缓存只是先垫着看：过一会儿还是要去教务拉最新的一版
    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        if (cacheHtml == null) return@LaunchedEffect
        delay(CACHE_HOLD_MS)
        view.loadUrl(ZF_ACADEMIC_URL)
    }

    // 页面里点进二级页时，返回键先退网页；退到起点再交给整体导航
    BackHandler(enabled = canGoBack) { webView?.goBack() }

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
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        // 与「成绩查询」共用同一套设置与 UA，登录态才是通的
                        configureZfWebView(this)
                        setBackgroundColor(if (darkNow.value) ZF_BG_DARK else ZF_BG_LIGHT)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                canGoBack = view?.canGoBack() == true
                                if (view == null || url == null) return
                                // 正方原生排版在手机上太挤，加载完就套上自己的排版
                                applyZfRestyle(view, darkNow.value)
                                when {
                                    // 会话过期，被丢回登录页：有存账号就自动登录一次
                                    isZfLoginUrl(url) -> {
                                        val creds = ZfCreds.load(ctx)
                                        if (creds != null && !autoLoginTried) {
                                            autoLoginTried = true
                                            awaitingAcademic = true
                                            view.evaluateJavascript(
                                                zfAutoLoginJs(creds.first, creds.second),
                                                null
                                            )
                                        }
                                    }

                                    // 学业页本身：内容已经渲染出来，把这版 HTML 存下来给下次垫底
                                    url.contains("xsxy") -> {
                                        awaitingAcademic = false
                                        view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                            val html = runCatching {
                                                JSONTokener(raw).nextValue() as? String
                                            }.getOrNull()
                                            if (!html.isNullOrEmpty()) ZfCache.write(ctx, html)
                                        }
                                    }

                                    // 登录成功后的跳转页（loginSuccess / 首页）：回到学业页
                                    awaitingAcademic -> {
                                        awaitingAcademic = false
                                        view.loadUrl(ZF_ACADEMIC_URL)
                                    }
                                }
                            }
                        }
                        if (cacheHtml != null) {
                            // 先把上次的整页 HTML 画上去，不用先看一屏登录页
                            loadDataWithBaseURL(
                                ZF_ACADEMIC_URL, cacheHtml, "text/html", "UTF-8", ZF_ACADEMIC_URL
                            )
                        } else {
                            loadUrl(ZF_ACADEMIC_URL)
                        }
                        webView = this
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                }
            )
        }
    }

    if (showCreds) {
        val saved = remember(showCreds) { ZfCreds.load(ctx) }
        var user by remember(showCreds) { mutableStateOf(saved?.first.orEmpty()) }
        var pwd by remember(showCreds) { mutableStateOf(saved?.second.orEmpty()) }

        AlertDialog(
            onDismissRequest = { showCreds = false },
            title = { Text("教务自动登录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "正方会话闲置一段时间会自动失效。保存学号密码后，进来发现是登录页就会自动登录。",
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
                }
            },
            confirmButton = {
                TextButton(
                    enabled = user.isNotBlank() && pwd.isNotBlank(),
                    onClick = {
                        ZfCreds.save(ctx, user.trim(), pwd)
                        autoLoginTried = false
                        awaitingAcademic = false
                        showCreds = false
                        // 立刻重走一遍：还有效就直接是数据页，过期了就走自动登录
                        webView?.loadUrl(ZF_ACADEMIC_URL)
                    }
                ) { Text("保存并登录") }
            },
            dismissButton = {
                Row {
                    if (saved != null) {
                        TextButton(
                            onClick = {
                                ZfCreds.clear(ctx)
                                ZfCache.clear(ctx)
                                autoLoginTried = false
                                awaitingAcademic = false
                                showCreds = false
                            }
                        ) { Text("清除", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { showCreds = false }) { Text("取消") }
                }
            }
        )
    }
}
