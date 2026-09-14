package com.tiji.mistakes.service

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tiji.mistakes.BuildConfig
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.KnowledgePointNormalizer
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class BackupImportMode { MERGE, REPLACE }

data class BackupPreview(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val mistakeCount: Int,
    val imageCount: Int,
    val reviewRecordCount: Int,
    val legacy: Boolean,
    val knowledgePointCount: Int = 0,
    /** Dry-run import plan; no database rows are changed while inspecting. */
    val willAdd: Int = 0,
    val willUpdate: Int = 0,
    val willSkip: Int = 0,
    val missingImages: Int = 0,
    val parseErrors: List<String> = emptyList()
)

data class BackupImportResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val imageCount: Int
)

/** Versioned, app-readable .tiji archive. It deliberately excludes API keys and AI working state. */
object BackupService {
    private const val FORMAT = "tiji-backup"
    private const val SCHEMA_VERSION = 3
    private const val MIN_READER_SCHEMA_VERSION = 3
    private const val MAX_ENTRY_BYTES = 40L * 1024L * 1024L
    private const val MAX_ARCHIVE_BYTES = 160L * 1024L * 1024L
    private const val MAX_ENTRIES = 20_000
    private const val MAX_RECORDS = 10_000

    suspend fun writeBackup(context: Context, uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val database = AppDatabase.get(context)
            val mistakes = database.mistakeDao().listForBackup()
            val idToStableId = mistakes.associate { it.id to it.stableId }
            val preferences = AppPreferences(context).exportBackupJson(idToStableId)
            val mistakeRecords = JSONArray()
            val reviewRecordEntities = database.reviewRecordDao().listAll()
                .filter { it.mistakeId in idToStableId }
            val reviewRecordJson = JSONArray()
            val knowledgePoints = database.knowledgePointDao().listAll()
            val knowledgePointById = knowledgePoints.associateBy(KnowledgePointEntity::id)
            val knowledgePointRecords = JSONArray()
            val crossRefs = database.mistakeKnowledgePointDao().listAll()
            val crossRefRecords = JSONArray()
            val images = mutableListOf<ExportImage>()

            mistakes.forEach { mistake ->
                val safeStableId = safeStableId(mistake.stableId)
                fun image(role: String, path: String?): String? {
                    val file = path?.let(::File)?.takeIf(File::isFile) ?: return null
                    val extension = file.extension.lowercase().takeIf {
                        it in setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
                    } ?: "jpg"
                    val entry = "images/$safeStableId-$role.$extension"
                    images += ExportImage(entry, file)
                    return entry
                }
                val sourcePaths = sourceImagePaths(mistake)
                val questionEntries = sourcePaths.mapIndexedNotNull { index, path ->
                    image("question-$index", path)
                }
                val questionEntry = questionEntries.firstOrNull()
                val answerEntry = image("answer", mistake.answerImagePath)
                val explanationEntry = image("explanation", mistake.explanationImagePath)
                val blockEntries = QuestionContentBlockCodec.decode(mistake.contentBlocks).mapIndexedNotNull { index, block ->
                    val entry = image("block-$index", block.path) ?: return@mapIndexedNotNull null
                    JSONObject()
                        .put("entry", entry)
                        .put("role", block.role.name)
                        .put("kind", block.kind.name)
                        .put("caption", block.caption)
                        .put("order", block.order)
                        .put("sourcePathEntry", sourcePaths.indexOf(block.sourcePath).takeIf { it >= 0 }?.let { questionEntries.getOrNull(it) } ?: JSONObject.NULL)
                }
                mistakeRecords.put(mistakeToJson(
                    mistake,
                    questionEntry,
                    answerEntry,
                    explanationEntry,
                    questionEntries,
                    blockEntries
                ))
            }

            reviewRecordEntities.forEach { record ->
                idToStableId[record.mistakeId]?.let { mistakeStableId ->
                    reviewRecordsToJson(record, mistakeStableId)?.let { reviewRecordsJson ->
                        reviewRecordJson.put(reviewRecordsJson)
                    }
                }
            }
            knowledgePoints.forEach { point ->
                knowledgePointRecords.put(
                    knowledgePointToJson(
                        point,
                        point.parentId?.let(knowledgePointById::get)?.stableId
                    )
                )
            }
            crossRefs.forEach { ref ->
                val mistakeStableId = idToStableId[ref.mistakeId] ?: return@forEach
                val pointStableId = knowledgePointById[ref.knowledgePointId]?.stableId ?: return@forEach
                crossRefRecords.put(
                    JSONObject()
                        .put("mistakeStableId", mistakeStableId)
                        .put("knowledgePointStableId", pointStableId)
                )
            }

            val exportedAt = System.currentTimeMillis()
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("minReaderSchemaVersion", MIN_READER_SCHEMA_VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("exportedAt", exportedAt)
                .put("mistakeCount", mistakes.size)
                .put("imageCount", images.size)
                .put("reviewRecordCount", reviewRecordJson.length())
                .put("knowledgePointCount", knowledgePoints.size)

            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    putJson(zip, "manifest.json", manifest)
                    putJson(zip, "data/mistakes.json", mistakeRecords)
                    putJson(zip, "data/preferences.json", preferences)
                    putJson(zip, "data/review_records.json", reviewRecordJson)
                    putJson(zip, "data/knowledge_points.json", knowledgePointRecords)
                    putJson(zip, "data/mistake_knowledge_points.json", crossRefRecords)
                    images.distinctBy(ExportImage::entry).forEach { image ->
                        zip.putNextEntry(ZipEntry(image.entry))
                        image.file.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("无法创建备份文件")

            BackupPreview(
                schemaVersion = SCHEMA_VERSION,
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = exportedAt,
                mistakeCount = mistakes.size,
                imageCount = images.distinctBy(ExportImage::entry).size,
                reviewRecordCount = reviewRecordJson.length(),
                knowledgePointCount = knowledgePoints.size,
                legacy = false
            )
        }
    }

    suspend fun inspectBackup(context: Context, uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = readArchive(context, uri)
            // Preview is deliberately lenient about missing image entries so the
            // user can see the exact count before the strict import is offered.
            val payload = runCatching { parsePayload(entries, strictImages = false) }.getOrElse { error ->
                return@runCatching BackupPreview(
                    schemaVersion = 0,
                    appVersion = "未知",
                    exportedAt = 0L,
                    mistakeCount = 0,
                    imageCount = 0,
                    reviewRecordCount = 0,
                    legacy = false,
                    parseErrors = listOf(error.message ?: "备份解析失败")
                )
            }
            val existing = AppDatabase.get(context).mistakeDao().listAll()
            val byStableId = existing.filter { it.stableId.isNotBlank() }.associateBy(MistakeEntity::stableId)
            val byFingerprint = existing.associateBy(::fingerprint)
            var willAdd = 0
            var willUpdate = 0
            var willSkip = 0
            payload.records.forEach { record ->
                val match = byStableId[record.entity.stableId] ?: byFingerprint[fingerprint(record.entity)]
                when {
                    match == null -> willAdd++
                    record.entity.updatedAt > match.updatedAt -> willUpdate++
                    else -> willSkip++
                }
            }
            payload.preview.copy(willAdd = willAdd, willUpdate = willUpdate, willSkip = willSkip)
        }
    }

    internal fun inspectEntries(
        entries: Map<String, ByteArray>,
        strictImages: Boolean = true
    ): BackupPreview = parsePayload(entries, strictImages).preview

    suspend fun importBackup(
        context: Context,
        uri: Uri,
        mode: BackupImportMode
    ): Result<BackupImportResult> = importBackup(
        context = context,
        uri = uri,
        mode = mode,
        database = AppDatabase.get(context),
        preferences = AppPreferences(context)
    )

    /**
     * Completes startup recovery for a journal left by process death. A Room
     * transaction is all-or-nothing, so a published path is retained only when
     * the current database still references it; otherwise the new files and
     * portable preference snapshot are removed together.
     */
    suspend fun recoverPendingImport(context: Context): BackupRecoveryAction =
        recoverPendingImport(context, AppDatabase.get(context), AppPreferences(context))

    internal suspend fun recoverPendingImport(
        context: Context,
        database: AppDatabase,
        preferences: AppPreferences?
    ): BackupRecoveryAction = withContext(Dispatchers.IO) {
        val coordinator = BackupImportCoordinator(context)
        val journalRead = coordinator.readResult()
        if (journalRead.status == BackupJournalReadStatus.CORRUPT) {
            // Never delete staging or published files when the only journal
            // version is unreadable. The diagnostic sidecar and the original
            // journal remain available for a deliberate repair.
            return@withContext BackupRecoveryAction.CORRUPT_JOURNAL
        }
        val journal = journalRead.journal ?: return@withContext BackupRecoveryAction.NONE
        val markerDao = database.backupImportCommitMarkerDao()
        suspend fun cleanupCommitted(): BackupRecoveryAction {
            finalizeCommittedImport(coordinator, database, journal)
            return BackupRecoveryAction.CLEANED_COMMITTED
        }
        suspend fun cleanupBeforeCommit(): BackupRecoveryAction {
            // Restore the portable settings while the journal still exists;
            // an I/O failure leaves both the journal and files for retry.
            restorePreferenceSnapshot(database, preferences, journal)
            coordinator.cleanup(journal, deleteCreatedFiles = true)
            database.backupImportCommitMarkerDao().clear(journal.importId)
            return BackupRecoveryAction.CLEANED_BEFORE_COMMIT
        }
        when (journal.phase) {
            BackupImportPhase.VALIDATED,
            BackupImportPhase.STAGED,
            BackupImportPhase.SNAPSHOT_CREATED -> {
                cleanupBeforeCommit()
            }
            BackupImportPhase.FILES_PUBLISHED -> {
                if (markerDao.isCommitted(journal.importId)) cleanupCommitted()
                else cleanupBeforeCommit()
            }
            BackupImportPhase.ROOM_COMMITTED,
            BackupImportPhase.PREFERENCES_COMMITTED ->
                if (markerDao.isCommitted(journal.importId)) cleanupCommitted() else cleanupBeforeCommit()
            BackupImportPhase.COMMITTED -> cleanupCommitted()
        }
    }

    /**
     * Finalizes a committed import without doing any business writes. A
     * recovery path may enter here before the journal reached COMMITTED, so
     * first make that phase durable. The marker is cleared only after all
     * transient files are removed; the journal is cleared last, making every
     * interruption safe to retry.
     */
    private suspend fun finalizeCommittedImport(
        coordinator: BackupImportCoordinator,
        database: AppDatabase,
        journal: BackupImportJournal
    ) {
        val committedJournal = if (journal.phase == BackupImportPhase.COMMITTED) {
            journal
        } else {
            coordinator.advance(BackupImportPhase.COMMITTED)
        }
        val referenced = MistakeRepository(database).allReferencedImagePaths()
        coordinator.cleanupPublishedWork(
            journal = committedJournal,
            referencedImagePaths = referenced,
            previousReferencedImagePaths = committedJournal.snapshot.referencedImagePaths
        )
        database.backupImportCommitMarkerDao().clear(committedJournal.importId)
        coordinator.clear()
    }

    private suspend fun restorePreferenceSnapshot(
        database: AppDatabase,
        preferences: AppPreferences?,
        journal: BackupImportJournal
    ) {
        val raw = journal.snapshot.preferencesJson ?: return
        if (preferences == null) return
        val stableToLocal = database.mistakeDao().listAll().associate { it.stableId to it.id }
        preferences.importBackupJson(JSONObject(raw), stableToLocal, replace = true)
    }

    /** Internal database seam used by compatibility tests without mutating device data. */
    internal suspend fun importBackup(
        context: Context,
        uri: Uri,
        mode: BackupImportMode,
        database: AppDatabase,
        preferences: AppPreferences? = null
    ): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        val coordinator = BackupImportCoordinator(context)
        check(recoverPendingImport(context, database, preferences) != BackupRecoveryAction.CORRUPT_JOURNAL) {
            "备份导入 journal 损坏，已保留现场，请先修复 journal.error"
        }
        val importId = UUID.randomUUID().toString()
        val stagingDirectory = File(context.filesDir, "staging/import_$importId")
        val rollbackDirectory = File(context.filesDir, "staging/rollback_$importId")
        val stagedImages = linkedMapOf<String, StagedImportImage>()
        val createdImagePaths = mutableSetOf<String>()
        val previousReferencedImagePaths = MistakeRepository(database).allReferencedImagePaths()
        var preferenceSnapshot: JSONObject? = null
        val result = runCatching {
            val payload = parsePayload(readArchive(context, uri))
            var journal = coordinator.begin(
                mode = mode,
                stagingDirectory = stagingDirectory,
                rollbackDirectory = rollbackDirectory
            )
            stagePayloadImages(context, payload, stagingDirectory, stagedImages)
            journal = coordinator.advance(BackupImportPhase.STAGED)
            val dao = database.mistakeDao()
            val reviewDao = database.reviewRecordDao()
            val knowledgePointDao = database.knowledgePointDao()
            val crossRefDao = database.mistakeKnowledgePointDao()
            val beforeImportMistakes = dao.listAll()
            preferenceSnapshot = preferences?.exportBackupJson(
                beforeImportMistakes.associate { it.id to it.stableId }
            )
            journal = coordinator.advance(
                BackupImportPhase.SNAPSHOT_CREATED,
                snapshot = BackupRollbackSnapshot(
                    preferencesJson = preferenceSnapshot?.toString(),
                    referencedImagePaths = previousReferencedImagePaths
                )
            )
            val existing = if (mode == BackupImportMode.REPLACE) emptyList() else beforeImportMistakes
            val existingPointsBeforeImport = knowledgePointDao.listAll()
            val existingPoints = if (mode == BackupImportMode.REPLACE) emptyList() else existingPointsBeforeImport
            val byStableId = existing.filter { it.stableId.isNotBlank() }.associateBy(MistakeEntity::stableId).toMutableMap()
            val byFingerprint = existing.associateBy(::fingerprint).toMutableMap()
            val pointsByStableId = existingPoints.associateBy(KnowledgePointEntity::stableId).toMutableMap()
            val pointsBySubjectName = existingPoints.associateBy { it.subject to it.normalizedName }.toMutableMap()
            val importedStableToLocalId = mutableMapOf<String, Long>()
            var inserted = 0
            var updated = 0
            var skipped = 0
            var copiedImages = 0

            // Room rows must never point at files that have not reached their
            // final private path. Promotion has its own rollback journal.
            rollbackDirectory.mkdirs()
            promoteStagedImages(stagedImages.values, createdImagePaths, rollbackDirectory)
            coordinator.advance(BackupImportPhase.FILES_PUBLISHED, createdImagePaths)

            database.withTransaction {
                if (mode == BackupImportMode.REPLACE) {
                    reviewDao.deleteAll()
                    crossRefDao.deleteAll()
                    dao.deleteAll()
                    knowledgePointDao.deleteAll()
                    pointsByStableId.clear()
                    pointsBySubjectName.clear()
                }
                payload.records.forEach { record ->
                    val incoming = record.entity
                    val existingMatch = if (mode == BackupImportMode.REPLACE) null
                        else byStableId[incoming.stableId] ?: byFingerprint[fingerprint(incoming)]
                    if (existingMatch != null && incoming.updatedAt <= existingMatch.updatedAt) {
                        importedStableToLocalId[incoming.stableId] = existingMatch.id
                        skipped += 1
                        return@forEach
                    }

                    fun importedImage(entry: String?, role: String, existingPath: String?): String? {
                        if (entry == null) {
                            return existingPath.takeIf { mode == BackupImportMode.MERGE }
                        }
                        val staged = stagedImages[stagedImageKey(incoming.stableId, role, entry)]
                            ?: return null
                        copiedImages += 1
                        return staged.target.absolutePath
                    }

                    val importedQuestionImages = record.questionImageEntries.mapIndexedNotNull { index, entry ->
                        importedImage(entry, "question-$index", null)
                    }
                    val existingQuestionImages = sourceImagePaths(existingMatch)
                    val questionImages = importedQuestionImages.ifEmpty {
                        if (mode == BackupImportMode.MERGE) existingQuestionImages else emptyList()
                    }
                    val questionImage = questionImages.firstOrNull()
                        ?: importedImage(record.questionImageEntry, "question", existingMatch?.imagePath)
                    val importedBlocks = record.contentBlockEntries.mapNotNull { block ->
                        val path = importedImage(block.entry, "block-${block.order}", null) ?: return@mapNotNull null
                        QuestionContentBlock(
                            role = runCatching { ContentBlockRole.valueOf(block.role) }.getOrDefault(ContentBlockRole.QUESTION),
                            kind = runCatching { ContentBlockKind.valueOf(block.kind) }.getOrDefault(ContentBlockKind.GRAPHIC),
                            path = path,
                            sourcePath = block.sourcePathEntry?.let { sourceEntry ->
                                val sourceIndex = record.questionImageEntries.indexOf(sourceEntry)
                                questionImages.getOrNull(sourceIndex)
                            },
                            caption = block.caption,
                            order = block.order
                        )
                    }

                    val prepared = incoming.copy(
                        id = existingMatch?.id ?: 0L,
                        stableId = existingMatch?.stableId ?: incoming.stableId,
                        imagePath = questionImage,
                        sourceImagePaths = if (questionImages.isNotEmpty()) {
                            JSONArray(questionImages).toString()
                        } else {
                            existingMatch?.sourceImagePaths.orEmpty()
                        },
                        contentBlocks = if (importedBlocks.isNotEmpty()) {
                            QuestionContentBlockCodec.encode(importedBlocks)
                        } else {
                            existingMatch?.contentBlocks.orEmpty()
                        },
                        answerImagePath = importedImage(record.answerImageEntry, "answer", existingMatch?.answerImagePath),
                        explanationImagePath = importedImage(record.explanationImageEntry, "explanation", existingMatch?.explanationImagePath),
                        deletedAt = null
                    )
                    val localId = if (prepared.id > 0L) {
                        dao.update(prepared)
                        prepared.id
                    } else {
                        dao.upsert(prepared)
                    }
                    importedStableToLocalId[incoming.stableId] = localId
                    val stored = prepared.copy(id = localId)
                    byStableId[stored.stableId] = stored
                    byFingerprint[fingerprint(stored)] = stored
                    if (existingMatch == null) inserted += 1 else updated += 1
                }

                val importedPoints = mutableMapOf<String, KnowledgePointEntity>()
                payload.knowledgePoints.forEach { incoming ->
                    val existingPoint = if (mode == BackupImportMode.REPLACE) null
                    else pointsByStableId[incoming.stableId]
                        ?: pointsBySubjectName[incoming.subject to incoming.normalizedName]
                    val stableId = existingPoint?.stableId ?: incoming.stableId
                    val candidate = KnowledgePointEntity(
                        id = existingPoint?.id ?: 0L,
                        stableId = stableId,
                        subject = incoming.subject,
                        name = incoming.name,
                        normalizedName = incoming.normalizedName,
                        parentId = null,
                        createdAt = minOf(existingPoint?.createdAt ?: incoming.createdAt, incoming.createdAt),
                        updatedAt = maxOf(existingPoint?.updatedAt ?: incoming.updatedAt, incoming.updatedAt)
                    )
                    val localId = if (candidate.id > 0L) {
                        knowledgePointDao.update(candidate)
                        candidate.id
                    } else {
                        val insertedId = knowledgePointDao.insertIgnore(candidate)
                        if (insertedId > 0L) insertedId
                        else knowledgePointDao.findByStableId(stableId)?.id
                            ?: knowledgePointDao.findBySubjectAndName(candidate.subject, candidate.normalizedName)?.id
                            ?: error("无法导入知识点：${candidate.name}")
                    }
                    val stored = candidate.copy(id = localId)
                    importedPoints[incoming.stableId] = stored
                    pointsByStableId[stored.stableId] = stored
                    pointsByStableId[incoming.stableId] = stored
                    pointsBySubjectName[stored.subject to stored.normalizedName] = stored
                }
                payload.knowledgePoints.forEach { incoming ->
                    val point = importedPoints[incoming.stableId] ?: return@forEach
                    val parentId = incoming.parentStableId?.let {
                        importedPoints[it]?.id ?: pointsByStableId[it]?.id
                    }
                    if (point.parentId != parentId) {
                        val updatedPoint = point.copy(parentId = parentId)
                        knowledgePointDao.update(updatedPoint)
                        importedPoints[incoming.stableId] = updatedPoint
                        pointsByStableId[updatedPoint.stableId] = updatedPoint
                    }
                }
                payload.crossRefs.forEach { ref ->
                    val mistakeId = importedStableToLocalId[ref.mistakeStableId] ?: return@forEach
                    val pointId = importedPoints[ref.knowledgePointStableId]?.id
                        ?: pointsByStableId[ref.knowledgePointStableId]?.id
                        ?: return@forEach
                    crossRefDao.insert(MistakeKnowledgePointCrossRef(mistakeId, pointId))
                }
                payload.reviewRecords.forEach { incoming ->
                    val mistakeId = importedStableToLocalId[incoming.mistakeStableId] ?: return@forEach
                    if (reviewDao.findDuplicate(
                            mistakeId = mistakeId,
                            reviewedAt = incoming.reviewedAt,
                            grade = incoming.grade,
                            masteryBefore = incoming.masteryBefore,
                            masteryAfter = incoming.masteryAfter,
                            intervalBeforeDays = incoming.intervalBeforeDays,
                            intervalAfterDays = incoming.intervalAfterDays,
                            previousNextReviewAt = incoming.previousNextReviewAt,
                            nextReviewAt = incoming.nextReviewAt
                        ) == null
                    ) {
                        reviewDao.insert(
                            ReviewRecordEntity(
                                mistakeId = mistakeId,
                                reviewedAt = incoming.reviewedAt,
                                grade = incoming.grade,
                                masteryBefore = incoming.masteryBefore,
                                masteryAfter = incoming.masteryAfter,
                                intervalBeforeDays = incoming.intervalBeforeDays,
                                intervalAfterDays = incoming.intervalAfterDays,
                                previousNextReviewAt = incoming.previousNextReviewAt,
                                nextReviewAt = incoming.nextReviewAt
                            )
                        )
                    }
                }
                knowledgePointDao.deleteOrphans()

                // Apply DataStore settings while the Room transaction is open.
                // DataStore cannot join Room's transaction; if either side
                // fails, the absent marker makes the failure path restore this
                // snapshot before any staged files are removed.
                preferences?.importBackupJson(
                    payload.preferences,
                    importedStableToLocalId,
                    replace = mode == BackupImportMode.REPLACE
                )
                val repository = MistakeRepository(database)
                if (payload.preview.schemaVersion < SCHEMA_VERSION && importedStableToLocalId.isNotEmpty()) {
                    repository.syncKnowledgePointsForMistakesInTransaction(importedStableToLocalId.values)
                }
                // Every Room write needed for the import's final business
                // state is complete before the marker is inserted.
                repository.sanitizeKnowledgePointParentsInTransactionForImport()
                // This marker is the final Room operation. It is deliberately
                // independent of image references so preference-only imports
                // have the same transaction fact as image imports.
                database.backupImportCommitMarkerDao().markCommitted(
                    importId = importId,
                    mode = mode.name,
                    committedAt = System.currentTimeMillis()
                )
            }
            coordinator.advance(BackupImportPhase.ROOM_COMMITTED)
            // DataStore is part of the import protocol even though it cannot
            // participate in Room's transaction. The pre-import snapshot is
            // restored in the failure path below when its write throws.
            coordinator.advance(BackupImportPhase.PREFERENCES_COMMITTED)

            coordinator.advance(BackupImportPhase.COMMITTED)
            BackupImportResult(inserted, updated, skipped, copiedImages)
        }
        if (result.isSuccess) {
            val committedJournal = requireNotNull(coordinator.read()) { "备份导入已成功但 journal 丢失" }
            finalizeCommittedImport(coordinator, database, committedJournal)
        } else {
            // The transaction may already have committed before a later
            // journal/sanitization step failed. The marker, rather than the
            // presence of images, decides whether compensation is allowed.
            val failedJournalRead = coordinator.readResult()
            if (failedJournalRead.status == BackupJournalReadStatus.CORRUPT) {
                // A newly corrupt journal is itself a recovery incident. Keep
                // its diagnostic and all files for deliberate startup repair.
            } else {
                val failedJournal = failedJournalRead.journal
                val markerCommitted = database.backupImportCommitMarkerDao().isCommitted(importId)
                if (markerCommitted) {
                    if (failedJournal != null) {
                        finalizeCommittedImport(coordinator, database, failedJournal)
                    } else {
                        stagingDirectory.deleteRecursively()
                        rollbackDirectory.deleteRecursively()
                        database.backupImportCommitMarkerDao().clear(importId)
                        coordinator.clear()
                    }
                } else {
                    // Room rolled back (or was never entered). Restore the
                    // portable DataStore snapshot before deleting the journal.
                    // If restoration fails, retain the journal and files so
                    // startup recovery can retry instead of reporting a false
                    // rollback.
                    val restore = runCatching {
                        if (failedJournal != null) {
                            restorePreferenceSnapshot(database, preferences, failedJournal)
                        } else if (preferenceSnapshot != null && preferences != null) {
                            val stableToLocal = database.mistakeDao().listAll().associate { it.stableId to it.id }
                            preferences.importBackupJson(
                                preferenceSnapshot,
                                stableToLocal,
                                replace = true
                            )
                        }
                    }
                    if (restore.isSuccess) {
                        ImageStorage.deletePrivateFiles(context, createdImagePaths)
                        if (failedJournal != null) {
                            coordinator.cleanup(failedJournal, deleteCreatedFiles = true)
                        } else {
                            stagingDirectory.deleteRecursively()
                            rollbackDirectory.deleteRecursively()
                            coordinator.clear()
                        }
                        database.backupImportCommitMarkerDao().clear(importId)
                    }
                }
            }
        }
        result
    }

    private fun parsePayload(entries: Map<String, ByteArray>, strictImages: Boolean = true): BackupPayload {
        val manifest = entries["manifest.json"]?.decodeUtf8()?.let(::JSONObject)
        return if (manifest?.optString("format") == FORMAT) {
            parseVersioned(entries, manifest, strictImages)
        } else {
            parseLegacy(entries, strictImages)
        }
    }

    private fun parseVersioned(
        entries: Map<String, ByteArray>,
        manifest: JSONObject,
        strictImages: Boolean
    ): BackupPayload {
        val schema = manifest.optInt("schemaVersion", 0)
        val minReaderSchema = manifest.optInt("minReaderSchemaVersion", schema)
        require(schema in 1..SCHEMA_VERSION && minReaderSchema in 1..schema) {
            "备份格式版本 $schema 暂不支持，请升级题迹后再导入"
        }
        val array = entries["data/mistakes.json"]?.decodeUtf8()?.let(::JSONArray)
            ?: error("备份缺少错题数据")
        require(array.length() <= MAX_RECORDS) { "备份中的错题数量过多" }
        val records = (0 until array.length()).map { index ->
            parseRecord(array.getJSONObject(index), legacyBase = null, availableEntries = entries.keys)
        }
        val expectedImages = records.flatMap { record ->
            record.questionImageEntries + listOfNotNull(record.questionImageEntry, record.answerImageEntry, record.explanationImageEntry) +
                record.contentBlockEntries.map(ImportedContentBlock::entry)
        }.distinct()
        val missingImages = expectedImages.count { it !in entries }
        if (strictImages) {
            expectedImages.forEach { name -> require(name in entries) { "备份缺少图片文件：$name" } }
        }
        val preferences = entries["data/preferences.json"]?.decodeUtf8()?.let(::JSONObject)
        val reviewRecords = parseReviewRecords(entries["data/review_records.json"]?.decodeUtf8())
        val knowledgePoints = parseKnowledgePoints(entries["data/knowledge_points.json"]?.decodeUtf8())
        val crossRefs = parseCrossRefs(entries["data/mistake_knowledge_points.json"]?.decodeUtf8())
        val preview = BackupPreview(
            schemaVersion = schema,
            appVersion = manifest.optString("appVersion", "未知"),
            exportedAt = manifest.optLong("exportedAt", 0L),
            mistakeCount = records.size,
            imageCount = records.sumOf { record ->
                (record.questionImageEntries + listOfNotNull(record.questionImageEntry, record.answerImageEntry, record.explanationImageEntry) +
                    record.contentBlockEntries.map(ImportedContentBlock::entry)).distinct().count { it in entries }
            },
            reviewRecordCount = if (entries["data/review_records.json"] != null) reviewRecords.size else countReviewRecords(preferences),
            legacy = false,
            knowledgePointCount = knowledgePoints.size,
            missingImages = missingImages
        )
        return BackupPayload(preview, records, preferences, entries, reviewRecords, knowledgePoints, crossRefs)
    }

    private fun parseLegacy(entries: Map<String, ByteArray>, strictImages: Boolean): BackupPayload {
        val jsonEntries = entries.keys.filter { it.endsWith(".json", ignoreCase = true) }
            .filterNot { it.startsWith("data/") || it == "manifest.json" }
            .sorted()
        require(jsonEntries.isNotEmpty()) { "不是有效的题迹备份文件" }
        require(jsonEntries.size <= MAX_RECORDS) { "备份中的错题数量过多" }
        val records = jsonEntries.map { name ->
            parseRecord(
                JSONObject(entries.getValue(name).decodeUtf8()),
                legacyBase = name.removeSuffix(".json"),
                availableEntries = entries.keys
            )
        }
        val expectedImages = records.flatMap { record ->
            record.questionImageEntries + listOfNotNull(record.questionImageEntry, record.answerImageEntry, record.explanationImageEntry) +
                record.contentBlockEntries.map(ImportedContentBlock::entry)
        }.distinct()
        val imageCount = expectedImages.count { it in entries }
        val missingImages = expectedImages.count { it !in entries }
        if (strictImages) {
            expectedImages.forEach { name -> require(name in entries) { "备份缺少图片文件：$name" } }
        }
        return BackupPayload(
            BackupPreview(0, "旧版备份", 0L, records.size, imageCount, 0, legacy = true, missingImages = missingImages),
            records,
            preferences = null,
            entries = entries,
            reviewRecords = emptyList(),
            knowledgePoints = emptyList(),
            crossRefs = emptyList()
        )
    }

    private fun parseRecord(
        json: JSONObject,
        legacyBase: String?,
        availableEntries: Set<String>
    ): ImportedRecord {
        val originalId = json.optLong("id", 0L)
        val createdAt = json.optLong("createdAt", System.currentTimeMillis())
        val title = json.optString("title", "未命名错题")
        val stableId = json.optString("stableId").trim().ifBlank {
            UUID.nameUUIDFromBytes("legacy:$originalId:$createdAt:$title".toByteArray(Charsets.UTF_8)).toString()
        }
        fun legacyImage(kind: String): String? = legacyBase?.let { base ->
            listOf("$base-$kind.jpg", "$base-$kind.jpeg", "$base-$kind.png", "$base-$kind.webp")
                .firstOrNull { it in availableEntries }
        }
        val legacyQuestion = legacyImage("题目")
        val questionEntries = json.optJSONArray("sourceImageEntries")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty().ifEmpty { listOfNotNull(json.optNullableString("imageEntry") ?: legacyQuestion) }
        val blockEntries = json.optJSONArray("contentBlockEntries")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val entry = item.optNullableString("entry") ?: return@mapNotNull null
                ImportedContentBlock(
                    entry = entry,
                    role = item.optString("role", ContentBlockRole.QUESTION.name),
                    kind = item.optString("kind", ContentBlockKind.GRAPHIC.name),
                    caption = item.optString("caption"),
                    order = item.optInt("order", index),
                    sourcePathEntry = item.optNullableString("sourcePathEntry")
                )
            }
        }.orEmpty()
        val entity = MistakeEntity(
            id = 0L,
            stableId = stableId,
            title = title,
            questionText = json.optString("questionText"),
            userAnswer = json.optString("userAnswer"),
            answerText = json.optString("answerText"),
            explanation = json.optString("explanation"),
            note = json.optString("note"),
            errorReason = json.optString("errorReason"),
            subject = json.optString("subject", "未分类"),
            questionType = json.optString("questionType", "未分类"),
            tags = json.optString("tags"),
            difficulty = Difficulty.normalize(json.optInt("difficulty", 0)),
            includeSourceImageInPdf = json.optBoolean("includeSourceImageInPdf", true),
            mastery = json.optInt("mastery", 0),
            ocrText = json.optString("ocrText"),
            uploadedAt = json.optLong("uploadedAt", createdAt),
            createdAt = createdAt,
            updatedAt = json.optLong("updatedAt", createdAt),
            lastReviewedAt = json.optNullableLong("lastReviewedAt"),
            nextReviewAt = json.optLong("nextReviewAt", createdAt),
            reviewCount = json.optInt("reviewCount", 0).coerceAtLeast(0),
            inReviewPlan = if (json.has("inReviewPlan")) json.optBoolean("inReviewPlan") else true,
            archived = json.optBoolean("archived", false),
            sourceImagePaths = "",
            contentBlocks = "",
            deletedAt = null
        )
        return ImportedRecord(
            entity,
            questionEntries.firstOrNull(),
            json.optNullableString("answerImageEntry") ?: legacyImage("答案"),
            json.optNullableString("explanationImageEntry") ?: legacyImage("解析"),
            questionEntries,
            blockEntries
        )
    }

    private fun mistakeToJson(
        mistake: MistakeEntity,
        imageEntry: String?,
        answerImageEntry: String?,
        explanationImageEntry: String?,
        sourceImageEntries: List<String>,
        contentBlockEntries: List<JSONObject>
    ) = JSONObject().apply {
        put("stableId", mistake.stableId)
        put("title", mistake.title)
        put("questionText", mistake.questionText)
        put("userAnswer", mistake.userAnswer)
        put("answerText", mistake.answerText)
        put("explanation", mistake.explanation)
        put("note", mistake.note)
        put("errorReason", mistake.errorReason)
        put("subject", mistake.subject)
        put("questionType", mistake.questionType)
        put("tags", mistake.tags)
        put("difficulty", mistake.difficulty)
        put("includeSourceImageInPdf", mistake.includeSourceImageInPdf)
        put("mastery", mistake.mastery)
        put("ocrText", mistake.ocrText)
        put("uploadedAt", mistake.uploadedAt)
        put("createdAt", mistake.createdAt)
        put("updatedAt", mistake.updatedAt)
        put("lastReviewedAt", mistake.lastReviewedAt ?: JSONObject.NULL)
        put("nextReviewAt", mistake.nextReviewAt)
        put("reviewCount", mistake.reviewCount)
        put("inReviewPlan", mistake.inReviewPlan)
        put("archived", mistake.archived)
        put("imageEntry", imageEntry ?: JSONObject.NULL)
        put("answerImageEntry", answerImageEntry ?: JSONObject.NULL)
        put("explanationImageEntry", explanationImageEntry ?: JSONObject.NULL)
        put("sourceImageEntries", JSONArray(sourceImageEntries))
        put("contentBlockEntries", JSONArray().apply { contentBlockEntries.forEach(::put) })
    }

    private fun reviewRecordsToJson(record: ReviewRecordEntity, mistakeStableId: String): JSONObject? {
        if (runCatching { ReviewGrade.valueOf(record.grade) }.isFailure) return null
        return JSONObject()
            .put("mistakeStableId", mistakeStableId)
            .put("reviewedAt", record.reviewedAt)
            .put("grade", record.grade)
            .put("masteryBefore", record.masteryBefore)
            .put("masteryAfter", record.masteryAfter)
            .put("intervalBeforeDays", record.intervalBeforeDays)
            .put("intervalAfterDays", record.intervalAfterDays)
            .put("previousNextReviewAt", record.previousNextReviewAt)
            .put("nextReviewAt", record.nextReviewAt)
    }

    private fun knowledgePointToJson(point: KnowledgePointEntity, parentStableId: String?): JSONObject = JSONObject()
        .put("stableId", point.stableId)
        .put("subject", point.subject)
        .put("name", point.name)
        .put("normalizedName", point.normalizedName)
        .put("parentStableId", parentStableId ?: JSONObject.NULL)
        .put("createdAt", point.createdAt)
        .put("updatedAt", point.updatedAt)

    private fun parseReviewRecords(raw: String?): List<ImportedReviewRecord> {
        if (raw == null) return emptyList()
        val array = JSONArray(raw)
        require(array.length() <= MAX_RECORDS) { "备份中的复习记录数量过多" }
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val grade = json.optString("grade").trim()
            require(runCatching { ReviewGrade.valueOf(grade) }.isSuccess) { "备份包含无效复习评价" }
            ImportedReviewRecord(
                mistakeStableId = json.optString("mistakeStableId").trim().also {
                    require(it.isNotBlank()) { "复习记录缺少错题 stableId" }
                },
                reviewedAt = json.optLong("reviewedAt"),
                grade = grade,
                masteryBefore = json.optInt("masteryBefore").coerceIn(0, 3),
                masteryAfter = json.optInt("masteryAfter").coerceIn(0, 3),
                intervalBeforeDays = json.optInt("intervalBeforeDays").coerceAtLeast(1),
                intervalAfterDays = json.optInt("intervalAfterDays").coerceAtLeast(1),
                previousNextReviewAt = json.optLong("previousNextReviewAt"),
                nextReviewAt = json.optLong("nextReviewAt")
            )
        }
    }

    private fun parseKnowledgePoints(raw: String?): List<ImportedKnowledgePoint> {
        if (raw == null) return emptyList()
        val array = JSONArray(raw)
        require(array.length() <= MAX_RECORDS) { "备份中的知识点数量过多" }
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val name = json.optString("name").trim()
            val normalizedName = json.optString("normalizedName").trim()
                .ifBlank { KnowledgePointNormalizer.normalizeName(name) }
            ImportedKnowledgePoint(
                stableId = json.optString("stableId").trim().also {
                    require(it.isNotBlank()) { "知识点缺少 stableId" }
                },
                subject = json.optString("subject", "未分类").trim().ifBlank { "未分类" },
                name = name.ifBlank { normalizedName },
                normalizedName = normalizedName,
                parentStableId = json.optNullableString("parentStableId"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }

    private fun parseCrossRefs(raw: String?): List<ImportedCrossRef> {
        if (raw == null) return emptyList()
        val array = JSONArray(raw)
        require(array.length() <= MAX_RECORDS * 5) { "备份中的知识点关系数量过多" }
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            ImportedCrossRef(
                mistakeStableId = json.optString("mistakeStableId").trim().also {
                    require(it.isNotBlank()) { "知识点关系缺少错题 stableId" }
                },
                knowledgePointStableId = json.optString("knowledgePointStableId").trim().also {
                    require(it.isNotBlank()) { "知识点关系缺少知识点 stableId" }
                }
            )
        }
    }

    private fun readArchive(context: Context, uri: Uri): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        context.contentResolver.openInputStream(uri)?.use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    require(result.size < MAX_ENTRIES) { "备份内文件数量过多" }
                    val name = entry.name.replace('\\', '/')
                    require(name.isNotBlank() && !name.startsWith('/') && name.split('/').none { it == ".." }) {
                        "备份包含不安全的文件路径"
                    }
                    require(name !in result) { "备份包含重复文件" }
                    val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_ARCHIVE_BYTES) { "备份文件过大" }
                    result[name] = bytes
                    zip.closeEntry()
                }
            }
        } ?: error("无法读取备份文件")
        return result
    }

    private fun readLimited(zip: ZipInputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "备份内单个文件过大" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /** Staged image state; the target path is what the committed row will own. */
    private data class StagedImportImage(
        val target: File,
        val staged: File?
    )

    /** Copies every referenced archive image into app-private staging before Room changes. */
    private fun stagePayloadImages(
        context: Context,
        payload: BackupPayload,
        stagingDirectory: File,
        stagedImages: MutableMap<String, StagedImportImage>
    ) {
        stagingDirectory.mkdirs()
        payload.records.forEach { record ->
            val stableId = record.entity.stableId
            fun stage(entry: String?, role: String) {
                if (entry == null) return
                val key = stagedImageKey(stableId, role, entry)
                if (key in stagedImages) return
                val bytes = payload.entries[entry] ?: error("备份缺少图片文件：$entry")
                stagedImages[key] = stageImportedImage(
                    context = context,
                    stableId = stableId,
                    role = role,
                    entry = entry,
                    bytes = bytes,
                    stagingDirectory = stagingDirectory,
                    sequence = stagedImages.size
                )
            }
            record.questionImageEntries.forEachIndexed { index, entry -> stage(entry, "question-$index") }
            stage(record.questionImageEntry, "question")
            stage(record.answerImageEntry, "answer")
            stage(record.explanationImageEntry, "explanation")
            record.contentBlockEntries.forEach { block -> stage(block.entry, "block-${block.order}") }
        }
    }

    private fun stageImportedImage(
        context: Context,
        stableId: String,
        role: String,
        entry: String,
        bytes: ByteArray,
        stagingDirectory: File,
        sequence: Int
    ): StagedImportImage {
        val extension = entry.substringAfterLast('.', "jpg").lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") } ?: "jpg"
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).take(6).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val target = File(directory, "import_${safeStableId(stableId)}_${role}_$digest.$extension")
        if (target.isFile && target.length() == bytes.size.toLong()) {
            return StagedImportImage(target = target, staged = null)
        }
        val staged = File(stagingDirectory, "${sequence}_${target.name}.tmp")
        staged.outputStream().use { it.write(bytes) }
        check(staged.length() == bytes.size.toLong()) { "无法写入备份图片暂存文件" }
        return StagedImportImage(target = target, staged = staged)
    }

    private fun promoteStagedImages(
        images: Collection<StagedImportImage>,
        createdImagePaths: MutableSet<String>,
        rollbackDirectory: File
    ) {
        data class PromotionJournal(val target: File, val previous: File?)
        val journal = mutableListOf<PromotionJournal>()
        try {
            images.forEachIndexed { index, image ->
                val staged = image.staged ?: return@forEachIndexed
                image.target.parentFile?.mkdirs()
                val previous = image.target.takeIf(File::isFile)?.let { target ->
                    File(rollbackDirectory, "previous_${index}_${target.name}").also {
                        target.copyTo(it, overwrite = true)
                    }
                }
                val entry = PromotionJournal(image.target, previous)
                journal += entry
                val incoming = File(image.target.parentFile, ".${image.target.name}.incoming")
                incoming.delete()
                staged.copyTo(incoming, overwrite = true)
                if (image.target.exists() && !image.target.delete()) {
                    error("无法替换备份图片")
                }
                if (!incoming.renameTo(image.target)) {
                    incoming.copyTo(image.target, overwrite = true)
                    check(incoming.delete()) { "无法完成备份图片迁移" }
                }
                check(staged.delete()) { "无法清理备份图片暂存文件" }
            }
            createdImagePaths += journal.map { it.target.absolutePath }
        } catch (error: Throwable) {
            // Restore every target touched by this promotion, including the
            // target currently being moved when a rename/copy fails halfway.
            journal.asReversed().forEach { entry ->
                runCatching {
                    entry.target.delete()
                    entry.previous?.takeIf(File::isFile)?.copyTo(entry.target, overwrite = true)
                }
                File(entry.target.parentFile, ".${entry.target.name}.incoming").delete()
            }
            createdImagePaths.clear()
            throw error
        }
    }

    private fun stagedImageKey(stableId: String, role: String, entry: String): String =
        "$stableId\u001f$role\u001f$entry"

    private fun fingerprint(mistake: MistakeEntity): String = listOf(
        mistake.title.trim().lowercase(),
        mistake.questionText.trim().lowercase(),
        mistake.createdAt.toString()
    ).joinToString("\u001f")

    /** @deprecated Only reads the legacy backup field for preview compatibility. */
    @Deprecated("Legacy backup preview compatibility only")
    private fun countReviewRecords(preferences: JSONObject?): Int {
        val mastery = preferences?.optJSONObject("reviewMastery") ?: return 0
        return mastery.keys().asSequence().sumOf { date -> mastery.optJSONObject(date)?.length() ?: 0 }
    }

    private fun safeStableId(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .take(80).ifBlank { UUID.randomUUID().toString() }

    private fun sourceImagePaths(mistake: MistakeEntity?): List<String> = runCatching {
        val array = JSONArray(mistake?.sourceImagePaths?.ifBlank { "[]" } ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(mistake?.imagePath) }

    private fun putJson(zip: ZipOutputStream, name: String, value: Any) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun ByteArray.decodeUtf8(): String = toString(Charsets.UTF_8)
    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private data class ExportImage(val entry: String, val file: File)
    private data class ImportedRecord(
        val entity: MistakeEntity,
        val questionImageEntry: String?,
        val answerImageEntry: String?,
        val explanationImageEntry: String?,
        val questionImageEntries: List<String> = emptyList(),
        val contentBlockEntries: List<ImportedContentBlock> = emptyList()
    )
    private data class ImportedContentBlock(
        val entry: String,
        val role: String,
        val kind: String,
        val caption: String,
        val order: Int,
        val sourcePathEntry: String?
    )
    private data class ImportedReviewRecord(
        val mistakeStableId: String,
        val reviewedAt: Long,
        val grade: String,
        val masteryBefore: Int,
        val masteryAfter: Int,
        val intervalBeforeDays: Int,
        val intervalAfterDays: Int,
        val previousNextReviewAt: Long,
        val nextReviewAt: Long
    )
    private data class ImportedKnowledgePoint(
        val stableId: String,
        val subject: String,
        val name: String,
        val normalizedName: String,
        val parentStableId: String?,
        val createdAt: Long,
        val updatedAt: Long
    )
    private data class ImportedCrossRef(
        val mistakeStableId: String,
        val knowledgePointStableId: String
    )
    private data class BackupPayload(
        val preview: BackupPreview,
        val records: List<ImportedRecord>,
        val preferences: JSONObject?,
        val entries: Map<String, ByteArray>,
        val reviewRecords: List<ImportedReviewRecord> = emptyList(),
        val knowledgePoints: List<ImportedKnowledgePoint> = emptyList(),
        val crossRefs: List<ImportedCrossRef> = emptyList()
    )
}
