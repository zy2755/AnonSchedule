package com.anon.schedule.ui.common

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs
import kotlin.math.roundToInt
import com.anon.schedule.ui.theme.LocalBackdrop
import com.anon.schedule.ui.theme.LocalCardAlpha
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
    // 列表型卡片（一组可点的行）把上下内边距交给自己，行与卡片边缘的距离才不会被「叠加」成一大块空白
    contentPadding: PaddingValues = PaddingValues(16.dp),
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
    // 卡片底色不透明度：有背景图时也要保住文字对比度，模糊和折射交给玻璃层，别指望底色透出来
    val bgAlpha = when {
        !glass -> 1f
        realGlass -> if (hasBg) 0.85f else 0.50f
        hasBg -> 0.86f
        else -> 0.60f
    } * LocalCardAlpha.current
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
                    when {
                        glass && !realGlass -> Modifier
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                                ),
                                shape = shape
                            )
                            .border(width = 1.dp, brush = borderBrush, shape = shape)
                        // 深色实心卡片落在纯黑底上，补一条极淡白描边托住边界
                        !glass && darkCard -> Modifier
                            .border(width = 1.dp, color = Color.White.copy(alpha = 0.06f), shape = shape)
                        else -> Modifier
                    }
                )
                .padding(contentPadding),
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
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
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = cardSecondaryColor()
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
// 原来这里的高饱和粉换成石墨灰：全站黑白化后不再有品牌粉，但彩色图标块仍保留其余色相做区分
val TilePink = Color(0xFF4A4A52)
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
            // 深色模式改纯黑底后卡片要自己「立」起来：实心中性灰 + 极淡白描边
            .background(
                (if (dark) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface).copy(alpha = LocalCardAlpha.current)
            )
            .then(
                if (dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape)
            ),
        content = content
    )
}

/** 卡片说明文字颜色：深色模式用 60% 白，浅色模式回落到主题次级色 */
@Composable
fun cardSecondaryColor(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(horizontal = 14.dp, vertical = 13.dp)
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
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 主标题加字重、副标题压到 60% 白并拉大行高：深色底上层次更清楚，也不挤
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = cardSecondaryColor(),
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

/** iOS 风胶囊开关：轨道更饱满、滑块更大，开启态用更鲜亮的绿色 */
@Composable
fun PillSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val trackOn = if (dark) Color(0xFF32D74B) else Color(0xFF28C76F)
    val trackOff = if (dark) Color(0xFF3A3A3E) else Color(0xFFE4E4E8)
    val track by animateColorAsState(
        targetValue = if (checked) trackOn else trackOff,
        animationSpec = tween(180),
        label = "switchTrack"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "switchThumb"
    )
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .clip(shape)
            .background(track)
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                shape
            )
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(28.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 加粗轨道 + 渐变填充的滑块：未填充部分用明显更深的中性底色，
 * 填充从品牌色淡到品牌色实，滑块是白色实心 + 品牌色描边。
 */
@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // 未填充轨道在深色下是深灰、浅色下是浅灰：和填充渐变有一眼可见的明暗差
    val trackBase = if (dark) Color(0xFF2A2A2E) else Color(0xFFE4E4E8)
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range <= 0f) 0f else ((value - valueRange.start) / range).coerceIn(0f, 1f)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val padPx = with(LocalDensity.current) { 12.dp.toPx() }
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)

    // 有档位时取最近的一档（周数滑块要逐周吸附）
    fun snap(v: Float): Float {
        if (steps <= 0 || range <= 0f) return v
        val segments = steps + 1
        val f = ((v - valueRange.start) / range).coerceIn(0f, 1f)
        return valueRange.start + (f * segments).roundToInt() / segments.toFloat() * range
    }

    fun setFromX(x: Float) {
        if (widthPx <= 0f) return
        // 轨道左右各留了一个拇指半径的内缩，触摸换算要减掉，否则贴边拖会差一截
        val usable = (widthPx - padPx * 2).coerceAtLeast(1f)
        val f = ((x - padPx) / usable).coerceIn(0f, 1f)
        onChange(snap(valueRange.start + f * range))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(valueRange, steps) {
                // 点按和拖动合并成一个手势循环：点哪跳哪，横向移动超过 touchSlop 后连续跟手
                awaitEachGesture {
                    val down = awaitFirstDown()
                    setFromX(down.position.x)
                    var dragging = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: break
                        if (!dragging && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            setFromX(change.position.x)
                            change.consume()
                        }
                    }
                    onFinished?.invoke()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackH = 10.dp.toPx()
            val thumbR = 10.dp.toPx()
            val cy = size.height / 2f
            val pad = thumbR + 2.dp.toPx()
            val trackW = (size.width - 2 * pad).coerceAtLeast(1f)
            val top = cy - trackH / 2f
            val corner = CornerRadius(trackH / 2f)
            drawRoundRect(
                color = trackBase,
                topLeft = Offset(pad, top),
                size = Size(trackW, trackH),
                cornerRadius = corner
            )
            val fillW = (trackW * fraction).coerceAtLeast(if (fraction > 0f) trackH else 0f)
            if (fillW > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(primary.copy(alpha = 0.55f), primary),
                        startX = pad,
                        endX = pad + trackW
                    ),
                    topLeft = Offset(pad, top),
                    size = Size(fillW, trackH),
                    cornerRadius = corner
                )
            }
            val cx = pad + trackW * fraction
            drawCircle(Color.White, radius = thumbR, center = Offset(cx, cy))
            drawCircle(
                color = primary.copy(alpha = 0.9f),
                radius = thumbR,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
        }
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
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (desc != null) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = cardSecondaryColor()
                )
            }
        }
        if (value != null) {
            // 右侧数值比说明文字更亮、更实，视线扫过去先看到「35%」再看解释
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
                    Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
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
