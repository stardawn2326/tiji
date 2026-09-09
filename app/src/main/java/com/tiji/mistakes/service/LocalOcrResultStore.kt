package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Same-UID handoff between the UI process and the isolated OCR process. */
internal object LocalOcrResultStore {
    private const val DIRECTORY = "ocr-results"

    fun file(context: Context, requestId: String): File =
        File(File(context.applicationContext.filesDir, DIRECTORY), "$requestId.json")

    fun clear(context: Context, requestId: String) {
        file(context, requestId).delete()
    }

    fun writeSuccess(context: Context, requestId: String, text: String) {
        writeSuccess(context, requestId, LocalOcrDocument(text))
    }

    fun writeSuccess(context: Context, requestId: String, document: LocalOcrDocument) {
        write(
            context,
            requestId,
            JSONObject().put("ok", true)
                .put("text", document.text)
                .put("diagramTextEvidence", document.diagramTextEvidence)
                .put("rawOcrTrace", document.rawOcrTrace)
                .put("orderedText", document.orderedText)
                .put("formulaCandidates", JSONArray(document.formulaCandidates))
                .put("diagramBlocks", JSONArray().apply {
                    document.diagramBlocks.forEach { block ->
                        put(
                            JSONObject()
                                .put("originalPath", block.originalPath)
                                .put("cropPath", block.cropPath ?: JSONObject.NULL)
                                .put("left", block.left)
                                .put("top", block.top)
                                .put("right", block.right)
                                .put("bottom", block.bottom)
                                .put("type", block.diagramType)
                                .put("labels", JSONArray(block.labels))
                        )
                    }
                })
        )
    }

    fun writeFailure(context: Context, requestId: String, message: String) {
        write(context, requestId, JSONObject().put("ok", false).put("error", message))
    }

    fun read(context: Context, requestId: String): Result<String>? {
        return readDocument(context, requestId)?.map(LocalOcrDocument::text)
    }

    fun readDocument(context: Context, requestId: String): Result<LocalOcrDocument>? {
        val resultFile = file(context, requestId)
        if (!resultFile.isFile) return null
        return runCatching {
            val json = JSONObject(resultFile.readText(Charsets.UTF_8))
            if (json.optBoolean("ok")) {
                val text = json.optString("text").takeIf(String::isNotBlank)
                    ?: error("本地 OCR 未返回识别文字")
                val diagrams = json.optJSONArray("diagramBlocks")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.optJSONObject(index) ?: return@mapNotNull null
                        val originalPath = item.optString("originalPath").takeIf(String::isNotBlank)
                            ?: return@mapNotNull null
                        DiagramBlock(
                            originalPath = originalPath,
                            cropPath = item.optString("cropPath").takeIf { it.isNotBlank() && it != "null" },
                            left = item.optDouble("left", 0.0).toFloat(),
                            top = item.optDouble("top", 0.0).toFloat(),
                            right = item.optDouble("right", 1.0).toFloat(),
                            bottom = item.optDouble("bottom", 1.0).toFloat(),
                            diagramType = item.optString("type", "figure"),
                            labels = item.optJSONArray("labels")?.let { labels ->
                                (0 until labels.length()).mapNotNull { labels.optString(it).takeIf(String::isNotBlank) }
                            }.orEmpty()
                        )
                    }
                }.orEmpty()
                LocalOcrDocument(
                    text = text,
                    diagramBlocks = diagrams,
                    diagramTextEvidence = json.optString("diagramTextEvidence").trim(),
                    rawOcrTrace = json.optString("rawOcrTrace").trim(),
                    orderedText = json.optString("orderedText").trim(),
                    formulaCandidates = json.optJSONArray("formulaCandidates")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty()
                )
            } else {
                error(json.optString("error").ifBlank { "本地 OCR 识别失败" })
            }
        }
    }

    private fun write(context: Context, requestId: String, json: JSONObject) {
        val resultFile = file(context, requestId)
        resultFile.parentFile?.mkdirs()
        val temporary = File(resultFile.parentFile, "${resultFile.name}.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(resultFile)) {
            resultFile.writeText(json.toString(), Charsets.UTF_8)
            temporary.delete()
        }
    }
}
