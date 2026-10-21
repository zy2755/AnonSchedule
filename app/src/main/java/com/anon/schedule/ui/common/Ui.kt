package com.anon.schedule.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.anon.schedule.ui.theme.LocalGlass
import com.anon.schedule.ui.theme.LocalHasBackground

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
    val shape = RoundedCornerShape(20.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val darkCard = surfaceColor.luminance() < 0.5f
    // 卡片底色不透明度：有背景图时更实（框与文字才看得清），无背景图时保留玻璃通透感
    val bgAlpha = when {
        !glass -> 1f
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
    Surface(
        shape = shape,
        color = Color.Transparent,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier
                // 先铺「托底」底色，玻璃开启时再叠一层白色高光 + 描边（先画的在下层）
                .background(brush = Brush.verticalGradient(listOf(bg, bg)), shape = shape)
                .then(
                    if (glass) Modifier
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
