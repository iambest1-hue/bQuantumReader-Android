package com.bquantum.bfastreader.data.api

import com.bquantum.bfastreader.data.model.BiliResponse
import com.bquantum.bfastreader.data.model.CommentResponse
import com.bquantum.bfastreader.data.model.NavData
import retrofit2.http.GET
import retrofit2.http.Query

interface BiliApiService {

    @GET("x/web-interface/view")
    suspend fun getVideoInfo(
        @Query("bvid") bvid: String
    ): BiliResponse<com.bquantum.bfastreader.data.model.VideoInfo>

    @GET("x/web-interface/nav")
    suspend fun getNav(): BiliResponse<NavData>

    @GET("x/player/wbi/v2")
    suspend fun getSubtitleList(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: Long,
        @Query("isGaiaAvoided") isGaiaAvoided: String = "false",
        @Query("web_location") webLocation: String = "1315873",
        @Query("dm_img_list") dmImgList: String = "[]",
        @Query("dm_img_str") dmImgStr: String = "AA",
        @Query("dm_cover_img_str") dmCoverImgStr: String = "AA",
        @Query("dm_img_inter") dmImgInter: String = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
    ): BiliResponse<com.bquantum.bfastreader.data.model.PlayerData>

    @GET("x/player/v2")
    suspend fun getPlayerData(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long
    ): BiliResponse<com.bquantum.bfastreader.data.model.PlayerData>

    @GET("x/web-interface/wbi/view")
    suspend fun getWbiVideoInfo(
        @Query("bvid") bvid: String,
        @Query("w_rid") wRid: String,
        @Query("wts") wts: Long,
        @Query("dm_img_list") dmImgList: String = "[]",
        @Query("dm_img_str") dmImgStr: String = "AA",
        @Query("dm_cover_img_str") dmCoverImgStr: String = "AA",
        @Query("dm_img_inter") dmImgInter: String = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
    ): BiliResponse<com.bquantum.bfastreader.data.model.VideoInfo>

    // 评论
    @GET("x/v2/reply/main")
    suspend fun getComments(
        @Query("type") type: Int,
        @Query("oid") oid: Long,
        @Query("mode") mode: Int = 3,
        @Query("ps") ps: Int = 20,
        @Query("pn") pn: Int = 1
    ): CommentResponse

    // QR 码登录
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): QrCodeResponse

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(
        @Query("qrcode_key") qrcodeKey: String
    ): QrPollResponse
}
