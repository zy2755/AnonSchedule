package com.anon.schedule.ui.mine

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * 正方教务的 WebView 统一配置。
 *
 * 「成绩查询」和「学业情况查询」是两个独立页面，但正方用同一个 Cookie（JSESSIONID）标记会话，
 * 而 WebView 的 CookieManager 在整个 App 里是同一个实例。所以只要两边配置一致、
 * 退出时把 Cookie 落到磁盘，就能做到「在任意一边登录一次，另一边进来就是登录态」。
 */

/** 教务系统根路径 */
internal const val ZF_ROOT = "http://58.56.101.14:8812/jwglxt/"

/** 登录页（成绩查询的「重新导入」也从这里进） */
internal const val ZF_LOGIN_URL = "${ZF_ROOT}xtgl/login_slogin.html"

/** 学业情况查询页（正方只给整页 HTML，没有可用的 JSON 接口，只能内嵌 WebView） */
internal const val ZF_ACADEMIC_URL =
    "${ZF_ROOT}xsxy/xsxyqk_cxXsxyqkIndex.html?gnmkdm=N105515&layout=default"

/** UA 后缀：两边都带同一个，避免会话被正方当成不同客户端 */
private const val ZF_UA_SUFFIX = " CourseTableApp"

/** 给教务用的 WebView 套上统一设置（成绩页与学业页共用） */
@SuppressLint("SetJavaScriptEnabled")
internal fun configureZfWebView(view: WebView) {
    view.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        // 教务是 http，页面里可能混着 https 的静态资源
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // 正方按桌面宽度排版，整页缩放才能一屏看全
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        userAgentString = userAgentString + ZF_UA_SUFFIX
        // 教务页面本身是浅色设计，系统深色下不要被自动反色，否则黑底白字跟原站表格对撞很刺眼
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isAlgorithmicDarkeningAllowed = false
        } else {
            @Suppress("DEPRECATION")
            forceDark = WebSettings.FORCE_DARK_OFF
        }
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        // 正方大量用 iframe，第三方 Cookie 也要收，否则登录后内嵌页是空的
        setAcceptThirdPartyCookies(view, true)
    }
}

/** 把内存里的 Cookie 写到磁盘：进程被杀后重新打开仍是登录态 */
internal fun flushZfCookies() {
    CookieManager.getInstance().flush()
}

/**
 * 「学业情况查询」页的重排版。
 *
 * 正方这套页面是给宽屏做的：每层缩进几十像素、字号偏大、卡片是整块蓝色，
 * 手机上越往下越窄，长指标还会断在词中间（「要求学 / 分:36.0」）。
 * 这里注入一段样式 + 少量脚本，把它改成适合手机的紧凑卡片：
 * 收掉缩进、统一白底卡片、把「要求学分:36.0」这类长标签压成「要求 36.0」。
 */
private const val ZF_CSS = """
header#show-head{display:none!important}
html,body{margin:0!important;padding:0!important;background:#eef1f6!important}
body{-webkit-text-size-adjust:100%!important}
#bodyContainer,#bodyContainer .container,#bodyContainer .row,.position_r{background:transparent!important;margin:0!important;padding:0!important;width:auto!important}
#bodyContainer{padding:10px 12px 28px!important;box-sizing:border-box!important}
#alertBox{background:#fff!important;border:1px solid #e7ebf2!important;border-radius:12px!important;padding:10px 12px!important;margin:0 0 10px!important;color:#5b6270!important;box-shadow:0 1px 2px rgba(16,24,40,.04)!important;word-break:break-word!important}
#alertBox,#alertBox *{line-height:1.7!important}
ul.treeview,ul.treeview ul{display:block!important;float:none!important;width:auto!important;margin:0!important;padding:0!important;list-style:none!important;background:none!important;border:0!important;box-shadow:none!important}
ul.treeview ul{padding-left:16px!important}
ul.treeview li{display:block!important;float:none!important;width:auto!important;position:relative!important;clear:both!important;margin:0!important;padding:0!important;list-style:none!important;background:none!important;border:0!important;box-shadow:none!important}
/* 原站的层级连线是画在 li / ul 上的，手机上又细又密，直接去掉，靠缩进表达层级 */
ul.treeview li:before,ul.treeview li:after{content:none!important;display:none!important;background:none!important;border:0!important}
ul.treeview .hitarea{position:absolute!important;top:9px!important;left:0!important;width:14px!important;height:14px!important;margin:0!important;float:none!important;box-sizing:border-box!important;background:#e6ebf5!important;border:1px solid #d3dbeb!important;border-radius:50%!important;z-index:9!important}
ul.treeview li.collapsable>.hitarea:after{content:""!important;position:absolute!important;left:3px!important;top:5px!important;width:6px!important;height:2px!important;background:#7890b8!important;border-radius:1px!important}
ul.treeview li.expandable>.hitarea:before{content:""!important;position:absolute!important;left:5px!important;top:3px!important;width:2px!important;height:6px!important;background:#7890b8!important;border-radius:1px!important}
ul.treeview .title{display:block!important;float:none!important;position:relative!important;width:auto!important;box-sizing:border-box!important;border:1px solid #e7ebf2!important;border-left:3px solid #93a9dc!important;outline:none!important;background:#fff!important;border-radius:12px!important;padding:8px 18px 8px 10px!important;margin:5px 0 5px 16px!important;box-shadow:0 1px 2px rgba(16,24,40,.05)!important;color:#23262b!important;font-size:12.5px!important;line-height:1.6!important;min-height:0!important;height:auto!important;text-align:left!important;word-break:keep-all!important;overflow-wrap:break-word!important}
/* 浅色系下不能再用同一块蓝：根节点深一点、子节点淡一点，缩进之外再给一层层级感 */
ul.treeview>li>.title{border-left-color:#7e9bd6!important}
ul.treeview li ul>li>.title{border-left-color:#b3c5e8!important}
/* 原站给树节点标题配的是浅色字，卡片改成白底后字就看不见了，这里统一压回深色 */
ul.treeview .title,ul.treeview .title *{color:#23262b!important;text-shadow:none!important;text-indent:0!important}
ul.treeview .title p.title1{margin:0!important;padding:0!important;font-size:12.5px!important;line-height:1.6!important;white-space:normal!important;font-weight:600!important;word-break:keep-all!important;overflow-wrap:break-word!important}
ul.treeview .title span[id^=showKc]{position:static!important;display:block!important;margin-top:2px!important}
ul.treeview .title .more{display:block!important;position:static!important;right:auto!important;top:auto!important;background:none!important;border:0!important;outline:none!important;padding:0!important;box-shadow:none!important;font-size:11.5px!important;color:#4a6fd4!important;text-decoration:none!important;font-weight:400!important}
ul.treeview .title .zt1{display:inline-block!important;padding:0 6px!important;border-radius:6px!important;background:#eef2fb!important;color:#5b7cd8!important;font-size:11px!important;font-weight:400!important}
ul.treeview .title i{transform:scale(.8)!important;transform-origin:100% 0!important}
/* 学分已满/未满的角标原来钉在整行的右边缘，贴到卡片右上角更收敛 */
ul.treeview .title i.zt3{position:absolute!important;right:7px!important;top:9px!important;left:auto!important;margin:0!important;z-index:9!important}
.more_con{overflow-x:auto!important;-webkit-overflow-scrolling:touch!important;box-sizing:border-box!important;width:auto!important;margin:6px 0 8px 16px!important;padding:4px 0!important;background:#fff!important;border:1px solid #e7ebf2!important;border-radius:10px!important}
.more_con table{font-size:11px!important;margin:0!important;min-width:760px!important;background:#fff!important;border:0!important;border-collapse:collapse!important}
.more_con table th,.more_con table td{padding:5px 6px!important;white-space:nowrap!important;background:#fff!important;border:0!important;vertical-align:middle!important;line-height:1.5!important}
.more_con table th{background:#eef2f9!important;color:#4d5563!important;font-weight:600!important}
.more_con table tr:nth-child(odd)>td{background:#fff!important}
.more_con table tr:nth-child(even)>td{background:#fafbfd!important}
.more_con table td{border-bottom:1px solid #f1f3f8!important}
.more_con a{color:#4a6fd4!important;text-decoration:none!important}

/* ---- 深色主题 ---- */
/* 页面本身是正方给的浅色稿，深色下靠这套覆盖改成和 App 一致的暗色卡片 */
html.anon-dark,html.anon-dark body{background:#101215!important}
html.anon-dark #alertBox{background:#1a1d22!important;border-color:#24282f!important;color:#a4acb8!important;box-shadow:none!important}
html.anon-dark ul.treeview .title{background:#1a1d22!important;border-color:#24282f!important;border-left-color:#4a6598!important;box-shadow:none!important}
html.anon-dark ul.treeview>li>.title{border-left-color:#5878b4!important}
html.anon-dark ul.treeview li ul>li>.title{border-left-color:#3c4d68!important}
html.anon-dark ul.treeview .title,html.anon-dark ul.treeview .title *{color:#e7e9ee!important}
html.anon-dark ul.treeview .title .more,html.anon-dark ul.treeview .title a,html.anon-dark .more_con a{color:#9dc0ff!important}
html.anon-dark ul.treeview .title .zt1{background:#262b33!important;color:#9dc0ff!important}
html.anon-dark ul.treeview .hitarea{background:#262b33!important;border-color:#333a44!important}
html.anon-dark ul.treeview li.collapsable>.hitarea:after{background:#98a3b3!important}
html.anon-dark ul.treeview li.expandable>.hitarea:before{background:#98a3b3!important}
html.anon-dark .more_con{background:#1a1d22!important;border-color:#24282f!important}
html.anon-dark .more_con table,html.anon-dark .more_con table th,html.anon-dark .more_con table td{background:#1a1d22!important;color:#dfe3e9!important}
html.anon-dark .more_con table th{background:#20242a!important;color:#a4acb8!important}
html.anon-dark .more_con table tr:nth-child(odd)>td{background:#1a1d22!important}
html.anon-dark .more_con table tr:nth-child(even)>td{background:#1e2228!important}
html.anon-dark .more_con table td{border-bottom-color:#24282f!important}
"""

/** 注入样式并把「要求学分:36.0」压成「要求 36.0」，避免长词被硬断行 */
private val ZF_RESTYLE_JS = """
(function(){
  document.documentElement.classList[__ANON_DARK__ ? 'add' : 'remove']('anon-dark');
  if(!document.getElementById('anon-zf-style')){
    var s=document.createElement('style');
    s.id='anon-zf-style';
    s.textContent=`$ZF_CSS`;
    (document.head||document.documentElement).appendChild(s);
  }
  var ab=document.getElementById('alertBox');
  if(ab&&!ab.getAttribute('data-anon')){
    ab.setAttribute('data-anon','1');
    // 原页面在这块里塞了隐藏副本和一堆 font/span，强行改样式会叠字，直接拍平成纯文本
    ab.textContent=(ab.textContent||'').replace(/\s+/g,' ').trim();
    ab.style.setProperty('line-height','22px','important');
    ab.style.setProperty('font-size','12.5px','important');
  }
  var ps=document.querySelectorAll('.title1');
  for(var i=0;i<ps.length;i++){
    var n=ps[i].firstChild;
    if(n&&n.nodeType===3&&n.nodeValue.indexOf('要求学分')>=0){
      n.nodeValue=n.nodeValue
        .replace(/要求学分\s*:\s*/g,'要求\u00A0')
        .replace(/未获得学分\s*:\s*/g,'未获得\u00A0')
        .replace(/获得学分\s*:\s*/g,'获得\u00A0')
        .replace(/\s{2,}/g,' ');
    }
  }
})();
"""

/** 页面底色，和 App 的深浅色对齐，避免加载过程中先闪一下白 */
internal const val ZF_BG_LIGHT = 0xFFEEF1F6.toInt()
internal const val ZF_BG_DARK = 0xFF101215.toInt()

/** 给已经加载完的教务页面套上重排版；深色下同时切到暗色卡片 */
internal fun applyZfRestyle(view: WebView, dark: Boolean) {
    view.evaluateJavascript(
        ZF_RESTYLE_JS.replace("__ANON_DARK__", if (dark) "true" else "false"),
        null
    )
}
