package com.bquantum.bfastreader.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bquantum.bfastreader.data.api.BiliApiService
import com.bquantum.bfastreader.data.local.BiliCredential
import com.bquantum.bfastreader.data.local.CookieProvider
import com.bquantum.bfastreader.data.local.CredentialStorage
import com.bquantum.bfastreader.data.model.SubtitleEntry
import com.bquantum.bfastreader.data.model.VideoInfo
import com.bquantum.bfastreader.data.model.VideoPage
import com.bquantum.bfastreader.data.repository.NoSubtitleException
import com.bquantum.bfastreader.data.repository.VideoRepository
import com.bquantum.bfastreader.domain.LinkParser
import com.bquantum.bfastreader.domain.MarkdownGen
import com.bquantum.bfastreader.domain.ResolvedLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

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
    val credential: BiliCredential = BiliCredential(),
    val loginRequired: Boolean = false,
    // 系列提取相关
    val seriesInfo: SeriesInfo? = null,
    val extractionMode: ExtractionMode = ExtractionMode.SINGLE,
    val seriesProgress: SeriesProgress? = null,
    val seriesResults: List<SeriesPartResult>? = null,
    val seriesMergedMarkdown: String = "",
    // URL 中指定的分P（?p=N）
    val pageNumber: Int? = null
)

data class SeriesInfo(
    val total: Int,
    val pages: List<VideoPage>
)

data class SeriesProgress(
    val current: Int,
    val total: Int,
    val part: String
)

data class SeriesPartResult(
    val page: Int,
    val part: String,
    val subtitles: List<SubtitleEntry>,
    val markdown: String,
    val elapsedMs: Long,
    val error: String? = null
)

enum class ExtractionMode {
    SINGLE,
    SERIES
}

enum class Phase {
    IDLE,
    PARSING,
    PARSED,
    EXTRACTING,
    SERIES_EXTRACTING,
    SERIES_DONE,
    DONE,
    ERROR
}

class HomeViewModel(
    private val repository: VideoRepository,
    private val credentialStorage: CredentialStorage,
    private val linkParser: LinkParser,
    private val api: BiliApiService,
    private val cookieProvider: CookieProvider
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var timerJob: Job? = null
    private var _seriesCancelled = false

    init {
        viewModelScope.launch {
            credentialStorage.credential.collect { cred ->
                _state.update { it.copy(credential = cred) }
                if (cred.isLoggedIn) {
                    restoreCookiesIfNeeded(cred)
                }
            }
        }
    }

    /** 从持久化凭证恢复 CookieProvider（解决 App 重启后 cookie 丢失问题） */
    private fun restoreCookiesIfNeeded(cred: BiliCredential) {
        if (cookieProvider.getCookie("SESSDATA") != null || cred.sessdata.isBlank()) return
        val cookies = buildList {
            add(Cookie.Builder().name("SESSDATA").value(cred.sessdata)
                .domain("api.bilibili.com").path("/").secure().httpOnly().build())
            if (cred.biliJct.isNotBlank())
                add(Cookie.Builder().name("bili_jct").value(cred.biliJct)
                    .domain("api.bilibili.com").path("/").httpOnly().build())
            if (cred.buvid3.isNotBlank())
                add(Cookie.Builder().name("buvid3").value(cred.buvid3)
                    .domain("api.bilibili.com").path("/").build())
            if (cred.dedeuserId.isNotBlank())
                add(Cookie.Builder().name("DedeUserID").value(cred.dedeuserId)
                    .domain("api.bilibili.com").path("/").build())
        }
        cookieProvider.saveFromResponse("https://api.bilibili.com".toHttpUrl(), cookies)
    }

    fun refreshCredential() {
        viewModelScope.launch {
            _state.update { it.copy(credential = credentialStorage.credential.first()) }
        }
    }

    fun updateUrl(url: String) {
        _state.update { it.copy(inputUrl = url, error = null, pageNumber = null) }
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
                        val pageNumber = linkParser.extractPageNumber(resolved.fullUrl)
                        parseResolvedBvid(resolved.bvid, pageNumber)
                    } else {
                        _state.update { it.copy(error = "无法解析短链接 $shortUrl", phase = Phase.ERROR) }
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message ?: "解析短链接失败", phase = Phase.ERROR) }
                }
            }
            return
        }

        val pageNumber = linkParser.extractPageNumber(url)
        viewModelScope.launch {
            parseResolvedBvid(bvid, pageNumber)
        }
    }

    private suspend fun parseResolvedBvid(bvid: String, pageNumber: Int? = null) {
        _state.update { it.copy(phase = Phase.PARSING, error = null) }
        try {
                val video = repository.getVideoInfo(bvid)
                val pages = video.pages ?: emptyList()
                val seriesInfo = if (pages.size > 1) {
                    SeriesInfo(total = pages.size, pages = pages)
                } else null
                _state.update {
                    it.copy(
                        phase = Phase.PARSED,
                        videoInfo = video,
                        currentBvid = bvid,
                        subtitles = emptyList(),
                        markdown = "",
                        error = null,
                        seriesInfo = seriesInfo,
                        extractionMode = if (pageNumber != null) ExtractionMode.SINGLE else ExtractionMode.SINGLE,
                        seriesProgress = null,
                        seriesResults = null,
                        seriesMergedMarkdown = "",
                        pageNumber = pageNumber
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = Phase.ERROR, error = e.message ?: "获取视频信息失败")
                }
            }
        }

    fun setExtractionMode(mode: ExtractionMode) {
        _state.update { it.copy(extractionMode = mode) }
    }

    fun extractSubtitles() {
        if (_state.value.extractionMode == ExtractionMode.SERIES) {
            extractSeries()
            return
        }

        val credential = _state.value.credential
        if (!credential.isLoggedIn) {
            _state.update {
                it.copy(phase = Phase.ERROR, error = "请先登录B站账号，再提取字幕", loginRequired = true)
            }
            return
        }

        val video = _state.value.videoInfo ?: return
        val bvid = _state.value.currentBvid ?: return
        val baseUrl = "https://www.bilibili.com/video/$bvid"
        val pageNumber = _state.value.pageNumber
        val url = if (pageNumber != null) "$baseUrl?p=$pageNumber" else baseUrl

        viewModelScope.launch {
            _state.update { it.copy(phase = Phase.EXTRACTING, error = null, elapsedMs = 0) }
            startTimer()

            try {
                // 服务端检测登录是否过期
                try {
                    val navResp = api.getNav()
                    if (navResp.data?.isLogin != true) {
                        _state.update {
                            it.copy(phase = Phase.ERROR, error = "B站登录已过期，请重新登录", loginRequired = true)
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                    // 网络异常时不阻断提取
                }

                // 并行获取字幕和评论
                val targetCid = _state.value.pageNumber?.let { video.pages?.getOrNull(it - 1)?.cid }
                    ?: video.cid
                val subtitlesDeferred = viewModelScope.async { repository.getSubtitles(bvid, targetCid) }
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

    fun cancelSeriesExtraction() {
        _seriesCancelled = true
    }

    private fun extractSeries() {
        val credential = _state.value.credential
        if (!credential.isLoggedIn) {
            _state.update {
                it.copy(phase = Phase.ERROR, error = "请先登录B站账号，再提取字幕", loginRequired = true)
            }
            return
        }

        val video = _state.value.videoInfo ?: return
        val bvid = _state.value.currentBvid ?: return
        val pages = _state.value.seriesInfo?.pages ?: return
        val totalPages = pages.size
        val url = "https://www.bilibili.com/video/$bvid"

        viewModelScope.launch {
            _state.update { it.copy(phase = Phase.SERIES_EXTRACTING, error = null, elapsedMs = 0, seriesResults = null, seriesMergedMarkdown = "") }
            _seriesCancelled = false
            startTimer()

            try {
                // 服务端检测登录是否过期
                try {
                    val navResp = api.getNav()
                    if (navResp.data?.isLogin != true) {
                        _state.update {
                            it.copy(phase = Phase.ERROR, error = "B站登录已过期，请重新登录", loginRequired = true)
                        }
                        return@launch
                    }
                } catch (_: Exception) {
                }

                val results = mutableListOf<SeriesPartResult>()
                // 只取第一集的评论
                val comments = try {
                    repository.getComments(video.aid)
                } catch (_: Exception) {
                    emptyList()
                }

                for ((index, page) in pages.withIndex()) {
                    if (_seriesCancelled) break

                    val pageNumber = index + 1
                    _state.update {
                        it.copy(seriesProgress = SeriesProgress(current = pageNumber, total = totalPages, part = page.part))
                    }

                    val partUrl = "$url?p=$pageNumber"
                    val partStartTime = System.currentTimeMillis()

                    val subtitleResult = repository.getSubtitlesSafe(bvid, page.cid, page.page)
                    val result = if (subtitleResult.isSuccess) {
                        val subtitles = subtitleResult.getOrDefault(emptyList())
                        val partMd = if (subtitles.isNotEmpty()) {
                            MarkdownGen.generatePart(video, page, subtitles, partUrl)
                        } else {
                            MarkdownGen.generatePlaceholder(page, "该分P暂无可用字幕")
                        }
                        val elapsed = System.currentTimeMillis() - partStartTime
                        SeriesPartResult(
                            page = pageNumber,
                            part = page.part,
                            subtitles = subtitles,
                            markdown = partMd,
                            elapsedMs = elapsed,
                            error = if (subtitles.isEmpty()) "无字幕" else null
                        )
                    } else {
                        val errorMsg = subtitleResult.exceptionOrNull()?.message ?: "提取失败"
                        val partMd = MarkdownGen.generatePlaceholder(page, errorMsg)
                        val elapsed = System.currentTimeMillis() - partStartTime
                        SeriesPartResult(
                            page = pageNumber,
                            part = page.part,
                            subtitles = emptyList(),
                            markdown = partMd,
                            elapsedMs = elapsed,
                            error = errorMsg
                        )
                    }
                    results.add(result)
                }

                val mergedMd = MarkdownGen.generateMerged(
                    results.map { MarkdownGen.PartInput(it.page, it.part, it.subtitles, it.markdown, it.error) },
                    video, url, comments
                )

                stopTimer()
                _state.update {
                    it.copy(
                        phase = Phase.SERIES_DONE,
                        seriesResults = results,
                        seriesProgress = null,
                        seriesMergedMarkdown = mergedMd,
                        elapsedMs = _state.value.elapsedMs
                    )
                }
            } catch (e: Exception) {
                stopTimer()
                _state.update {
                    it.copy(phase = Phase.ERROR, error = e.message ?: "系列提取失败")
                }
            }
        }
    }

    fun dismissSeriesResult() {
        _state.update {
            it.copy(
                phase = Phase.PARSED,
                seriesProgress = null,
                seriesResults = null,
                seriesMergedMarkdown = "",
                elapsedMs = 0
            )
        }
    }

    fun dismissError() {
        val prevPhase = if (_state.value.videoInfo != null) Phase.PARSED else Phase.IDLE
        _state.update { it.copy(phase = prevPhase, error = null, loginRequired = false) }
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
