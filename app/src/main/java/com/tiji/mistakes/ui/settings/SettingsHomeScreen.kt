package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiSurfaceCard

@Composable
internal fun SettingsHomeScreen(
    resetScrollToken: Int,
    activeAiProfile: AiProfile,
    reviewPlanEnabled: Boolean,
    dailyReviewLimit: Int,
    themeMode: ThemeMode,
    themePalette: ThemePalette,
    onOpenAiSettings: () -> Unit,
    onOpenReviewSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("my_settings_list"),
        contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ConceptPageHeader("设置", "管理 AI、复习、数据和外观；学习内容仍在对应一级页面完成。")
        }
        item {
            SettingsHomeSection("AI") {
                SettingsHomeRow(
                    title = "AI 模型",
                    subtitle = "当前模型：${activeAiProfile.model}",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = onOpenAiSettings
                )
            }
        }
        item {
            SettingsHomeSection("复习") {
                SettingsHomeRow(
                    title = "复习计划",
                    subtitle = if (reviewPlanEnabled) "已开启 · 每日上限 $dailyReviewLimit 题" else "尚未开启，安排今天和接下来的复习节奏",
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = onOpenReviewSettings
                )
            }
        }
        item {
            SettingsHomeSection("数据") {
                SettingsHomeRow(
                    title = "备份与恢复",
                    subtitle = "导出、检查、合并恢复或重置本机数据",
                    icon = Icons.Outlined.FileOpen,
                    onClick = onOpenDataSettings
                )
            }
        }
        item {
            SettingsHomeSection("外观") {
                SettingsHomeRow(
                    title = "显示模式与主题",
                    subtitle = "${themeMode.label} · ${themePalette.label}",
                    icon = Icons.Outlined.Style,
                    onClick = onOpenAppearanceSettings
                )
            }
        }
        item {
            SettingsHomeSection("关于") {
                SettingsHomeRow(
                    title = "关于题迹",
                    subtitle = "版本、说明和使用边界",
                    icon = Icons.Outlined.Lightbulb,
                    onClick = onOpenAbout
                )
            }
        }
    }
}

@Composable
private fun SettingsHomeSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        TijiSurfaceCard { content() }
    }
}

@Composable
private fun SettingsHomeRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .testTag("my_setting_$title")
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = "打开$title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
