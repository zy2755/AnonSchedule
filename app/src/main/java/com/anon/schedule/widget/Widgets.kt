package com.anon.schedule.widget

import android.content.Context

/** 统一刷新入口：App 退后台 / 数据变动后调用，把所有小组件都更新一遍 */
object Widgets {
    fun refreshAll(ctx: Context) {
        TodaySmallWidget.refresh(ctx)
        TodayLargeWidget.refresh(ctx)
        TodayWideWidget.refresh(ctx)
        TodayTomorrowWidget.refresh(ctx)
    }
}
