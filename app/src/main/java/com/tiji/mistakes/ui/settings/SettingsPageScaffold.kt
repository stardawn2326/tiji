@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiScreen
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun SettingsPageScaffold(
    title: String,
    pageTag: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text(title) },
                navigationIcon = {
                    TijiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().testTag(pageTag)) {
            content(padding)
        }
    }
}
