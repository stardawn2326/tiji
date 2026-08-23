package com.tiji.mistakes

import com.tiji.mistakes.service.BackupService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupServiceTest {
    @Test
    fun inspectsVersionedBackup() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":1,"appVersion":"0.5.2","exportedAt":123}"""
            ),
            "data/mistakes.json" to bytes(
                """[{"stableId":"stable-1","title":"测试题","questionText":"1+1=?","imageEntry":"images/stable-1-question.png"}]"""
            ),
            "data/preferences.json" to bytes("{}"),
            "images/stable-1-question.png" to byteArrayOf(1, 2, 3)
        )

        val preview = BackupService.inspectEntries(entries)

        assertEquals(1, preview.schemaVersion)
        assertEquals(1, preview.mistakeCount)
        assertEquals(1, preview.imageCount)
        assertTrue(!preview.legacy)
    }

    @Test
    fun acceptsAdditiveFutureVersionWithCompatibleReader() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":2,"minReaderSchemaVersion":1,"appVersion":"1.2.0","exportedAt":123}"""
            ),
            "data/mistakes.json" to bytes(
                """[{"stableId":"stable-1","title":"future"}]"""
            ),
            "data/preferences.json" to bytes("{}")
        )

        val preview = BackupService.inspectEntries(entries)

        assertEquals(2, preview.schemaVersion)
        assertEquals(1, preview.mistakeCount)
    }

    @Test
    fun recognizesLegacyPngImage() {
        val entries = mapOf(
            "mistake-1.json" to bytes(
                """{"id":1,"title":"旧版题目","questionText":"题干","createdAt":123}"""
            ),
            "mistake-1-题目.png" to byteArrayOf(1, 2, 3)
        )

        val preview = BackupService.inspectEntries(entries)

        assertTrue(preview.legacy)
        assertEquals(1, preview.mistakeCount)
        assertEquals(1, preview.imageCount)
    }

    @Test
    fun rejectsMissingVersionedImage() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":1,"appVersion":"0.5.2","exportedAt":123}"""
            ),
            "data/mistakes.json" to bytes(
                """[{"stableId":"stable-1","title":"测试题","imageEntry":"images/missing.png"}]"""
            )
        )

        val error = runCatching { BackupService.inspectEntries(entries) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
}
