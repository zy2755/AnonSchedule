package com.anon.schedule.ui.importer

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anon.schedule.data.CourseBean
import com.anon.schedule.data.DetailBean
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.common.GlassCard
import org.json.JSONObject
import java.net.URLDecoder
import java.util.TreeSet

// ============================ 教务导入 ============================

private const val ZF_URL = "http://58.56.101.14:8812/jwglxt/xtgl/login_slogin.html"

/**
 * 在教务页面里直接请求「学生课表」JSON 接口（zftal v5 页面是 JS 拼的空壳，
 * 抓 HTML 拿不到课，所以走它自己的 xskbcx_cxXsgrkb.html 接口）。
 */
private const val ZF_SCHEDULE_JS = """
(function () {
  function val(id) { var e = document.getElementById(id); return e && e.value ? e.value : ''; }
  try {
    var xnm = val('xnm'), xqm = val('xqm'), gnm = val('gnmkdm') || 'N2151';
    var dt = new Date(), yy = dt.getFullYear(), mm = dt.getMonth() + 1;
    if (!xnm) { xnm = String(mm >= 9 ? yy : yy - 1); }
    if (!xqm) { xqm = (mm >= 9 || mm <= 1) ? '3' : (mm <= 7 ? '12' : '16'); }
    var p = location.pathname || '/';
    var dir = p.substring(0, p.lastIndexOf('/') + 1);
    var urls = [];
    var i = p.indexOf('/jwglxt/');
    if (i >= 0) { urls.push(p.substring(0, i) + '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnm); }
    urls.push(dir + 'xskbcx_cxXsgrkb.html?gnmkdm=' + gnm);
    var body = 'xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) +
               '&kzlx=ck&xsdm=&kclbdm=&kclxdm=&gnmkdm=' + encodeURIComponent(gnm);
    var k = 0;
    function next() {
      if (k >= urls.length) {
        try { window.local_obj.showJson('{"error":"接口未返回课表数据，请先登录并进入「个人课表查询」页面再试"}'); } catch (e) {}
        return;
      }
      var xhr = new XMLHttpRequest();
      xhr.open('POST', urls[k++], true);
      xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
      xhr.timeout = 15000;
      xhr.onreadystatechange = function () {
        if (xhr.readyState !== 4) { return; }
        var t = xhr.responseText || '';
        if (xhr.status === 200 && t.indexOf('"kbList"') >= 0) {
          try { window.local_obj.showJson(t); } catch (e) {}
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
fun ImportZfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(ZF_URL) }
    var status by remember { mutableStateOf("请在下方页面登录教务系统，登录成功后点「读取课表」") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教务导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val bridge = remember {
                object {
                    @JavascriptInterface
                    fun showJson(json: String) {
                        val result = runCatching { parseZfJson(json) }.getOrElse { emptyList() }
                        val msg = try {
                            val err = JSONObject(json).optString("error")
                            if (err.isNotEmpty()) err else null
                        } catch (e: Exception) {
                            null
                        }
                        status = when {
                            msg != null -> msg
                            result.isEmpty() -> "没有解析到课程，请先进入「个人课表查询」页面再点「读取课表」"
                            else -> {
                                importParsed(result)
                                "导入成功！共 ${result.size} 条课程安排，えら〜い！"
                            }
                        }
                    }
                }
            }

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
                        loadUrl(ZF_URL)
                        webView = this
                    }
                }
            )

            Button(
                onClick = {
                    status = "正在向教务读取课表…"
                    webView?.evaluateJavascript(ZF_SCHEDULE_JS, null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("读取课表", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

// ============================ CSV 导入 ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCsvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("选择一个 CSV 文件导入（UTF-8 / GBK 编码都行）") }
    var pending by remember { mutableStateOf<List<RawCourse>>(emptyList()) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let(::decodeCsvBytes)
            pending = if (text == null) emptyList() else parseCsv(text)
            status = if (pending.isEmpty()) "没有解析到课程，检查一下文件格式？"
            else "解析到 ${pending.size} 条课程安排，点「确认导入」加进课表"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CSV 导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("CSV 格式（每行一条安排）：", style = MaterialTheme.typography.titleSmall)
                Text(
                    "课程名称,星期,开始节数,结束节数,老师,地点,周数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "示例：高等数学,1,3,4,马晓,教学楼301,1-16",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "周数写法：1-16（连续）/ 1、2（离散）/ 2、4、6（隔周）/ 1-8、10-16（混搭）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(status, style = MaterialTheme.typography.bodyMedium)

            OutlinedButton(onClick = { pickFile.launch(arrayOf("text/*", "text/comma-separated-values", "text/csv", "*/*")) }) {
                Text("选择 CSV 文件")
            }

            if (pending.isNotEmpty()) {
                pending.take(10).forEach { c ->
                    val weekText = if (c.startWeek == c.endWeek) "第${c.startWeek}周"
                    else "第${c.startWeek}-${c.endWeek}周" + when (c.type) {
                        1 -> "(单周)"; 2 -> "(双周)"; else -> ""
                    }
                    Text(
                        "· ${c.name} 周${"一二三四五六日"[c.day - 1]} 第${c.startNode}-${c.startNode + c.step - 1}节 " +
                            "$weekText ${c.teacher} ${c.room}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = {
                    importParsed(pending)
                    status = "导入成功！共 ${pending.size} 条课程安排，えら〜い！"
                    pending = emptyList()
                }) {
                    Text("确认导入")
                }
            }
        }
    }
}

// ============================ 解析 ============================

/** 解析后的一条课程安排 */
data class RawCourse(
    val name: String,
    val day: Int,
    val startNode: Int,
    val step: Int,
    val startWeek: Int,
    val endWeek: Int,
    val type: Int,
    val teacher: String,
    val room: String
)

/** 正方教务 kbList JSON → 课程安排 */
fun parseZfJson(json: String): List<RawCourse> {
    val root = JSONObject(json)
    val array = root.optJSONArray("kbList") ?: return emptyList()
    val result = ArrayList<RawCourse>()
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val name = (o.optString("kcmc").ifEmpty { o.optString("kcbmc") })
            .replace(Regex("""[（(][^）)]*[）)]"""), "")
            .trim()
        if (name.length <= 1) continue
        val day = o.optString("xqj").toIntOrNull() ?: continue
        if (day !in 1..7) continue
        val jcText = o.optString("jc").ifEmpty { o.optString("jcor") }
            .replace('－', '-').replace('~', '-')
        val node = parseNodes(jcText) ?: continue
        val spans = parseWeeks(o.optString("zcd"))
        if (spans.isEmpty()) continue
        val room = o.optString("cdmc").ifEmpty { o.optString("xqmc") }
        val teacher = o.optString("xm")
        for ((s, e, t) in spans) {
            result += RawCourse(
                name = name, day = day,
                startNode = node.first, step = node.second,
                startWeek = s, endWeek = e, type = t,
                teacher = teacher, room = room
            )
        }
    }
    return result
}

/** "1-2" → (开始节次, 节数) */
private fun parseNodes(jc: String): Pair<Int, Int>? {
    val nums = Regex("\\d+").findAll(jc).map { it.value.toInt() }.toList()
    if (nums.isEmpty()) return null
    val start = nums.first()
    val end = if (nums.size >= 2) nums[1] else start
    return start to (end - start + 1).coerceAtLeast(1)
}

/** "1-16周(单)" / "3-18周(双)" / "1-8周|10-16周" → (开始周, 结束周, 类型) 列表 */
private fun parseWeeks(zcd: String): List<Triple<Int, Int, Int>> {
    val result = ArrayList<Triple<Int, Int, Int>>()
    for (part in zcd.split("|", "，", ",", ";", "；")) {
        val text = part.trim()
        if (text.isEmpty()) continue
        val type = when {
            text.contains("单") -> 1
            text.contains("双") -> 2
            else -> 0
        }
        val nums = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        when {
            nums.isEmpty() -> continue
            nums.size == 1 -> result += Triple(nums[0], nums[0], type)
            else -> result += Triple(nums[0], nums[1], type)
        }
    }
    return result
}

/** CSV 字节解码：UTF-8（带不带 BOM 都行），解码出坏字就回退 GBK */
private fun decodeCsvBytes(bytes: ByteArray): String {
    val utf8 = bytes.decodeToString()
    val text = if (utf8.contains('�')) {
        runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
    } else {
        utf8
    }
    return text.removePrefix("﻿")
}

/** 引号感知的 CSV 切分：支持 "a,b" 字段内含逗号和 "" 转义引号 */
private fun splitCsvLine(line: String): List<String> {
    val result = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ch == ',' && !inQuotes -> {
                result.add(sb.toString())
                sb.setLength(0)
            }
            else -> sb.append(ch)
        }
        i++
    }
    result.add(sb.toString())
    return result
}

/**
 * 周数表达式 → 周集合：支持 "1-16"（连续）、"1、2"（离散）、"2、4、6"（隔周）、
 * "1-8、10-16"（混搭）、"1-16(单)"（限定单/双周）
 */
private fun parseWeekSet(expr: String): List<Int> {
    val weeks = TreeSet<Int>()
    for (part in Regex("[、，,;；|/\\s]+").split(expr)) {
        var text = part.trim()
        if (text.isEmpty()) continue
        var parity = 0 // 0 不限 1 单周 2 双周
        val pm = Regex("[（(]?\\s*([单双])\\s*[)）]?").find(text)
        if (pm != null) {
            parity = if (pm.groupValues[1] == "单") 1 else 2
            text = text.replace(pm.value, "")
        }
        text = text.replace("周", "")
        val nums = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) continue
        val a = minOf(nums[0], nums.getOrElse(1) { nums[0] })
        val b = maxOf(nums[0], nums.getOrElse(1) { nums[0] })
        for (w in a..b) {
            if (parity == 1 && w % 2 == 0) continue
            if (parity == 2 && w % 2 == 1) continue
            if (w in 1..30) weeks.add(w)
        }
    }
    return weeks.toList()
}

/**
 * 周集合贪心压缩成 (开始周, 结束周, 类型) 段，类型 0=每周 1=单周 2=双周：
 * 差 1 连续合并为每周段；差 2 同奇偶合并为单/双周段；其余各自成段
 */
private fun compressWeeks(weeks: List<Int>): List<Triple<Int, Int, Int>> {
    val spans = ArrayList<Triple<Int, Int, Int>>()
    var i = 0
    while (i < weeks.size) {
        val start = weeks[i]
        if (i + 1 < weeks.size && weeks[i + 1] - start == 2) {
            var j = i + 1
            while (j < weeks.size && weeks[j] - weeks[j - 1] == 2) j++
            spans += Triple(start, weeks[j - 1], if (start % 2 == 1) 1 else 2)
            i = j
        } else if (i + 1 < weeks.size && weeks[i + 1] - start == 1) {
            var j = i + 1
            while (j < weeks.size && weeks[j] - weeks[j - 1] == 1) j++
            spans += Triple(start, weeks[j - 1], 0)
            i = j
        } else {
            spans += Triple(start, start, 0)
            i++
        }
    }
    return spans
}

/** CSV 文本 → 课程安排。模板 7 列：课程名称,星期,开始节数,结束节数,老师,地点,周数 */
fun parseCsv(text: String): List<RawCourse> {
    val result = ArrayList<RawCourse>()
    for (line in text.lineSequence()) {
        val raw = line.trim()
        if (raw.isEmpty()) continue
        val cols = splitCsvLine(raw).map { it.trim() }
        val head = cols.firstOrNull().orEmpty()
        if (head.isEmpty() || head.contains("课程名称") || head.contains("课程名") ||
            head.equals("name", true) || head.equals("course", true)
        ) {
            continue // 表头
        }

        // 旧 9 列格式兼容：课程名称,星期,开始节次,节数,开始周,结束周,周次类型,教师,地点
        if (cols.size >= 9 && cols[6] in setOf("每周", "单周", "双周", "0", "1", "2")) {
            val day = cols[1].toIntOrNull() ?: continue
            val startNode = cols[2].toIntOrNull() ?: continue
            val startWeek = cols[4].toIntOrNull() ?: continue
            val endWeek = cols[5].toIntOrNull() ?: startWeek
            result += RawCourse(
                name = head, day = day.coerceIn(1, 7),
                startNode = startNode.coerceIn(1, 12),
                step = (cols[3].toIntOrNull() ?: 1).coerceIn(1, 8),
                startWeek = startWeek.coerceIn(1, 30),
                endWeek = endWeek.coerceIn(1, 30),
                type = when (cols[6]) {
                    "单周", "1" -> 1
                    "双周", "2" -> 2
                    else -> 0
                },
                teacher = cols.getOrElse(7) { "" },
                room = cols.getOrElse(8) { "" }
            )
            continue
        }

        // 模板 7 列（列数多于 7 时取前 7 列）
        if (cols.size < 7) continue
        val day = cols[1].toIntOrNull() ?: continue
        val startNode = cols[2].toIntOrNull() ?: continue
        val endNode = cols[3].toIntOrNull() ?: startNode
        if (day !in 1..7 || startNode < 1 || endNode < startNode) continue
        for ((s, e, t) in compressWeeks(parseWeekSet(cols[6]))) {
            result += RawCourse(
                name = head, day = day,
                startNode = startNode.coerceIn(1, 12),
                step = (endNode - startNode + 1).coerceIn(1, 8),
                startWeek = s, endWeek = e, type = t,
                teacher = cols[4], room = cols[5]
            )
        }
    }
    return result
}

/** 把解析结果写进当前课表（同名课程合并为一门，多个时间段是多条安排） */
private fun importParsed(list: List<RawCourse>) {
    Repo.update { s ->
        val colors = HashMap<String, Int>()
        var courses = s.courses
        var details = s.details
        for (raw in list) {
            val existing = courses.firstOrNull { it.tableId == s.currentTableId && it.name == raw.name }
            val courseId = if (existing != null) {
                existing.id
            } else {
                val id = Repo.nextId()
                val color = colors.size % 8
                colors[raw.name] = color
                courses = courses + CourseBean(id, raw.name, color, s.currentTableId)
                id
            }
            val duplicated = details.any {
                it.courseId == courseId && it.day == raw.day && it.startNode == raw.startNode &&
                    it.step == raw.step && it.startWeek == raw.startWeek && it.endWeek == raw.endWeek
            }
            if (!duplicated) {
                details = details + DetailBean(
                    id = Repo.nextId(), courseId = courseId,
                    day = raw.day, room = raw.room, teacher = raw.teacher,
                    startNode = raw.startNode, step = raw.step,
                    startWeek = raw.startWeek, endWeek = raw.endWeek, type = raw.type,
                    tableId = s.currentTableId
                )
            }
        }
        s.copy(courses = courses, details = details)
    }
}
