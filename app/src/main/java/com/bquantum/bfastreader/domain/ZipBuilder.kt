package com.bquantum.bfastreader.domain

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipBuilder {

    data class ZipInput(
        val page: Int,
        val part: String,
        val markdown: String
    )

    /**
     * 构建系列字幕 ZIP 包
     * @param parts 每集信息（用于生成 P01_xxx.md 等独立文件）
     * @param mergedMarkdown 全系列合并 Markdown 内容
     * @param seriesTitle 系列名称，用于 ZIP 文件名
     * @param cacheDir 应用缓存目录
     * @return 生成的 ZIP 文件
     */
    fun buildSeriesZip(
        parts: List<ZipInput>,
        mergedMarkdown: String,
        seriesTitle: String,
        cacheDir: File
    ): File {
        val safeName = MarkdownGen.sanitizeFilename(seriesTitle)
        val zipFile = File(cacheDir, "${safeName}.zip")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // 写入每集独立文件
            for (part in parts) {
                val entryName = "P${part.page.toString().padStart(2, '0')}_${MarkdownGen.sanitizeFilename(part.part)}.md"
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(part.markdown.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 写入合并文件
            val mergedName = "全系列_${safeName}.md"
            zos.putNextEntry(ZipEntry(mergedName))
            zos.write(mergedMarkdown.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return zipFile
    }
}
