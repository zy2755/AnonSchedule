package com.anon.schedule.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * 液态玻璃（AGSL）— 着色器逐字提取自 npatch.apk (top.nkbe.npatch v1.0.7)。
 * 效果链：模糊垫底 → 边缘 SDF 折射透镜 → 方向性高光 + 跟随手指的径向高光 → 超椭圆轮廓。
 * 「液」的动态来自 GlassMotion：按压/点按/换页多路弹簧逐帧驱动 refractionHeight / offset / cornerRadii。
 * 降级：API 33+ 完整折射 + AGSL 高光；API 31-32 模糊 + 渐变高光描边；API ≤30 透明色垫 + 描边。
 */

/** 主折射透镜：圆角矩形 SDF + 距离梯度折弯，只作用在边缘 refractionHeight 带宽内。 */
private const val LIQUID_GLASS_REFRACTION = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

/**
 * 方向性镜面高光（重力/光照角度沿边框扫一圈）。
 * 相对 npatch 原着色器做了两处适配（原样照搬会把整个内表面照成一片奶白）：
 * 1) dot 取 max(...,0) 单侧受光，背光边不再对称发亮；
 * 2) 乘 SDF 边缘掩码 edge，高光只留在 rim 宽度的边环上，中心保持通透。
 */
private const val SPECULAR_HIGHLIGHT = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;
uniform float rim;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = max(dot(grad, normal), 0.0);
    float intensity = pow(d, falloff);
    float edge = smoothstep(-rim, 0.0, sdRoundedRect(centeredCoord, halfSize, radius));
    return color * intensity * edge;
}
"""

/** 跟随位置(如手指)的径向高光点。 */
private const val RADIAL_GLOW = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}
"""

/** 液态玻璃参数（单位：像素，按底栏药丸尺寸调校）。 */
data class GlassParams(
    val cornerRadiusPx: Float,
    /** 边缘折射带宽度 */
    val refractionHeightPx: Float,
    /** 边缘最大折弯位移（参考实现取负值：refractionAmount = -refractionHeight） */
    val refractionAmount: Float,
    /** 深度/内凹感 */
    val depthEffect: Float = 2.5f,
    /** 折射采样对齐偏移（弹簧晃动时逐帧更新，静止应为 0） */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** 高光方向角（默认左上受光） */
    val highlightAngleRad: Float = (Math.PI * 1.25).toFloat(),
    /** 高光衰减（越大越集中） */
    val highlightFalloff: Float = 3.0f,
    val highlightColor: Color = Color.White.copy(alpha = 0.45f),
    /** 跟随手指的径向高光 */
    val glowColor: Color = Color.White.copy(alpha = 0.55f),
    val glowRadiusPx: Float = 110f,
)

/**
 * API 33+ 返回「边缘折射」RenderEffect（content 输入 = 本节点绘制内容），
 * 低版本返回 null（由调用方降级为纯模糊/色垫）。
 */
@Composable
fun rememberGlassRefraction(size: IntSize, params: GlassParams): RenderEffect? {
    if (Build.VERSION.SDK_INT < 33 || size == IntSize.Zero) return null
    return remember(size, params) {
        val shader = RuntimeShader(LIQUID_GLASS_REFRACTION)
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        // 对齐偏移：静止为 0（透镜居中对称），弹簧晃动时由 GlassMotion 逐帧注入
        shader.setFloatUniform("offset", params.offsetX, params.offsetY)
        shader.setFloatUniform(
            "cornerRadii",
            params.cornerRadiusPx, params.cornerRadiusPx, params.cornerRadiusPx, params.cornerRadiusPx
        )
        shader.setFloatUniform("refractionHeight", params.refractionHeightPx)
        shader.setFloatUniform("refractionAmount", params.refractionAmount)
        shader.setFloatUniform("depthEffect", params.depthEffect)
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/**
 * 玻璃高光层：API 33+ 用 AGSL 方向性高光；低版本用渐变描边近似（左上受光、底部暗边）。
 * 叠在玻璃层之上、内容之下。
 */
fun Modifier.glassSpecular(params: GlassParams): Modifier = this.drawWithCache {
    val r = params.cornerRadiusPx
    if (Build.VERSION.SDK_INT >= 33) {
        val shader = RuntimeShader(SPECULAR_HIGHLIGHT)
        shader.setFloatUniform("cornerRadii", r, r, r, r)
        shader.setColorUniform("color", params.highlightColor.toArgb())
        shader.setFloatUniform("angle", params.highlightAngleRad)
        shader.setFloatUniform("falloff", params.highlightFalloff)
        // 边环宽度随折射带联动：弹簧脉冲时高光环一起呼吸
        shader.setFloatUniform("rim", params.refractionHeightPx * 2.2f)
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): androidx.compose.ui.graphics.Shader {
                shader.setFloatUniform("size", size.width, size.height)
                return shader
            }
        }
        onDrawBehind { drawRect(brush = brush) }
    } else {
        val corner = CornerRadius(r)
        val topLight = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.80f),
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.05f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        val bottomShade = Brush.linearGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)),
            start = Offset(0f, 0f),
            end = Offset(0f, size.height)
        )
        onDrawBehind {
            drawRoundRect(brush = topLight, cornerRadius = corner, style = Stroke(1.4.dp.toPx()))
            drawRoundRect(brush = bottomShade, cornerRadius = corner, style = Stroke(1.2.dp.toPx()))
        }
    }
}

/**
 * 跟随手指的径向高光点：API 33+ 用 AGSL；低版本用径向渐变近似。
 * 位置/透明度由 [GlassMotion] 的弹簧驱动，抬手后淡出。
 */
fun Modifier.glassGlow(motion: GlassMotion, params: GlassParams): Modifier = this.drawWithCache {
    if (Build.VERSION.SDK_INT >= 33) {
        val shader = RuntimeShader(RADIAL_GLOW)
        shader.setColorUniform("color", params.glowColor.toArgb())
        shader.setFloatUniform("radius", params.glowRadiusPx)
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): androidx.compose.ui.graphics.Shader {
                shader.setFloatUniform("size", size.width, size.height)
                return shader
            }
        }
        onDrawBehind {
            val a = motion.glowAlpha.value
            if (a > 0.01f) {
                shader.setFloatUniform("position", motion.glowX.value, motion.glowY.value)
                drawRect(brush = brush, alpha = a)
            }
        }
    } else {
        onDrawBehind {
            val a = motion.glowAlpha.value
            if (a > 0.01f) {
                val c = Offset(motion.glowX.value, motion.glowY.value)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(params.glowColor, Color.Transparent),
                        center = c,
                        radius = params.glowRadiusPx
                    ),
                    alpha = a
                )
            }
        }
    }
}

/**
 * 弹簧驱动的「液」态状态机。
 * 按压 → 高光点跟随 + 折射带增宽；点按/换页 → 折射带脉冲 + 透镜朝滑动方向晃一下再回弹。
 * 所有量静止时归零，因此空闲时不产生任何重组。
 */
@Stable
class GlassMotion(private val scope: CoroutineScope) {
    // 欠阻尼弹簧：回弹时带可见振荡，即「液滴晃动」观感
    private val wobble = spring<Float>(dampingRatio = 0.42f, stiffness = 320f)
    private val soft = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)
    private val follow = spring<Float>(dampingRatio = 1f, stiffness = 900f)

    /** 折射带增宽量（px） */
    val refractBoost = Animatable(0f)
    /** 透镜晃动偏移（px） */
    val sloshX = Animatable(0f)
    val sloshY = Animatable(0f)
    /** 圆角脉冲（px） */
    val cornerBoost = Animatable(0f)
    /** 跟随手指的高光点 */
    val glowX = Animatable(0f)
    val glowY = Animatable(0f)
    val glowAlpha = Animatable(0f)

    /** 点按/换页脉冲：折射带与圆角先撑开再回弹 */
    fun pulse(amount: Float, dirX: Float = 0f) {
        scope.launch {
            launch {
                refractBoost.animateTo(amount, wobble)
                refractBoost.animateTo(0f, wobble)
            }
            launch {
                cornerBoost.animateTo(amount * 0.30f, wobble)
                cornerBoost.animateTo(0f, wobble)
            }
            if (dirX != 0f) {
                launch {
                    sloshX.animateTo(dirX, wobble)
                    sloshX.animateTo(0f, wobble)
                }
            }
        }
    }

    /** 按下：高光点落到手指处并淡入，同时折射带轻微增宽（按压感） */
    fun press(x: Float, y: Float) {
        scope.launch {
            glowX.snapTo(x)
            glowY.snapTo(y)
            launch { glowAlpha.animateTo(1f, soft) }
            launch { refractBoost.animateTo(6f, soft) }
        }
    }

    /** 拖动：高光点跟随手指 */
    fun dragTo(x: Float, y: Float) {
        scope.launch {
            launch { glowX.animateTo(x, follow) }
            launch { glowY.animateTo(y, follow) }
        }
    }

    /** 抬手：高光淡出、折射带回落、透镜晃动回弹 */
    fun release() {
        scope.launch {
            launch { glowAlpha.animateTo(0f, soft) }
            launch { refractBoost.animateTo(0f, wobble) }
            launch { sloshX.animateTo(0f, wobble) }
        }
    }

    /** 拖选时把横向增量的一小部分累进透镜晃动（夹幅 ±16px），抬手由 [release] 回弹 */
    fun sloshBy(dx: Float) {
        scope.launch { sloshX.snapTo((sloshX.value + dx * 0.12f).coerceIn(-16f, 16f)) }
    }
}

@Composable
fun rememberGlassMotion(): GlassMotion {
    val scope = rememberCoroutineScope()
    return remember { GlassMotion(scope) }
}

/**
 * iOS 风格连续圆角（超椭圆 / squircle）轮廓。
 * 用分段参数曲线近似 |x|^n + |y|^n = r^n（n≈4.6），供 clip / border / shadow 复用同一 silhouette。
 * 说明：参考实现用烘焙 SDF 位图 + AGSL 遮罩做超椭圆；此处改用几何轮廓，
 * 视觉等价且无需位图烘焙，低版本同样可用。
 */
class SuperellipseShape(
    private val radius: androidx.compose.ui.unit.Dp,
    private val exponent: Float = 4.6f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        return Outline.Generic(superellipsePath(size, r, exponent))
    }

    override fun toString(): String = "SuperellipseShape($radius, n=$exponent)"
}

private const val SUPERELLIPSE_STEPS = 14

private fun superellipsePath(size: Size, r: Float, n: Float): Path {
    val path = Path()
    val w = size.width
    val h = size.height
    if (r <= 0.5f) {
        path.addRect(Rect(Offset.Zero, size))
        return path
    }
    val e = 2.0 / n
    // 四分之一超椭圆弧：t 从 0(=x 轴) 到 π/2(=y 轴)，返回相对角心的偏移点
    fun arc(cx: Float, cy: Float, sx: Float, sy: Float): List<Offset> {
        val pts = ArrayList<Offset>(SUPERELLIPSE_STEPS + 1)
        for (i in 0..SUPERELLIPSE_STEPS) {
            val t = (i.toFloat() / SUPERELLIPSE_STEPS) * (Math.PI / 2.0)
            val x = (r * cos(t).pow(e)).toFloat()
            val y = (r * sin(t).pow(e)).toFloat()
            pts += Offset(cx + sx * x, cy + sy * y)
        }
        return pts
    }
    // 从顶边左端顺时针走：顶边 → 右上 → 右边 → 右下 → 底边 → 左下 → 左边 → 左上
    path.moveTo(r, 0f)
    path.lineTo(w - r, 0f)
    arc(w - r, r, 1f, -1f).forEach { path.lineTo(it.x, it.y) }   // 右上：从 (w-r,0) 到 (w,r)
    path.lineTo(w, h - r)
    arc(w - r, h - r, 1f, 1f).forEach { path.lineTo(it.x, it.y) } // 右下：从 (w,h-r) 到 (w-r,h)
    path.lineTo(r, h)
    arc(r, h - r, -1f, 1f).forEach { path.lineTo(it.x, it.y) }    // 左下：从 (r,h) 到 (0,h-r)
    path.lineTo(0f, r)
    arc(r, r, -1f, -1f).forEach { path.lineTo(it.x, it.y) }       // 左上：从 (0,r) 到 (r,0)
    path.close()
    return path
}
