package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
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
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.design.TijiDimens

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
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            TijiPageHeader("设置", "让题迹适合你的学习习惯")
        }
        item {
            Spacer(Modifier.height(12.dp))
            SettingsHomeRow(
                title = "AI 模型",
                subtitle = "当前模型：${activeAiProfile.model}",
                icon = Icons.Outlined.AutoAwesome,
                onClick = onOpenAiSettings
            )
        }
        item {
            SettingsHomeDivider()
            SettingsHomeRow(
                title = "复习计划",
                subtitle = if (reviewPlanEnabled) "已开启 · 每日上限 $dailyReviewLimit 题" else "尚未开启，安排今天和接下来的复习节奏",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onOpenReviewSettings
            )
        }
        item {
            SettingsHomeDivider()
            SettingsHomeRow(
                title = "备份与恢复",
                subtitle = "导出、检查、合并恢复或重置本机数据",
                icon = Icons.Outlined.FileOpen,
                onClick = onOpenDataSettings
            )
        }
        item {
            SettingsHomeDivider()
            SettingsHomeRow(
                title = "显示模式与主题",
                subtitle = "${themeMode.label} · ${themePalette.label}",
                icon = Icons.Outlined.Style,
                onClick = onOpenAppearanceSettings
            )
        }
        item {
            SettingsHomeDivider()
            SettingsHomeRow(
                title = "关于题迹",
                subtitle = "版本与使用说明",
                icon = Icons.Outlined.Lightbulb,
                onClick = onOpenAbout
            )
        }
    }
}

@Composable
private fun SettingsHomeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 40.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
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
