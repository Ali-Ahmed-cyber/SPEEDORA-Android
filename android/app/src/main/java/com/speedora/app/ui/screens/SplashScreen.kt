package com.speedora.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedora.app.R
import com.speedora.app.core.designsystem.LocalReducedMotion
import com.speedora.app.core.designsystem.SpeedoraColors
import com.speedora.app.core.designsystem.SpeedoraEmblem
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val scale = remember { Animatable(if (reducedMotion) 1f else 0.86f) }
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            alpha.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
            scale.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
            delay(650)
        } else {
            delay(250)
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SpeedoraColors.PureWhite,
                        SpeedoraColors.SoftBlue.copy(alpha = 0.65f),
                        SpeedoraColors.PureWhite
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(SpeedoraColors.PureWhite),
                contentAlignment = Alignment.Center
            ) {
                SpeedoraEmblem(
                    size = 84.dp,
                    animateSignal = !reducedMotion
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "SPEEDORA",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.4.sp
                ),
                color = SpeedoraColors.MidnightNavy
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.slogan),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = SpeedoraColors.RoyalBlue
            )

            Spacer(modifier = Modifier.height(28.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(140.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = SpeedoraColors.RoyalBlue,
                trackColor = SpeedoraColors.SoftBlue
            )
        }
    }
}
