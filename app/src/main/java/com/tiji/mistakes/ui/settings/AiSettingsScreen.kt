@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiTextField
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.OcrModelDownloadService
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.settings.components.CombinedOcrSettingsCard
import com.tiji.mistakes.ui.design.TijiSettingGroup
import java.util.UUID
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun AiSettingsScreen(
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    aiVisualProfiles: List<AiVisualProfile>,
    aiVisualBindings: Map<String, String>,
    ocrModelManager: OcrModelManager,
    onSaveAiConfig: (String, String) -> Unit,
    onAiProfiles: (List<AiProfile>) -> Unit,
    onActiveAiProfile: (String) -> Unit,
    onOpenVisualAssistConfig: (String) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureKeyStore(context) }
    val ocrModelState by ocrModelManager.combinedState.collectAsStateWithLifecycle()
    val aiService = remember { AiVisionService() }
    var selectedProfileId by remember(activeAiProfileId) { mutableStateOf(activeAiProfileId) }
    val selectedProfile = aiProfiles.firstOrNull { it.id == selectedProfileId }
    var profileName by remember(selectedProfileId, aiProfiles) {
        mutableStateOf(selectedProfile?.name ?: "默认 AI")
    }
    var preset by remember(selectedProfileId, selectedProfile?.endpoint, selectedProfile?.model) {
        mutableStateOf(AiProviderPreset.detect(selectedProfile?.endpoint ?: aiEndpoint, selectedProfile?.model ?: aiModel))
    }
    var endpoint by remember(selectedProfileId, selectedProfile?.endpoint) {
        mutableStateOf(selectedProfile?.endpoint ?: aiEndpoint)
    }
    var model by remember(selectedProfileId, selectedProfile?.model) {
        mutableStateOf(selectedProfile?.model ?: aiModel)
    }
    var apiKey by remember(selectedProfileId) { mutableStateOf(secureStore.read(selectedProfileId)) }
    val selectedVisualProfile = aiVisualProfiles.firstOrNull { it.id == aiVisualBindings[selectedProfileId] }
    var connectionMessage by remember { mutableStateOf("") }

    SettingsPageScaffold(title = "AI 模型", pageTag = "settings_ai", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("settings_ai_list"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TijiSettingGroup("AI 模型配置", Icons.Outlined.AutoAwesome) {
                    Text(
                        "按需配置，未配置时核心功能完全离线。每套配置独立保存服务商、模型和本机密钥。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("已保存配置", style = MaterialTheme.typography.labelLarge)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("ai_profile_list")
                    ) {
                        items(aiProfiles, key = { it.id }) { profile ->
                            TijiChip(
                                selected = selectedProfileId == profile.id,
                                onClick = {
                                    selectedProfileId = profile.id
                                    onActiveAiProfile(profile.id)
                                },
                                label = { Text(profile.name) }
                            )
                        }
                        item {
                            TijiSecondaryButton(onClick = {
                                val fresh = AiProfile(
                                    UUID.randomUUID().toString(),
                                    "新 AI 配置",
                                    AppPreferences.DEFAULT_ENDPOINT,
                                    AppPreferences.DEFAULT_MODEL
                                )
                                onAiProfiles(aiProfiles + fresh)
                                selectedProfileId = fresh.id
                                profileName = fresh.name
                                endpoint = fresh.endpoint
                                model = fresh.model
                                apiKey = ""
                                onActiveAiProfile(fresh.id)
                            }) { Text("新增配置") }
                        }
                    }
                    Text("服务商预设", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AiProviderPreset.entries) { value ->
                            TijiChip(
                                selected = preset == value,
                                onClick = {
                                    preset = value
                                    endpoint = value.endpoint
                                    model = value.model
                                },
                                label = { Text(value.label) }
                            )
                        }
                    }
                    Text(
                        preset.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TijiTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("配置名称（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ai_profile_name")
                    )
                    TijiTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("服务地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ai_endpoint")
                    )
                    TijiTextField(
                        value = model,
                        onValueChange = { model = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("模型 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ai_model")
                    )
                    Text(
                        "模型 ID 必须与服务商支持的模型一致。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preset.modelOptions.isNotEmpty()) {
                        Text("推荐模型（点击填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(preset.modelOptions) { option ->
                                Column {
                                    TijiChip(
                                        selected = model.equals(option, ignoreCase = true),
                                        onClick = { model = option },
                                        label = { Text(option, maxLines = 1) }
                                    )
                                    Text(
                                        preset.modelModalityLabel(option),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    com.tiji.mistakes.ui.design.TijiSecretField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key（本机加密保存）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ai_api_key")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("视觉辅助", style = MaterialTheme.typography.labelLarge)
                        selectedVisualProfile?.let {
                            Text(
                                "已绑定：${it.model}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } ?: Text(
                            "未绑定视觉辅助模型；文本解题仍可独立使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TijiButton(
                                onClick = {
                                    val profile = AiProfile(
                                        selectedProfileId,
                                        profileName.ifBlank { "未命名配置" },
                                        endpoint.trim(),
                                        model.trim()
                                    )
                                    val nextProfiles = if (aiProfiles.any { it.id == selectedProfileId }) {
                                        aiProfiles.map { existing -> if (existing.id == selectedProfileId) profile else existing }
                                    } else {
                                        aiProfiles + profile
                                    }
                                    onAiProfiles(nextProfiles)
                                    onActiveAiProfile(selectedProfileId)
                                    secureStore.save(apiKey, selectedProfileId)
                                    onSaveAiConfig(endpoint, model)
                                    connectionMessage = "配置已保存"
                                },
                                modifier = Modifier.weight(1f).testTag("ai_save_config")
                            ) { Text("保存配置") }
                            TijiSecondaryButton(
                                onClick = {
                                    connectionMessage = "正在测试…"
                                    scope.launch {
                                        val result = aiService.testConnection(endpoint, model, apiKey)
                                        connectionMessage = result.fold(
                                            { "连接成功" },
                                            { "连接失败：${it.message ?: "未知错误"}" }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("ai_test_connection")
                            ) { Text("测试连接") }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TijiTextButton(
                                onClick = { onOpenVisualAssistConfig(selectedProfileId) },
                                contentPadding = PaddingValues(0.dp),
                                enabled = selectedProfileId.isNotBlank()
                            ) {
                                Text(if (selectedVisualProfile == null) "添加视觉辅助配置" else "查看视觉辅助配置")
                            }
                            Spacer(Modifier.weight(1f))
                            TijiTextButton(
                                onClick = {
                                    val fallback = aiProfiles.filterNot { it.id == selectedProfileId }.firstOrNull()
                                        ?: AiProfile(
                                            AppPreferences.DEFAULT_PROFILE_ID,
                                            "默认 AI",
                                            AppPreferences.DEFAULT_ENDPOINT,
                                            AppPreferences.DEFAULT_MODEL
                                        )
                                    onDeleteAiProfile(selectedProfileId)
                                    selectedProfileId = fallback.id
                                    profileName = fallback.name
                                    endpoint = fallback.endpoint
                                    model = fallback.model
                                    apiKey = secureStore.read(fallback.id)
                                },
                                contentPadding = PaddingValues(0.dp),
                                enabled = selectedProfileId.isNotBlank()
                            ) { Text("删除配置") }
                        }
                    }
                    if (connectionMessage.isNotBlank()) {
                        Text(connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                CombinedOcrSettingsCard(
                    status = ocrModelState,
                    packageSizeLabel = ocrModelManager.downloadPackageSizeLabel(),
                    onEnable = { OcrModelDownloadService.start(context, force = false) },
                    onStop = { OcrModelDownloadService.stop(context) },
                    onUpdate = { OcrModelDownloadService.start(context, force = true) },
                    onClear = ocrModelManager::clearCombined
                )
            }
            item {
                TijiSettingGroup("视觉输入说明", Icons.Outlined.Image) {
                    Text(
                        "视觉辅助配置独立绑定到当前文本 Profile，可在此进入管理；图片输入测试和 OCR 模型状态也不会改变文本模型设置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
