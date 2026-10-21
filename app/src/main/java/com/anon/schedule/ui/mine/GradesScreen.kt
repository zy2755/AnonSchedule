package com.anon.schedule.ui.mine

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anon.schedule.data.GradeBean
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.common.GlassCard
import org.json.JSONObject
import java.util.Locale

// ============================ 教务成绩抓取 ============================

/** 教务登录页（与「教务导入」同一个入口） */
private const val ZF_LOGIN_URL = "http://58.56.101.14:8812/jwglxt/xtgl/login_slogin.html"

/** 表格后三列固定列宽（课程名列自适应） */
private val COL_CREDIT = 52.dp
private val COL_POINT = 52.dp
private val COL_SCORE = 62.dp

/**
 * 在教务页面里直接请求「学生成绩」JSON 接口（复刻课表导入的候选 URL + 失败自动重试套路）。
 * 各校正方版本的接口路径不一致，这里逐个候选尝试；学年/学期优先读页面上的隐藏输入，读不到按当前日期推算。
 */
private const val ZF_GRADE_JS = """
(function () {
  function val(id) { var e = document.getElementById(id); return e && e.value ? e.value : ''; }
  try {
    var xnm = val('xnm'), xqm = val('xqm'), gnm = val('gnmkdm') || 'N305005';
    var dt = new Date(), yy = dt.getFullYear(), mm = dt.getMonth() + 1;
    if (!xnm) { xnm = String(mm >= 9 ? yy : yy - 1); }
    if (!xqm) { xqm = (mm >= 9 || mm <= 1) ? '3' : (mm <= 7 ? '12' : '16'); }
    var p = location.pathname || '/';
    var dir = p.substring(0, p.lastIndexOf('/') + 1);
    var urls = [];
    var i = p.indexOf('/jwglxt/');
    if (i >= 0) {
      var root = p.substring(0, i) + '/jwglxt/';
      urls.push(root + 'cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=' + gnm);
      urls.push(root + 'xscjcx/xscjcx_cxXscj.html?doType=query&gnmkdm=' + gnm);
    }
    urls.push(dir + 'cjcx_cxXsgrcj.html?doType=query&gnmkdm=' + gnm);
    var body = 'xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) +
               '&kcmc=&_search=false&nd=' + Date.now() +
               '&queryModel.showCount=100&queryModel.currentPage=1' +
               '&queryModel.sortName=&queryModel.sortOrder=asc&time=0&gnmkdm=' + encodeURIComponent(gnm);
    var k = 0;
    function next() {
      if (k >= urls.length) {
        try { window.local_obj.showJson('{"error":"接口未返回成绩数据，请先登录并进入「信息查询 → 学生成绩查询」页面再试"}'); } catch (e) {}
        return;
      }
      var xhr = new XMLHttpRequest();
      xhr.open('POST', urls[k++], true);
      xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
      xhr.timeout = 15000;
      xhr.onreadystatechange = function () {
        if (xhr.readyState !== 4) { return; }
        var t = xhr.responseText || '';
        if (xhr.status === 200 && (t.indexOf('"items"') >= 0 || t.indexOf('"list"') >= 0)) {
          // 返回里常没有学年/学期，补在对象头上，便于 App 按学期归档
          var o = t.indexOf('{');
          var wrapped = (o >= 0)
            ? '{"xnm":"' + xnm + '","xqm":"' + xqm + '",' + t.substring(o + 1)
            : t;
          try { window.local_obj.showJson(wrapped); } catch (e) {}
        } else { next(); }
      };
      xhr.ontimeout = next;
      xhr.onerror = next;
      try { xhr.send(body); } catch (e) { next(); }
    }
    next();
  } catch (e) {
    try { window.local_obj.showJson('{"error":"' + e + '"}'); } catch (e2) {}
  }
})();
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(onBack: () -> Unit) {
    val st by Repo.state.collectAsState()
    val grades = st.grades

    // 视图模式：已有成绩就直接看表格（离线可看），否则先登录抓取；点「重新导入」回到登录页
    var showLogin by remember { mutableStateOf(grades.isEmpty()) }
    var url by remember { mutableStateOf(ZF_LOGIN_URL) }
    var status by remember { mutableStateOf("请在下方页面登录教务系统，登录成功后点「读取成绩」") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // JS 桥：读取成功后落库并切到表格视图
    val bridge = remember {
        object {
            @JavascriptInterface
            fun showJson(json: String) {
                val result = runCatching { parseZfGrades(json) }.getOrElse { emptyList() }
                val msg = try {
                    JSONObject(json).optString("error")
                } catch (e: Exception) {
                    ""
                }
                when {
                    msg.isNotEmpty() -> status = msg
                    result.isEmpty() -> status = "没有读到成绩，请先进入「信息查询 → 学生成绩查询」页面再点「读取成绩」"
                    else -> {
                        importGrades(result)
                        status = "读取成功！共 ${result.size} 条成绩，えら〜い！"
                        showLogin = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成绩") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (showLogin) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("教务地址") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(onClick = { webView?.loadUrl(url) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("前往")
                    }
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.userAgentString = settings.userAgentString + " CourseTableApp"
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }
                            addJavascriptInterface(bridge, "local_obj")
                            loadUrl(ZF_LOGIN_URL)
                            webView = this
                        }
                    }
                )

                Button(
                    onClick = {
                        status = "正在向教务读取成绩…"
                        webView?.evaluateJavascript(ZF_GRADE_JS, null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("读取成绩", modifier = Modifier.padding(start = 6.dp))
                }
            }
        } else {
            ResultPane(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                grades = grades,
                order = st.gradeTabOrder,
                onReimport = {
                    status = "请在下方页面登录教务系统，登录成功后点「读取成绩」"
                    showLogin = true
                }
            )
        }
    }
}

// ============================ 成绩表格视图 ============================

/** 学期标签栏 + 汇总 + 表格 + 重新导入 */
@Composable
private fun ResultPane(
    modifier: Modifier,
    grades: List<GradeBean>,
    order: List<String>,
    onReimport: () -> Unit
) {
    val semesters = remember(grades, order) { orderedSemesters(grades, order) }
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menuKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val current = picked?.takeIf { it in semesters } ?: semesters.firstOrNull()

    Column(modifier.padding(horizontal = 12.dp)) {
        if (current == null) {
            Text(
                "还没有成绩数据，点下面的「重新导入」登录教务读取吧～",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            val list = grades.filter { it.xnm to it.xqm == current }
            SemesterTabs(
                keys = semesters,
                current = current,
                onPick = { picked = it },
                onLongPress = { menuKey = it }
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    semesterTitle(current.first, current.second),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                GradeSummary(list)
                Spacer(Modifier.height(12.dp))
                GradeTable(list)
                Spacer(Modifier.height(12.dp))
            }
        }

        Button(
            onClick = onReimport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text("重新导入", modifier = Modifier.padding(start = 6.dp))
        }
    }

    // 长按标签弹出的菜单：左移 / 右移 / 删除（像 Excel 的 sheet 表）
    val key = menuKey
    if (key != null) {
        val index = semesters.indexOf(key)
        AlertDialog(
            onDismissRequest = { menuKey = null },
            title = { Text(semesterTitle(key.first, key.second)) },
            text = {
                Column {
                    TextButton(
                        onClick = { moveSemester(semesters, key, -1); menuKey = null },
                        enabled = index > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("← 左移") }
                    TextButton(
                        onClick = { moveSemester(semesters, key, +1); menuKey = null },
                        enabled = index in 0 until semesters.lastIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("→ 右移") }
                    TextButton(
                        onClick = { deleteSemester(key); menuKey = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("删除此学期", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuKey = null }) { Text("取消") }
            }
        )
    }
}

/** 横向可滚动的学期标签；跨学年时在标签前带上学年（后两位）以区分。长按弹出操作菜单 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SemesterTabs(
    keys: List<Pair<String, String>>,
    current: Pair<String, String>,
    onPick: (Pair<String, String>) -> Unit,
    onLongPress: (Pair<String, String>) -> Unit
) {
    val multiYear = keys.map { it.first }.distinct().size > 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { key ->
            val selected = key == current
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.combinedClickable(
                    onClick = { onPick(key) },
                    onLongClick = { onLongPress(key) }
                )
            ) {
                val label =
                    if (multiYear && key.first.isNotEmpty()) "${key.first.takeLast(2)} ${termName(key.second)}"
                    else termName(key.second)
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** 汇总卡：加权平均分 / 总学分 / 平均绩点（只算有效项，无有效项显示「—」） */
@Composable
private fun GradeSummary(grades: List<GradeBean>) {
    val totalCredit = grades.sumOf { it.credit }
    val numCredit = grades.filter { it.scoreNum != null }.sumOf { it.credit }
    val weighted = if (numCredit > 0)
        grades.sumOf { (it.scoreNum ?: 0.0) * it.credit } / numCredit else null
    val gpCredit = grades.filter { it.gradePoint != null }.sumOf { it.credit }
    val gpa = if (gpCredit > 0)
        grades.sumOf { (it.gradePoint ?: 0.0) * it.credit } / gpCredit else null

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryItem("加权平均分", weighted)
            SummaryItem("总学分", totalCredit)
            SummaryItem("平均绩点", gpa)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value?.let { fmtNum(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 成绩表格：表头（科目/学分/绩点/考核，高亮底色）+ 每门课一行 */
@Composable
private fun GradeTable(list: List<GradeBean>) {
    val headBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    var detail by remember { mutableStateOf<GradeBean?>(null) }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(headBg)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeadCell("科目", Modifier.weight(1f), TextAlign.Start)
            HeadCell("学分", Modifier.width(COL_CREDIT), TextAlign.Center)
            HeadCell("绩点", Modifier.width(COL_POINT), TextAlign.Center)
            HeadCell("考核", Modifier.width(COL_SCORE), TextAlign.Center)
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = 0.5.dp,
            color = lineColor
        )
        list.forEachIndexed { index, g ->
            GradeTableRow(g, onClick = { detail = g })
            if (index != list.lastIndex) {
                HorizontalDivider(thickness = 0.5.dp, color = lineColor)
            }
        }
    }

    // 点某一行弹出课程详情（长课名完整显示 + 学分/绩点/考核）
    val d = detail
    if (d != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailItem("学分", fmtNum(d.credit))
                        DetailItem("绩点", d.gradePoint?.let { fmtNum(it) } ?: "—")
                        DetailItem("考核", d.score.ifEmpty { "—" })
                    }
                    if (d.teacher.isNotEmpty()) {
                        Text(
                            "任课老师：${d.teacher}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun HeadCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = align,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 课程详情弹窗里的一项：数值 + 标签 */
@Composable
private fun DetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GradeTableRow(g: GradeBean, onClick: () -> Unit) {
    val failed = (g.scoreNum != null && g.scoreNum < 60) || g.score.contains("挂科")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            g.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            fmtNum(g.credit),
            modifier = Modifier.width(COL_CREDIT),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            g.gradePoint?.let { fmtNum(it) } ?: "—",
            modifier = Modifier.width(COL_POINT),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            g.score.ifEmpty { "—" },
            modifier = Modifier.width(COL_SCORE),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 数字格式化：最多两位小数，去掉多余的 0（2.0→"2"、1.5→"1.5"、1.36→"1.36"） */
private fun fmtNum(v: Double): String =
    "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')

/** 学期简称："3"→第一学期 "12"→第二学期 "16"→第三学期，其余原样 */
private fun termName(xqm: String): String = when (xqm) {
    "3" -> "第一学期"
    "12" -> "第二学期"
    "16" -> "第三学期"
    else -> if (xqm.isEmpty()) "未分学期" else "学期 $xqm"
}

/** 学期全称（带学年） */
private fun semesterTitle(xnm: String, xqm: String): String {
    val term = termName(xqm)
    return if (xnm.isEmpty()) term else "$xnm 学年 $term"
}

/** 标签顺序：默认新的在前（学年降序、同学年学期降序）；用户长按调整过的按 order 排在后面，新读到的学期自动插到最前 */
private fun orderedSemesters(grades: List<GradeBean>, order: List<String>): List<Pair<String, String>> {
    val present = grades.map { it.xnm to it.xqm }.distinct()
    val custom = order.mapNotNull { parseKey(it) }.filter { it in present }
    val customSet = custom.toSet()
    val rest = present.filterNot { it in customSet }
        .sortedWith(
            compareByDescending<Pair<String, String>> { it.first }
                .thenByDescending { termOrder(it.second) }
        )
    return rest + custom
}

/** 标签键 "学年|学期" 与 Pair 的互转 */
private fun keyStr(k: Pair<String, String>): String = "${k.first}|${k.second}"

private fun parseKey(s: String): Pair<String, String>? {
    val i = s.indexOf('|')
    return if (i < 0) null else s.substring(0, i) to s.substring(i + 1)
}

/** 把标签移动到相邻位置，并把完整顺序保存下来 */
private fun moveSemester(semesters: List<Pair<String, String>>, target: Pair<String, String>, delta: Int) {
    val i = semesters.indexOf(target)
    val j = i + delta
    if (i < 0 || j !in semesters.indices) return
    val swapped = semesters.toMutableList().apply { this[i] = semesters[j]; this[j] = semesters[i] }
    Repo.update { it.copy(gradeTabOrder = swapped.map { k -> keyStr(k) }) }
}

/** 删除某学期的成绩数据（本地移除，之后重新读取可恢复） */
private fun deleteSemester(target: Pair<String, String>) = Repo.update { s ->
    s.copy(
        grades = s.grades.filterNot { (it.xnm to it.xqm) == target },
        gradeTabOrder = s.gradeTabOrder.filterNot { parseKey(it) == target }
    )
}

private fun termOrder(xqm: String): Int = when (xqm) {
    "3" -> 1
    "12" -> 2
    "16" -> 3
    else -> xqm.toIntOrNull() ?: 99
}

// ============================ 解析 / 落库 ============================

/** 解析后的一条成绩 */
data class RawGrade(
    val name: String,
    val credit: Double,
    val score: String,
    val scoreNum: Double?,
    val gradePoint: Double?,
    val teacher: String,
    val xnm: String,
    val xqm: String
)

/** 取成绩文本：先试常见字段名，再兜底扫任意以「cj」结尾且非空的字段（各校正方版本字段名不一） */
private fun pickScore(o: JSONObject): String {
    for (k in listOf("kccj", "zcj", "cj", "kscj", "zpcj", "zpcjz")) {
        val v = o.optString(k).trim()
        if (v.isNotEmpty()) return v
    }
    val keys = o.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        if (k.endsWith("cj") && !k.startsWith("xf")) {
            val v = o.optString(k).trim()
            if (v.isNotEmpty()) return v
        }
    }
    return ""
}

/** 正方成绩 JSON → 成绩列表（items/list 数组键 + 多字段名兜底） */
fun parseZfGrades(json: String): List<RawGrade> {
    val root = JSONObject(json)
    val array = root.optJSONArray("items") ?: root.optJSONArray("list") ?: return emptyList()
    val rootXnm = root.optString("xnm")
    val rootXqm = root.optString("xqm")
    val result = ArrayList<RawGrade>()
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val name = o.optString("kcmc").trim()
        if (name.isEmpty()) continue
        val credit = o.optString("xf").toDoubleOrNull() ?: 0.0
        if (credit <= 0.0) continue
        val score = pickScore(o)
        val scoreNum = Regex("\\d+(?:\\.\\d+)?").find(score)?.value?.toDoubleOrNull()
        // 没有 jd 时用「学分绩点 ÷ 学分」反推绩点
        val gradePoint = o.optString("jd").toDoubleOrNull()
            ?: o.optString("xfjd").toDoubleOrNull()?.let { if (credit > 0) it / credit else null }
        result += RawGrade(
            name = name,
            credit = credit,
            score = score,
            scoreNum = scoreNum,
            gradePoint = gradePoint,
            teacher = o.optString("jsxm").ifEmpty { o.optString("xm") },
            xnm = o.optString("xnm").ifEmpty { rootXnm },
            xqm = o.optString("xqm").ifEmpty { rootXqm }
        )
    }
    return result
}

/** 按学期覆盖写入：同一学年的同一学期再读一次就以最新为准，其它学期保留（多学期累积） */
private fun importGrades(list: List<RawGrade>) = Repo.update { s ->
    val keys = list.map { it.xnm to it.xqm }.toSet()
    s.copy(
        grades = s.grades.filterNot { (it.xnm to it.xqm) in keys } +
            list.map {
                GradeBean(
                    id = Repo.nextId(),
                    name = it.name,
                    credit = it.credit,
                    score = it.score,
                    scoreNum = it.scoreNum,
                    gradePoint = it.gradePoint,
                    xnm = it.xnm,
                    xqm = it.xqm,
                    teacher = it.teacher
                )
            }
    )
}
