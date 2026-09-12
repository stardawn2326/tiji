package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Style
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiSettingGroup

@Composable
internal fun AppearanceSettingsScreen(
    themeMode: ThemeMode,
    themePalette: ThemePalette,
    onThemeMode: (ThemeMode) -> Unit,
    onThemePalette: (ThemePalette) -> Unit,
    onBack: () -> Unit
) {
    SettingsPageScaffold(title = "外观", pageTag = "settings_appearance", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TijiSettingGroup("显示模式", Icons.Outlined.Style) {
                    Text("选择题迹在本机上的显示方式。")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(ThemeMode.entries) { value ->
                            TijiChip(
                                selected = themeMode == value,
                                onClick = { onThemeMode(value) },
                                label = { Text(value.label) }
                            )
                        }
                    }
                }
            }
            item {
                TijiSettingGroup("主题色", Icons.Outlined.Style) {
                    Text("用于按钮、选中状态和学习提示的强调色。")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(ThemePalette.entries) { value ->
                            TijiChip(
                                selected = themePalette == value,
                                onClick = { onThemePalette(value) },
                                label = { Text(value.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}
