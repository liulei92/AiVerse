package com.aiverse.feature.agent.domain.tool.skill

import android.content.Context
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.skill.SkillForm
import com.aiverse.feature.agent.domain.skill.SkillRepository
import com.aiverse.feature.agent.domain.skill.SkillSaveError
import com.aiverse.feature.agent.domain.skill.SkillScope
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * 让 AI 管理技能（SKILL.md）：列出、创建、编辑、删除与禁用/启用。
 *
 * 技能是 `~/.aiverse/skills/<name>/SKILL.md`（全局）或 `<projectRoot>/.aiverse/skills/<name>/SKILL.md`（项目级）
 * 下的指令文件。修改技能配置会在下一次会话生效（可用技能清单为会话级缓存）。
 *
 * 技能名是标识符，会拼进目录名与 frontmatter，统一严格 ASCII（字母/数字/连字符/下划线，≤64），与 MCP 命名一致。
 */
class ManageSkillTool @Inject constructor(
    private val skillRepository: SkillRepository,
    @param:ApplicationContext private val appContext: Context
) : AgentTool() {
    private companion object {
        const val TAG = "ManageSkillTool"
        const val MAX_NAME_LENGTH = 64
        val NAME_REGEX = Regex("[a-zA-Z0-9_-]+")
    }

    override val name = "manageSkill"
    override val description: String
        get() = appContext.getString(R.string.tool_skill_description)
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.MODIFY_AGENT_CONFIG)

    override fun effectiveCapabilities(args: Map<String, JsonElement>): Set<ToolCapability> {
        return when (args["action"]?.jsonPrimitive?.contentOrNull) {
            "list" -> setOf(ToolCapability.READ_AGENT_CONFIG)
            else -> capabilities
        }
    }

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型：list=列出技能；create=新建技能；edit=编辑技能；delete=删除技能；disable=禁用/启用技能",
            enum = listOf("list", "create", "edit", "delete", "disable")
        ),
        "name" to ToolParameter(
            name = "name",
            type = ParameterType.STRING,
            description = "技能名称。create 为新建名；edit 为要编辑的现有技能名（不改名则省略 new_name）；delete/disable 为目标技能名。只能含 ASCII 字母、数字、下划线与连字符，最长 64 字符",
            required = false
        ),
        "new_name" to ToolParameter(
            name = "new_name",
            type = ParameterType.STRING,
            description = "编辑时用于改名的目标名称（仅 edit 可选；省略表示不改名）。改名只改 SKILL.md frontmatter 的 name，不改目录名，避免正文里的脚本引用断裂",
            required = false
        ),
        "description" to ToolParameter(
            name = "description",
            type = ParameterType.STRING,
            description = "技能描述：何时使用该技能（create/edit 必填；edit 省略则保留原描述）",
            required = false
        ),
        "body" to ToolParameter(
            name = "body",
            type = ParameterType.STRING,
            description = "SKILL.md 正文：技能的完整指令内容（create/edit 必填）",
            required = false
        ),
        "scope" to ToolParameter(
            name = "scope",
            type = ParameterType.STRING,
            description = "配置作用域：global（全局，默认）或 project（当前项目，仅当前工作区生效，项目级优先于全局）",
            enum = listOf("global", "project"),
            required = false
        ),
        "enabled" to ToolParameter(
            name = "enabled",
            type = ParameterType.BOOLEAN,
            description = "是否启用（仅 disable 操作必填）：true=启用，false=禁用",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val name = args["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "未指定"
        val scope = resolveScope(args)
        val scopeLabel = scopeLabel(scope)
        val summary = when (action) {
            "create" -> "创建技能：$name（$scopeLabel）"
            "edit" -> "编辑技能：$name（$scopeLabel）"
            "delete" -> "删除技能：$name（$scopeLabel）"
            "disable" -> "设置技能状态：$name（$scopeLabel）"
            "list" -> "列出技能（$scopeLabel）"
            else -> "管理技能"
        }
        return PendingToolPermission(
            id = callId,
            toolName = this.name,
            title = appContext.getString(R.string.tool_skill_confirm_title),
            summary = summary,
            details = "操作：$action\n技能：$name\n作用域：$scopeLabel\n该操作会修改 Agent 的技能配置，将在下一次会话生效。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：action")
        val scope = resolveScope(args)
        val scopeLabel = scopeLabel(scope)

        return try {
            when (action) {
                "list" -> listSkills()
                "create" -> createSkill(args, scope, scopeLabel)
                "edit" -> editSkill(args, scope, scopeLabel)
                "delete" -> deleteSkill(args, scope, scopeLabel)
                "disable" -> setDisabled(args, scope, scopeLabel)
                else -> ToolResult.Error("未知的 action：$action")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "manage_skill 执行失败: ${e.message}", e)
            ToolResult.Error(appContext.getString(R.string.tool_skill_io_failed))
        }
    }

    /** 列出全部技能（名称/描述/来源作用域/是否禁用），同名项目级优先。 */
    private fun listSkills(): ToolResult {
        val disabled = skillRepository.disabledNames()
        val root = buildJsonObject {
            skillRepository.listAllSkills().forEach { entry ->
                putJsonObject(entry.skill.name) {
                    put("description", entry.skill.description)
                    put("scope", if (entry.scope == SkillScope.PROJECT) "project" else "global")
                    put("disabled", entry.skill.name.lowercase() in disabled)
                }
            }
        }
        return ToolResult.Success(root)
    }

    private fun createSkill(
        args: Map<String, JsonElement>,
        scope: SkillScope,
        scopeLabel: String
    ): ToolResult {
        val name = requiredName(args) ?: return ToolResult.Error("create 缺少必需参数：name")
        validateName(name)?.let { return it }
        val description = args["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val body = args["body"]?.jsonPrimitive?.contentOrNull ?: ""
        if (body.isBlank()) return ToolResult.Error(appContext.getString(R.string.tool_skill_empty_body))

        val error = skillRepository.save(
            SkillForm(name = name, description = description, instructions = body),
            scope
        )
        return error?.let { saveError(it, name) }
            ?: ToolResult.Success(JsonPrimitive(appContext.getString(R.string.tool_skill_created, scopeLabel, name)))
    }

    private fun editSkill(
        args: Map<String, JsonElement>,
        scope: SkillScope,
        scopeLabel: String
    ): ToolResult {
        val originalName = requiredName(args) ?: return ToolResult.Error("edit 缺少必需参数：name")
        val existing = skillRepository.listAllSkills().firstOrNull {
            it.scope == scope && it.skill.name.equals(originalName, ignoreCase = true)
        } ?: return ToolResult.Error(appContext.getString(R.string.tool_skill_not_found, scopeLabel, originalName))

        val body = args["body"]?.jsonPrimitive?.contentOrNull ?: ""
        if (body.isBlank()) return ToolResult.Error(appContext.getString(R.string.tool_skill_empty_body))

        val newName = args["new_name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        val targetName = newName ?: originalName
        validateName(targetName)?.let { return it }

        val description = args["description"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: existing.skill.description

        // 原样带回 required_tools / file_whitelist，避免保存后丢字段；改名只改 frontmatter，不动目录名。
        val error = skillRepository.save(
            SkillForm(
                name = targetName,
                description = description,
                instructions = body,
                requiredTools = existing.skill.requiredTools,
                fileWhitelist = existing.skill.fileWhitelist
            ),
            scope,
            originalName = originalName
        )
        return error?.let { saveError(it, targetName) }
            ?: ToolResult.Success(JsonPrimitive(appContext.getString(R.string.tool_skill_updated, scopeLabel, targetName)))
    }

    private fun deleteSkill(
        args: Map<String, JsonElement>,
        scope: SkillScope,
        scopeLabel: String
    ): ToolResult {
        val name = requiredName(args) ?: return ToolResult.Error("delete 缺少必需参数：name")
        val ok = skillRepository.deleteSkill(name, scope)
        return if (ok) {
            ToolResult.Success(JsonPrimitive(appContext.getString(R.string.tool_skill_deleted, scopeLabel, name)))
        } else {
            ToolResult.Error(appContext.getString(R.string.tool_skill_not_found, scopeLabel, name))
        }
    }

    private fun setDisabled(
        args: Map<String, JsonElement>,
        scope: SkillScope,
        scopeLabel: String
    ): ToolResult {
        val name = requiredName(args) ?: return ToolResult.Error("disable 缺少必需参数：name")
        val exists = skillRepository.listAllSkills().any {
            it.scope == scope && it.skill.name.equals(name, ignoreCase = true)
        }
        if (!exists) return ToolResult.Error(appContext.getString(R.string.tool_skill_not_found, scopeLabel, name))
        val enabled = args["enabled"]?.jsonPrimitive?.booleanOrNull
            ?: return ToolResult.Error("disable 缺少必需参数：enabled")
        skillRepository.setSkillDisabled(name, disabled = !enabled, scope)
        val res = if (enabled) R.string.tool_skill_set_enabled else R.string.tool_skill_set_disabled
        return ToolResult.Success(JsonPrimitive(appContext.getString(res, scopeLabel, name)))
    }

    private fun requiredName(args: Map<String, JsonElement>): String? =
        args["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun validateName(name: String): ToolResult? {
        if (!NAME_REGEX.matches(name) || name.length > MAX_NAME_LENGTH) {
            return ToolResult.Error(appContext.getString(R.string.tool_skill_invalid_name, name))
        }
        return null
    }

    private fun saveError(error: SkillSaveError, name: String): ToolResult = when (error) {
        SkillSaveError.INVALID_NAME -> ToolResult.Error(appContext.getString(R.string.tool_skill_invalid_name, name))
        SkillSaveError.EMPTY_INSTRUCTIONS -> ToolResult.Error(appContext.getString(R.string.tool_skill_empty_body))
        SkillSaveError.NAME_CONFLICT -> ToolResult.Error(appContext.getString(R.string.tool_skill_name_conflict, name))
        SkillSaveError.IO_FAILED -> ToolResult.Error(appContext.getString(R.string.tool_skill_io_failed))
    }

    private fun resolveScope(args: Map<String, JsonElement>): SkillScope =
        when (args["scope"]?.jsonPrimitive?.contentOrNull) {
            "project", "PROJECT" -> SkillScope.PROJECT
            else -> SkillScope.GLOBAL
        }

    private fun scopeLabel(scope: SkillScope): String =
        appContext.getString(if (scope == SkillScope.PROJECT) R.string.mcp_scope_project else R.string.mcp_scope_global)
}
