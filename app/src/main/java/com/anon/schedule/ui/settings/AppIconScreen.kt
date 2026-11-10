package com.anon.schedule.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.R
import com.anon.schedule.data.AppIcon
import com.anon.schedule.data.AppIcons
import com.anon.schedule.data.Repo
import com.anon.schedule.ui.common.GroupCard
import com.anon.schedule.ui.common.GroupLabel
import com.anon.schedule.ui.common.PageHeader
import com.anon.schedule.ui.common.cardSecondaryColor

@Composable
fun AppIconScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 以系统里实际启用的 alias 为准，保证从别的设备恢复备份后也能看到真实状态
    var selected by remember { mutableStateOf(AppIcons.current(context)) }

    fun choose(icon: AppIcon) {
        if (icon == selected) return
        if (AppIcons.apply(context, icon)) {
            selected = icon
            Repo.update { st -> st.copy(settings = st.settings.copy(appIcon = icon.id)) }
            Toast.makeText(context, "已切换图标，回桌面看看（部分桌面要几秒才刷新）", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "切换失败，稍后再试一次", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = { PageHeader("软件图标", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            GroupLabel("内置图标")
            GroupCard {
                AppIcon.entries.forEach { icon ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choose(icon) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(iconRes(icon)),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 13.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                icon.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                icon.desc,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                color = cardSecondaryColor()
                            )
                        }
                        if (icon == selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "当前图标",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            GroupLabel("说明")
            GroupCard {
                Text(
                    "桌面图标只能在 App 里预置的这几款之间切换——安卓不允许运行时把图标换成相册里的任意图片。\n\n" +
                        "切换实际做的是启用 / 禁用清单里对应的启动入口，所以桌面会有一小会儿刷新；" +
                        "MIUI / 澎湃OS 等定制桌面可能要回桌面等几秒才会更新，个别桌面需要重启桌面进程。\n\n" +
                        "选中项会保存在本机，重启手机或覆盖安装后依然保持。",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = cardSecondaryColor(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun iconRes(icon: AppIcon): Int = when (icon) {
    AppIcon.Default -> R.drawable.ic_launcher
    AppIcon.Sky -> R.drawable.ic_launcher_sky
    AppIcon.Mint -> R.drawable.ic_launcher_mint
    AppIcon.Amber -> R.drawable.ic_launcher_amber
}
