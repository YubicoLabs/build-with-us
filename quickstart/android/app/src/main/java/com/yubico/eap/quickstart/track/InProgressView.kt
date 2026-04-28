package com.yubico.eap.quickstart.track

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview
@Composable
fun InProgressView() {
    val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 360.0f,
        label = "rotation",
        animationSpec = infiniteRepeatable(
            tween(1000),
            RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier.Companion
            .padding(48.dp)
            .fillMaxWidth()
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            fontSize = 100.sp,
            text = "⏳"
        )
    }
}
