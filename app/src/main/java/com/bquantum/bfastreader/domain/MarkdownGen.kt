package com.bquantum.bfastreader.domain

import com.bquantum.bfastreader.data.model.Comment
import com.bquantum.bfastreader.data.model.SubtitleEntry
import com.bquantum.bfastreader.data.model.VideoInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MarkdownGen {

    fun generate(
        video: VideoInfo,
        subtitles: List<SubtitleEntry>,
        url: String,
        comments: List<Comment> = emptyList()
    ): String {
        val lines = mutableListOf<String>()

        lines.add("# ${video.title}")
        lines.add("")

        val metaParts = mutableListOf<String>()
        metaParts.add("UP主: ${video.owner.name}")
        metaParts.add("时长: ${formatDuration(video.duration)}")
        lines.add("> 来源: $url")
        lines.add("> ${metaParts.joinToString(" | ")}")

        // 视频简介
        if (!video.desc.isNullOrBlank()) {
            lines.add("")
            lines.add("> 简介: ${video.desc}")
        }

        video.stat?.let { stat ->
            val statInfo = buildString {
                append("播放: ${formatNumber(stat.view)}")
                append(" | 弹幕: ${formatNumber(stat.danmaku)}")
                append(" | 评论: ${formatNumber(stat.reply)}")
                append(" | 收藏: ${formatNumber(stat.favorite)}")
                append(" | 硬币: ${formatNumber(stat.coin)}")
                append(" | 分享: ${formatNumber(stat.share)}")
                append(" | 点赞: ${formatNumber(stat.like)}")
            }
            lines.add("> $statInfo")
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lines.add("> 提取时间: $dateStr")
        lines.add("")

        for (sub in subtitles) {
            val ts = formatTimestamp(sub.from)
            lines.add("[$ts] ${sub.content}")
        }

        lines.add("")
        lines.add("---")
        lines.add("")
        lines.add("> 共 ${subtitles.size} 条字幕 · 提取时间: $dateStr")

        if (comments.isNotEmpty()) {
            lines.add("")
            lines.add("---")
            lines.add("")
            lines.add("## 视频评论")
            lines.add("")
            lines.add("> 共 ${comments.size} 条评论（按热度排序）")
            lines.add("")

            for (c in comments) {
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(c.ctime * 1000))
                val userName = c.member?.uname ?: "匿名用户"
                val content = c.content ?: ""
                lines.add("- **$userName** ($date)")
                lines.add("  $content")
                if (c.likes > 0) lines.add("  *${c.likes} 赞*")
                lines.add("")
            }
        }

        return lines.joinToString("\n")
    }

    fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            "$h:${pad(m)}:${pad(s)}"
        } else {
            "${m}:${pad(s)}"
        }
    }

    fun formatTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "${pad(h)}:${pad(m)}:${pad(s)}"
        } else {
            "${pad(m)}:${pad(s)}"
        }
    }

    private fun pad(n: Long): String = n.toString().padStart(2, '0')

    private fun formatNumber(n: Long): String {
        return when {
            n >= 10000 -> "${n / 10000}.${(n % 10000) / 1000}万"
            else -> n.toString()
        }
    }
}
