package com.anon.schedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anon.schedule.data.Repo
import com.anon.schedule.notify.TodoReminder

/**
 * 「待办 DDL」小组件里点小勾：直接把这条待办标记完成。
 *
 * 小组件进程可能没经过 MainActivity，先 [Repo.init] 从磁盘加载，
 * 改完立刻 [Repo.flush] 落盘（广播处理完进程随时可能被回收），
 * 再撤掉它的到期提醒并重画桌面。
 */
class TodoWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        if (id == 0L) return

        Repo.init(context)
        Repo.update { st ->
            st.copy(todos = st.todos.map { if (it.id == id) it.copy(done = true) else it })
        }
        Repo.flush()
        TodoReminder.scheduleAll(context)
        Widgets.refreshAll(context)
    }

    companion object {
        const val ACTION_DONE = "com.anon.schedule.action.WIDGET_TODO_DONE"
        const val EXTRA_ID = "widget_todo_id"
    }
}
