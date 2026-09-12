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
internal fun TijiCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Checkbox(checked, onCheckedChange, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled,
        colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant))
}
@Composable
internal fun TijiSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(checked, onCheckedChange, modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = SwitchDefaults.colors(uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant))
}
@Composable
internal fun TijiRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    RadioButton(selected, onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled)
}

/** Intrinsic height lets translated and enlarged labels wrap without clipping. */
@Composable
internal fun <T> TijiSegmentedControl(options: List<T>, selected: T?, onSelected: (T) -> Unit,
    label: (T) -> String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().selectableGroup().background(MaterialTheme.colorScheme.surfaceVariant, TijiShapes.M)
        .padding(4.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(TijiShapes.S)
                .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                .selectable(active, enabled = enabled, role = Role.Tab, onClick = { onSelected(option) })
                .padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label(option), style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
