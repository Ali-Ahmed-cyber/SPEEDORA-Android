package com.speedora.app.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Original SPEEDORA Emblem:
 * Combines dual opposing wireless signal arcs (connectivity), interlocking kinetic wings (speed + bidirectional transfer),
 * and a centered security diamond (authenticated encryption).
 */
@Composable
fun SpeedoraEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    primaryColor: Color = SpeedoraColors.RoyalBlue,
    navyColor: Color = SpeedoraColors.MidnightNavy,
    arcColor: Color = SpeedoraColors.RoyalBlue.copy(alpha = 0.28f),
    animateSignal: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val shouldAnimate = animateSignal && !reducedMotion

    val pulse = if (shouldAnimate) {
        val transition = rememberInfiniteTransition(label = "emblem_signal")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "signal_phase"
        )
        value
    } else {
        0.5f
    }

    Canvas(modifier = modifier.size(size)) {
        drawSpeedoraGeometry(
            primaryColor = primaryColor,
            navyColor = navyColor,
            arcColor = arcColor,
            signalPhase = pulse,
            animated = shouldAnimate
        )
    }
}

@Composable
fun SpeedoraLogoLockup(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 36.dp,
    showSubtitle: Boolean = false,
    subtitle: String = ""
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SpeedoraEmblem(size = emblemSize)
        androidx.compose.foundation.layout.Column {
            Text(
                text = "SPEEDORA",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (showSubtitle && subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SpeedoraColors.RoyalBlue
                )
            }
        }
    }
}

@Composable
fun SpeedoraTransferEmblem(
    isActive: Boolean,
    isCompleted: Boolean,
    isFailed: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp
) {
    val reducedMotion = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "transfer_emblem")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "transfer_phase"
    )

    val primary = when {
        isFailed -> SpeedoraColors.ErrorCrimson
        isCompleted -> SpeedoraColors.SuccessGreen
        else -> SpeedoraColors.RoyalBlue
    }
    val secondary = when {
        isFailed -> SpeedoraColors.ErrorSoft
        isCompleted -> SpeedoraColors.SuccessSoft
        else -> SpeedoraColors.SoftBlue
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f

        drawCircle(color = secondary, radius = radius * 0.92f, center = center)

        if (isActive && !reducedMotion) {
            val sweepRadius = radius * (0.68f + phase * 0.28f)
            drawCircle(
                color = primary.copy(alpha = (1f - phase) * 0.4f),
                radius = sweepRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        drawSpeedoraGeometry(
            primaryColor = primary,
            navyColor = if (isFailed) SpeedoraColors.ErrorCrimson else SpeedoraColors.MidnightNavy,
            arcColor = primary.copy(alpha = 0.35f),
            signalPhase = if (isActive && !reducedMotion) phase else 0.5f,
            animated = isActive && !reducedMotion,
            scaleFactor = 0.62f
        )
    }
}

private fun DrawScope.drawSpeedoraGeometry(
    primaryColor: Color,
    navyColor: Color,
    arcColor: Color,
    signalPhase: Float,
    animated: Boolean,
    scaleFactor: Float = 1f
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val dim = size.minDimension * scaleFactor
    val strokeW = (dim * 0.082f).coerceAtLeast(2f)

    // Outer wireless arcs
    val outerRadius = dim * 0.43f
    val innerRadius = dim * 0.30f
    val outerAlpha = if (animated) 0.35f + 0.55f * (1f - signalPhase) else 1f
    val innerAlpha = if (animated) 0.45f + 0.55f * signalPhase else 1f

    drawArc(
        color = arcColor.copy(alpha = (arcColor.alpha * outerAlpha).coerceIn(0.15f, 1f)),
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - outerRadius, cy - outerRadius),
        size = Size(outerRadius * 2f, outerRadius * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
    drawArc(
        color = arcColor.copy(alpha = (arcColor.alpha * outerAlpha).coerceIn(0.15f, 1f)),
        startAngle = 0f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - outerRadius, cy - outerRadius),
        size = Size(outerRadius * 2f, outerRadius * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    // Inner arcs
    drawArc(
        color = primaryColor.copy(alpha = innerAlpha),
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - innerRadius, cy - innerRadius),
        size = Size(innerRadius * 2f, innerRadius * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
    drawArc(
        color = primaryColor.copy(alpha = innerAlpha),
        startAngle = 0f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - innerRadius, cy - innerRadius),
        size = Size(innerRadius * 2f, innerRadius * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    // Kinetic bidirectional transfer wings
    val wingHalf = dim * 0.24f
    val wingPath = Path().apply {
        moveTo(cx - wingHalf * 0.85f, cy)
        lineTo(cx, cy - wingHalf * 0.85f)
        lineTo(cx + wingHalf * 1.05f, cy - wingHalf * 0.85f)
        lineTo(cx + wingHalf * 0.25f, cy)
        lineTo(cx + wingHalf * 0.85f, cy)
        lineTo(cx, cy + wingHalf * 0.85f)
        lineTo(cx - wingHalf * 1.05f, cy + wingHalf * 0.85f)
        lineTo(cx - wingHalf * 0.25f, cy)
        close()
    }
    drawPath(path = wingPath, color = navyColor)

    // Security center diamond
    val d = dim * 0.085f
    val diamond = Path().apply {
        moveTo(cx, cy - d)
        lineTo(cx + d, cy)
        lineTo(cx, cy + d)
        lineTo(cx - d, cy)
        close()
    }
    drawPath(path = diamond, color = SpeedoraColors.PureWhite)
}
