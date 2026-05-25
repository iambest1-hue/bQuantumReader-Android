package com.bquantum.bfastreader.ui.screen

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bquantum.bfastreader.data.api.BiliApiService
import com.bquantum.bfastreader.data.api.WbiSign
import com.bquantum.bfastreader.data.local.BiliCredential
import com.bquantum.bfastreader.data.local.CredentialStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val statusText: String = "",
    val credential: BiliCredential = BiliCredential()
)

class LoginViewModel(
    private val api: BiliApiService,
    private val credentialStorage: CredentialStorage,
    private val wbiSign: WbiSign
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private var switchingAccount = false


    init {
        viewModelScope.launch {
            credentialStorage.credential.collect { cred ->
                if (cred.isLoggedIn && !_state.value.credential.isLoggedIn) {
                    _state.update { it.copy(credential = cred) }
                }
            }
        }
    }
    fun onWebViewLoginSuccess(cred: BiliCredential) {
        viewModelScope.launch {
            try {
                // 切换账号时，先清除旧凭证
                if (switchingAccount) {
                    switchingAccount = false
                    wbiSign.clearCache()
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.flush()
                }
                _state.update { it.copy(statusText = "正在获取用户信息...") }

                // 关键：显式将 cookie 种到 api.bilibili.com 域名
                // WebView 登录后的 cookie 域名可能不覆盖 api 子域
                spreadCookiesToApiDomain(cred)

                // 通过 nav API 获取用户名
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
            } catch (e: Exception) {
                credentialStorage.save(cred)
                _state.update { it.copy(statusText = "", credential = cred) }
            }
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
        // 强制刷新 CookieManager
        cm.flush()
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun logout() {
        viewModelScope.launch {
            credentialStorage.clear()
            wbiSign.clearCache()
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            _state.update { LoginUiState() }
        }
    }

    fun prepareSwitchAccount() {
        switchingAccount = true
    }
}
