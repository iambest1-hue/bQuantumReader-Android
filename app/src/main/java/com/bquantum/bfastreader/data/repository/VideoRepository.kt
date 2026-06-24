package com.bquantum.bfastreader.data.repository

import com.bquantum.bfastreader.data.api.BiliApiService
import com.bquantum.bfastreader.data.api.WbiSign
import com.bquantum.bfastreader.data.model.Comment
import com.bquantum.bfastreader.data.model.CommentResponse
import com.bquantum.bfastreader.data.model.SubtitleBody
import com.bquantum.bfastreader.data.model.SubtitleEntry
import com.bquantum.bfastreader.data.model.VideoInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class VideoRepository(
    private val api: BiliApiService,
    private val wbiSign: WbiSign,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun getVideoInfo(bvid: String): VideoInfo {
        val response = api.getVideoInfo(bvid)
        if (response.code != 0 || response.data == null) {
            throw Exception(response.message.ifEmpty { "获取视频信息失败" })
        }
        val video = response.data
        val cid = video.pages?.firstOrNull()?.cid ?: video.cid
        return video.copy(cid = cid)
    }

    /** 通过 WBI 签名的 view API 获取包含字幕的完整视频信息 */
    suspend fun getVideoInfoWithSubtitles(bvid: String): Pair<VideoInfo, List<String>> {
        val mixinKey = wbiSign.getMixinKey(bvid)

        val (dmImgStr, dmCoverStr) = randomDmStrs()
        val params = mapOf(
            "bvid" to bvid,
            "dm_img_list" to "[]",
            "dm_img_str" to dmImgStr,
            "dm_cover_img_str" to dmCoverStr,
            "dm_img_inter" to """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
        )
        val (wRid, wts) = wbiSign.signParams(params, mixinKey)

        val resp = api.getWbiVideoInfo(
            bvid = bvid, wRid = wRid, wts = wts,
            dmImgList = "[]", dmImgStr = dmImgStr,
            dmCoverImgStr = dmCoverStr,
            dmImgInter = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
        )
        if (resp.code != 0 || resp.data == null) {
            throw Exception("获取视频详情失败: ${resp.message}")
        }
        val video = resp.data
        val cid = video.pages?.firstOrNull()?.cid ?: video.cid
        val fixedVideo = video.copy(cid = cid)

        val subtitleUrls = video.subtitle?.list?.map { it.subtitleUrl } ?: emptyList()
        return fixedVideo to subtitleUrls
    }

    /** 通过 /x/player/wbi/v2 获取字幕内容 */
    suspend fun getSubtitles(bvid: String, cid: Long, page: Int? = null): List<SubtitleEntry> {
        val errors = mutableListOf<String>()

        // 方案A: WBI签名API (含重试)
        for (attempt in 1..2) {
            try {
                val result = getSubtitlesViaWbi(bvid, cid)
                if (result.isNotEmpty()) return result
                if (attempt == 1) {
                    // 返回空列表可能是WBI密钥过期，清缓存重试
                    wbiSign.clearCache()
                    errors.add("WBI-$attempt: 空列表,重试")
                    continue
                }
                errors.add("WBI-$attempt: 返回空字幕列表")
            } catch (e: NoSubtitleException) {
                throw e
            } catch (e: Exception) {
                errors.add("WBI-$attempt: ${e.message}")
                if (attempt == 1) wbiSign.clearCache()
            }
        }

        // 方案B: /x/player/v2 非WBI
        try {
            val result = tryGetSubtitlesViaPlayerV2(bvid, cid)
            if (result != null) {
                if (result.isEmpty()) errors.add("PlayerV2: 空字幕列表")
                else return result
            } else {
                errors.add("PlayerV2: 请求失败")
            }
        } catch (e: Exception) {
            errors.add("PlayerV2: ${e.message}")
        }

        // 方案C: 视频页 HTML（多P视频带上 ?p=N 参数）
        val html = fetchVideoPage(bvid, page)
        if (html != null) {
            val subUrl = extractSubtitleUrl(html)
            if (subUrl != null) return fetchSubtitleContent(subUrl)
            errors.add("HTML: 未匹配到 subtitle_url")
        } else {
            errors.add("HTML: 页面获取失败")
        }

        throw Exception(errors.joinToString(" | ").ifEmpty { "该视频暂无字幕" })
    }

    private suspend fun getSubtitlesViaWbi(bvid: String, cid: Long): List<SubtitleEntry> {
        val mixinKey = wbiSign.getMixinKey(bvid)

        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString()
        )
        val (wRid, wts) = wbiSign.signParams(params, mixinKey)

        val resp = api.getSubtitleList(
            bvid = bvid, cid = cid, wRid = wRid, wts = wts
        )
        if (resp.code != 0) throw Exception("WBI API 返回 code=${resp.code}: ${resp.message}")
        val subtitles = resp.data?.subtitle?.subtitles
        if (subtitles.isNullOrEmpty()) return emptyList()
        return fetchSubtitleContent(subtitles.first().subtitleUrl)
    }

    /** 下载并解析指定字幕 URL */
    suspend fun downloadSubtitle(subtitleUrl: String): List<SubtitleEntry> =
        fetchSubtitleContent(subtitleUrl)

    /** 安全获取单集字幕，不抛异常 */
    suspend fun getSubtitlesSafe(bvid: String, cid: Long, page: Int? = null): Result<List<SubtitleEntry>> {
        return try {
            val subtitles = getSubtitles(bvid, cid, page)
            Result.success(subtitles)
        } catch (e: NoSubtitleException) {
            Result.success(emptyList()) // 无字幕视为可继续的空结果
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取视频全部评论（翻页直到获取完，最多 200 条） */
    suspend fun getComments(aid: Long, maxPages: Int = 10): List<Comment> {
        val allComments = mutableListOf<Comment>()
        for (page in 1..maxPages) {
            val resp = api.getComments(type = 1, oid = aid, mode = 3, ps = 20, pn = page)
            if (resp.code != 0 || resp.data == null) break
            val replies = resp.data.replies ?: emptyList()
            allComments.addAll(replies)
            // 如果当前页不足 20 条，说明已到最后一页
            if (replies.size < 20) break
        }
        return allComments
    }

    // 移除 tryGetSubtitlesViaWbi 旧方法，改为上面的 getSubtitlesViaWbi

    private suspend fun tryGetSubtitlesViaPlayerV2(bvid: String, cid: Long): List<SubtitleEntry>? {
        return try {
            val resp = api.getPlayerData(bvid, cid)
            if (resp.code != 0) return null
            val subtitles = resp.data?.subtitle?.subtitles
            if (subtitles.isNullOrEmpty()) return emptyList()
            fetchSubtitleContent(subtitles.first().subtitleUrl)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchVideoPage(bvid: String, page: Int? = null): String? {
        val url = if (page != null && page > 1) {
            "https://www.bilibili.com/video/$bvid?p=$page"
        } else {
            "https://www.bilibili.com/video/$bvid"
        }
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                okHttpClient.newCall(request).execute().body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractSubtitleUrl(html: String): String? {
        val regex = Regex(""""subtitle_url"\s*:\s*"([^"]+)"""")
        return regex.find(html)?.groupValues?.get(1)
    }

    private suspend fun fetchSubtitleContent(url: String): List<SubtitleEntry> {
        return withContext(Dispatchers.IO) {
            val fullUrl = if (url.startsWith("//")) "https:$url" else url
            val request = Request.Builder()
                .url(fullUrl)
                .header("Referer", "https://www.bilibili.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("字幕内容为空")
            val subtitleBody = gson.fromJson(body, SubtitleBody::class.java)
            val entries = subtitleBody.body ?: emptyList()
            entries.map { entry ->
                SubtitleEntry(
                    from = (entry.from * 1000).toLong().coerceAtLeast(0),
                    to = (entry.to * 1000).toLong().coerceAtLeast(0),
                    content = entry.content
                )
            }
        }
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

        private fun randomDmStrs(): Pair<String, String> {
            val r = "ABCDEFGHIJK"
            return "${r.random()}${r.random()}" to "${r.random()}${r.random()}"
        }
    }
}

class NoSubtitleException : Exception("该视频暂无字幕")
