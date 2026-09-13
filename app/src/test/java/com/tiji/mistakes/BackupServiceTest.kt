package com.tiji.mistakes

import com.tiji.mistakes.service.BackupService
import org.json.JSONObject
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
    fun acceptsSchemaTwoBackupWithCompatibleReader() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":2,"minReaderSchemaVersion":2,"appVersion":"1.2.0","exportedAt":123}"""
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
    fun inspectsSchemaThreeStructuredLearningData() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":3,"minReaderSchemaVersion":3,"appVersion":"1.4.1","exportedAt":123,"reviewRecordCount":1,"knowledgePointCount":1}"""
            ),
            "data/mistakes.json" to bytes(
                """[{"stableId":"mistake-1","title":"函数题"}]"""
            ),
            "data/preferences.json" to bytes("{}"),
            "data/review_records.json" to bytes(
                """[{"mistakeStableId":"mistake-1","reviewedAt":100,"grade":"GOOD","masteryBefore":1,"masteryAfter":2,"intervalBeforeDays":1,"intervalAfterDays":2,"previousNextReviewAt":100,"nextReviewAt":200}]"""
            ),
            "data/knowledge_points.json" to bytes(
                """[{"stableId":"kp-1","subject":"数学","name":"函数","normalizedName":"函数","createdAt":1,"updatedAt":1}]"""
            ),
            "data/mistake_knowledge_points.json" to bytes(
                """[{"mistakeStableId":"mistake-1","knowledgePointStableId":"kp-1"}]"""
            )
        )

        val preview = BackupService.inspectEntries(entries)

        assertEquals(3, preview.schemaVersion)
        assertEquals(1, preview.reviewRecordCount)
        assertEquals(1, preview.knowledgePointCount)
    }

    @Test
    fun legacySchemaTwoReaderRejectsSchemaThreeWriterContract() {
        val writerManifest = JSONObject()
            .put("format", "tiji-backup")
            .put("schemaVersion", 3)
            .put("minReaderSchemaVersion", 3)

        assertTrue(!legacySchemaTwoReaderAccepts(writerManifest))
    }

    @Test
    fun rejectsUnsupportedFutureSchema() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":4,"minReaderSchemaVersion":3}"""
            ),
            "data/mistakes.json" to bytes("[]")
        )

        assertTrue(runCatching { BackupService.inspectEntries(entries) }.isFailure)
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

    @Test
    fun lenientInspectionReportsMissingVersionedImagesBeforeImport() {
        val entries = mapOf(
            "manifest.json" to bytes(
                """{"format":"tiji-backup","schemaVersion":1,"appVersion":"0.5.2","exportedAt":123}"""
            ),
            "data/mistakes.json" to bytes(
                """[{"stableId":"stable-1","title":"测试题","imageEntry":"images/missing.png"}]"""
            )
        )

        val preview = BackupService.inspectEntries(entries, strictImages = false)

        assertEquals(1, preview.missingImages)
        assertEquals(0, preview.imageCount)
    }

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)

    private fun legacySchemaTwoReaderAccepts(manifest: JSONObject): Boolean {
        val schema = manifest.optInt("schemaVersion", 0)
        val minReaderSchema = manifest.optInt("minReaderSchemaVersion", schema)
        return schema >= 1 && minReaderSchema <= 2
    }
}
