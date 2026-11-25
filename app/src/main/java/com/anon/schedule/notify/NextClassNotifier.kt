package com.anon.schedule.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.anon.schedule.MainActivity
import com.anon.schedule.R
import com.anon.schedule.data.tableSettings
import com.anon.schedule.widget.WidgetData
import java.time.LocalDate
import java.time.LocalTime

/**
 * 通知栏常驻「下节课」。
 *
 * 不自己排闹钟：复用小组件的到点自刷链路（[com.anon.schedule.widget.Widgets.refreshAll]
 * 会在上下课、跨零点时被叫醒），每次刷新时重算一遍内容即可。
 * 关闭开关时调用 [cancel] 撤掉通知，渠道保留在系统设置里（用户可单独静音）。
 */
object NextClassNotifier {

    const val CHANNEL_ID = "next_class"
    private const val NOTIFY_ID = 9001

    fun refresh(ctx: Context) {
        val st = WidgetData.load(ctx)
        if (!st.tableSettings().nextClassNotifyEnabled) {
            cancel(ctx)
            return
        }
        if (!ClassReminder.canPostNotifications(ctx)) return

        val today = LocalDate.now()
        val now = LocalTime.now()
        val next = WidgetData.currentOrNext(WidgetData.day(st, today), now)

        val title: String
        val text: String
        if (next != null) {
            title = WidgetData.statusOf(next, now) + "：" + next.name
            text = listOf(next.timeText, WidgetData.subText(next))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        } else {
            // 今天的课上完了（或今天没课）：改成提示明天第一节
            val first = WidgetData.day(st, today.plusDays(1)).minByOrNull { it.startNode }
            if (first == null) {
                cancel(ctx)
                return
            }
            title = "明天第一节：" + first.name
            text = listOf(first.timeText, WidgetData.subText(first))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }

        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(launchIntent(ctx))
            .build()
        runCatching { nm.notify(NOTIFY_ID, n) }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID)
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "下节课", NotificationManager.IMPORTANCE_LOW).apply {
                description = "通知栏常驻显示下一节课，上下课自动更新"
                setShowBadge(false)
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
}
