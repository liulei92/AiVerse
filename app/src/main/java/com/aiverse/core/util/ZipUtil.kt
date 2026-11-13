package com.aiverse.core.util

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把若干条目打包为内存 zip 字节流。
 *
 * [entries] 每项为（zip 内相对路径, 文件字节）；路径含 `/` 时由 [ZipOutputStream] 自动建父目录，
 * 因此不需要显式写目录条目。供技能分享/导出、文件树打包等共用，避免各处重复写 ZipOutputStream 样板。
 *
 * 任何一步失败（如写入异常）返回 null，调用方据此提示失败。
 */
fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray? = runCatching {
    ByteArrayOutputStream().use { bos ->
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        bos.toByteArray()
    }
}.getOrNull()
