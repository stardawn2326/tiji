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
internal fun TijiCard(modifier: Modifier = Modifier, shape: Shape = TijiShapes.M,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation: CardElevation = CardDefaults.cardElevation(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape, colors, elevation, border, content)
}
@Composable
internal fun TijiCard(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = TijiShapes.M, colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation: CardElevation = CardDefaults.cardElevation(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(onClick, modifier, enabled, shape, colors, elevation, border, content = content)
}

/** Low level host for media, grouped content and selection surfaces. */
@Composable
internal fun TijiSurface(modifier: Modifier = Modifier, shape: Shape = TijiShapes.M,
    color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null,
    content: @Composable () -> Unit) {
    Surface(modifier, shape, color, contentColor, tonalElevation, shadowElevation, border, content)
}
@Composable
internal fun TijiSurface(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = TijiShapes.M, color: Color = MaterialTheme.colorScheme.surface, contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null,
    content: @Composable () -> Unit) {
    Surface(onClick, modifier, enabled, shape, color, contentColor, tonalElevation, shadowElevation, border, content = content)
}
