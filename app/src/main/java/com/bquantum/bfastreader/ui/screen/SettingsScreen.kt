package com.bquantum.bfastreader.ui.screen

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bquantum.bfastreader.data.local.BiliCredential
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showWebView by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showWebView) showWebView = false
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showWebView) {
                LoginWebView(
                    onLoginSuccess = { cred ->
                        viewModel.onWebViewLoginSuccess(cred)
                        showWebView = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.credential.isLoggedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "已登录",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.credential.userName.ifEmpty { "B站用户" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "UID: ${state.credential.dedeuserId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(80.dp))
                    Text(
                        text = "登录后可使用完整功能\n（字幕提取、评论获取）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { showWebView = true }) {
                        Text("登录B站账号")
                    }
                    if (state.statusText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            state.statusText,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginWebView(
    onLoginSuccess: (BiliCredential) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        // 登录成功后B站会重定向到首页
                        if ((url.contains("bilibili.com") && !url.contains("passport") && !url.contains("login"))) {
                            val cookieStr = CookieManager.getInstance().getCookie(url) ?: ""
                            if (cookieStr.contains("SESSDATA")) {
                                val cred = parseCookies(cookieStr)
                                onLoginSuccess(cred)
                            }
                        }
                    }
                }

                CookieManager.getInstance().removeAllCookies(null)
                loadUrl("https://passport.bilibili.com/login")
            }
        },
        modifier = modifier
    )
}

private fun parseCookies(cookieStr: String): BiliCredential {
    val map = cookieStr.split(";").associate { part ->
        val trimmed = part.trim()
        val eq = trimmed.indexOf('=')
        if (eq > 0) trimmed.substring(0, eq) to trimmed.substring(eq + 1)
        else trimmed to ""
    }
    return BiliCredential(
        sessdata = map["SESSDATA"] ?: "",
        biliJct = map["bili_jct"] ?: "",
        buvid3 = map["buvid3"] ?: "",
        dedeuserId = map["DedeUserID"] ?: ""
    )
}
