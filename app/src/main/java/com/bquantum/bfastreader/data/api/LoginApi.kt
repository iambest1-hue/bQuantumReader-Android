package com.bquantum.bfastreader.data.api

import com.google.gson.annotations.SerializedName

data class QrCodeData(
    val url: String,
    @SerializedName("qrcode_key")
    val qrcodeKey: String
)

data class QrCodeResponse(
    val code: Int = -1,
    @SerializedName("message")
    val message: String = "",
    val data: QrCodeData?
)

data class QrPollData(
    val url: String?,
    @SerializedName("refresh_token")
    val refreshToken: String?
)

data class QrPollResponse(
    val code: Int = -1,
    @SerializedName("message")
    val message: String = "",
    val data: QrPollData?
)

data class NavUserData(
    @SerializedName("isLogin")
    val isLogin: Boolean,
    @SerializedName("uname")
    val userName: String?,
    val mid: Long?,
    @SerializedName("wbi_img")
    val wbiImg: com.bquantum.bfastreader.data.model.WbiImg?
)

object QrPollCode {
    const val SUCCESS = 0
    const val WAITING = 86038
    const val SCANNED = 86090
    const val EXPIRED = 86101
}
