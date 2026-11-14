package com.anon.schedule.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anon.schedule.widget.Widgets

/** 闹钟到点：弹一条提醒，并把后面一周的排程顺延 */
class ClassReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMIND = "com.anon.schedule.action.CLASS_REMIND"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val content = ClassReminder.contentOf(intent) ?: return
        ClassReminder.show(context, content)
        ClassReminder.scheduleAll(context)
    }
}

/** 开机 / 更新安装 / 改时间后重排提醒（关机期间错过的闹钟不会自动补） */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClassReminder.scheduleAll(context)
        // 顺便把小组件刷一次并接上自刷闹钟，避免关机跨天之后还停在旧日期
        Widgets.refreshAll(context)
    }
}
