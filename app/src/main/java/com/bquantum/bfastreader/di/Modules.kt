package com.bquantum.bfastreader.di

import android.webkit.CookieManager
import com.bquantum.bfastreader.App
import com.bquantum.bfastreader.data.api.BiliApiService
import com.bquantum.bfastreader.data.api.WbiSign
import com.bquantum.bfastreader.data.local.CookieProvider
import com.bquantum.bfastreader.data.local.CredentialStorage
import com.bquantum.bfastreader.data.repository.LoginRepository
import com.bquantum.bfastreader.data.repository.VideoRepository
import com.bquantum.bfastreader.domain.LinkParser
import com.bquantum.bfastreader.ui.screen.HomeViewModel
import com.bquantum.bfastreader.ui.screen.LoginViewModel
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val appModule = module {
    single { CookieProvider() }

    single {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val cookieProvider: CookieProvider = get()

        OkHttpClient.Builder()
            .cookieJar(cookieProvider)
            .addInterceptor { chain ->
                val original = chain.request()
                val cookiesFromCm = cookieManager.getCookie(original.url.toString())

                // CookieManager 为空时从 CookieProvider 回退
                val cookieHeader = if (!cookiesFromCm.isNullOrBlank()) {
                    cookiesFromCm
                } else {
                    cookieProvider.loadForRequest(original.url)
                        .joinToString("; ") { "${it.name}=${it.value}" }
                        .ifBlank { null }
                }

                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .apply {
                        if (cookieHeader != null) {
                            header("Cookie", cookieHeader)
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single { Gson() }

    single { CredentialStorage(App.instance) }

    single { LinkParser(get()) }

    single { WbiSign(get(), get()) }

    single {
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single {
        get<Retrofit>().create(BiliApiService::class.java)
    }

    single {
        VideoRepository(get(), get(), get(), get())
    }

    single {
        LoginRepository(get(), get())
    }

    viewModel { LoginViewModel(get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
}
