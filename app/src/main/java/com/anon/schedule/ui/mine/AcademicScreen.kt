package com.anon.schedule.ui.mine

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 正方教务的「学业情况查询」页面。
 *
 * 这块内容正方只给整页 HTML，没有可用的 JSON 接口，所以直接内嵌 WebView。
 * 登录态与「成绩查询」共用同一个 CookieManager，WebView 设置也统一走 [configureZfWebView]，
 * 所以在任意一边登录一次，另一边进来就是登录态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicScreen(onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }

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
                factory = { ctx ->
                    WebView(ctx).apply {
                        // 与「成绩查询」共用同一套设置与 UA，登录态才是通的
                        configureZfWebView(this)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                canGoBack = view?.canGoBack() == true
                                // 正方原生排版在手机上太挤，加载完就套上自己的排版
                                if (view != null) applyZfRestyle(view)
                            }
                        }
                        loadUrl(ZF_ACADEMIC_URL)
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
}
