package com.bquantum.bfastreader.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bquantum.bfastreader.domain.MarkdownGen
import com.bquantum.bfastreader.domain.ZipBuilder
import com.bquantum.bfastreader.ui.component.ActionButtons
import com.bquantum.bfastreader.ui.component.LinkInput
import com.bquantum.bfastreader.ui.component.ProgressSection
import com.bquantum.bfastreader.ui.component.ResultSheet
import com.bquantum.bfastreader.ui.component.VideoInfoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SAF launcher for ZIP download
    val zipSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && state.seriesResults != null) {
            val videoTitle = state.videoInfo?.title ?: "output"
            scope.launch {
                withContext(Dispatchers.IO) {
                    val zipFile = ZipBuilder.buildSeriesZip(
                        parts = state.seriesResults!!.map {
                            ZipBuilder.ZipInput(it.page, it.part, it.markdown)
                        },
                        mergedMarkdown = state.seriesMergedMarkdown,
                        seriesTitle = videoTitle,
                        cacheDir = context.cacheDir
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        zipFile.inputStream().use { it.copyTo(out) }
                    }
                    zipFile.delete()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ZIP 已保存", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // SAF launcher for merged markdown download
    val mdSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null && state.seriesMergedMarkdown.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(state.seriesMergedMarkdown.toByteArray(Charsets.UTF_8))
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCredential()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("b量子速读") },
                actions = {
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
                enabled = state.phase != Phase.EXTRACTING &&
                    state.phase != Phase.SERIES_EXTRACTING &&
                    state.phase != Phase.PARSING
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.videoInfo != null || state.phase == Phase.PARSING) {
                VideoInfoCard(videoInfo = state.videoInfo)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 系列模式选择（解析后一直显示，包括提取中和完成后）
            if (state.seriesInfo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "检测到共 ${state.seriesInfo!!.total} 集系列视频",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val currentPage = state.pageNumber ?: 1
                            val currentPart = state.videoInfo?.pages
                                ?.firstOrNull { it.page == currentPage }
                                ?.part?.take(30) ?: ""
                            val pageLabel = if (state.pageNumber != null) "P${state.pageNumber} $currentPart" else "P1 $currentPart"
                            FilterChip(
                                selected = state.extractionMode == ExtractionMode.SINGLE,
                                onClick = { viewModel.setExtractionMode(ExtractionMode.SINGLE) },
                                label = {
                                    Text("仅提取当前视频：$pageLabel",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FilterChip(
                                selected = state.extractionMode == ExtractionMode.SERIES,
                                onClick = { viewModel.setExtractionMode(ExtractionMode.SERIES) },
                                label = { Text("提取全系列（共${state.seriesInfo!!.total}集）") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            ActionButtons(
                hasVideo = state.videoInfo != null,
                isExtracting = state.phase == Phase.EXTRACTING || state.phase == Phase.SERIES_EXTRACTING,
                onExtractSubtitles = { viewModel.extractSubtitles() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProgressSection(
                visible = state.phase == Phase.EXTRACTING || state.phase == Phase.SERIES_EXTRACTING,
                elapsedMs = state.elapsedMs,
                seriesProgress = state.seriesProgress,
                onCancel = if (state.phase == Phase.SERIES_EXTRACTING) {
                    { viewModel.cancelSeriesExtraction() }
                } else null
            )
        }
    }

    // 错误对话框
    if (state.error != null && state.phase == Phase.ERROR) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("提示") },
            text = { Text(state.error ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("确定")
                }
            },
            dismissButton = if (state.loginRequired) {
                {
                    TextButton(onClick = {
                        viewModel.dismissError()
                        onSettings()
                    }) {
                        Text("去登录")
                    }
                }
            } else null
        )
    }

    // 单集结果
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

    // 系列结果对话框
    if (state.phase == Phase.SERIES_DONE && state.seriesResults != null) {
        val results = state.seriesResults!!
        val successCount = results.count { it.error == null }
        val failedCount = results.count { it.error != null }
        val totalSubtitles = results.sumOf { it.subtitles.size }
        val totalChars = results.sumOf { r -> r.subtitles.sumOf { s -> s.content.length } }
        val videoTitle = state.videoInfo?.title ?: "output"

        AlertDialog(
            onDismissRequest = { viewModel.dismissSeriesResult() },
            title = { Text("系列提取完成") },
            text = {
                Column {
                    Text("共 ${results.size} 集")
                    Text("成功: $successCount 集 · 失败: $failedCount 集")
                    Text("字幕: $totalSubtitles 条 · $totalChars 字")
                    Text("用时: ${state.elapsedMs / 1000}.${(state.elapsedMs % 1000) / 100}s")

                    if (failedCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("失败集:", fontWeight = FontWeight.Medium)
                        results.filter { it.error != null }.forEach {
                            Text("  P${it.page} ${it.part}: ${it.error}")
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val mergedFilename = MarkdownGen.sanitizeFilename("全系列_$videoTitle") + ".md"
                        mdSaveLauncher.launch(mergedFilename)
                    }) {
                        Text("下载合并文件")
                    }
                    Button(onClick = {
                        val zipFilename = MarkdownGen.sanitizeFilename(videoTitle) + ".zip"
                        zipSaveLauncher.launch(zipFilename)
                    }) {
                        Text("下载 ZIP 包（含各分p）")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(onClick = { viewModel.dismissSeriesResult() }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
