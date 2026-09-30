package com.tiji.mistakes.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal object TijiShapes {
    val XS = RoundedCornerShape(8.dp)
    val S = RoundedCornerShape(12.dp)
    val M = RoundedCornerShape(18.dp)
    val L = RoundedCornerShape(24.dp)
    val XL = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(999.dp)
    val material = Shapes(XS, S, M, L, XL)
}
