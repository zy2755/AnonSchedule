package com.anon.schedule.ui.mine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.anon.schedule.data.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
.more_con table th{background:#eef2f9!important;color:#4d5563!important;font-weight:600!important;position:relative!important;z-index:6!important}
.more_con table tr:nth-child(odd)>td{background:#fff!important}
.more_con table tr:nth-child(even)>td{background:#fafbfd!important}
.more_con table td{border-bottom:1px solid #f1f3f8!important}
.more_con a{color:#4a6fd4!important;text-decoration:none!important}
/* 快照页自己补的展开：点卡片加上 anon-open 就把明细表放出来 */
ul.treeview li.anon-open>.more_con{display:block!important}

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
  /* 表头吸顶：
     明细表外面套了一层 overflow-x:auto 的容器，CSS 的 position:sticky 会被这个
     滚动容器吃掉（它不纵向滚动，sticky 就退化成 static）。所以改成滚动时把表头
     单元格往下推：表头贴住顶部，等整张表滚到底再跟着一起走。 */
  function anonStickyThead(){
    var wraps=document.querySelectorAll('.more_con');
    for(var i=0;i<wraps.length;i++){
      var wrap=wraps[i];
      var table=wrap.querySelector('table');
      if(!table) continue;
      var tr=table.tHead?table.tHead.rows[0]:(table.rows[0]||null);
      if(!tr||!tr.cells||!tr.cells.length) continue;
      var headH=tr.offsetHeight||32;
      var maxOff=Math.max(0,(wrap.clientHeight||table.offsetHeight)-headH);
      var cur=parseFloat(tr.getAttribute('data-anon-top'))||0;
      if(cur>maxOff) cur=maxOff;
      // 用表头单元格自己的位置反推：cell 的 rect 已含当前位移，减掉就是没位移时该在的位置
      // （注意不能量 tr，行盒不会跟着单元格的相对位移动）
      var staticTop=tr.cells[0].getBoundingClientRect().top-cur;
      var off=staticTop<0?Math.min(-staticTop,maxOff):0;
      if(Math.abs(off-cur)>0.5){
        for(var j=0;j<tr.cells.length;j++) tr.cells[j].style.top=off+'px';
        tr.setAttribute('data-anon-top',off);
      }
    }
  }
  if(!window.__anonStickyBound){
    window.__anonStickyBound=true;
    var ticking=false;
    window.__anonOnScroll=function(){
      if(ticking) return;
      ticking=true;
      requestAnimationFrame(function(){ticking=false;anonStickyThead();});
    };
    window.addEventListener('scroll',window.__anonOnScroll,{passive:true});
    window.addEventListener('resize',window.__anonOnScroll,{passive:true});
  }
  anonStickyThead();
})();
"""

/** 页面底色，和 App 的深浅色对齐，避免加载过程中先闪一下白 */
internal const val ZF_BG_LIGHT = 0xFFEEF1F6.toInt()
internal const val ZF_BG_DARK = 0xFF101215.toInt()

/**
 * 快照页补的交互：点标题卡或「更多」展开/收起下面的课程明细表。
 *
 * 快照是剥过脚本的 DOM，原站自己那套展开逻辑已经没了，只能自己绑一份；
 * 原始 HTML 页面脚本还在，不注入这段，否则两边各切一次等于没切。
 */
private const val ZF_SNAP_JS = """
<script id="anon-more-js">
(function(){
  if(window.__anonMoreBound) return;
  window.__anonMoreBound=true;
  document.addEventListener('click',function(e){
    var el=e.target;
    if(!el||!el.closest) return;
    var li=el.closest('li');
    if(!li) return;
    if(!el.closest('.more')&&!el.closest('.title')) return;
    var box=li.querySelector('.more_con');
    if(!box){
      var nx=li.nextElementSibling;
      if(nx&&(' '+nx.className+' ').indexOf(' more_con ')>=0) box=nx;
    }
    if(!box) return;
    /* 用 class + !important 控制显隐：原站明细表的 display:none 可能带 !important，
       直接改内联样式压不住 */
    li.classList.toggle('anon-open');
    e.preventDefault();
  },true);
})();
</script>
"""

private val HTML_TAG = Regex("<html\\b([^>]*)>", RegexOption.IGNORE_CASE)
private val CLASS_ATTR = Regex("class=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

/**
 * 把重排版样式和深色 class 直接拼进 HTML 源码。
 *
 * 页面一渲染就是我们的排版，不用等 onPageFinished 再注入，少闪一下原始样式；
 * 深色主题下连 `html.anon-dark` 一起带上，否则缓存页第一帧会是浅色的。
 */
internal fun zfWithStyle(html: String, dark: Boolean, snapshot: Boolean = false): String {
    var out = html
    val m = HTML_TAG.find(out)
    if (m != null) {
        var attrs = m.groupValues[1]
        if (dark && !attrs.contains("anon-dark")) {
            val c = CLASS_ATTR.find(attrs)
            attrs = if (c != null) {
                val merged = "${c.groupValues[1]} anon-dark".trim()
                attrs.replaceRange(c.range, "class=\"$merged\"")
            } else {
                "$attrs class=\"anon-dark\""
            }
        }
        // 打个标记，让重排脚本知道这版是剥过脚本的快照
        if (snapshot && !attrs.contains("data-anon-snap")) attrs += " data-anon-snap=\"1\""
        out = out.substring(0, m.range.first) + "<html$attrs>" + out.substring(m.range.last + 1)
    }
    if (!out.contains("anon-zf-style")) {
        val style = "<style id=\"anon-zf-style\">$ZF_CSS</style>"
        val idx = out.indexOf("</head>", ignoreCase = true)
        out = if (idx >= 0) out.substring(0, idx) + style + out.substring(idx) else style + out
    }
    // 快照没有脚本，原站的「更多」展开得自己补回来
    if (snapshot && !out.contains("anon-more-js")) {
        val b = out.lastIndexOf("</body>", ignoreCase = true)
        out = if (b >= 0) out.substring(0, b) + ZF_SNAP_JS + out.substring(b) else out + ZF_SNAP_JS
    }
    return out
}

/**
 * 直接向教务要服务器的原始 HTML。
 *
 * WebView 只能拿到执行完脚本的 DOM，把 DOM 再喂回去会让页面脚本二次执行，
 * 「并且 / 或者」这类由脚本插入的节点会重复出现，所以缓存必须存原始 HTML。
 */
internal suspend fun zfFetchHtml(context: Context, url: String, cookie: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context) + ZF_UA_SUFFIX)
            if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

/** 给已经加载完的教务页面套上重排版；深色下同时切到暗色卡片 */
internal fun applyZfRestyle(view: WebView, dark: Boolean) {
    view.evaluateJavascript(
        ZF_RESTYLE_JS.replace("__ANON_DARK__", if (dark) "true" else "false"),
        null
    )
}

/**
 * 判断某个 URL 是不是正方的登录页。
 *
 * 正方会话过期后不会给你一个「请登录」的提示，而是整页换成登录页，
 * 所以只能靠 URL 判断。
 */
internal fun isZfLoginUrl(url: String?): Boolean =
    url != null && url.contains("login_slogin")

/** 缓存的学业页 HTML：冷启动时先拿它顶上去，不用先看一屏登录页 */
internal object ZfCache {
    /** 服务器原始 HTML：在线刷新时用来显示（脚本正常执行，树可以折叠） */
    private const val NAME = "zf_academic.html"

    /** 渲染之后的 DOM 快照：离线/首帧用，剥掉脚本再加载，内容完整且不会重复 */
    private const val VIEW = "zf_academic_view.html"

    fun read(context: Context): String? = runCatching {
        val f = File(context.filesDir, NAME)
        if (!f.exists() || f.length() == 0L) return@runCatching null
        val html = f.readText(Charsets.UTF_8)
        // 早期版本缓存的是「执行完脚本的 DOM」，重新加载会让页面脚本再跑一遍、内容重复，直接作废
        if (html.contains("anon-zf-style")) null else html
    }.getOrNull()

    fun write(context: Context, html: String) = runCatching {
        if (html.length < 512) return@runCatching
        File(context.filesDir, NAME).writeText(html, Charsets.UTF_8)
    }

    /** 渲染后的 DOM 快照：整棵课程树都在里面，断网也能完整显示 */
    fun readView(context: Context): String? = runCatching {
        val f = File(context.filesDir, VIEW)
        if (!f.exists() || f.length() == 0L) null else f.readText(Charsets.UTF_8)
    }.getOrNull()

    fun writeView(context: Context, html: String) = runCatching {
        if (html.length < 512) return@runCatching
        File(context.filesDir, VIEW).writeText(html, Charsets.UTF_8)
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, NAME).delete() }
        runCatching { File(context.filesDir, VIEW).delete() }
    }
}

private val SCRIPT_TAG = Regex("<script\\b[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)

/**
 * 准备一份「只用于显示」的快照。
 *
 * 快照里的 DOM 是脚本跑完之后的样子，直接加载会让原站脚本再初始化一次
 * （「并且 / 或者」「共 N 门」会翻倍），所以把 `<script>` 全部剥掉：
 * 树已经画在 DOM 里了，静态看没问题，只是不能折叠。
 */
internal fun zfSnapshotForView(html: String): String = SCRIPT_TAG.replace(html, "")

/** evaluateJavascript 的返回值是 JSON 字面量，解一层才能当 HTML 用 */
internal fun zfDecodeJsString(raw: String?): String? {
    if (raw.isNullOrEmpty() || raw == "null") return null
    return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
}

/** 课程树节点数：用来判断树是不是还在追加 */
internal const val ZF_TREE_COUNT_JS =
    "(function(){return document.querySelectorAll('ul.treeview li').length;})()"

/**
 * 取一份「渲染结果」：树节点数、文字签名、整页 DOM。
 *
 * 节点数用来判断树是不是画完了（树是脚本异步画的，「加载完」不等于树出来了）；
 * 签名去掉每次请求都会变的「统计时间 … 之前」，用来判断内容到底变没变——
 * 没变就别重载页面，否则整页会白闪一下。
 *
 * 签名只取 `#alertBox` 和课程树的 `innerText`：
 * - 用 `innerText` 而不是 `textContent`，是因为树里夹着内联 `<script>`，
 *   而主页面显示的是剥过脚本的快照，`textContent` 会把脚本文本也算进去，两边对不上；
 * - 只取这两块，是因为主页面注入的样式会把 `#show-head` 之类隐藏掉，
 *   整页 `innerText` 两侧宽度不一样；
 * - 去掉「学分:」，是因为 [ZF_RESTYLE_JS] 会把「要求学分:36.0」压成「要求36.0」，
 *   主页面已经是压过的样子，不归一化两边永远对不上。
 */
internal val ZF_RENDERED_JS = """
(function(){
  try{
    var n=document.querySelectorAll('ul.treeview li').length;
    var a=document.getElementById('alertBox'),t=document.querySelector('ul.treeview');
    var sig=((a?a.innerText:'')+'|'+(t?t.innerText:'')).replace(/\s+/g,'').replace(/统计时间[\s\S]{0,32}?之前/,'').replace(/学分[:：]/g,'');
    return JSON.stringify({n:n,sig:sig,html:document.documentElement.outerHTML});
  }catch(e){ return '{}'; }
})();
"""

/** 只取文字签名：跟当前显示的内容比一比，决定要不要重载 */
internal val ZF_SIG_JS = """
(function(){
  try{
    var a=document.getElementById('alertBox'),t=document.querySelector('ul.treeview');
    return ((a?a.innerText:'')+'|'+(t?t.innerText:'')).replace(/\s+/g,'').replace(/统计时间[\s\S]{0,32}?之前/,'').replace(/学分[:：]/g,'');
  }catch(e){ return ''; }
})();
"""

/**
 * 教务账号密码（加密存本机，只用于自动登录）。
 *
 * 正方登录时密码会被页面里的 RSA 再加密一次，抓不到明文，所以只能让用户单独输一遍。
 * 存法和澡堂账号一致，走 [SecurePrefs]（Keystore），不落明文、不上传。
 */
internal object ZfCreds {
    private const val K_USER = "zf_user"
    private const val K_PWD = "zf_pwd"

    fun load(context: Context): Pair<String, String>? {
        val user = SecurePrefs.getString(context, K_USER)?.takeIf { it.isNotBlank() } ?: return null
        val pwd = SecurePrefs.getString(context, K_PWD)?.takeIf { it.isNotBlank() } ?: return null
        return user to pwd
    }

    fun save(context: Context, user: String, pwd: String) {
        SecurePrefs.putString(context, K_USER, user)
        SecurePrefs.putString(context, K_PWD, pwd)
    }

    fun clear(context: Context) {
        SecurePrefs.remove(context, K_USER)
        SecurePrefs.remove(context, K_PWD)
    }
}

/**
 * 自动登录脚本：把学号密码填进正方登录页，再点页面自己的登录按钮。
 *
 * 密码不在我们这边加密——登录页的 `login.js` 会在提交前用页面里的 RSA 公钥处理，
 * 所以只要把值填进 `#mm` 然后触发 `#dl` 的点击，就能复用学校自己的登录流程。
 * 已经填过值就跳过，避免和用户手动输入打架。
 */
internal fun zfAutoLoginJs(user: String, pwd: String): String = """
(function(){
  try{
    var y=document.getElementById('yhm'),m=document.getElementById('mm'),b=document.getElementById('dl');
    if(!y||!m||!b) return 'noform';
    if(y.value||m.value) return 'filled';
    y.value=${JSONObject.quote(user)};
    m.value=${JSONObject.quote(pwd)};
    m.type='password';
    y.dispatchEvent(new Event('input',{bubbles:true}));
    m.dispatchEvent(new Event('input',{bubbles:true}));
    setTimeout(function(){ b.click(); },80);
    return 'ok';
  }catch(e){ return 'err'; }
})();
"""
