package com.anon.schedule.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 内置启动图标。
 *
 * 安卓不允许在运行时把桌面图标换成用户自选的任意图片，只能在清单里预先声明多个
 * activity-alias（一款图标一个），再通过启用 / 禁用对应组件来切换。
 * 这里的 alias 名字必须和 AndroidManifest.xml 里的一致。
 */
enum class AppIcon(val id: Int, val alias: String, val label: String, val desc: String) {
    Default(0, "LauncherDefault", "原版插画", "天空与课程表插画（默认）"),
    Sky(1, "LauncherSky", "天空蓝", "蓝底课程表卡片"),
    Mint(2, "LauncherMint", "薄荷绿", "绿底课程表卡片"),
    Amber(3, "LauncherAmber", "暖橙", "橙底课程表卡片");

    fun component(context: Context): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.$alias")

    companion object {
        fun of(id: Int): AppIcon = entries.firstOrNull { it.id == id } ?: Default
    }
}

object AppIcons {

    /** 某个 alias 现在是否启用；DEFAULT 状态按清单里的 android:enabled 算（只有原版默认开启） */
    private fun isEnabled(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        return when (pm.getComponentEnabledSetting(icon.component(context))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> icon == AppIcon.Default
        }
    }

    /** 当前生效的图标：以系统组件状态为准（重启 / 覆盖安装后仍然有效） */
    fun current(context: Context): AppIcon =
        AppIcon.entries.firstOrNull { isEnabled(context, it) } ?: AppIcon.Default

    /**
     * 切换图标：先启用目标 alias、再禁用其余 alias。
     * 顺序是有意为之——中途宁可短暂出现两个入口，也不要出现「一个入口都没有」的空档。
     */
    fun apply(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        return runCatching {
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            AppIcon.entries.forEach { other ->
                if (other != icon) {
                    pm.setComponentEnabledSetting(
                        other.component(context),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }.isSuccess
    }

    /**
     * 启动时对齐持久化的选中项：只有系统状态和目标不一致才动手，
     * 避免每次冷启动都去改组件、白白触发一次桌面刷新。
     */
    fun sync(context: Context, id: Int) {
        val desired = AppIcon.of(id)
        val enabled = AppIcon.entries.filter { isEnabled(context, it) }
        if (enabled.size == 1 && enabled.first() == desired) return
        apply(context, desired)
    }
}
