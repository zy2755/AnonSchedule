package com.anon.schedule.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anon.schedule.MainActivity
import com.anon.schedule.R
import com.anon.schedule.data.tableSettings
import com.anon.schedule.widget.WidgetData
import java.time.LocalDate
import java.time.ZoneId

/**
 * 上课前提醒：按当天课程排精确闹钟，到点弹普通通知。
 *
 * 「上超级岛」（澎湃OS 焦点通知）需要小米推送的焦点通知权限，个人开发者主体拿不到，
 * 所以这里把通知内容（[ReminderContent]）和发送（[show]）单独隔开：
 * 以后拿到权限，只需要换掉 [show] 的实现，排程与内容组装都不用动。
 */
object ClassReminder {

    const val CHANNEL_ID = "class_reminder"

    /** 提前量可选值（分钟） */
    val LEAD_OPTIONS = listOf(5, 10, 15, 30)

    /** 一次只排未来这么多天：每次闹钟响起后整体重排，窗口自动前移，长期只占几十个闹钟 */
    private const val HORIZON_DAYS = 7

    private const val PREFS = "class_reminder_alarms"
    private const val KEY_CODES = "codes"

    private const val EXTRA_ID = "reminder_id"
    private const val EXTRA_NAME = "reminder_name"
    private const val EXTRA_TIME = "reminder_time"
    private const val EXTRA_ROOM = "reminder_room"
    private const val EXTRA_LEAD = "reminder_lead"

    /** 一条待发提醒的内容：内容与发送解耦，方便以后换焦点通知（超级岛） */
    data class ReminderContent(
        val id: Int,
        val name: String,
        val timeText: String,
        val roomText: String,
        val leadMinutes: Int
    )

    // ---------- 权限 ----------

    fun canPostNotifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** 跳系统页让用户放开「闹钟和提醒」 */
    fun requestExactAlarmPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ---------- 排程 ----------

    /**
     * 按当前课表重排未来一周的提醒。
     * 内部会先撤掉旧闹钟；未开启提醒时等价于全部清空。
     */
    fun scheduleAll(ctx: Context) {
        cancelAll(ctx)
        // 闹钟可能在 App 进程被杀后到达，这里顺手保证数据已从磁盘读入
        val st = WidgetData.load(ctx)
        val settings = st.tableSettings()
        if (!settings.reminderEnabled) return
        val lead = settings.reminderLeadMinutes.coerceAtLeast(1)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val codes = mutableSetOf<String>()

        for (offset in 0 until HORIZON_DAYS) {
            val date = today.plusDays(offset.toLong())
            WidgetData.day(st, date).forEachIndexed { idx, course ->
                val start = course.start ?: return@forEachIndexed
                val triggerAt = date.atTime(start).atZone(zone).toInstant().toEpochMilli() -
                    lead * 60_000L
                if (triggerAt <= now) return@forEachIndexed

                val code = date.toEpochDay().toInt() * 100 + idx
                val content = ReminderContent(
                    id = code,
                    name = course.name,
                    timeText = course.timeText,
                    roomText = course.roomText,
                    leadMinutes = lead
                )
                val pi = PendingIntent.getBroadcast(
                    ctx, code, intentOf(ctx, content),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (setAlarm(am, triggerAt, pi)) codes += code.toString()
            }
        }

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CODES, codes).apply()
    }

    /** 撤掉之前排过的全部提醒 */
    fun cancelAll(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        if (codes.isEmpty()) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        codes.forEach { key ->
            val code = key.toIntOrNull() ?: return@forEach
            // 取回原来的 PendingIntent：filterEquals 只比 action/组件，不带 extras 也能命中
            val pi = PendingIntent.getBroadcast(
                ctx, code, intentOf(ctx, null),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am?.cancel(pi)
                pi.cancel()
            }
        }
        prefs.edit().remove(KEY_CODES).apply()
    }

    /** 精确闹钟被系统限制时退回非精确：会晚几分钟，但总比不响好 */
    private fun setAlarm(am: AlarmManager, triggerAt: Long, pi: PendingIntent): Boolean {
        if (canScheduleExact(am)) {
            val ok = runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }.isSuccess
            if (ok) return true
        }
        return runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }.isSuccess
    }

    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    // ---------- 发送 ----------

    fun contentOf(intent: Intent): ReminderContent? {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val name = intent.getStringExtra(EXTRA_NAME) ?: return null
        return ReminderContent(
            id = id,
            name = name,
            timeText = intent.getStringExtra(EXTRA_TIME).orEmpty(),
            roomText = intent.getStringExtra(EXTRA_ROOM).orEmpty(),
            leadMinutes = intent.getIntExtra(EXTRA_LEAD, 0)
        )
    }

    /** 唯一的发送出口：以后接入澎湃OS 焦点通知（超级岛）只改这里 */
    fun show(ctx: Context, content: ReminderContent) {
        if (!canPostNotifications(ctx)) return
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(content.id, buildNotification(ctx, content).build()) }
    }

    private fun buildNotification(ctx: Context, c: ReminderContent) =
        NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.name)
            .setContentText(
                listOf(c.timeText, c.roomText).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "马上要上课了" }
            )
            .setSubText(if (c.leadMinutes > 0) "${c.leadMinutes} 分钟后上课" else "马上上课")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(launchIntent(ctx))

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "每节课开始前提醒"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun launchIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intentOf(ctx: Context, content: ReminderContent?): Intent =
        Intent(ctx, ClassReminderReceiver::class.java).apply {
            action = ClassReminderReceiver.ACTION_REMIND
            if (content != null) {
                putExtra(EXTRA_ID, content.id)
                putExtra(EXTRA_NAME, content.name)
                putExtra(EXTRA_TIME, content.timeText)
                putExtra(EXTRA_ROOM, content.roomText)
                putExtra(EXTRA_LEAD, content.leadMinutes)
            }
        }
}
