package com.bquantum.bfastreader.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bquantum.bfastreader.ui.screen.SeriesProgress

@Composable
fun ProgressSection(
    visible: Boolean,
    elapsedMs: Long,
    seriesProgress: SeriesProgress? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildProgressText(elapsedMs, seriesProgress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun buildProgressText(elapsedMs: Long, seriesProgress: SeriesProgress?): String {
    val base = formatElapsed(elapsedMs)
    if (seriesProgress != null) {
        return "正在提取第 ${seriesProgress.current}/${seriesProgress.total} 集：${seriesProgress.part} · $base"
    }
    return base
}

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1000
    val tenths = (ms % 1000) / 100
    return "已用时: $seconds.${tenths}s"
}
