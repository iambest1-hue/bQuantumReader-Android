package com.bquantum.bfastreader.data.model

import com.google.gson.annotations.SerializedName

data class CommentResponse(
    val code: Int,
    val message: String,
    val data: CommentData?
)

data class CommentData(
    val replies: List<Comment>?,
    val page: CommentPage?
)

data class CommentPage(
    val num: Int,
    val count: Int
)

data class Comment(
    val rpid: Long,
    val content: CommentContent?,
    val member: CommentMember?,
    val ctime: Long,
    @SerializedName("like")
    val likes: Int,
    val rcount: Int
) {
    val message: String get() = content?.message ?: ""
}

data class CommentContent(
    val message: String?
)

data class CommentMember(
    val uname: String?
)
