package com.anon.schedule.widget

import android.content.Context

/** 统一刷新入口：App 退后台 / 数据变动后调用，把所有小组件都更新一遍 */
object Widgets {
    fun refreshAll(ctx: Context) {
        TodaySmallWidget.refresh(ctx)
        TodayLargeWidget.refresh(ctx)
        TodayWideWidget.refresh(ctx)
        TodayTomorrowWidget.refresh(ctx)
        // 刷完接着排下一个边界：跨零点、上下课都会自动再刷一次
        WidgetRefresh.scheduleNext(ctx)
    }
}
