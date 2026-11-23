package com.example.speedopro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ── Paleta neón automotriz ───────────────────────────────────────────────────
private val NeonCyan    = Color(0xFF00E5FF)
private val NeonOrange  = Color(0xFFFF6D00)
private val NeonRed     = Color(0xFFFF1744)
private val GaugeBg     = Color(0xFF0D1117)
private val ArcTrack    = Color(0xFF1E2A38)
private val TickMajor   = Color(0xFFB0BEC5)
private val TickMinor   = Color(0xFF455A64)
private val NeedleColor = Color(0xFFFFFFFF)

// ── Ángulos del arco (grados, origen = eje X positivo) ───────────────────────
private const val ARC_START_DEG = 150f   // posición 0
private const val ARC_SWEEP_DEG = 240f   // recorrido total

@Composable
fun AnalogSpeedometer(
    speedValue: Float,          // valor actual en la unidad de display (km/h o mph)
    maxScale: Float,            // 240f km/h ó 160f mph
    unitLabel: String,          // "km/h" ó "mph"
    size: Dp = 300.dp,
) {
    // Aguja con física de resorte
    val animatedFraction by animateFloatAsState(
        targetValue = (speedValue / maxScale).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 80f),
        label = "needle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f * 0.88f

            drawGaugeBackground(cx, cy, radius)
            drawArcTrack(cx, cy, radius)
            drawProgressArc(cx, cy, radius, animatedFraction)
            drawTicks(cx, cy, radius, maxScale)
            drawNeedle(cx, cy, radius, animatedFraction)
            drawCenterHub(cx, cy)
            drawSpeedText(cx, cy, radius, speedValue, unitLabel)
        }
    }
}

// ── Fondo circular ───────────────────────────────────────────────────────────
private fun DrawScope.drawGaugeBackground(cx: Float, cy: Float, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF16202E), GaugeBg),
            center = Offset(cx, cy),
            radius = radius
        ),
        center = Offset(cx, cy),
        radius = radius
    )
    // Borde exterior
    drawCircle(
        color = Color(0xFF263340),
        center = Offset(cx, cy),
        radius = radius,
        style = Stroke(width = 3.dp.toPx())
    )
}

// ── Arco de fondo (pista) ────────────────────────────────────────────────────
private fun DrawScope.drawArcTrack(cx: Float, cy: Float, radius: Float) {
    val arcRadius = radius * 0.82f
    val stroke = 14.dp.toPx()
    val topLeft = Offset(cx - arcRadius, cy - arcRadius)
    val arcSize = Size(arcRadius * 2, arcRadius * 2)
    drawArc(
        color = ArcTrack,
        startAngle = ARC_START_DEG,
        sweepAngle = ARC_SWEEP_DEG,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}

// ── Arco de progreso con degradado ───────────────────────────────────────────
private fun DrawScope.drawProgressArc(
    cx: Float, cy: Float, radius: Float, fraction: Float
) {
    val arcRadius = radius * 0.82f
    val stroke = 14.dp.toPx()
    val topLeft = Offset(cx - arcRadius, cy - arcRadius)
    val arcSize = Size(arcRadius * 2, arcRadius * 2)
    val sweep = ARC_SWEEP_DEG * fraction

    if (sweep < 0.5f) return

    // Degradado cyan → naranja → rojo según velocidad
    val gradientColors = listOf(NeonCyan, NeonOrange, NeonRed)
    // Dibujamos sobre la pista usando un brush rotativo
    drawArc(
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to NeonCyan,
                0.50f to NeonOrange,
                1.00f to NeonRed
            ),
            center = Offset(cx, cy)
        ),
        startAngle = ARC_START_DEG,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
        alpha = 0.95f
    )
    // Brillo/glow extra
    drawArc(
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to NeonCyan.copy(alpha = 0.3f),
                0.50f to NeonOrange.copy(alpha = 0.3f),
                1.00f to NeonRed.copy(alpha = 0.3f)
            ),
            center = Offset(cx, cy)
        ),
        startAngle = ARC_START_DEG,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke + 6.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.4f
    )
}

// ── Marcas y etiquetas numéricas ─────────────────────────────────────────────
private fun DrawScope.drawTicks(
    cx: Float, cy: Float, radius: Float, maxScale: Float
) {
    val majorCount = 8          // 0,30,60,90,120,150,180,210,240 → 9 puntos = 8 intervalos
    val minorPerMajor = 4       // divisiones menores entre mayores
    val totalTicks = majorCount * minorPerMajor
    val angleStep = ARC_SWEEP_DEG / totalTicks.toFloat()

    val outerR  = radius * 0.80f
    val innerMajor = radius * 0.68f
    val innerMinor = radius * 0.74f
    val labelR  = radius * 0.56f

    // Paint nativo para texto
    val textPaint = android.graphics.Paint().apply {
        color = TickMajor.toArgb()
        textSize = (radius * 0.09f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.MONOSPACE,
            android.graphics.Typeface.BOLD
        )
    }

    for (i in 0..totalTicks) {
        val angleDeg = ARC_START_DEG + angleStep * i
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        val isMajor = (i % minorPerMajor == 0)
        val innerR  = if (isMajor) innerMajor else innerMinor
        val tickColor = if (isMajor) TickMajor else TickMinor
        val tickWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx()

        drawLine(
            color = tickColor,
            start = Offset(cx + outerR * cosA, cy + outerR * sinA),
            end   = Offset(cx + innerR * cosA, cy + innerR * sinA),
            strokeWidth = tickWidth
        )

        // Número en marcas mayores
        if (isMajor) {
            val speedVal = (maxScale * i / totalTicks).toInt()
            val lx = cx + labelR * cosA
            val ly = cy + labelR * sinA + textPaint.textSize * 0.35f
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(speedVal.toString(), lx, ly, textPaint)
            }
        }
    }
}

// ── Aguja ────────────────────────────────────────────────────────────────────
private fun DrawScope.drawNeedle(
    cx: Float, cy: Float, radius: Float, fraction: Float
) {
    val angleDeg = ARC_START_DEG + ARC_SWEEP_DEG * fraction
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()

    val tipR  = radius * 0.72f
    val tailR = radius * 0.18f

    // Sombra de aguja
    drawLine(
        color = Color.Black.copy(alpha = 0.5f),
        start = Offset(cx - tailR * cosA + 2f, cy - tailR * sinA + 2f),
        end   = Offset(cx + tipR  * cosA + 2f, cy + tipR  * sinA + 2f),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Aguja principal
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.7f), NeedleColor),
            start  = Offset(cx - tailR * cosA, cy - tailR * sinA),
            end    = Offset(cx + tipR  * cosA, cy + tipR  * sinA),
        ),
        start = Offset(cx - tailR * cosA, cy - tailR * sinA),
        end   = Offset(cx + tipR  * cosA, cy + tipR  * sinA),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
}

// ── Eje central ──────────────────────────────────────────────────────────────
private fun DrawScope.drawCenterHub(cx: Float, cy: Float) {
    drawCircle(color = Color(0xFF1E2A38), center = Offset(cx, cy), radius = 14.dp.toPx())
    drawCircle(color = NeonCyan, center = Offset(cx, cy), radius = 6.dp.toPx())
    drawCircle(
        color = NeonCyan.copy(alpha = 0.3f),
        center = Offset(cx, cy),
        radius = 10.dp.toPx()
    )
}

// ── Lectura numérica pequeña inferior ────────────────────────────────────────
private fun DrawScope.drawSpeedText(
    cx: Float, cy: Float, radius: Float,
    speed: Float, unitLabel: String
) {
    val speedPaint = android.graphics.Paint().apply {
        color = NeonCyan.toArgb()
        textSize = radius * 0.22f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.MONOSPACE,
            android.graphics.Typeface.BOLD
        )
    }
    val unitPaint = android.graphics.Paint().apply {
        color = TickMajor.toArgb()
        textSize = radius * 0.10f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT
    }
    val textY = cy + radius * 0.48f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(
            "%.0f".format(speed), cx, textY, speedPaint
        )
        canvas.nativeCanvas.drawText(
            unitLabel, cx, textY + unitPaint.textSize * 1.3f, unitPaint
        )
    }
}
