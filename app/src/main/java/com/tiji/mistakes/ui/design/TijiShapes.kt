package com.tiji.mistakes.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal object TijiShapes {
    val XS = RoundedCornerShape(6.dp)
    val S = RoundedCornerShape(8.dp)
    val M = RoundedCornerShape(12.dp)
    val L = RoundedCornerShape(16.dp)
    val XL = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(999.dp)
    val material = Shapes(XS, S, M, L, XL)
}
