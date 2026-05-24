package com.bquantum.bfastreader.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bquantum.bfastreader.data.local.BiliCredential
import com.bquantum.bfastreader.data.local.CredentialStorage
import com.bquantum.bfastreader.data.model.SubtitleEntry
import com.bquantum.bfastreader.data.model.VideoInfo
import com.bquantum.bfastreader.data.repository.NoSubtitleException
import com.bquantum.bfastreader.data.repository.VideoRepository
import com.bquantum.bfastreader.domain.LinkParser
import com.bquantum.bfastreader.domain.MarkdownGen
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val inputUrl: String = "",
    val currentBvid: String? = null,
    val videoInfo: VideoInfo? = null,
    val subtitles: List<SubtitleEntry> = emptyList(),
    val markdown: String = "",
    val commentCount: Int = 0,
    val phase: Phase = Phase.IDLE,
    val error: String? = null,
    val elapsedMs: Long = 0,
    val credential: BiliCredential = BiliCredential()
)

enum class Phase {
    IDLE,
    PARSING,
    PARSED,
    EXTRACTING,
    DONE,
    ERROR
}

class HomeViewModel(
    private val repository: VideoRepository,
    private val credentialStorage: CredentialStorage,
    private val linkParser: LinkParser
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            credentialStorage.credential.collect { cred ->
                _state.update { it.copy(credential = cred) }
            }
        }
    }

    fun updateUrl(url: String) {
        _state.update { it.copy(inputUrl = url, error = null) }
    }

    fun parseLink(url: String = _state.value.inputUrl) {
        var bvid = linkParser.extractBvid(url)
        if (bvid == null) {
            _state.update { it.copy(error = "未识别到有效的 BV 号或 av 号", phase = Phase.ERROR) }
            return
        }

        // 处理 b23.tv 短链接
        if (bvid.startsWith("b23:") && linkParser.hasShortLink(url)) {
            val shortUrl = bvid.removePrefix("b23:")
            _state.update { it.copy(phase = Phase.PARSING, error = null) }
            viewModelScope.launch {
                try {
                    val resolved = linkParser.resolveShortUrl(shortUrl)
                    if (resolved != null) {
                        parseResolvedBvid(resolved)
                    } else {
                        _state.update { it.copy(error = "无法解析短链接 $shortUrl", phase = Phase.ERROR) }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message ?: "解析短链接失败", phase = Phase.ERROR) }
                }
            }
            return
        }

        viewModelScope.launch {
            parseResolvedBvid(bvid)
        }
    }

    private suspend fun parseResolvedBvid(bvid: String) {
        _state.update { it.copy(phase = Phase.PARSING, error = null) }
        try {
                val video = repository.getVideoInfo(bvid)
                _state.update {
                    it.copy(
                        phase = Phase.PARSED,
                        videoInfo = video,
                        currentBvid = bvid,
                        subtitles = emptyList(),
                        markdown = "",
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = Phase.ERROR, error = e.message ?: "获取视频信息失败")
                }
            }
        }

    fun extractSubtitles() {
        val video = _state.value.videoInfo ?: return
        val bvid = _state.value.currentBvid ?: return
        val url = "https://www.bilibili.com/video/$bvid"

        viewModelScope.launch {
            _state.update { it.copy(phase = Phase.EXTRACTING, error = null, elapsedMs = 0) }
            startTimer()

            try {
                // 并行获取字幕和评论
                val subtitlesDeferred = viewModelScope.async { repository.getSubtitles(bvid, video.cid) }
                val commentsDeferred = viewModelScope.async {
                    try {
                        repository.getComments(video.aid)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val subtitles = subtitlesDeferred.await()
                val comments = commentsDeferred.await()

                val markdown = MarkdownGen.generate(video, subtitles, url, comments)
                _state.update {
                    it.copy(phase = Phase.DONE, subtitles = subtitles, markdown = markdown, commentCount = comments.size, error = null)
                }
            } catch (e: NoSubtitleException) {
                _state.update { it.copy(phase = Phase.ERROR, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.ERROR, error = e.message ?: "提取字幕失败") }
            } finally {
                stopTimer()
            }
        }
    }

    fun dismissError() {
        val prevPhase = if (_state.value.videoInfo != null) Phase.PARSED else Phase.IDLE
        _state.update { it.copy(phase = prevPhase, error = null) }
    }

    fun dismissResult() {
        _state.update { it.copy(phase = Phase.PARSED, subtitles = emptyList(), markdown = "", commentCount = 0, elapsedMs = 0) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        val startTime = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                _state.update { it.copy(elapsedMs = elapsed) }
                delay(100)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
