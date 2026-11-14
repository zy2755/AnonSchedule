package com.anon.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 小组件的「到点自刷」闹钟。
 *
 * 系统给的 updatePeriodMillis 在 MIUI / HyperOS 上经常不投递（App 进程被杀之后更明显），
 * 结果就是跨过零点日期一直停在昨天，必须手动点一下才更新。
 * 这里自己排一条链式闹钟：每次只排「最近的一个边界」——今天每节课的开始 / 结束时刻，
 * 或者明天的 00:00:10——响一次就刷新所有小组件，并把下一个边界接着排上。
 */
object WidgetRefresh {

    const val ACTION = "com.anon.schedule.action.WIDGET_REFRESH"

    private const val REQUEST = 0x7766

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = nextBoundary(ctx) ?: return
        val pi = pending(ctx)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val ok = runCatching {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                // 没有精确闹钟权限时允许系统推迟几分钟：日期偶尔晚几分钟变，总比不刷好
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        }.isSuccess
        if (!ok) runCatching { am.set(AlarmManager.RTC_WAKEUP, next, pi) }
    }

    /** 下一个需要重画的时刻：今天还没到的上课 / 下课点，和明天的零点之后 */
    private fun nextBoundary(ctx: Context): Long? {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val midnight = today.plusDays(1).atStartOfDay(zone).plusSeconds(10).toInstant().toEpochMilli()

        val courseMarks = runCatching {
            WidgetData.today(WidgetData.load(ctx))
                .flatMap { listOfNotNull(it.start, it.end) }
        }.getOrElse { emptyList<LocalTime>() }
            .map { today.atTime(it).atZone(zone).toInstant().toEpochMilli() }
            .filter { it > nowMs + 30_000 }

        return (courseMarks + midnight).minOrNull()
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            REQUEST,
            Intent(ctx, WidgetRefreshReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** 到点刷新一次小组件；下一个边界由 [Widgets.refreshAll] 顺手接上 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Widgets.refreshAll(context)
    }
}
