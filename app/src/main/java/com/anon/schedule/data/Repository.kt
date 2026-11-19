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
        alignCurrentWeek()
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

    /**
     * 启动时把「当前周」校准到真实周次。
     *
     * 翻页会记住看的是第几周（写进 settings.currentWeek），这样下次打开还停在上次
     * 退出时翻到的周，容易让人把"屏幕上的那一周"当成"这一周"。这里按开学日期
     * 重新算一遍今天的真实周次覆盖掉它；没设开学日期的课表保持原样。
     */
    private fun alignCurrentWeek() {
        val st = _state.value
        val today = LocalDate.now()
        fun aligned(s: Settings): Settings {
            val start = s.startDate ?: return s
            val week = CourseLogic.weekOf(LocalDate.ofEpochDay(start), today)
                .coerceIn(1, s.maxWeek.coerceAtLeast(1))
            return if (s.currentWeek == week) s else s.copy(currentWeek = week)
        }
        val newGlobal = aligned(st.settings)
        val newTables = st.tables.map { t ->
            t.settings?.let { ts -> t.copy(settings = aligned(ts)) } ?: t
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

    /** 导出：把当前全部数据（课表、课程、设置、成绩）转成一个 JSON 文本 */
    fun exportJson(): String = json.encodeToString(AppState.serializer(), _state.value)

    /**
     * 导入：整体替换当前数据。
     * 解析失败或文件里根本没有课表时直接报错，不会把现有数据清空。
     */
    fun restoreJson(text: String): Result<Unit> = runCatching {
        val decoded = json.decodeFromString(AppState.serializer(), text)
        require(decoded.tables.isNotEmpty()) { "备份文件里没有课表数据" }
        _state.value = repairIds(decoded)
        ensureStartDate()
        alignCurrentWeek()
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
        return CourseLogic.weekOf(start, date)
    }

    /** 第 week 周的星期 day（1=周一 ... 7=周日）对应的日期 */
    fun dateOfWeekDay(st: AppState, week: Int, day: Int, tableId: Long = st.currentTableId): LocalDate? {
        val start = startDate(st, tableId) ?: return null
        return CourseLogic.dateOfWeekDay(start, week, day)
    }

    /** 该安排在第 week 周是否有效 */
    fun validInWeek(d: DetailBean, week: Int): Boolean = CourseLogic.validInWeek(d, week)

    /**
     * 某一天要展示的课程卡片（含临时变动处理）。
     * 具体规则见 [CourseLogic.dayCourses]，这里只负责把当前课表的配置取出来喂进去。
     */
    fun dayCourses(st: AppState, date: LocalDate, week: Int, tableId: Long = st.currentTableId): List<ShownCourse> =
        CourseLogic.dayCourses(
            st = st,
            date = date,
            week = week,
            tableId = tableId,
            startDate = startDate(st, tableId),
            showOtherWeek = st.tableSettings(tableId).showOtherWeek
        )

    /**
     * 展示用的一天课程：在 [dayCourses] 基础上按课表网格的叠放规则去掉被覆盖的原课——
     * 调课/加课卡优先占位，同一节次上的当天原课不再展示（撤销变动后自动恢复）。
     * 「今日课表」页与桌面小组件都用这个口径，避免网格里已隐藏的课还出现在别处。
     */
    fun displayDayCourses(st: AppState, date: LocalDate, week: Int, tableId: Long = st.currentTableId): List<ShownCourse> =
        CourseLogic.displayDayCourses(
            st = st,
            date = date,
            week = week,
            tableId = tableId,
            startDate = startDate(st, tableId),
            showOtherWeek = st.tableSettings(tableId).showOtherWeek
        )
}

/** 网格/今日课表里展示的一张卡片 */
data class ShownCourse(
    val detail: DetailBean,
    val course: CourseBean,
    val isOther: Boolean,
    val record: ChangeRecord?
)
