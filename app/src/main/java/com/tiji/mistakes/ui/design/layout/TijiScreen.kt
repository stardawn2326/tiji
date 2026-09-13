@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.tiji.mistakes.ui.design

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@Composable
internal fun TijiScreen(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}, snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit) {
    Scaffold(modifier.imePadding(), topBar, bottomBar, snackbarHost, floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = contentWindowInsets,
        content = content)
}
@Composable
internal fun TijiTopBar(title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) {
    Surface(color = colors.containerColor) {
        Row(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            navigationIcon()
            Box(Modifier.weight(1f).padding(horizontal = 12.dp).semantics { heading() }) {
                ProvideTextStyle(MaterialTheme.typography.headlineMedium, title)
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
@Composable
internal fun TijiBottomActionBar(modifier: Modifier = Modifier, supportingText: @Composable () -> Unit = {}, content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically, content = content)
            supportingText()
        }
    }
}
