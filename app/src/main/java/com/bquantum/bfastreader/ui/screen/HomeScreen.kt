package com.bquantum.bfastreader.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bquantum.bfastreader.ui.component.ActionButtons
import com.bquantum.bfastreader.ui.component.LinkInput
import com.bquantum.bfastreader.ui.component.ProgressSection
import com.bquantum.bfastreader.ui.component.ResultSheet
import com.bquantum.bfastreader.ui.component.VideoInfoCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("b量子速读") },
                actions = {
                    // 登录状态指示
                    if (state.credential.isLoggedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.credential.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = state.credential.avatarUrl.replace("http://", "https://"),
                                    contentDescription = "头像",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = state.credential.userName.ifEmpty { "已登录" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            LinkInput(
                url = state.inputUrl,
                onUrlChange = { viewModel.updateUrl(it) },
                onParse = { viewModel.parseLink() },
                enabled = state.phase != Phase.EXTRACTING && state.phase != Phase.PARSING
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.videoInfo != null || state.phase == Phase.PARSING) {
                VideoInfoCard(videoInfo = state.videoInfo)
                Spacer(modifier = Modifier.height(12.dp))
            }

            ActionButtons(
                hasVideo = state.videoInfo != null,
                isExtracting = state.phase == Phase.EXTRACTING,
                onExtractSubtitles = { viewModel.extractSubtitles() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProgressSection(
                visible = state.phase == Phase.EXTRACTING,
                elapsedMs = state.elapsedMs
            )
        }
    }

    if (state.error != null && state.phase == Phase.ERROR) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("提示") },
            text = { Text(state.error ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("确定")
                }
            }
        )
    }

    if (state.phase == Phase.DONE && state.markdown.isNotEmpty()) {
        ResultSheet(
            markdown = state.markdown,
            subtitles = state.subtitles,
            commentCount = state.commentCount,
            videoTitle = state.videoInfo?.title ?: "output",
            elapsedMs = state.elapsedMs,
            onDismiss = { viewModel.dismissResult() }
        )
    }
}
