package com.bquantum.bfastreader.data.model

import com.google.gson.annotations.SerializedName

data class BiliResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class VideoInfo(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    @SerializedName("pic")
    val coverUrl: String,
    val duration: Long,
    val desc: String?,
    val owner: VideoOwner,
    val pages: List<VideoPage>?,
    val subtitle: VideoSubtitle?,
    val stat: VideoStat?
)

data class VideoStat(
    val view: Long,
    val danmaku: Long,
    val reply: Long,
    val favorite: Long,
    val coin: Long,
    val share: Long,
    val like: Long
)

data class VideoSubtitle(
    val list: List<SubtitleInfo>?
)

data class VideoOwner(
    val name: String,
    val face: String?
)

data class VideoPage(
    val cid: Long,
    val page: Int,
    val part: String
)

data class NavData(
    @SerializedName("isLogin")
    val isLogin: Boolean?,
    @SerializedName("uname")
    val userName: String?,
    val mid: Long?,
    @SerializedName("wbi_img")
    val wbiImg: WbiImg?
)

data class WbiImg(
    @SerializedName("img_url")
    val imgUrl: String,
    @SerializedName("sub_url")
    val subUrl: String
)

data class PlayerData(
    val subtitle: SubtitleData?
)

data class SubtitleData(
    val subtitles: List<SubtitleInfo>?
)

data class SubtitleInfo(
    val id: Long?,
    @SerializedName("subtitle_url")
    val subtitleUrl: String,
    @SerializedName("lan_doc")
    val language: String,
    @SerializedName("lan")
    val langCode: String
)

data class SubtitleBody(
    val body: List<SubtitleRawEntry>?
)

data class SubtitleRawEntry(
    val from: Double,
    val to: Double,
    val content: String
)
