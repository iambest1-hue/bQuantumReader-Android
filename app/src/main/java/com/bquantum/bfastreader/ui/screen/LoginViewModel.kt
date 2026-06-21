package com.bquantum.bfastreader.ui.screen

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bquantum.bfastreader.data.api.BiliApiService
import com.bquantum.bfastreader.data.api.QrPollCode
import com.bquantum.bfastreader.data.api.WbiSign
import com.bquantum.bfastreader.data.local.BiliCredential
import com.bquantum.bfastreader.data.local.CredentialStorage
import com.bquantum.bfastreader.data.repository.LoginRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class QrPhase {
    IDLE,
    SHOWING,
    SCANNED,
    EXPIRED,
    FAILED
}

data class LoginUiState(
    val statusText: String = "",
    val credential: BiliCredential = BiliCredential(),
    val qrUrl: String? = null,
    val qrcodeKey: String? = null,
    val qrPhase: QrPhase = QrPhase.IDLE,
    val isPolling: Boolean = false
)

class LoginViewModel(
    private val api: BiliApiService,
    private val credentialStorage: CredentialStorage,
    private val wbiSign: WbiSign,
    private val loginRepository: LoginRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            credentialStorage.credential.collect { cred ->
                _state.update { it.copy(credential = cred) }
            }
        }
    }

    fun refreshCredential() {
        viewModelScope.launch {
            _state.update { it.copy(credential = credentialStorage.credential.first()) }
        }
    }

    /** 启动 QR 扫码登录 */
    fun startQrLogin() {
        _state.update { it.copy(qrPhase = QrPhase.IDLE, qrUrl = null, qrcodeKey = null, isPolling = false) }
        pollJob?.cancel()

        viewModelScope.launch {
            _state.update { it.copy(statusText = "正在获取二维码...") }
            try {
                val resp = loginRepository.generateQrCode()
                if (resp.code == 0 && resp.data != null) {
                    _state.update {
                        it.copy(
                            statusText = "",
                            qrUrl = resp.data.url,
                            qrcodeKey = resp.data.qrcodeKey,
                            qrPhase = QrPhase.SHOWING
                        )
                    }
                    pollQrLogin(resp.data.qrcodeKey)
                } else {
                    _state.update { it.copy(statusText = "获取二维码失败", qrPhase = QrPhase.FAILED) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(statusText = e.message ?: "获取二维码失败", qrPhase = QrPhase.FAILED) }
            }
        }
    }

    /** 轮询扫码状态 */
    private fun pollQrLogin(qrcodeKey: String) {
        pollJob = viewModelScope.launch {
            _state.update { it.copy(isPolling = true) }
            while (isActive) {
                delay(2000)
                try {
                    val resp = loginRepository.pollQrCode(qrcodeKey)
                    when (resp.code) {
                        QrPollCode.WAITING -> {
                            // 保持 SHOWING 状态，继续轮询
                        }
                        QrPollCode.SCANNED -> {
                            _state.update { it.copy(qrPhase = QrPhase.SCANNED) }
                        }
                        QrPollCode.SUCCESS -> {
                            _state.update { it.copy(qrPhase = QrPhase.IDLE, isPolling = false) }
                            val cred = loginRepository.extractCredential()
                            finalizeLogin(cred)
                            return@launch
                        }
                        QrPollCode.EXPIRED -> {
                            _state.update { it.copy(qrPhase = QrPhase.EXPIRED, isPolling = false) }
                            return@launch
                        }
                    }
                } catch (_: Exception) {
                    // 网络错误不中断轮询
                }
            }
        }
    }

    /** 刷新二维码 */
    fun refreshQrCode() {
        startQrLogin()
    }

    /** WebView 登录成功后的处理 */
    fun onWebViewLoginSuccess(cred: BiliCredential) {
        viewModelScope.launch {
            try {
                wbiSign.clearCache()
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()

                _state.update { it.copy(statusText = "正在获取用户信息...") }
                spreadCookiesToApiDomain(cred)
                finalizeLogin(cred)
            } catch (_: Exception) {
                credentialStorage.save(cred)
                _state.update { it.copy(statusText = "", credential = cred) }
            }
        }
    }

    /** 登录完成的公共流程：spread cookies → nav 富化 → 保存 */
    private suspend fun finalizeLogin(cred: BiliCredential) {
        wbiSign.clearCache()
        spreadCookiesToApiDomain(cred)

        try {
            val navResp = api.getNav()
            if (navResp.code == 0 && navResp.data != null) {
                val nav = navResp.data
                val fullCred = cred.copy(
                    userName = nav.userName ?: "",
                    dedeuserId = nav.mid?.toString() ?: cred.dedeuserId,
                    avatarUrl = nav.face ?: ""
                )
                credentialStorage.save(fullCred)
                _state.update { it.copy(statusText = "", credential = fullCred) }
            } else {
                credentialStorage.save(cred)
                _state.update { it.copy(statusText = "", credential = cred) }
            }
        } catch (_: Exception) {
            credentialStorage.save(cred)
            _state.update { it.copy(statusText = "", credential = cred) }
        }
    }

    /** 将登录 cookie 显式种到 api.bilibili.com，确保 API 调用能携带 */
    private fun spreadCookiesToApiDomain(cred: BiliCredential) {
        val cm = CookieManager.getInstance()
        val apiUrl = "https://api.bilibili.com"
        if (cred.sessdata.isNotBlank()) {
            cm.setCookie(apiUrl, "SESSDATA=${cred.sessdata}; domain=.bilibili.com; path=/; secure")
        }
        if (cred.biliJct.isNotBlank()) {
            cm.setCookie(apiUrl, "bili_jct=${cred.biliJct}; domain=.bilibili.com; path=/")
        }
        if (cred.buvid3.isNotBlank()) {
            cm.setCookie(apiUrl, "buvid3=${cred.buvid3}; domain=.bilibili.com; path=/")
        }
        if (cred.dedeuserId.isNotBlank()) {
            cm.setCookie(apiUrl, "DedeUserID=${cred.dedeuserId}; domain=.bilibili.com; path=/")
        }
        cm.flush()
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    fun logout() {
        pollJob?.cancel()
        viewModelScope.launch {
            credentialStorage.clear()
            wbiSign.clearCache()
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            _state.update { LoginUiState() }
        }
    }
}
