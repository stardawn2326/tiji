package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeSearchQueryTest {
    @Test
    fun createsAndGroupsWithBindArgsForEveryKeyword() {
        val spec = MistakeSearchQuery.buildSpec(listOf("函数", "OCR"))

        assertTrue(spec.sql.contains("deletedAt IS NULL AND archived = 0"))
        assertTrue(spec.sql.contains("title LIKE ?"))
        assertFalse(spec.sql.contains("note LIKE ?"))
        assertFalse(spec.sql.contains("tags LIKE ?"))
        assertFalse(spec.sql.contains("errorReason LIKE ?"))
        assertFalse(spec.sql.contains("ocrText LIKE ?"))
        assertEquals(14, spec.bindArgs.size)
        assertEquals("%函数%", spec.bindArgs.first())
        assertEquals("%OCR%", spec.bindArgs.last())
        assertTrue(spec.sql.contains(") AND ("))
    }

    @Test
    fun escapesLikeWildcardsAndLeavesUserTextOutOfSql() {
        val keyword = "100%_\\student's"
        val spec = MistakeSearchQuery.buildSpec(listOf(keyword))

        assertEquals("100\\%\\_\\\\student's", MistakeSearchQuery.escapeLike(keyword))
        assertEquals("%100\\%\\_\\\\student's%", spec.bindArgs.first())
        assertFalse(spec.sql.contains(keyword))
        assertTrue(spec.sql.contains("ESCAPE '\\'"))
    }
}
