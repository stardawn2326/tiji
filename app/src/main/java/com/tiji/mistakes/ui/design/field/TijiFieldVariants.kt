package com.tiji.mistakes.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
internal fun TijiSecretField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null, singleLine: Boolean = true, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    TijiTextField(value, onValueChange, modifier, enabled = enabled, label = label, singleLine = singleLine,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TijiIconButton({ visible = !visible }) {
            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (visible) "隐藏密钥" else "显示密钥")
        } })
}

@Composable
internal fun TijiSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null) {
    TijiTextField(value, onValueChange, modifier, label = { Text("搜索") }, placeholder = placeholder,
        singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) })
}
