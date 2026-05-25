package com.bquantum.bfastreader.data.api

import com.bquantum.bfastreader.data.model.NavData
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WbiSign(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val MIXIN_KEY_TABLE = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 37, 12, 52, 56, 7,
        0, 16, 38, 11, 6, 34, 55, 39, 57, 22, 1, 26, 44, 24, 51, 13,
        36, 20, 40, 4, 17, 48, 21, 30, 25, 41, 54, 59
    )

    private var cachedMixinKey: String? = null
    private val mutex = Mutex()

    fun clearCache() {
        cachedMixinKey = null
    }

    suspend fun getMixinKey(bvid: String? = null): String {
        cachedMixinKey?.let { return it }
        mutex.withLock {
            cachedMixinKey?.let { return it }

            val errors = mutableListOf<String>()

            // 方法1: nav API
            try {
                tryGetFromNav().let {
                    cachedMixinKey = it
                    return it
                }
            } catch (e: Exception) {
                errors.add("nav: ${e.message}")
            }

            // 方法2: B站首页
            try {
                tryGetFromPage("https://www.bilibili.com/").let {
                    cachedMixinKey = it
                    return it
                }
            } catch (e: Exception) {
                errors.add("首页: ${e.message}")
            }

            // 方法3: 视频页
            if (bvid != null) {
                try {
                    tryGetFromPage("https://www.bilibili.com/video/$bvid").let {
                        cachedMixinKey = it
                        return it
                    }
                } catch (e: Exception) {
                    errors.add("视频页: ${e.message}")
                }
            }

            throw Exception(errors.joinToString(" | ").ifEmpty { "所有方法均失败" })
        }
    }

    private suspend fun tryGetFromNav(): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/nav")
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/")
                .build()

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                throw Exception("nav ${e.javaClass.simpleName}: ${e.message ?: "(无详情)"}")
            }

            val body = response.body?.string() ?: throw Exception("nav 返回空body(code=${response.code})")

            val json = try {
                gson.fromJson(body, NavResponse::class.java)
            } catch (e: Exception) {
                val preview = body.take(200)
                throw Exception("nav JSON解析: ${e.javaClass.simpleName}, body=$preview")
            }

            if (json.code != 0) throw Exception("nav code=${json.code}: ${json.message}")
            if (json.data == null) throw Exception("nav data为null")
            if (json.data.wbiImg == null) throw Exception("nav 无wbi_img(未登录)")

            val wbi = json.data.wbiImg
            extractMixinKey(wbi.imgUrl, wbi.subUrl)
        }
    }

    private suspend fun tryGetFromPage(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val response = try {
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                throw Exception("页面 ${e.javaClass.simpleName}: ${e.message ?: "(无详情)"}")
            }

            if (!response.isSuccessful) throw Exception("页面HTTP ${response.code}")
            val html = response.body?.string() ?: throw Exception("页面空body")

            val (imgUrl, subUrl) = extractWbiFromInitState(html)
                ?: throw Exception("页面未找到wbi_img(len=${html.length})")

            extractMixinKey(imgUrl, subUrl)
        }
    }

    private fun extractWbiFromInitState(html: String): Pair<String, String>? {
        val startMarker = "window.__INITIAL_STATE__="
        val startIdx = html.indexOf(startMarker)
        if (startIdx == -1) return null

        val jsonStart = startIdx + startMarker.length
        // 找 JSON 结束: ;(function 或 ;window. 或 </script>
        val endMarkers = listOf(";(function", ";window.__", ";</script>")
        var jsonEnd = html.length
        for (marker in endMarkers) {
            val idx = html.indexOf(marker, jsonStart)
            if (idx != -1 && idx < jsonEnd) jsonEnd = idx
        }

        val jsonStr = html.substring(jsonStart, jsonEnd).trim()
        if (jsonStr.length < 10) return null

        return try {
            val parser = JsonParser()
            val root = parser.parse(jsonStr).asJsonObject
            val wbiImg = root.getAsJsonObject("wbiImg")
            val imgUrl = wbiImg.get("img_url").asString
            val subUrl = wbiImg.get("sub_url").asString
            imgUrl to subUrl
        } catch (_: Exception) {
            // JsonParser 太严格，尝试宽松正则匹配 wbiImg 块
            fallbackRegexExtract(html)
        }
    }

    private fun fallbackRegexExtract(html: String): Pair<String, String>? {
        // 在 __INITIAL_STATE__ 附近找 wbiImg 的 img_url 和 sub_url
        val imgUrl = Regex(""""img_url"\s*:\s*"(https://[^"]+)"""").find(html)?.groupValues?.get(1)
        val subUrl = Regex(""""sub_url"\s*:\s*"(https://[^"]+)"""").find(html)?.groupValues?.get(1)
        return if (imgUrl != null && subUrl != null) imgUrl to subUrl else null
    }

    private fun extractMixinKey(imgUrl: String, subUrl: String): String {
        val rawKey = extractFileName(imgUrl) + extractFileName(subUrl)
        val sb = StringBuilder(32)
        for (i in 0 until 32) {
            if (MIXIN_KEY_TABLE[i] < rawKey.length) {
                sb.append(rawKey[MIXIN_KEY_TABLE[i]])
            }
        }
        return sb.toString()
    }

    private fun extractFileName(url: String): String =
        url.substringAfterLast('/').substringBeforeLast('.')

    fun signParams(params: Map<String, String>, mixinKey: String): Pair<String, Long> {
        val wts = System.currentTimeMillis() / 1000
        val allParams = params.toMutableMap()
        allParams["wts"] = wts.toString()
        // web_location 必须存在，B站默认值 1550101
        if (!allParams.containsKey("web_location")) {
            allParams["web_location"] = "1550101"
        }

        val query = allParams.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                "${encode(k)}=${encode(v)}"
            }

        val sign = Md5.hash(query + mixinKey)
        return sign to wts
    }

    /** 添加 WBI2 风控参数 */
    fun addWbi2Params(params: MutableMap<String, String>) {
        val dmRand = "ABCDEFGHIJK"
        params["dm_img_list"] = "[]"
        params["dm_img_str"] = "${dmRand.random()}${dmRand.random()}"
        params["dm_cover_img_str"] = "${dmRand.random()}${dmRand.random()}"
        params["dm_img_inter"] = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
    }

    private fun encode(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~' ->
                    sb.append(c)
                else -> {
                    val bytes = c.toString().toByteArray(Charsets.UTF_8)
                    for (b in bytes) {
                        sb.append('%')
                        val hex = (b.toInt() and 0xff).toString(16).uppercase()
                        if (hex.length == 1) sb.append('0')
                        sb.append(hex)
                    }
                }
            }
        }
        return sb.toString()
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}

private data class NavResponse(
    val code: Int,
    val message: String = "",
    val data: NavData?
)
