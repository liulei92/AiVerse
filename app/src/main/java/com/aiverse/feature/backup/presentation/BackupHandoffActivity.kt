package com.aiverse.feature.backup.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.backup.domain.BackupManager
import com.aiverse.feature.backup.domain.HandoffProtocol
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * beta 接收正式版回传的备份数据：通道 1 用 FileProvider URI 直读，通道 2 从 127.0.0.1 HTTP 服务下载。
 * 文件头携带随机密钥（[HandoffProtocol]），剥掉后进入常规导入；导入完成回传确认给正式版，由其清理临时文件。
 */
@AndroidEntryPoint
class BackupHandoffActivity : ComponentActivity() {

    @Inject lateinit var backupManager: BackupManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent ?: run { finish(); return }
        val requestId = intent.getStringExtra(HandoffProtocol.EXTRA_REQUEST_ID) ?: ""
        scope.launch { runCatching { importHandoff(intent, requestId) }
            .onFailure { FileLogger.e(TAG, "导入交接备份失败", it) } }
    }

    /** 按 intent 里的通道取到剥离密钥头后的备份文件，导入后回传确认。 */
    private suspend fun importHandoff(intent: Intent, requestId: String) {
        val source = withContext(Dispatchers.IO) {
            intent.getStringExtra(HandoffProtocol.EXTRA_HANDOFF_URI)?.let { uri ->
                contentResolver.openInputStream(Uri.parse(uri)) ?: return@withContext null
            } ?: downloadFromHttp(
                intent.getIntExtra(HandoffProtocol.EXTRA_HANDOFF_PORT, -1),
                intent.getStringExtra(HandoffProtocol.EXTRA_HANDOFF_FILE)
            )?.inputStream()
        } ?: run { FileLogger.w(TAG, "取不到交接备份流"); confirm(requestId); return }

        val payload = File(cacheDir, "handoff-payload.tar.gz")
        var password: CharArray? = null
        var prepared: File? = null
        try {
            source.use { input ->
                password = HandoffProtocol.stripKeyHeader(input, payload.outputStream().buffered())
            }
            prepared = backupManager.prepareImport(payload.inputStream(), password)
            val result = backupManager.import(prepared.inputStream(), null)
            FileLogger.i(TAG, if (result.isSuccess) "导入交接备份成功" else "导入交接备份失败：${result.exceptionOrNull()}")
            confirm(requestId)
        } finally {
            prepared?.delete()
            payload.delete()
            password?.fill('\u0000')
        }
    }

    /** 通道 2：从正式版的 loopback HTTP 服务下载备份文件。 */
    private fun downloadFromHttp(port: Int, fileName: String?): File? {
        if (port <= 0 || fileName == null) return null
        val file = File(cacheDir, fileName)
        return try {
            val url = java.net.URL("http://127.0.0.1:$port/$fileName")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            val input: java.io.InputStream = conn.inputStream
            input.use { stream ->
                val output = file.outputStream()
                output.use { out -> stream.copyTo(out) }
            }
            file
        } catch (e: Exception) {
            FileLogger.w(TAG, "HTTP 下载交接备份失败", e)
            null
        }
    }

    /** 回传确认给正式版（定向 com.aiverse），由其清理临时文件与 HTTP 服务。 */
    private fun confirm(requestId: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("aiverse://auth/confirmed"))
                .setPackage("com.aiverse")
                .putExtra(HandoffProtocol.EXTRA_REQUEST_ID, requestId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { FileLogger.w(TAG, "回传确认失败", it) }
    }

    private companion object { const val TAG = "BackupHandoff" }
}