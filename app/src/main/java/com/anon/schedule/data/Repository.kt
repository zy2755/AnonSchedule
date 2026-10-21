package com.anon.schedule.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 全部数据的唯一入口：内存 StateFlow + JSON 文件持久化。
 */
object Repo {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private lateinit var file: File
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** 数据是否已从磁盘读入：未读完前不落盘，避免刚启动就退后台用默认值覆盖 data.json */
    @Volatile
    private var loaded = false

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "data.json")
        // 同步读完再返回：调用方在 setContent 之前就能拿到持久化的主题/背景，
        // 首帧直接渲染正确主题，不会先闪一下"默认浅色主题再切深色"；文件只有几十 KB，这点开销可忽略
        val decoded = runCatching {
            if (file.exists()) json.decodeFromString<AppState>(file.readText()) else AppState()
        }.getOrNull()
        _state.value = repairIds(decoded ?: AppState())
        loaded = true
        ensureStartDate()
        save()
    }

    /**
     * 未设置开学日期时自动补一个推算起点（假设"当前周"包含今天），
     * 让星期表头的日期与"今天"高亮开箱即用；之后可在设置里改成真实开学日期。
     * 只在启动时补一次，保证周↔日期映射稳定（翻页时不会漂移）。
     */
    private fun ensureStartDate() {
        val st = _state.value
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        fun inferred(week: Int) = monday.minusDays(((week.coerceAtLeast(1) - 1) * 7).toLong()).toEpochDay()
        val newGlobal = if (st.settings.startDate == null)
            st.settings.copy(startDate = inferred(st.settings.currentWeek)) else st.settings
        val newTables = st.tables.map { t ->
            val ts = t.settings
            if (ts != null && ts.startDate == null) t.copy(settings = ts.copy(startDate = inferred(ts.currentWeek))) else t
        }
        if (newGlobal != st.settings || newTables != st.tables) {
            _state.value = st.copy(settings = newGlobal, tables = newTables)
        }
    }

    /** 旧版本曾用毫秒时间戳当 id，可能撞车；加载时重新编号保证 id 唯一 */
    private fun repairIds(s: AppState): AppState {
        val seen = HashSet<Long>()
        fun uid(): Long {
            var id = nextId()
            while (!seen.add(id)) id = nextId()
            return id
        }
        val courseMap = HashMap<Long, Long>()
        val courses = s.courses.map { c ->
            if (seen.add(c.id)) c else c.copy(id = uid()).also { courseMap[c.id] = it.id }
        }
        val details = s.details.map { d ->
            val nd = if (seen.add(d.id)) d else d.copy(id = uid())
            if (courseMap.containsKey(nd.courseId)) nd.copy(courseId = courseMap.getValue(nd.courseId)) else nd
        }
        return s.copy(courses = courses, details = details)
    }

    fun update(transform: (AppState) -> AppState) {
        _state.value = transform(_state.value)
        save()
    }

    /** 修改某课表的配置：没有独立配置时先从默认配置拷一份再改 */
    fun updateSettings(
        tableId: Long = _state.value.currentTableId,
        transform: (Settings) -> Settings
    ) {
        update { st ->
            val idx = st.tables.indexOfFirst { it.id == tableId }
            if (idx < 0) return@update st
            val cur = st.tables[idx].settings ?: st.settings
            val newTables = st.tables.toMutableList().also {
                it[idx] = it[idx].copy(settings = transform(cur))
            }
            st.copy(tables = newTables)
        }
    }

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private val writeLock = Any()
    private const val SAVE_DEBOUNCE_MS = 150L

    /** 异步 + 防抖写盘：连续多次更新只落盘一次，且不占用主线程（翻页记周等高频更新不掉帧） */
    private fun save() {
        if (!loaded) return
        val snapshot = _state.value
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            writeNow(snapshot)
        }
    }

    /** 立即落盘（App 退后台/退出前调用，避免防抖窗口内丢数据） */
    fun flush() {
        if (!loaded) return
        saveJob?.cancel()
        writeNow(_state.value)
    }

    private fun writeNow(snapshot: AppState) {
        synchronized(writeLock) {
            // 原子落盘：先写同目录临时文件并 fsync，再 rename 覆盖。
            // 进程被杀/断电最多留下一个 .tmp，data.json 不会被截断成半截坏文件（旧 writeText 会）
            val tmp = File(file.parentFile, file.name + ".tmp")
            val result = runCatching {
                val bytes = json.encodeToString(AppState.serializer(), snapshot).toByteArray()
                java.io.FileOutputStream(tmp).use { os ->
                    os.write(bytes)
                    os.fd.sync()
                }
                if (!tmp.renameTo(file)) {
                    // 极个别文件系统 rename 失败时退回直写，保证数据至少落盘
                    file.writeBytes(bytes)
                }
            }
            result.exceptionOrNull()?.let { android.util.Log.e("Repo", "data.json 落盘失败", it) }
            if (tmp.exists()) tmp.delete()
        }
    }

    private var lastId = 0L

    /** 单调递增的唯一 id：同毫秒内连发也不会撞车 */
    fun nextId(): Long {
        var id = System.currentTimeMillis()
        if (id <= lastId) id = lastId + 1
        lastId = id
        return id
    }

    // ---------- 查询辅助 ----------

    fun courseOf(st: AppState, detail: DetailBean): CourseBean? =
        st.courses.firstOrNull { it.id == detail.courseId }

    /** 开学日期（第一周周一） */
    fun startDate(st: AppState, tableId: Long = st.currentTableId): LocalDate? =
        st.tableSettings(tableId).startDate?.let { LocalDate.ofEpochDay(it) }

    /** 某日期属于第几周；未设置开学日期返回 null */
    fun weekOf(st: AppState, date: LocalDate, tableId: Long = st.currentTableId): Int? {
        val start = startDate(st, tableId) ?: return null
        val days = ChronoUnit.DAYS.between(start, date)
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    /** 第 week 周的星期 day（1=周一 ... 7=周日）对应的日期 */
    fun dateOfWeekDay(st: AppState, week: Int, day: Int, tableId: Long = st.currentTableId): LocalDate? {
        val start = startDate(st, tableId) ?: return null
        return start.plusDays(((week - 1) * 7 + (day - 1)).toLong())
    }

    /** 该安排在第 week 周是否有效 */
    fun validInWeek(d: DetailBean, week: Int): Boolean {
        if (week < d.startWeek || week > d.endWeek) return false
        return when (d.type) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }

    /**
     * 某一天要展示的课程卡片（含临时变动处理）。
     * @param applyChanges 是否应用调课/加课/停课记录（课表网格与今日课表都要应用）
     */
    fun dayCourses(st: AppState, date: LocalDate, week: Int, tableId: Long = st.currentTableId): List<ShownCourse> {
        val tableDetails = st.details.filter { it.tableId == tableId }
        val day = date.dayOfWeek.value // 1=周一 ... 7=周日

        var base = tableDetails.filter { it.day == day }
        val shown = ArrayList<ShownCourse>()

        // 调课：fromDate 当天的课整体挪走 → 从当天移除，并在 toDate 当天出现
        val movedOut = st.records.filter { it.type == 0 && it.fromDate == date.toEpochDay() }
        val movedIn = st.records.filter { it.type == 0 && it.toDate == date.toEpochDay() }
        // 停课
        val suspended = st.records.filter { it.type == 2 && it.fromDate == date.toEpochDay() }
        // 临时加课：支持跨多天（endDate）与单双周过滤，旧数据（endDate=0, parity=0）行为不变
        val today = date.toEpochDay()
        val added = st.records.filter { r ->
            r.type == 1 && today >= r.fromDate &&
                    today <= (if (r.endDate > 0L) r.endDate else r.fromDate) &&
                    when (r.parity) {
                        1 -> week % 2 == 1
                        2 -> week % 2 == 0
                        else -> true
                    }
        }

        // 调课：fromDate 当天被挪走的课 → 从当天移除（name 为空 = 当天全部课程整体挪走）
        for (m in movedOut) {
            base = if (m.name.isEmpty()) emptyList()
            else base.filter { courseOf(st, it)?.name != m.name }
        }

        for (s in suspended) {
            base = if (s.name.isEmpty()) emptyList()
            else base.filter { courseOf(st, it)?.name != s.name }
        }

        val showOther = st.tableSettings(tableId).showOtherWeek
        for (d in base) {
            val c = courseOf(st, d) ?: continue
            val other = !validInWeek(d, week)
            // 非本周课程：设置里关掉就不显示；另外"已经上完的"（本周已超过它的结束周）也不再显示
            if (other && (!showOther || d.endWeek < week)) continue
            shown += ShownCourse(d, c, other, null)
        }

        // 被挪到今天的课：取“原日期那天”本该上的课
        for (r in movedIn) {
            val from = LocalDate.ofEpochDay(r.fromDate)
            val fromWeek = weekOf(st, from, tableId) ?: 1
            for (d in tableDetails.filter { it.day == from.dayOfWeek.value && validInWeek(it, fromWeek) }) {
                val c = courseOf(st, d) ?: continue
                // 指定课程名的调课只挪这一门，并采用目标节次
                if (r.name.isNotEmpty() && c.name != r.name) continue
                val nd = if (r.name.isNotEmpty() && r.startNode >= 1) {
                    d.copy(startNode = r.startNode, step = r.step)
                } else d
                shown += ShownCourse(nd, c, false, r)
            }
        }

        // 临时加课
        for (r in added) {
            val ghost = CourseBean(id = -r.id, name = r.name, color = 3, tableId = tableId)
            val d = DetailBean(
                id = -r.id, courseId = ghost.id, day = day, room = r.room,
                teacher = r.teacher, startNode = r.startNode, step = r.step
            )
            shown += ShownCourse(d, ghost, false, r)
        }

        return shown
    }
}

/** 网格/今日课表里展示的一张卡片 */
data class ShownCourse(
    val detail: DetailBean,
    val course: CourseBean,
    val isOther: Boolean,
    val record: ChangeRecord?
)
