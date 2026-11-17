package com.aiverse.feature.backup.data

import com.aiverse.core.util.FileLogger
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 127.0.0.1 上的临时 HTTP 服务器，把单个备份文件通过 loopback 交给同机的 beta 进程。
 *
 * 只服务一个文件：请求路径必须等于文件名，否则 404；处理完一个请求后继续监听，便于 beta 重试。
 * 只绑 127.0.0.1（不绑 0.0.0.0），[stop] 或超时 [TIMEOUT_MS] 后自动关闭并删掉被服务的文件。
 * 不引入第三方库，[ServerSocket] + 手写最简 HTTP 响应即可。
 */
class HandoffHttpServer(
    /** 停止时是否删除被服务的文件（数据已交付/超时废弃时都是删除）。 */
    private val deleteFileOnStop: Boolean = false,
) {
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    /** 实际绑定的端口；[start] 成功后可用（[start] 传 0 时由系统分配）。 */
    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    /** 启动服务器，返回监听端口。端口为 0 时自动选可用端口。 */
    fun start(file: File, port: Int = 0): Int {
        if (!file.isFile) throw IOException("要服务的文件不存在：${file.name}")
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        serverSocket = socket
        this.port = socket.localPort
        isRunning = true
        running.set(true)

        val thread = Thread({ acceptLoop(file) }, "handoff-http")
        thread.isDaemon = true
        worker = thread
        thread.start()

        // 60 秒兜底：beta 没来取也自动关、并删掉待服务的文件。
        autoStop = timeoutExecutor.schedule({ stop() }, TIMEOUT_MS, TimeUnit.MILLISECONDS)

        FileLogger.i(TAG, "交接 HTTP 服务器已启动：127.0.0.1:${socket.localPort} 服务 ${file.name}")
        return socket.localPort
    }

    private var autoStop: java.util.concurrent.ScheduledFuture<*>? = null

    private val timeoutExecutor: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "handoff-http-timeout").apply { isDaemon = true }
        }
    }

    /** 停止服务器，释放端口。 */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        isRunning = false
        autoStop?.cancel(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (deleteFileOnStop) {
            runCatching { servedFile?.delete() }
            servedFile = null
        }
        FileLogger.i(TAG, "交接 HTTP 服务器已停止")
    }

    private var servedFile: File? = null

    private fun acceptLoop(file: File) {
        servedFile = file
        val socket = serverSocket ?: return
        try {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (running.get()) FileLogger.w(TAG, "交接 HTTP 接受连接失败", e)
                    break
                }
                handleClient(client, file)
            }
        } finally {
            runCatching { serverSocket?.close() }
            serverSocket = null
            isRunning = false
        }
    }

    /** 处理单个请求；请求异常只断开这个连接，不影响后续请求（beta 可能重试）。 */
    private fun handleClient(client: Socket, file: File) {
        try {
            client.use { conn ->
                conn.soTimeout = REQUEST_TIMEOUT_MS.toInt()
                val requestLine = conn.getInputStream().bufferedReader().readLine().orEmpty()
                val target = requestLine.split(' ').getOrNull(1).orEmpty()
                val path = target.substringBefore('?')
                if (path != "/" + file.name) {
                    writeResponse(conn, 404, "Not Found", null)
                    return
                }
                writeResponse(conn, 200, "OK", file)
            }
        } catch (e: IOException) {
            FileLogger.w(TAG, "交接 HTTP 请求处理失败", e)
        }
    }

    private fun writeResponse(conn: Socket, code: Int, message: String, file: File?) {
        val headers = buildString {
            append("HTTP/1.1 $code $message\r\n")
            append("Content-Type: application/octet-stream\r\n")
            if (file != null) {
                append("Content-Length: ${file.length()}\r\n")
                append("Accept-Ranges: bytes\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        conn.getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            if (file != null) file.inputStream().use { it.copyTo(this) }
            flush()
        }
    }

    private companion object {
        private const val TAG = "HandoffHttp"
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val TIMEOUT_MS = 60L
    }
}