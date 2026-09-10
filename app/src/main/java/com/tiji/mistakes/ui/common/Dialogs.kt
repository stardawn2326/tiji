package com.tiji.mistakes.ui.common


internal val TijiErrorReasonOptions = listOf("概念不清", "计算错误", "粗心", "审题错误", "方法不熟")

internal fun parseErrorReasons(raw: String): List<String> = raw
    .split(',', '，', ';', '；', '|')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
