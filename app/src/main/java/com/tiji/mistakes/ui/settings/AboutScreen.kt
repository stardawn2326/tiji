package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.BuildConfig
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiPaperCard
import com.tiji.mistakes.ui.settings.components.SettingCard

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    SettingsPageScaffold(title = "关于题迹", pageTag = "settings_about", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TijiPageHeader("题迹", "把错题留下，把复习做成真正会做。")
            }
            item {
                SettingCard("版本", Icons.Outlined.Lightbulb) {
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "本地优先：错题、复习计划和导出数据默认保存在本机。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "AI 仅在你主动发起解题或连接测试时联网。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                TijiPaperCard {
                    Text("使用边界", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "当前版本面向个人学习使用；AI 结果请结合题目和教材自行核对。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
