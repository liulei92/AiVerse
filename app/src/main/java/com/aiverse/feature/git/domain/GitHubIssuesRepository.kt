package com.aiverse.feature.git.domain

import com.aiverse.feature.credentials.domain.repository.CredentialRepository
import com.aiverse.feature.git.domain.model.GitHubComment
import com.aiverse.feature.git.domain.model.GitHubIssue
import com.aiverse.feature.git.domain.model.GitHubMilestone
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 Git remote URL 解析 GitHub 的 owner/repo。
 *
 * 支持 `https://github.com/owner/repo.git`、`https://github.com/owner/repo`、`git@github.com:owner/repo.git`
 * 三种形式；非 GitHub 仓库或格式无法解析时返回 null。
 */
internal fun parseGithubRepoUrl(url: String): Pair<String, String>? {
    val trimmed = url.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null

    // SSH 形式：git@github.com:owner/repo.git
    Regex("""^git@github\.com:([^/]+)/(.+?)(?:\.git)?$""").find(trimmed)?.let { m ->
        val owner = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")
        if (owner.isNotBlank() && repo.isNotBlank()) return owner to repo
        return null
    }

    // HTTPS 形式：https://github.com/owner/repo(.git)
    val withoutScheme = trimmed.removePrefix("https://").removePrefix("http://")
    if (!withoutScheme.startsWith("github.com/")) return null
    val parts = withoutScheme.removePrefix("github.com/").removeSuffix(".git").split("/")
    if (parts.size < 2) return null
    val owner = parts[0]
    val repo = parts[1]
    if (owner.isBlank() || repo.isBlank()) return null
    return owner to repo
}

/** GitHub issue 请求失败（网络、HTTP 状态码、解析、目标不合法）。 */
class GitHubIssuesException(message: String) : Exception(message)

/**
 * 从 GitHub REST API 拉取当前仓库的 issue 列表、详情，并执行 issue 写操作。
 *
 * 每次请求实时解析当前 remote 的 owner/repo 并读取 github.com 的 token；host 非 github.com
 * 或无 token 时返回 [Result.failure]。网络在 [Dispatchers.IO] 执行，失败统一走失败结果。
 * 写操作（create/edit/close/comment/label/assignee/milestone/lock）仅 GitHub 且有 token 时执行，
 * 由上层工具层负责 ASK 权限确认，本类不做静默远端写入。
 */
@Singleton
class GitHubIssuesRepository @Inject constructor(
    private val client: OkHttpClient,
    private val credentialRepository: CredentialRepository,
    private val gitRepository: GitRepository
) {
    private companion object {
        const val GITHUB_HOST = "github.com"
        const val API_BASE = "https://api.github.com/repos"
        const val PER_PAGE = 100
        const val JSON_MEDIA = "application/vnd.github+json"
        const val CONTENT_TYPE = "application/json; charset=utf-8"
    }

    private val gson = Gson()

    /** 当前仓库的 GitHub issue 列表（已过滤掉 Pull Request 项）。 */
    suspend fun listIssues(state: String = "open"): Result<List<GitHubIssue>> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues?state=$state&per_page=$PER_PAGE"
                val body = execute(url, token)
                val type = object : TypeToken<List<GitHubIssue>>() {}.type
                val issues: List<GitHubIssue> = gson.fromJson(body, type)
                Result.success(issues.filter { !it.isPullRequest })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 单条 GitHub issue 详情。 */
    suspend fun getIssue(number: Long): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number"
                val body = execute(url, token)
                Result.success(gson.fromJson(body, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 单条 issue 的评论列表。 */
    suspend fun getComments(number: Long): Result<List<GitHubComment>> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number/comments?per_page=$PER_PAGE"
                val body = execute(url, token)
                val type = object : TypeToken<List<GitHubComment>>() {}.type
                Result.success(gson.fromJson(body, type))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 创建 issue；labels/assignees 为名称/login 列表，均可为 null。 */
    suspend fun createIssue(
        title: String,
        body: String? = null,
        labels: List<String>? = null,
        assignees: List<String>? = null
    ): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val payload = HashMap<String, Any>()
                payload["title"] = title
                if (!body.isNullOrBlank()) payload["body"] = body
                if (!labels.isNullOrEmpty()) payload["labels"] = labels
                if (!assignees.isNullOrEmpty()) payload["assignees"] = assignees
                val url = "$API_BASE/$owner/$repo/issues"
                val resp = execute(url, token, method = "POST", jsonBody = gson.toJson(payload))
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 编辑 issue 的标题/正文（null 表示不改）。 */
    suspend fun editIssue(
        number: Long,
        title: String? = null,
        body: String? = null
    ): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val payload = HashMap<String, Any>()
                title?.let { payload["title"] = it }
                body?.let { payload["body"] = it }
                val url = "$API_BASE/$owner/$repo/issues/$number"
                val resp = execute(url, token, method = "PATCH", jsonBody = gson.toJson(payload))
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 设置 issue 状态：\"open\" / \"closed\"；可选传 [stateReason]（"completed"/"not_planned"）写入 state_reason。 */
    suspend fun setIssueState(number: Long, state: String, stateReason: String? = null): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number"
                val body = if (stateReason != null) {
                    gson.toJson(mapOf("state" to state, "state_reason" to stateReason))
                } else {
                    gson.toJson(mapOf("state" to state))
                }
                val resp = execute(url, token, method = "PATCH", jsonBody = body)
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 关闭 issue；可选传 [stateReason]（"completed"/"not_planned"）写入 state_reason。 */
    suspend fun closeIssue(number: Long, stateReason: String? = null): Result<GitHubIssue> =
        setIssueState(number, "closed", stateReason)

    /** 重开 issue。 */
    suspend fun reopenIssue(number: Long): Result<GitHubIssue> = setIssueState(number, "open")

    /** 添加评论，返回创建的评论。 */
    suspend fun addComment(number: Long, body: String): Result<GitHubComment> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number/comments"
                val resp = execute(url, token, method = "POST", jsonBody = gson.toJson(mapOf("body" to body)))
                Result.success(gson.fromJson(resp, GitHubComment::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 编辑评论，返回更新后的评论。 */
    suspend fun editComment(commentId: Long, body: String): Result<GitHubComment> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/comments/$commentId"
                val resp = execute(url, token, method = "PATCH", jsonBody = gson.toJson(mapOf("body" to body)))
                Result.success(gson.fromJson(resp, GitHubComment::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 删除评论。 */
    suspend fun deleteComment(commentId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/comments/$commentId"
                execute(url, token, method = "DELETE")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 为 issue 追加标签（按名称），返回更新后的 issue。 */
    suspend fun addLabels(number: Long, labels: List<String>): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number/labels"
                val resp = execute(url, token, method = "POST", jsonBody = gson.toJson(labels))
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 从 issue 移除单个标签（按名称），返回更新后的 issue。 */
    suspend fun removeLabel(number: Long, label: String): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val encoded = java.net.URLEncoder.encode(label, "UTF-8").replace("+", "%20")
                val url = "$API_BASE/$owner/$repo/issues/$number/labels/$encoded"
                val resp = execute(url, token, method = "DELETE")
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 仓库贡献者（含 committer 与协作者）的 login 列表，供受理人选择。 */
    suspend fun getContributors(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/contributors?per_page=$PER_PAGE"
                val body = execute(url, token)
                val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                val list: List<Map<String, Any?>> = gson.fromJson(body, type)
                Result.success(list.mapNotNull { it["login"] as? String }.filter { it.isNotBlank() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 设置 issue 受理人（按 login 列表，空列表表示清空），返回更新后的 issue。 */
    suspend fun setAssignees(number: Long, assignees: List<String>): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number"
                val resp = execute(url, token, method = "PATCH", jsonBody = gson.toJson(mapOf("assignees" to assignees)))
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 仓库里程碑列表（open + closed），供设置里程碑。 */
    suspend fun getMilestones(): Result<List<GitHubMilestone>> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/milestones?state=all&per_page=$PER_PAGE"
                val body = execute(url, token)
                val type = object : TypeToken<List<GitHubMilestone>>() {}.type
                Result.success(gson.fromJson(body, type))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 设置 issue 里程碑（null 表示清除），返回更新后的 issue。 */
    suspend fun setMilestone(number: Long, milestoneNumber: Long?): Result<GitHubIssue> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number"
                val payload = HashMap<String, Any?>()
                payload["milestone"] = milestoneNumber
                val resp = execute(url, token, method = "PATCH", jsonBody = gson.toJson(payload))
                Result.success(gson.fromJson(resp, GitHubIssue::class.java))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 锁定（locked=true）/解锁（locked=false）issue，限制非协作者讨论。 */
    suspend fun setIssueLock(number: Long, locked: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveTarget()
                if (target == null) return@withContext errorResult()
                val (owner, repo, token) = target
                val url = "$API_BASE/$owner/$repo/issues/$number/lock"
                execute(url, token, method = if (locked) "PUT" else "DELETE")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 当前仓库是否配置了 GitHub token（供 ViewModel 计算 [hasGitHubToken] 状态）。 */
    suspend fun hasGitHubToken(): Boolean =
        credentialRepository.findForHost(GITHUB_HOST)?.token?.isNotBlank() == true

    /** 解析当前 remote 为 (owner, repo, token)；非 GitHub 或无 token 返回 null。 */
    private suspend fun resolveTarget(): Triple<String, String, String>? {
        val url = gitRepository.getRepoUrl()
        val pair = parseGithubRepoUrl(url) ?: return null
        val token = credentialRepository.findForHost(GITHUB_HOST)?.token?.takeIf { it.isNotBlank() }
            ?: return null
        return Triple(pair.first, pair.second, token)
    }

    /** 统一失败结果（非 GitHub / 无 token 时），错误信息仅用于日志与通用失败态，不直接展示给用户。 */
    private fun errorResult(): Result<Nothing> =
        Result.failure(GitHubIssuesException("GitHub issues unavailable: repository is not GitHub or token is missing"))

    /**
     * 执行一次 GitHub API 请求。GET 无 body；POST/PATCH 带 [jsonBody]；PUT/DELETE 无 body（部分返回 204 空体）。
     * 非 2xx 时抛 [GitHubIssuesException]（含状态码与响应体）。
     */
    private fun execute(
        url: String,
        token: String,
        method: String = "GET",
        jsonBody: String? = null
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", JSON_MEDIA)
            .header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> {
                val mediaType = CONTENT_TYPE.toMediaType()
                val body: RequestBody = jsonBody?.toRequestBody(mediaType)
                    ?: "{}".toRequestBody(mediaType)
                builder.method(method, body)
            }
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = runCatching { resp.body?.string() }.getOrNull()
                throw GitHubIssuesException("HTTP ${resp.code}${errBody?.let { ": $it" } ?: ""}")
            }
            return resp.body?.string().orEmpty()
        }
    }
}
