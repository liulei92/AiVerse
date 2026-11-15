package com.aiverse.feature.backup.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.feature.backup.data.BetaHandoffRepository
import com.aiverse.feature.backup.domain.HandoffProtocol
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 测试版侧的「从正式版导入数据」引导卡。
 *
 * 只在 [BetaHandoffPromptHost] 判定为 true 时弹出：当前是测试版包、本机已装正式版、
 * 自身还没有数据、用户此前没点过「暂不」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaHandoffPromptCard(
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalUIDensityTokens.current

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0.dp) },
        dialogMaxWidth = 600.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(t.lg)
                .padding(bottom = t.xl)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FeatherIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(t.scale(20))
                )
                Spacer(Modifier.width(t.md))
                Text(
                    text = stringResource(R.string.beta_handoff_prompt_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(t.md))
            Text(
                text = stringResource(R.string.beta_handoff_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(t.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(t.sm)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(t.touchTarget)
                ) {
                    Text(stringResource(R.string.common_later))
                }
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).height(t.touchTarget)
                ) {
                    Text(
                        text = stringResource(R.string.backup_import_btn),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * 挂载在 [com.aiverse.MainActivity] 的同意流程通过之后：只读 [BetaHandoffRepository.promptVisibleFlow]，
 * 命中才展示 [BetaHandoffPromptCard]。用户点「导入」或「暂不」都会持久化，不重复打扰。
 */
@Composable
fun BetaHandoffPromptHost(repository: BetaHandoffRepository) {
    val visible by repository.promptVisibleFlow.collectAsStateWithLifecycle(initialValue = false)
    if (!visible) return
    BetaHandoffPromptSheet(repository = repository)
}

/** 引导卡自身：本次安装内只展示一次，关闭后从组合树里移除。 */
@Composable
private fun BetaHandoffPromptSheet(repository: BetaHandoffRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showing by remember { mutableStateOf(true) }
    if (!showing) return

    BetaHandoffPromptCard(
        onImport = {
            showing = false
            scope.launch { repository.markPromptDismissed() }
            launchReleaseAuthGrant(context)
        },
        onDismiss = {
            showing = false
            scope.launch { repository.markPromptDismissed() }
        }
    )
}

/**
 * 用 deep link 拉起正式版侧的授权页。
 *
 * `requestId` 供正式版回执时原样带回，`requester` 记录发起接管的包名（测试版为
 * `com.aiverse.beta`，取当前包名而非硬编码）。目标包不存在时静默失败，不打断当前页面。
 */
fun launchReleaseAuthGrant(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BetaHandoffRepository.AUTH_GRANT_URI))
        .setPackage(BetaHandoffRepository.RELEASE_PACKAGE)
        .putExtra(HandoffProtocol.EXTRA_REQUEST_ID, UUID.randomUUID().toString())
        .putExtra(BetaHandoffRepository.EXTRA_REQUESTER, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}