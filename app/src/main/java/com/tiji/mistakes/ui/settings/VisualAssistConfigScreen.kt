@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.ui.design.TijiSettingGroup
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun VisualAssistConfigScreen(
    textProfile: AiProfile,
    existingProfile: AiVisualProfile?,
    onBack: () -> Unit,
    onSave: (AiVisualProfile) -> Unit,
    onDelete: (AiVisualProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureKeyStore(context) }
    val aiService = remember { AiVisionService() }
    val visualPresets = remember {
        listOf(
            AiProviderPreset.OPENAI,
            AiProviderPreset.GEMINI,
            AiProviderPreset.DEEPSEEK,
            AiProviderPreset.QWEN,
            AiProviderPreset.KIMI,
            AiProviderPreset.CUSTOM
        )
    }
    fun visionModels(value: AiProviderPreset): List<String> = when (value) {
        AiProviderPreset.CUSTOM -> emptyList()
        else -> value.modelOptions.filter { value.supportsVisionFor(it) }
    }
    val initialPreset = AiProviderPreset.detect(
        existingProfile?.endpoint ?: AiProviderPreset.OPENAI.endpoint,
        existingProfile?.model ?: AiProviderPreset.OPENAI.model
    )
    var preset by remember(existingProfile?.id, textProfile.id) { mutableStateOf(initialPreset) }
    var endpoint by remember(existingProfile?.id, textProfile.id) {
        mutableStateOf(existingProfile?.endpoint ?: AiProviderPreset.OPENAI.endpoint)
    }
    var model by remember(existingProfile?.id, textProfile.id) {
        mutableStateOf(existingProfile?.model ?: AiProviderPreset.OPENAI.model)
    }
    var apiKey by remember(existingProfile?.id, textProfile.id) {
        mutableStateOf(
            existingProfile?.let { profile ->
                secureStore.read(profile.id).ifBlank { profile.keyProfileId?.let(secureStore::read).orEmpty() }
            }.orEmpty()
        )
    }
    var connectionMessage by remember { mutableStateOf("") }

    SettingsPageScaffold(title = "视觉辅助配置", pageTag = "visual_config", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TijiSettingGroup("视觉服务商", Icons.Outlined.Image) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visualPresets) { value ->
                            TijiChip(
                                selected = preset == value,
                                onClick = {
                                    preset = value
                                    endpoint = value.endpoint
                                    val options = visionModels(value)
                                    model = options.firstOrNull() ?: value.model
                                },
                                label = { Text(value.label, maxLines = 1, softWrap = false) }
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
                        value = endpoint,
                        onValueChange = { endpoint = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("服务地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TijiTextField(
                        value = model,
                        onValueChange = { model = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("视觉模型 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val modelOptions = visionModels(preset)
                    if (modelOptions.isNotEmpty()) {
                        Text(
                            "推荐模型（点击填入）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(modelOptions) { option ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TijiChip(
                                        selected = model.equals(option, ignoreCase = true),
                                        onClick = { model = option },
                                        label = { Text(option, maxLines = 1, softWrap = false) }
                                    )
                                    Text(
                                        if (preset == AiProviderPreset.OPENAI) "视觉模型" else "多模态模型",
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TijiButton(
                        onClick = {
                            val profile = AiVisualProfile(
                                id = existingProfile?.id ?: UUID.randomUUID().toString(),
                                name = "${textProfile.name} · 视觉辅助",
                                endpoint = endpoint.trim(),
                                model = model.trim(),
                                textProfileId = textProfile.id,
                                keyProfileId = existingProfile?.keyProfileId ?: textProfile.id
                            )
                            if (apiKey.isNotBlank()) secureStore.save(apiKey, profile.id)
                            onSave(profile)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                    TijiSecondaryButton(
                        onClick = {
                            connectionMessage = "正在测试图片输入…"
                            scope.launch {
                                val key = apiKey.ifBlank {
                                    existingProfile?.keyProfileId?.let(secureStore::read).orEmpty()
                                }
                                val result = aiService.testVisionConnection(endpoint, model, key)
                                connectionMessage = result.fold(
                                    { "图片输入测试成功" },
                                    { "测试失败：${it.message ?: "未知错误"}" }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("测试图片输入") }
                }
                if (existingProfile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TijiTextButton(
                            onClick = { onDelete(existingProfile) },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("删除视觉辅助配置") }
                    }
                }
                if (connectionMessage.isNotBlank()) {
                    Text(
                        connectionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
