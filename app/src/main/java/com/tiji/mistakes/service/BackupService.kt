package com.tiji.mistakes.service

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tiji.mistakes.BuildConfig
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeEntity
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
    val legacy: Boolean
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
    private const val SCHEMA_VERSION = 2
    private const val MIN_READER_SCHEMA_VERSION = 2
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
            val records = JSONArray()
            val images = mutableListOf<ExportImage>()

            mistakes.forEach { mistake ->
                val safeStableId = safeStableId(mistake.stableId)
                fun image(role: String, path: String?): String? {
                    val file = path?.let(::File)?.takeIf(File::isFile) ?: return null
                    val extension = file.extension.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
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
                records.put(mistakeToJson(
                    mistake,
                    questionEntry,
                    answerEntry,
                    explanationEntry,
                    questionEntries,
                    blockEntries
                ))
            }

            val exportedAt = System.currentTimeMillis()
            val reviewRecords = countReviewRecords(preferences)
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("minReaderSchemaVersion", MIN_READER_SCHEMA_VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("exportedAt", exportedAt)
                .put("mistakeCount", mistakes.size)
                .put("imageCount", images.size)
                .put("reviewRecordCount", reviewRecords)

            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    putJson(zip, "manifest.json", manifest)
                    putJson(zip, "data/mistakes.json", records)
                    putJson(zip, "data/preferences.json", preferences)
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
                reviewRecordCount = reviewRecords,
                legacy = false
            )
        }
    }

    suspend fun inspectBackup(context: Context, uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        runCatching { parsePayload(readArchive(context, uri)).preview }
    }

    internal fun inspectEntries(entries: Map<String, ByteArray>): BackupPreview = parsePayload(entries).preview

    suspend fun importBackup(
        context: Context,
        uri: Uri,
        mode: BackupImportMode
    ): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = parsePayload(readArchive(context, uri))
            val database = AppDatabase.get(context)
            val dao = database.mistakeDao()
            val existing = if (mode == BackupImportMode.REPLACE) emptyList() else dao.listAll()
            val byStableId = existing.filter { it.stableId.isNotBlank() }.associateBy(MistakeEntity::stableId).toMutableMap()
            val byFingerprint = existing.associateBy(::fingerprint).toMutableMap()
            val importedStableToLocalId = mutableMapOf<String, Long>()
            var inserted = 0
            var updated = 0
            var skipped = 0
            var copiedImages = 0

            database.withTransaction {
                if (mode == BackupImportMode.REPLACE) dao.deleteAll()
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
                        val bytes = payload.entries[entry] ?: return null
                        copiedImages += 1
                        return storeImportedImage(context, incoming.stableId, role, entry, bytes)
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
                    val localId = dao.upsert(prepared).let { insertedId ->
                        if (prepared.id > 0L) prepared.id else insertedId
                    }
                    importedStableToLocalId[incoming.stableId] = localId
                    val stored = prepared.copy(id = localId)
                    byStableId[stored.stableId] = stored
                    byFingerprint[fingerprint(stored)] = stored
                    if (existingMatch == null) inserted += 1 else updated += 1
                }
            }

            AppPreferences(context).importBackupJson(
                payload.preferences,
                importedStableToLocalId,
                replace = mode == BackupImportMode.REPLACE
            )
            BackupImportResult(inserted, updated, skipped, copiedImages)
        }
    }

    private fun parsePayload(entries: Map<String, ByteArray>): BackupPayload {
        val manifest = entries["manifest.json"]?.decodeUtf8()?.let(::JSONObject)
        return if (manifest?.optString("format") == FORMAT) parseVersioned(entries, manifest) else parseLegacy(entries)
    }

    private fun parseVersioned(entries: Map<String, ByteArray>, manifest: JSONObject): BackupPayload {
        val schema = manifest.optInt("schemaVersion", 0)
        val minReaderSchema = manifest.optInt("minReaderSchemaVersion", schema)
        require(schema >= 1 && minReaderSchema <= SCHEMA_VERSION) { "备份格式版本 $schema 暂不支持，请升级题迹后再导入" }
        val array = entries["data/mistakes.json"]?.decodeUtf8()?.let(::JSONArray)
            ?: error("备份缺少错题数据")
        require(array.length() <= MAX_RECORDS) { "备份中的错题数量过多" }
        val records = (0 until array.length()).map { index ->
            parseRecord(array.getJSONObject(index), legacyBase = null, availableEntries = entries.keys)
        }
        records.flatMap { record ->
            record.questionImageEntries + listOfNotNull(record.questionImageEntry, record.answerImageEntry, record.explanationImageEntry) +
                record.contentBlockEntries.map(ImportedContentBlock::entry)
        }.distinct().forEach { name -> require(name in entries) { "备份缺少图片文件：$name" } }
        val preferences = entries["data/preferences.json"]?.decodeUtf8()?.let(::JSONObject)
        val preview = BackupPreview(
            schemaVersion = schema,
            appVersion = manifest.optString("appVersion", "未知"),
            exportedAt = manifest.optLong("exportedAt", 0L),
            mistakeCount = records.size,
            imageCount = records.sumOf { record ->
                (record.questionImageEntries + listOfNotNull(record.questionImageEntry, record.answerImageEntry, record.explanationImageEntry) +
                    record.contentBlockEntries.map(ImportedContentBlock::entry)).distinct().count { it in entries }
            },
            reviewRecordCount = countReviewRecords(preferences),
            legacy = false
        )
        return BackupPayload(preview, records, preferences, entries)
    }

    private fun parseLegacy(entries: Map<String, ByteArray>): BackupPayload {
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
        val imageCount = records.sumOf { record ->
            (record.questionImageEntries + listOfNotNull(record.questionImageEntry, record.answerImageEntry, record.explanationImageEntry) +
                record.contentBlockEntries.map(ImportedContentBlock::entry)).distinct().count { it in entries }
        }
        return BackupPayload(
            BackupPreview(0, "旧版备份", 0L, records.size, imageCount, 0, legacy = true),
            records,
            preferences = null,
            entries = entries
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
            difficulty = json.optInt("difficulty", 0).coerceIn(0, 5),
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

    private fun storeImportedImage(
        context: Context,
        stableId: String,
        role: String,
        entry: String,
        bytes: ByteArray
    ): String {
        val extension = entry.substringAfterLast('.', "jpg").lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).take(6).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val target = File(directory, "import_${safeStableId(stableId)}_${role}_$digest.$extension")
        if (!target.isFile || target.length() != bytes.size.toLong()) {
            val temporary = File(directory, "${target.name}.tmp")
            temporary.outputStream().use { it.write(bytes) }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "无法保存备份图片" }
        }
        return target.absolutePath
    }

    private fun fingerprint(mistake: MistakeEntity): String = listOf(
        mistake.title.trim().lowercase(),
        mistake.questionText.trim().lowercase(),
        mistake.createdAt.toString()
    ).joinToString("\u001f")

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
    private data class BackupPayload(
        val preview: BackupPreview,
        val records: List<ImportedRecord>,
        val preferences: JSONObject?,
        val entries: Map<String, ByteArray>
    )
}
