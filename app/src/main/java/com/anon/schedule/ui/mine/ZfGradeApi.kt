package com.anon.schedule.ui.mine

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * 成绩的原生抓取（参考开源项目 zhengfang-apk 的思路）：
 * WebView 只负责登录，之后拿它的 Cookie 直接请求教务接口，不再靠页面里注入 JS。
 *
 * - 各版本正方的接口路径不一致：按候选依次尝试，记住哪个能用；
 * - 先读成绩页的学年/学期下拉框，逐个学期查询（自动翻页）；
 * - 没有成绩的学期直接丢弃，不给空标签。
 */
object ZfGradeApi {

    private const val PAGE_SIZE = 200

    /** 成绩查询接口候选（新版正方 / 旧版学生成绩） */
    private val QUERY_PATHS = listOf(
        "cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005",
        "cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005",
        "xscjcx/xscjcx_cxXscj.html?doType=query&gnmkdm=N305005"
    )

    /** 成绩页（从中解析学年 / 学期下拉框） */
    private val TERM_PAGE_PATHS = listOf(
        "cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default",
        "cjcx/cjcx_cxXsgrcj.html?gnmkdm=N305005"
    )

    /** 已探明可用的接口：后续学期优先用它，少走弯路 */
    private var workingPath: String? = null

    /**
     * 读取全部学期的成绩。进度（正在读哪个学期）通过 [onProgress] 回调给界面。
     * 读取失败或一条都没有时抛出带中文说明的异常。
     */
    suspend fun fetchAll(loginUrl: String, onProgress: (String) -> Unit = {}): List<RawGrade> {
        val root = rootOf(loginUrl)
        val cookie = withContext(Dispatchers.Main.immediate) {
            CookieManager.getInstance().getCookie(root)
        }?.takeIf { it.isNotBlank() }
            ?: error("没读到登录状态，请先在下方页面登录教务系统")
        return withContext(Dispatchers.IO) {
            val terms = discoverTerms(root, cookie) ?: guessTerms()
            val result = ArrayList<RawGrade>()
            // 按学年倒序逐学年读；整个学年一条成绩都没有，就不再往前翻（入学前的学年不会有数据）
            val byYear = terms.groupBy { it.first }
            var firstYear = true
            for ((_, pairs) in byYear) {
                var found = 0
                for ((xnm, xqm) in pairs) {
                    onProgress("正在读取 $xnm 学年${termLabel(xqm)}…")
                    val list = fetchTerm(root, cookie, xnm, xqm)
                    found += list.size
                    result += list
                }
                // 最新学年可能只是还没出分，允许它为空；再往前的空学年直接收工
                if (found == 0 && !firstYear) break
                firstYear = false
            }
            if (result.isEmpty()) {
                error("没有读到成绩，请确认已登录，并进入「信息查询 → 学生成绩查询」页面后再试")
            }
            result
        }
    }

    /** 登录地址 → jwglxt 根路径（http://host:port/jwglxt/） */
    private fun rootOf(loginUrl: String): String {
        val i = loginUrl.indexOf("/jwglxt/")
        return if (i >= 0) loginUrl.substring(0, i + "/jwglxt/".length)
        else loginUrl.substringBeforeLast('/').trimEnd('/') + "/"
    }

    // ---------------------------- 学期列表 ----------------------------

    /** 解析成绩页里的学年 / 学期下拉框；拿不到就返回 null，改用日期推算 */
    private fun discoverTerms(root: String, cookie: String): List<Pair<String, String>>? {
        val latest = currentStartYear()
        for (path in TERM_PAGE_PATHS) {
            val html = runCatching { request(root + path, cookie, null) }.getOrNull() ?: continue
            val years = optionValues(html, "xnm")
                .mapNotNull { it.toIntOrNull() }
                .filter { it <= latest }
            val terms = optionValues(html, "xqm").filter { it.toIntOrNull() != null }
            if (years.isEmpty() || terms.isEmpty()) continue
            return years.flatMap { y -> terms.map { q -> y.toString() to q } }
                .distinct()
                .sortedWith(
                    compareByDescending<Pair<String, String>> { it.first }
                        .thenByDescending { termOrder(it.second) }
                )
        }
        return null
    }

    /** 兜底：从当前学年往前推 6 个学年 × 两个学期 */
    private fun guessTerms(): List<Pair<String, String>> {
        val latest = currentStartYear()
        return (0..6).map { latest - it }
            .flatMap { y -> listOf(y.toString() to "3", y.toString() to "12") }
    }

    /** 当前学年起始年（9 月以后算新学年） */
    private fun currentStartYear(): Int {
        val cal = Calendar.getInstance()
        return if (cal.get(Calendar.MONTH) + 1 >= 9) cal.get(Calendar.YEAR) else cal.get(Calendar.YEAR) - 1
    }

    private fun optionValues(html: String, id: String): List<String> {
        val select = Regex("(?is)<select[^>]*id=[\"']?$id[\"']?[^>]*>(.*?)</select>")
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("(?i)<option[^>]*value=[\"']?([^\"'>\\s]+)")
            .findAll(select).map { it.groupValues[1] }.toList()
    }

    private fun termOrder(xqm: String): Int = when (xqm) {
        "3" -> 1
        "12" -> 2
        "16" -> 3
        else -> xqm.toIntOrNull() ?: 99
    }

    private fun termLabel(xqm: String): String = when (xqm) {
        "3" -> "第一学期"
        "12" -> "第二学期"
        "16" -> "第三学期"
        else -> "学期 $xqm"
    }

    // ---------------------------- 单学期查询 ----------------------------

    /**
     * 一个学期的成绩：接口返回合法 JSON 才算数。
     * 一旦某个接口读到过成绩就锁定它，后续学期（包括空学期）只请求这一个；
     * 还没锁定前才依次试候选。
     */
    private fun fetchTerm(root: String, cookie: String, xnm: String, xqm: String): List<RawGrade> {
        workingPath?.let { path ->
            val list = fetchPath(root + path, cookie, xnm, xqm)
            if (list != null) return list
            workingPath = null // 已探明的接口失效，重新探测
        }
        var sawJson = false
        for (path in QUERY_PATHS) {
            val list = fetchPath(root + path, cookie, xnm, xqm) ?: continue
            sawJson = true
            if (list.isNotEmpty()) {
                workingPath = path
                return list
            }
        }
        // 返回过合法 JSON 但所有接口都为空 → 该学期确实没有成绩，不展示
        if (!sawJson) error("教务接口没有返回成绩数据，请重新登录后再试")
        return emptyList()
    }

    /** 同一接口翻页读完；返回 null 表示这个接口不可用（非 JSON / 跳登录页） */
    private fun fetchPath(url: String, cookie: String, xnm: String, xqm: String): List<RawGrade>? {
        val all = ArrayList<RawGrade>()
        var rawCount = 0
        var page = 1
        while (page <= 100) {
            val text = runCatching { request(url, cookie, queryBody(xnm, xqm, page)) }.getOrNull()
                ?: return null
            val json = text.trimStart()
            if (!json.startsWith("{")) return null
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val items = obj.optJSONArray("items") ?: obj.optJSONArray("list") ?: return null
            // 返回里常不带学年/学期：补在对象头上，便于按学期归档
            all += parseZfGrades("{\"xnm\":\"$xnm\",\"xqm\":\"$xqm\"," + json.substring(1))
            rawCount += items.length()
            val total = obj.optInt("totalResult", obj.optInt("totalCount", obj.optInt("count", 0)))
            if (items.length() < PAGE_SIZE || (total > 0 && rawCount >= total)) break
            page++
        }
        return all
    }

    private fun queryBody(xnm: String, xqm: String, page: Int): String =
        "xnm=$xnm&xqm=$xqm&kcmc=&_search=false&nd=${System.currentTimeMillis()}" +
            "&queryModel.showCount=$PAGE_SIZE&queryModel.currentPage=$page" +
            "&queryModel.sortName=&queryModel.sortOrder=asc&time=0&gnmkdm=N305005"

    // ---------------------------- HTTP ----------------------------

    private fun request(url: String, cookie: String, body: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = if (body == null) "GET" else "POST"
            setRequestProperty("Cookie", cookie)
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 CourseTableApp")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(conn.contentType.orEmpty())?.groupValues?.get(1)
                ?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
            String(bytes, charset)
        } finally {
            conn.disconnect()
        }
    }
}
