package com.tiji.mistakes

import com.tiji.mistakes.data.KnowledgePointNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KnowledgePointNormalizerTest {
    @Test
    fun parsesChineseEnglishSeparatorsAndDuplicateWhitespace() {
        assertEquals(
            listOf("函数 单调性", "极值"),
            KnowledgePointNormalizer.parseTags(" 函数   单调性，函数 单调性, 极值;;")
        )
    }

    @Test
    fun stableIdIsDeterministicAndSubjectScoped() {
        val first = KnowledgePointNormalizer.stableId("数学", "函数单调性")
        assertEquals(first, KnowledgePointNormalizer.stableId("数学", "函数单调性"))
        assertNotEquals(first, KnowledgePointNormalizer.stableId("物理", "函数单调性"))
    }
}
