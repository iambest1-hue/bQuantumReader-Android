package com.bquantum.bfastreader.data.repository

import com.bquantum.bfastreader.data.api.QrCodeResponse
import com.bquantum.bfastreader.data.api.QrPollResponse
import com.bquantum.bfastreader.data.local.BiliCredential
import com.bquantum.bfastreader.data.local.CookieProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class LoginRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val cookieProvider: CookieProvider
        get() {
            val jar = okHttpClient.cookieJar
            return jar as? CookieProvider
                ?: throw IllegalStateException("CookieJar is not CookieProvider")
        }

    suspend fun generateQrCode(): QrCodeResponse {
        return withContext(Dispatchers.IO) {
            warmUp()

            // 尝试新版端点
            val primaryUrl = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header"
            var result = tryGenerate(primaryUrl)
            if (result != null && result.code == 0 && result.data != null) return@withContext result

            // 降级：尝试不带 source 参数
            val noSourceUrl = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
            result = tryGenerate(noSourceUrl)
            if (result != null && result.code == 0 && result.data != null) return@withContext result

            // 降级：尝试旧版端点
            val legacyUrl = "https://passport.bilibili.com/qrcode/getLoginUrl"
            result = tryGenerate(legacyUrl)
            if (result != null && result.code == 0 && result.data != null) return@withContext result

            // 全部失败，重试一次：先重新 warmUp 再试
            warmUp()
            val retryResult = tryGenerate(primaryUrl)
            if (retryResult != null && retryResult.code == 0 && retryResult.data != null) return@withContext retryResult

            val retry2 = tryGenerate(noSourceUrl)
            if (retry2 != null && retry2.code == 0 && retry2.data != null) return@withContext retry2

            val retry3 = tryGenerate(legacyUrl)
            if (retry3 != null && retry3.code == 0 && retry3.data != null) return@withContext retry3

            // 全部失败，返回最后一个错误
            QrCodeResponse(-1, "获取二维码失败：${retry3?.message ?: retry2?.message ?: retryResult?.message ?: result?.message ?: "所有端点均失败"}", null)
        }
    }

    private suspend fun tryGenerate(url: String): QrCodeResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: return@use null
                    gson.fromJson(body, QrCodeResponse::class.java)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://www.bilibili.com/")
                    .header("User-Agent", UA)
                    .build()
                okHttpClient.newCall(request).execute().use { }
            } catch (_: Exception) { }
        }
    }

    suspend fun pollQrCode(qrcodeKey: String): QrPollResponse {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey")
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    gson.fromJson(body, QrPollResponse::class.java)
                }
            } catch (e: Exception) {
                QrPollResponse(-1, e.message ?: "网络错误", null)
            }
        }
    }

    /** 从 CookieJar 中提取 B站 登录凭证 */
    fun extractCredential(): BiliCredential {
        return BiliCredential(
            sessdata = cookieProvider.getCookie("SESSDATA") ?: "",
            biliJct = cookieProvider.getCookie("bili_jct") ?: "",
            buvid3 = cookieProvider.getCookie("buvid3") ?: "",
            dedeuserId = cookieProvider.getCookie("DedeUserID") ?: ""
        )
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
