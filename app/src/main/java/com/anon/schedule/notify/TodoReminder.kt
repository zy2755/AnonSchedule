package com.anon.schedule.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anon.schedule.MainActivity
import com.anon.schedule.R
import com.anon.schedule.data.CourseTodo
import com.anon.schedule.data.TodoLogic
import com.anon.schedule.widget.WidgetData
import java.time.LocalDate
import java.time.ZoneId

/**
 * 待办到期提醒：为设有截止时间的待办排一条精确闹钟，到点发通知。
 *
 * 排程与「上课提醒」分开（不同渠道、不同 prefs），互不覆盖；
 * 待办新增/改期/勾掉/删除后由调用方重新 [scheduleAll] 一次。
 */
object TodoReminder {

    const val CHANNEL_ID = "todo_reminder"

    /** 一次只排未来这么多天，避免一次塞几百个闹钟 */
    private const val HORIZON_DAYS = 30L

    private const val PREFS = "todo_reminder_alarms"
    private const val KEY_CODES = "codes"

    private const val EXTRA_ID = "todo_id"
    private const val EXTRA_TEXT = "todo_text"
    private const val EXTRA_DUE = "todo_due"

    /** 把待办 id 稳定地映射成 PendingIntent 的 requestCode */
    private fun codeOf(todo: CourseTodo): Int =
        ((todo.id xor (todo.id ushr 32)) and 0x7FFFFFFF).toInt()

    /** 按当前待办清单重排未来的到期提醒；没有需要提醒的等价于全部清空 */
    fun scheduleAll(ctx: Context) {
        cancelAll(ctx)
        val st = WidgetData.load(ctx)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val limit = now + HORIZON_DAYS * 24 * 60 * 60 * 1000L
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val codes = mutableSetOf<String>()

        st.todos.filter { it.remind && !it.done && it.dueDate > 0L }.forEach { todo ->
            val triggerAt = TodoLogic.dueMillis(todo, zone) ?: return@forEach
            if (triggerAt <= now || triggerAt > limit) return@forEach
            val code = codeOf(todo)
            val dueText = TodoLogic.dueLabel(todo, LocalDate.now(zone))
            val pi = PendingIntent.getBroadcast(
                ctx, code, intentOf(ctx, todo, dueText),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (setAlarm(am, triggerAt, pi)) codes += code.toString()
        }

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CODES, codes).apply()
    }

    /** 撤掉之前排过的全部待办提醒 */
    fun cancelAll(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        if (codes.isEmpty()) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        codes.forEach { key ->
            val code = key.toIntOrNull() ?: return@forEach
            // filterEquals 只比 action / 组件 / requestCode，不带 extras 也能取回原 PendingIntent
            val pi = PendingIntent.getBroadcast(
                ctx, code, intentOf(ctx, null, ""),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am?.cancel(pi)
                pi.cancel()
            }
        }
        prefs.edit().remove(KEY_CODES).apply()
    }

    private fun setAlarm(am: AlarmManager, triggerAt: Long, pi: PendingIntent): Boolean {
        val exact = Build.VERSION.SDK_INT < 31 || runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        if (exact) {
            val ok = runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }.isSuccess
            if (ok) return true
        }
        return runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }.isSuccess
    }

    /** 闹钟到点：发通知。文案从 extras 里取，课程名现查（课程可能已经改过名） */
    fun showFromIntent(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val todoId = intent.getLongExtra(EXTRA_ID, 0L)
        val due = intent.getStringExtra(EXTRA_DUE).orEmpty()
        val courseName = runCatching {
            val st = WidgetData.load(ctx)
            st.courses.firstOrNull { it.id == st.todos.firstOrNull { t -> t.id == todoId }?.courseId }?.name
        }.getOrNull()
        if (!ClassReminder.canPostNotifications(ctx)) return
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val body = listOfNotNull(courseName?.takeIf { it.isNotBlank() }, due.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifBlank { "待办到期了" }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setContentText(body)
            .setSubText("待办提醒")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(launchIntent(ctx))
            .build()
        val notifyId = ((todoId xor (todoId ushr 32)) and 0x7FFFFFFF).toInt()
        runCatching { nm.notify(notifyId, n) }
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "待办提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "待办到达截止时间时提醒"
                enableVibration(true)
            }
        )
    }

    private fun launchIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intentOf(ctx: Context, todo: CourseTodo?, dueText: String): Intent =
        Intent(ctx, TodoReminderReceiver::class.java).apply {
            action = TodoReminderReceiver.ACTION_REMIND
            if (todo != null) {
                putExtra(EXTRA_ID, todo.id)
                putExtra(EXTRA_TEXT, todo.text)
                putExtra(EXTRA_DUE, dueText)
            }
        }
}

/** 待办闹钟到点 */
class TodoReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMIND = "com.anon.schedule.action.TODO_REMIND"
    }

    override fun onReceive(context: Context, intent: Intent) {
        TodoReminder.showFromIntent(context, intent)
        // 提醒发完顺手重排，把后面 30 天接着排上
        TodoReminder.scheduleAll(context)
    }
}
