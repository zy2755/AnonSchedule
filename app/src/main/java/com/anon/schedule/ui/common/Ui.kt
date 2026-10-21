package com.anon.schedule.ui.common

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anon.schedule.ui.theme.LocalBackdrop
import com.anon.schedule.ui.theme.LocalGlass
import com.anon.schedule.ui.theme.LocalHasBackground
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/** 课程卡片配色 */
val courseColors = listOf(
    Color(0xFFFF8FBB), Color(0xFF7FA8FF), Color(0xFF6FD6B8),
    Color(0xFFFFC46B), Color(0xFFB99CFF), Color(0xFFFF7A7A),
    Color(0xFF5EC8E8), Color(0xFF9CD46A)
)

fun courseColor(index: Int): Color = courseColors[((index % courseColors.size) + courseColors.size) % courseColors.size]

/** 液态玻璃开启时的半透明卡片；关闭时是普通卡片 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlass.current
    val hasBg = LocalHasBackground.current
    // 真玻璃采样源：AppRoot 录好的「背景图 + 遮罩」图层，卡片只从它取像素
    val backdrop = LocalBackdrop.current
    val shape = RoundedCornerShape(20.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val darkCard = surfaceColor.luminance() < 0.5f
    // API 31 起 RenderEffect 才成立；更低版本退回原来的半透明底色
    val realGlass = glass && backdrop != null && Build.VERSION.SDK_INT >= 31
    // 卡片底色不透明度：真玻璃时留得更透（模糊 + 折射透出来），无背景图时也保留通透感
    val bgAlpha = when {
        !glass -> 1f
        realGlass -> if (hasBg) 0.62f else 0.50f
        hasBg -> 0.86f
        else -> 0.60f
    }
    val bg = surfaceColor.copy(alpha = bgAlpha)
    // 描边：深色卡片用白色高光；浅色卡片改用 outline 灰线，保证「框」在浅底上也看得见
    val borderBrush = if (darkCard) Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f))
    ) else Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.outline.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    )
    // 玻璃光学层：模糊 → 提亮 → 边缘折射/色散（API 33+），外圈高光 + 投影由库自绘
    val glassModifier = if (realGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(radius = 8.dp.toPx())
                if (Build.VERSION.SDK_INT >= 33) {
                    lens(
                        refractionHeight = 14.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                }
            },
            highlight = {
                Highlight(
                    width = 0.9.dp,
                    blurRadius = 0.5.dp,
                    style = HighlightStyle.Default(intensity = if (darkCard) 0.46f else 0.55f)
                )
            },
            shadow = { Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.16f)) },
            // 底色画在「折射层之上、内容之下」，否则会被模糊层盖掉
            onDrawSurface = { drawRect(bg) }
        )
    } else {
        Modifier
    }
    Surface(
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier
                // 先画玻璃采样层（含底色），非玻璃模式再补半透明底色；玻璃关闭或无采样源时只剩底色 + 高光 + 描边
                .then(glassModifier)
                .then(
                    if (realGlass) Modifier
                    else Modifier.background(brush = Brush.verticalGradient(listOf(bg, bg)), shape = shape)
                )
                .then(
                    if (glass && !realGlass) Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                            ),
                            shape = shape
                        )
                        .border(width = 1.dp, brush = borderBrush, shape = shape)
                    else Modifier
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** 设置项一行 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

/** 沉浸式页面标题：透明底 + 大标题 + 返回键（参考项目设置页顶部） */
@Composable
fun PageHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 分组卡片风（参考 zhengfang）：小标题 + 圆角卡片 + 彩色图标块 */
val TileBlue = Color(0xFF4C8DFF)
val TileGreen = Color(0xFF31C48D)
val TilePurple = Color(0xFF8B6BF5)
val TileOrange = Color(0xFFF5A524)
val TilePink = Color(0xFFE8629B)
val TileRed = Color(0xFFEF5350)
val TileCyan = Color(0xFF22B8CF)

/** 分组小标题（写在卡片外面） */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        modifier = modifier.padding(start = 26.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** 分组圆角卡片：深色模式用 surfaceVariant 提亮，浅色模式用 surface 白底 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(
                if (dark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surface
            )
            // 浅色底和背景太接近，补一条细描边让卡片边界看得见
            .then(
                if (dark) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape)
            ),
        content = content
    )
}

/** 卡片内一行：彩色圆角图标块 + 标题/副标题 + 尾部件（默认箭头） */
@Composable
fun GroupRow(
    icon: ImageVector,
    tileColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 13.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        trailing()
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
    }
}

/** iOS 风绿色胶囊开关（参考项目的开关样式） */
@Composable
fun PillSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF34C759),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

/** 纯文字设置行（左：标题+说明，右：值/开关），照 WakeUp 样式 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextRow(
    title: String,
    desc: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    onClick != null && onLongClick != null ->
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
            .padding(vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        trailing()
    }
}

/** 数字选择弹窗（点数字可直接键盘输入） */
@Composable
fun NumberPickerDialog(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf(value) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(value.toString()) }
    val focusRequester = remember { FocusRequester() }

    fun commitInput() {
        input.toIntOrNull()?.let { current = it.coerceIn(range) }
        input = current.toString()
        editing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (editing) commitInput()
                        current = (current - 1).coerceIn(range)
                        input = current.toString()
                    }) { Text("−") }
                    if (editing) {
                        // 键盘直输模式：数字键盘，实时夹到合法范围
                        BasicTextField(
                            value = input,
                            onValueChange = { s ->
                                input = s.filter { it.isDigit() }.take(4)
                                input.toIntOrNull()?.let { current = it.coerceIn(range) }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { commitInput() }),
                            modifier = Modifier
                                .width(96.dp)
                                .focusRequester(focusRequester)
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    } else {
                        Text(
                            "$current$suffix",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    input = current.toString()
                                    editing = true
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                    TextButton(onClick = {
                        if (editing) commitInput()
                        current = (current + 1).coerceIn(range)
                        input = current.toString()
                    }) { Text("＋") }
                    Text("(${range.first}-${range.last})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "点数字可直接输入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (editing) commitInput()
                onConfirm(current)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    )
}

@Composable
fun VerticalSpacer(height: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height.dp))
}
