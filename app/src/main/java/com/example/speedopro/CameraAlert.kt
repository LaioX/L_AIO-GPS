package com.example.speedopro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val AlertRed      = Color(0xFFFF1744)
private val AlertYellow   = Color(0xFFFFD600)
private val AlertBg       = Color(0xFF1A0008)
private val AlertSurface  = Color(0xFF280010)

// ════════════════════════════════════════════════════════════════════════════
//  CameraAlertBanner — se muestra encima de todo cuando hay cámara cerca
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun CameraAlertBanner(
    visible: Boolean,
    metersAway: Float,
    cameraName: String,
    alertRadiusM: Float,
) {
    // Pulso del borde: destella más rápido cuanto más cerca
    val pulseDuration = when {
        metersAway < 100f -> 300
        metersAway < 200f -> 500
        else              -> 800
    }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue  = 1.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    // Color cambia de amarillo a rojo al acercarse
    val dangerFraction = ((alertRadiusM - metersAway) / alertRadiusM).coerceIn(0f, 1f)
    val alertColor = lerp(AlertYellow, AlertRed, dangerFraction)

    AnimatedVisibility(
        visible = visible,
        enter   = expandVertically() + fadeIn(),
        exit    = shrinkVertically() + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(AlertBg, AlertSurface)
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            alertColor.copy(alpha = pulseAlpha),
                            AlertRed.copy(alpha = pulseAlpha * 0.6f),
                            alertColor.copy(alpha = pulseAlpha)
                        )
                    ),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icono pulsante
                Icon(
                    imageVector        = Icons.Default.Videocam,
                    contentDescription = "Cámara",
                    tint               = alertColor,
                    modifier           = Modifier
                        .size(38.dp)
                        .scale(iconScale)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "⚠ CÁMARA DE VELOCIDAD",
                        color      = alertColor,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (cameraName.isNotBlank()) {
                        Text(
                            text     = cameraName,
                            color    = alertColor.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Contador regresivo de metros
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "${metersAway.roundToInt()}",
                        color      = alertColor,
                        fontSize   = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text     = "metros",
                        color    = alertColor.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CameraSettingsRow — slider de radio de alerta + botón actualizar
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun CameraSettingsRow(
    alertRadiusM: Float,
    onRadiusChange: (Float) -> Unit,
    cameraCount: Int,
    updateState: CameraUpdateState,
    onUpdateClick: () -> Unit,
    isMetric: Boolean,
) {
    val CardDark    = Color(0xFF131E2B)
    val NeonCyan    = Color(0xFF00E5FF)
    val NeonOrange  = Color(0xFFFF6D00)
    val TextSec     = Color(0xFF78909C)

    // Convierte metros de alerta para display
    val displayRadius = if (isMetric) alertRadiusM.roundToInt() else (alertRadiusM * 3.28084f).roundToInt()
    val radiusUnit    = if (isMetric) "m" else "ft"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Encabezado
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint     = NeonOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Cámaras ($cameraCount)",
                    color      = NeonOrange,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Botón actualizar
            UpdateButton(state = updateState, onClick = onUpdateClick, accentColor = NeonCyan)
        }

        Spacer(Modifier.height(8.dp))

        // Label del slider
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Radio de alerta", color = TextSec, fontSize = 11.sp)
            Text(
                "$displayRadius $radiusUnit",
                color      = NeonCyan,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Slider 100m – 500m
        Slider(
            value         = alertRadiusM,
            onValueChange = onRadiusChange,
            valueRange    = 100f..500f,
            steps         = 7,   // 100,150,200,250,300,350,400,450,500 → 8 pasos visibles
            colors        = SliderDefaults.colors(
                thumbColor             = NeonCyan,
                activeTrackColor       = NeonCyan,
                inactiveTrackColor     = NeonCyan.copy(alpha = 0.2f),
                activeTickColor        = NeonCyan.copy(alpha = 0.4f),
                inactiveTickColor      = NeonCyan.copy(alpha = 0.1f),
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Botón de actualización con estado ────────────────────────────────────────
sealed class CameraUpdateState {
    object Idle    : CameraUpdateState()
    object Loading : CameraUpdateState()
    data class Done(val added: Int)  : CameraUpdateState()
    data class Fail(val msg: String) : CameraUpdateState()
}

@Composable
private fun UpdateButton(
    state: CameraUpdateState,
    onClick: () -> Unit,
    accentColor: Color,
) {
    val (label, enabled) = when (state) {
        is CameraUpdateState.Idle    -> "Actualizar" to true
        is CameraUpdateState.Loading -> "Descargando…" to false
        is CameraUpdateState.Done    -> "+${state.added} nuevas" to true
        is CameraUpdateState.Fail    -> "Reintentar" to true
    }
    androidx.compose.material3.TextButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text       = label,
            color      = if (enabled) accentColor else accentColor.copy(alpha = 0.4f),
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Interpolación de color lineal ─────────────────────────────────────────────
private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)
