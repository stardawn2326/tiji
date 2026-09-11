@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.settings

import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiji.mistakes.BuildConfig
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupPreview
import com.tiji.mistakes.service.BackupService
import com.tiji.mistakes.service.OcrModelDownloadService
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.ui.settings.components.CombinedOcrSettingsCard
import com.tiji.mistakes.ui.common.reviewDateKey
import com.tiji.mistakes.ui.common.weekLabels
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.review.components.ReviewAllocationRow
import com.tiji.mistakes.ui.settings.components.SettingCard
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
        listOf(AiProviderPreset.OPENAI, AiProviderPreset.GEMINI, AiProviderPreset.DEEPSEEK, AiProviderPreset.QWEN, AiProviderPreset.KIMI, AiProviderPreset.CUSTOM)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视觉辅助配置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingCard("视觉服务商", Icons.Outlined.Image) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visualPresets) { value ->
                            FilterChip(
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
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("服务地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("视觉模型 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val modelOptions = visionModels(preset)
                    if (modelOptions.isNotEmpty()) {
                        Text("推荐模型（点击填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(modelOptions) { option ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FilterChip(
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
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key（本机加密保存）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
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
                    OutlinedButton(
                        onClick = {
                            connectionMessage = "正在测试图片输入…"
                            scope.launch {
                                val key = apiKey.ifBlank { existingProfile?.keyProfileId?.let(secureStore::read).orEmpty() }
                                val result = aiService.testVisionConnection(endpoint, model, key)
                                connectionMessage = result.fold({ "图片输入测试成功" }, { "测试失败：${it.message ?: "未知错误"}" })
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("测试图片输入") }
                }
                if (existingProfile != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { onDelete(existingProfile) },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("删除视觉辅助配置") }
                    }
                }
                if (connectionMessage.isNotBlank()) {
                    Text(connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    resetScrollToken: Int,
    pageTag: String = "settings_overview",
    initialItemIndex: Int = 0,
    themeMode: ThemeMode,
    themePalette: ThemePalette,
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    aiVisualProfiles: List<AiVisualProfile>,
    aiVisualBindings: Map<String, String>,
    dailyReviewLimit: Int,
    reviewSubjects: String,
    reviewPlanEnabled: Boolean,
    randomReview: Boolean,
    mistakes: List<MistakeEntity>,
    backgroundScope: CoroutineScope,
    ocrModelManager: OcrModelManager,
    aiExcludeSourceImageByDefault: Boolean,
    onAiExcludeSourceImageByDefault: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onThemePalette: (ThemePalette) -> Unit,
    onSaveAiConfig: (String, String) -> Unit,
    onAiProfiles: (List<AiProfile>) -> Unit,
    onActiveAiProfile: (String) -> Unit,
    onOpenVisualAssistConfig: (String) -> Unit,
    onDailyReviewLimit: (Int) -> Unit,
    onReviewSubjects: (String) -> Unit,
    onReviewPlanEnabled: (Boolean) -> Unit,
    onRandomReview: (Boolean) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onResetData: ((String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsListState = rememberLazyListState()
    LaunchedEffect(resetScrollToken, initialItemIndex) {
        if (initialItemIndex > 0) {
            settingsListState.scrollToItem(initialItemIndex)
        } else if (resetScrollToken > 0) {
            settingsListState.scrollToItem(0)
        }
    }
    val secureStore = remember { SecureKeyStore(context) }
    val ocrModelState by ocrModelManager.combinedState.collectAsStateWithLifecycle()
    var backupMessage by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importingBackup by remember { mutableStateOf(false) }
    var showResetWarning by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var resettingData by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) backgroundScope.launch {
            backupMessage = "正在导出题迹数据…"
            BackupService.writeBackup(context, uri).fold(
                onSuccess = { preview -> backupMessage = "备份完成：${preview.mistakeCount} 道错题、${preview.imageCount} 张图片" },
                onFailure = { error -> backupMessage = "备份失败：${error.message ?: "未知错误"}" }
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backgroundScope.launch {
            backupMessage = "正在检查备份…"
            BackupService.inspectBackup(context, uri).fold(
                onSuccess = { preview ->
                    importUri = uri
                    importPreview = preview
                    backupMessage = ""
                },
                onFailure = { error -> backupMessage = "无法读取备份：${error.message ?: "未知错误"}" }
            )
        }
    }
    val aiService = remember { AiVisionService() }
    var selectedProfileId by remember(activeAiProfileId) { mutableStateOf(activeAiProfileId) }
    val selectedProfile = aiProfiles.firstOrNull { it.id == selectedProfileId }
    var profileName by remember(selectedProfileId, aiProfiles) { mutableStateOf(selectedProfile?.name ?: "默认 AI") }
    var preset by remember(selectedProfileId, selectedProfile?.endpoint, selectedProfile?.model) {
        mutableStateOf(AiProviderPreset.detect(selectedProfile?.endpoint ?: aiEndpoint, selectedProfile?.model ?: aiModel))
    }
    var endpoint by remember(selectedProfileId, selectedProfile?.endpoint) { mutableStateOf(selectedProfile?.endpoint ?: aiEndpoint) }
    var model by remember(selectedProfileId, selectedProfile?.model) { mutableStateOf(selectedProfile?.model ?: aiModel) }
    var apiKey by remember(selectedProfileId) { mutableStateOf(secureStore.read(selectedProfileId)) }
    val selectedVisualProfile = aiVisualProfiles.firstOrNull { it.id == aiVisualBindings[selectedProfileId] }
    var connectionMessage by remember { mutableStateOf("") }
    var reviewLimitText by remember(dailyReviewLimit) { mutableStateOf(dailyReviewLimit.toString()) }
    var quotaTexts by remember(reviewSubjects, mistakes) {
        mutableStateOf(
            reviewSubjects.split(';')
                .mapNotNull {
                    val rawKey = it.substringBefore('=')
                    val value = it.substringAfter('=', "").toIntOrNull()
                    val day = rawKey.substringBefore(':').toIntOrNull()
                    val subject = rawKey.substringAfter(':', "").substringBefore('|').trim()
                    if (day == null || subject.isBlank() || value == null) null
                    else "$day:$subject" to value.toString()
                }
                .groupBy({ it.first }, { it.second.toIntOrNull() ?: 0 })
                .mapValues { (_, values) -> values.sum().toString() }
        )
    }
    val reviewGroups = remember(mistakes) {
        mistakes.map { it.subject.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
    }
    var selectedWeekday by remember { mutableIntStateOf(1) }
    fun restoreBackup(mode: BackupImportMode) {
        val source = importUri ?: return
        importPreview = null
        importingBackup = true
        backgroundScope.launch {
            BackupService.importBackup(context, source, mode).fold(
                onSuccess = { result ->
                    backupMessage = "恢复完成：新增 ${result.inserted}、更新 ${result.updated}、跳过 ${result.skipped} 道错题"
                },
                onFailure = { error -> backupMessage = "恢复失败：${error.message ?: "未知错误"}" }
            )
            importingBackup = false
            importUri = null
        }
    }

    if (showResetWarning) {
        AlertDialog(
            onDismissRequest = { showResetWarning = false },
            title = { Text("重置本机数据？") },
            text = {
                Text("将删除本机保存的全部错题、图片、复习计划和每日掌握记录。AI 配置和 API Key 不会删除，建议先导出数据。")
            },
            confirmButton = {
                Button(onClick = {
                    showResetWarning = false
                    showResetConfirmation = true
                }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showResetWarning = false }) { Text("取消") } }
        )
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!resettingData) showResetConfirmation = false },
            title = { Text("确认永久重置？") },
            text = { Text("第二次确认：数据删除后无法从本机恢复。确定要删除全部错题和图片吗？") },
            confirmButton = {
                Button(
                    enabled = !resettingData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        resettingData = true
                        showResetConfirmation = false
                        onResetData { message ->
                            resettingData = false
                            backupMessage = message ?: "本机数据已重置"
                        }
                    }
                ) { Text(if (resettingData) "正在重置…" else "确认重置") }
            },
            dismissButton = {
                TextButton(enabled = !resettingData, onClick = { showResetConfirmation = false }) { Text("取消") }
            }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!importingBackup) { importPreview = null; importUri = null } },
            title = { Text(if (preview.legacy) "导入旧版题迹备份" else "导入题迹数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("错题 ${preview.mistakeCount} 道 · 图片 ${preview.imageCount} 张 · 复习记录 ${preview.reviewRecordCount} 条 · 知识点 ${preview.knowledgePointCount} 个")
                    Text(
                        if (preview.legacy) "检测到旧版 ZIP，将自动迁移为当前数据结构。"
                        else "数据版本 ${preview.schemaVersion} · 来源应用 ${preview.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("推荐合并导入：按稳定编号和内容去重，并保留较新的记录。", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !importingBackup, onClick = { restoreBackup(BackupImportMode.REPLACE) }) {
                        Text("清空现有数据后恢复")
                    }
                }
            },
            confirmButton = {
                Button(enabled = !importingBackup, onClick = { restoreBackup(BackupImportMode.MERGE) }) {
                    Text(if (importingBackup) "正在恢复…" else "合并导入")
                }
            },
            dismissButton = {
                TextButton(enabled = !importingBackup, onClick = { importPreview = null; importUri = null }) { Text("取消") }
            }
        )
    }
    LazyColumn(
        state = settingsListState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag(pageTag)
    ) {
        item {
            ConceptPageHeader("我的", "管理复习计划、AI 配置和题迹数据。")
        }
        item {
            SettingCard("外观", Icons.Outlined.Style) {
                Text("显示模式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { value -> FilterChip(selected = themeMode == value, onClick = { onThemeMode(value) }, label = { Text(value.label) }) }
                }
            }
        }
        item {
            SettingCard("复习计划", Icons.Outlined.CalendarMonth) {
                Text("按科目分配，调整每天复习数量，未完成题目顺延到下一天。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("开启复习计划", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = reviewPlanEnabled, onCheckedChange = onReviewPlanEnabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("复习抽取方式", style = MaterialTheme.typography.bodyLarge); Text(if(randomReview) "从全部计划错题随机抽取" else "按遗忘曲线从到期错题抽取", style=MaterialTheme.typography.bodySmall) }
                    Switch(checked=randomReview,onCheckedChange=onRandomReview)
                }
                OutlinedTextField(
                    value = reviewLimitText,
                    onValueChange = { reviewLimitText = it.filter(Char::isDigit).take(3) },
                    label = { Text("每日复习题数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("科目分配", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items((1..7).toList()) { day ->
                        FilterChip(
                            selected = selectedWeekday == day,
                            onClick = { selectedWeekday = day },
                            label = { Text(weekLabels[day - 1], style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }
                val allocatedForDay = reviewGroups.sumOf { key -> quotaTexts["$selectedWeekday:$key"]?.toIntOrNull() ?: 0 }
                if(reviewGroups.isEmpty()) {
                    Text("录入错题后可在这里按科目分配数量", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    reviewGroups.forEach { key ->
                        val label = key
                        val storageKey="$selectedWeekday:$key"
                        val count=quotaTexts[storageKey]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val rowMax = maxOf(count, dailyReviewLimit - (allocatedForDay - count))
                        ReviewAllocationRow(
                            label = label,
                            count = count,
                            maxCount = rowMax,
                            onCountChange = { value -> quotaTexts = quotaTexts + (storageKey to value.coerceIn(0, rowMax).toString()) }
                        )
                    }
                    Text(
                        "已分配 $allocatedForDay / $dailyReviewLimit · ${if (allocatedForDay == dailyReviewLimit) "计划平衡" else "可继续调整"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = {
                    onDailyReviewLimit(reviewLimitText.toIntOrNull()?.coerceIn(1, 100) ?: dailyReviewLimit)
                    onReviewSubjects(quotaTexts.entries.filter { it.value.toIntOrNull()!=null }.joinToString(";") { "${it.key}=${it.value}" })
                    connectionMessage = "复习计划已保存，明日继续按此安排"
                }) { Text("保存复习计划") }
            }
        }
        item {
            SettingCard("AI 兼容接口", Icons.Outlined.AutoAwesome) {
                Text("按需配置，未配置时核心功能完全离线。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("已保存配置", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(aiProfiles, key = { it.id }) { profile ->
                        FilterChip(
                            selected = selectedProfileId == profile.id,
                            onClick = { selectedProfileId = profile.id; onActiveAiProfile(profile.id) },
                            label = { Text(profile.name) }
                        )
                    }
                    item {
                        OutlinedButton(onClick = {
                            val fresh = AiProfile(UUID.randomUUID().toString(), "新 AI 配置", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
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
                        FilterChip(selected = preset == value, onClick = { preset = value; endpoint = value.endpoint; model = value.model }, label = { Text(value.label) })
                    }
                }
                Text(
                    preset.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(profileName, { profileName = it }, label = { Text("配置名称（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endpoint, { endpoint = it; preset = AiProviderPreset.CUSTOM }, label = { Text("服务地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it; preset = AiProviderPreset.CUSTOM }, label = { Text("模型 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("模型 ID 必须与服务商支持的模型一致。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preset.modelOptions.isNotEmpty()) {
                    Text("推荐模型（点击填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(preset.modelOptions) { option ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilterChip(
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
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key（本机加密保存）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Visual assistance is an independent binding. It is
                    // available for every text configuration, including one
                    // whose current solving model also happens to accept images.
                    if (selectedProfileId.isNotBlank()) {
                        Text("视觉辅助", style = MaterialTheme.typography.labelLarge)
                        selectedVisualProfile?.let {
                            Text(
                                "已绑定：${it.model}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            val profile = AiProfile(selectedProfileId, profileName.ifBlank { "未命名配置" }, endpoint.trim(), model.trim())
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
                        }, modifier = Modifier.weight(1f)) { Text("保存配置") }
                        OutlinedButton(onClick = {
                            connectionMessage = "正在测试…"
                            scope.launch {
                                val result = aiService.testConnection(endpoint, model, apiKey)
                                connectionMessage = result.fold({ "连接成功" }, { "连接失败：${it.message ?: "未知错误"}" })
                            }
                        }, modifier = Modifier.weight(1f)) { Text("测试连接") }
                    }
                    if (selectedProfileId.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { onOpenVisualAssistConfig(selectedProfileId) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (selectedVisualProfile == null) "添加视觉辅助配置" else "查看视觉辅助配置")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    val fallback = aiProfiles.filterNot { it.id == selectedProfileId }.firstOrNull()
                                        ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                                    onDeleteAiProfile(selectedProfileId)
                                    selectedProfileId = fallback.id
                                    profileName = fallback.name
                                    endpoint = fallback.endpoint
                                    model = fallback.model
                                    apiKey = secureStore.read(fallback.id)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("删除配置") }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val fallback = aiProfiles.filterNot { it.id == selectedProfileId }.firstOrNull()
                                    ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                                onDeleteAiProfile(selectedProfileId)
                                selectedProfileId = fallback.id
                                profileName = fallback.name
                                endpoint = fallback.endpoint
                                model = fallback.model
                                apiKey = secureStore.read(fallback.id)
                            }) { Text("删除配置") }
                        }
                    }
                }
                if (connectionMessage.isNotBlank()) Text(connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            SettingCard("PDF 导出", Icons.Outlined.PictureAsPdf) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("是否导出照片原图", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "打开后导出处理后的黑白原图 PDF。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !aiExcludeSourceImageByDefault,
                        onCheckedChange = { exportOriginal -> onAiExcludeSourceImageByDefault(!exportOriginal) }
                    )
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
            SettingCard("数据", Icons.Outlined.FolderOpen) {
                Text("可供各版本读取：包含错题、图片、复习计划以及每日掌握记录，不包含API Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { backupLauncher.launch("题迹数据-${reviewDateKey()}.tiji") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.size(8.dp)); Text("导出题迹数据 (.tiji)")
                }
                OutlinedButton(enabled = !importingBackup, onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/zip")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.size(8.dp)); Text(if (importingBackup) "正在恢复…" else "导入并迁移数据")
                }
                OutlinedButton(
                    enabled = !importingBackup && !resettingData,
                    onClick = { showResetWarning = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("重置本机数据")
                }
                if (backupMessage.isNotBlank()) Text(backupMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            SettingCard("关于", Icons.Outlined.Lightbulb) {
                Text("由 AI 全程制作", fontWeight = FontWeight.Bold)
                Text("仅个人用途，请勿转载或商用。当前版本：v${BuildConfig.VERSION_NAME}。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
