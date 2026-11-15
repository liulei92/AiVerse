package com.aiverse.feature.backup.domain

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * 正式版 → beta 的备份数据交接协议。
 *
 * 加密密钥不经过 Intent extra（跨包 extra 会被系统用于 Activity 匹配查询而暴露），
 * 而是随数据一起落在文件头：`AIVERSE-HANDOFF v1 <hex密钥>`，之后紧接 [BackupCrypto] 的分块加密流。
 * beta 端用 [stripKeyHeader] 剥掉这一行拿到口令与明文载荷，再走常规导入链路。
 */
object HandoffProtocol {

    /** beta 包的 applicationId（正式版在 [AuthGrantActivity] 里做签名比对与 setPackage 定向）。 */
    const val BETA_PACKAGE = "com.aiverse.beta"

    // ── Intent 协议 ──────────────────────────────────────────

    /** beta → 正式版：授权请求标识。 */
    const val EXTRA_REQUEST_ID = "requestId"
    /** 正式版 → beta：通道 1 的 FileProvider URI。 */
    const val EXTRA_HANDOFF_URI = "handoffUri"
    /** 正式版 → beta：通道 2 的监听端口。 */
    const val EXTRA_HANDOFF_PORT = "handoffPort"
    /** 正式版 → beta：通道 2 的文件名（也是 HTTP 请求路径）。 */
    const val EXTRA_HANDOFF_FILE = "handoffFile"

    private const val KEY_BYTES = 32

    private val random = SecureRandom()

    /** 随机 256 位口令：仅用于把备份文件加密成一个不可预测的密钥，不落盘、不记忆。 */
    fun newKeyPassword(): CharArray {
        val bytes = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }.toCharArray()
    }

    /** 把密钥写进文件头。 */
    fun writeKeyHeader(output: OutputStream, password: CharArray) {
        output.write(headerLine(password).toByteArray(Charsets.US_ASCII))
    }

    /** 剥掉文件头，返回口令；[output] 只收到头行之后的加密载荷。 */
    fun stripKeyHeader(input: InputStream, output: OutputStream): CharArray {
        var lineLen = 0
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw IllegalArgumentException("备份文件缺少交接密钥头")
            if (b == '\n'.code) break
            line.append(b.toChar())
            lineLen++
            if (lineLen > 512) throw IllegalArgumentException("备份文件的交接密钥头已损坏")
        }
        val marker = "AIVERSE-HANDOFF v1 "
        if (!line.startsWith(marker)) throw IllegalArgumentException("不是有效的交接备份文件")
        val hex = line.substring(marker.length)
        return ByteArray(hex.length / 2)
            .also { bytes -> for (i in bytes.indices) bytes[i] = (hex.substring(i * 2, i * 2 + 2).toInt(16)).toByte() }
            .toHexString().toCharArray()
            .also { copyRest(input, output) }
    }

    private fun headerLine(password: CharArray): String = "AIVERSE-HANDOFF v1 " + String(password)

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun copyRest(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
        }
        output.flush()
    }
}