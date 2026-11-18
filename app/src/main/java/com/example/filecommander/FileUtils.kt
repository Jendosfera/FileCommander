package com.example.filecommander

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

object FileUtils {

    /** Рекурсивно считает размер каталога в байтах. */
    fun folderSize(dir: File): Long {
        var size = 0L
        try {
            dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        } catch (_: Exception) { /* доступ запрещён — пропускаем */ }
        return size
    }

    /** Размер в МБ с 3 знаками после запятой. */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0.000 MB"
        return String.format(Locale.US, "%.3f MB", bytes / (1024.0 * 1024.0))
    }

    fun copy(src: File, target: File) {
        if (src.isDirectory) {
            src.copyRecursively(target, overwrite = true)
        } else {
            target.parentFile?.mkdirs()
            Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun move(src: File, target: File) {
        copy(src, target)
        src.deleteRecursively()
    }
}