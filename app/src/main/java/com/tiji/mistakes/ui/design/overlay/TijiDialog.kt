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
internal fun TijiDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, properties: DialogProperties = DialogProperties()) {
    AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, icon, title,
        text?.let { body -> { Column(Modifier.verticalScroll(rememberScrollState())) { body() } } },
        shape = TijiShapes.XL, containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant, tonalElevation = 6.dp, properties = properties)
}
@Composable
internal fun TijiBottomSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(), content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest, modifier, sheetState, shape = TijiShapes.XL,
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, content = content)
}
@Composable
internal fun TijiMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismissRequest, modifier, shape = TijiShapes.M,
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, content = content)
}
@Composable
internal fun TijiMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null, enabled: Boolean = true) {
    DropdownMenuItem(text, onClick, modifier.heightIn(min = 48.dp), leadingIcon, trailingIcon, enabled)
}
