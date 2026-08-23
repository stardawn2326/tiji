package com.tiji.mistakes.service

import android.content.ContentResolver
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tiji.mistakes.data.MistakeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportService {
    private enum class PdfMode { ALL, QUESTIONS, ANSWERS }

    fun writePdf(resolver: ContentResolver, uri: Uri, mistakes: List<MistakeEntity>, exportOriginalImagesOnly: Boolean = false): Result<Unit> =
        writePdfInternal(resolver, uri, mistakes, PdfMode.ALL, exportOriginalImagesOnly)

    fun writeQuestionPdf(resolver: ContentResolver, uri: Uri, mistakes: List<MistakeEntity>, exportOriginalImagesOnly: Boolean = false): Result<Unit> =
        writePdfInternal(resolver, uri, mistakes, PdfMode.QUESTIONS, exportOriginalImagesOnly)

    fun writeAnswerPdf(resolver: ContentResolver, uri: Uri, mistakes: List<MistakeEntity>, exportOriginalImagesOnly: Boolean = false): Result<Unit> =
        writePdfInternal(resolver, uri, mistakes, PdfMode.ANSWERS, exportOriginalImagesOnly)

    private fun writePdfInternal(
        resolver: ContentResolver,
        uri: Uri,
        mistakes: List<MistakeEntity>,
        mode: PdfMode,
        exportOriginalImagesOnly: Boolean
    ): Result<Unit> = runCatching {
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val left = 40f
            val right = pageWidth - 40f
            val contentWidth = right - left
            val contentBottom = 798f
            val navy = Color.rgb(36, 70, 104)
            val blue = Color.rgb(59, 94, 204)
            val muted = Color.rgb(112, 132, 153)
            val divider = Color.rgb(216, 226, 236)
            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 22f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val pageHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 10.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navy
                textSize = 13f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(43, 59, 75)
                textSize = 10.5f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blue
                textSize = 9f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muted
                textSize = 9f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = divider
                strokeWidth = 0.8f
            }
            val imageBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = divider
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val heading = when (mode) {
                PdfMode.ALL -> "题迹错题册"
                PdfMode.QUESTIONS -> "题迹错题练习册"
                PdfMode.ANSWERS -> "题迹答案与解析册"
            }
            val exportedOn = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date())
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var y = 0f

            fun drawHeader(firstPage: Boolean) {
                if (firstPage) {
                    page.canvas.drawRoundRect(RectF(left, 35f, left + 5f, 70f), 2.5f, 2.5f, accentPaint)
                    page.canvas.drawText(heading, left + 16f, 59f, headingPaint)
                    val summary = "共 ${mistakes.size} 道题  ·  导出于 $exportedOn"
                    page.canvas.drawText(summary, left + 16f, 82f, metaPaint)
                    page.canvas.drawLine(left, 98f, right, 98f, dividerPaint)
                    y = 116f
                } else {
                    page.canvas.drawText(heading, left, 37f, pageHeaderPaint)
                    val summary = "共 ${mistakes.size} 道题"
                    page.canvas.drawText(summary, right - pageHeaderPaint.measureText(summary), 37f, pageHeaderPaint)
                    page.canvas.drawLine(left, 52f, right, 52f, dividerPaint)
                    y = 67f
                }
            }

            fun drawFooter() {
                page.canvas.drawLine(left, 810f, right, 810f, dividerPaint)
                page.canvas.drawText("题迹 · 错题整理与复习", left, 827f, metaPaint)
                val number = "第 $pageNumber 页"
                page.canvas.drawText(number, right - metaPaint.measureText(number), 827f, metaPaint)
            }

            fun newPage() {
                drawFooter()
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                drawHeader(firstPage = false)
            }

            fun ensureSpace(requiredHeight: Float) {
                if (y + requiredHeight > contentBottom) newPage()
            }

            fun drawWrappedText(text: String, paint: Paint, lineHeight: Float, maxWidth: Float = contentWidth) {
                wrapForPdf(text, paint, maxWidth).forEach { line ->
                    ensureSpace(lineHeight)
                    page.canvas.drawText(line, left, y, paint)
                    y += lineHeight
                }
            }

            fun drawSection(label: String, source: String) {
                val text = formatForPdf(source)
                if (text.isBlank()) return
                ensureSpace(34f)
                page.canvas.drawText(label, left, y, labelPaint)
                y += 14.5f
                drawWrappedText(text, bodyPaint, 18f)
                y += 4f
            }

            fun drawImage(path: String?, blackAndWhite: Boolean = false) {
                val file = path?.let(::File)
                if (file?.isFile != true) return
                val bitmap = if (blackAndWhite) {
                    ImageProcessor.decodeDocumentClean(file.absolutePath)
                } else {
                    BitmapFactory.decodeFile(file.absolutePath)
                } ?: return
                try {
                    val scale = minOf(
                        (contentWidth * 0.72f) / bitmap.width.toFloat(),
                        185f / bitmap.height.toFloat(),
                        1.1f
                    )
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    ensureSpace(height + 14f)
                    val imageLeft = left + (contentWidth - width) / 2f
                    val destination = RectF(imageLeft, y, imageLeft + width, y + height)
                    page.canvas.drawBitmap(bitmap, null, destination, bodyPaint)
                    y += height + 10f
                } finally {
                    bitmap.recycle()
                }
            }

            fun drawImageSection(label: String, path: String?, blackAndWhite: Boolean = false) {
                val file = path?.let(::File)
                if (file?.isFile != true) return
                ensureSpace(32f)
                if (label.isNotBlank()) {
                    page.canvas.drawText(label, left, y, labelPaint)
                    y += 14.5f
                }
                drawImage(path, blackAndWhite)
            }

            fun drawContentBlockImages(mistake: MistakeEntity, role: ContentBlockRole, label: String) {
                QuestionContentBlockCodec.decode(mistake.contentBlocks)
                    .filter { it.role == role }
                    .forEach { block ->
                        drawImageSection("", block.path, blackAndWhite = block.kind == ContentBlockKind.GRAPHIC)
                    }
            }

            fun drawAnswerArea(questionNumber: Int) {
                if (y + 84f > contentBottom) {
                    newPage()
                    page.canvas.drawText("第 $questionNumber 题（续）", left, y, questionPaint)
                    y += 23f
                }
                page.canvas.drawText("作答区", left, y, labelPaint)
                y += 15f
                repeat(5) {
                    y += 12f
                    page.canvas.drawLine(left, y, right, y, dividerPaint)
                }
                y += 8f
            }

            fun drawQuestionHeader(index: Int, mistake: MistakeEntity) {
                val title = formatForPdf(mistake.title).ifBlank { "错题" }
                val titleLines = wrapForPdf("${index + 1}. $title", questionPaint, contentWidth - 14f)
                val metadata = listOf(mistake.subject, mistake.questionType)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString("  ·  ")
                val metadataLines = wrapForPdf(metadata, metaPaint, contentWidth - 14f)
                val blockHeight = titleLines.size * 19f + metadataLines.size * 13f + 6f
                ensureSpace(maxOf(blockHeight, 46f))
                val blockTop = y - 14f
                page.canvas.drawRoundRect(
                    RectF(left, blockTop, left + 4f, blockTop + maxOf(blockHeight - 2f, 32f)),
                    2f,
                    2f,
                    accentPaint
                )
                titleLines.forEach { line ->
                    page.canvas.drawText(line, left + 14f, y, questionPaint)
                    y += 19f
                }
                metadataLines.forEach { line ->
                    page.canvas.drawText(line, left + 14f, y, metaPaint)
                    y += 13f
                }
                y += 6f
            }

            fun estimateImageSpace(path: String?): Float {
                val file = path?.let(::File)
                if (file?.isFile != true) return 0f
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return 0f
                val scale = minOf(
                    (contentWidth * 0.72f) / options.outWidth.toFloat(),
                    185f / options.outHeight.toFloat(),
                    1.1f
                )
                return options.outHeight * scale + 10f
            }

            fun estimateSectionSpace(source: String): Float {
                val text = formatForPdf(source)
                if (text.isBlank()) return 0f
                return 18.5f + wrapForPdf(text, bodyPaint, contentWidth).size * 16.5f
            }

            fun estimateHeaderSpace(index: Int, mistake: MistakeEntity): Float {
                val title = formatForPdf(mistake.title).ifBlank { "错题" }
                val titleLines = wrapForPdf("${index + 1}. $title", questionPaint, contentWidth - 14f)
                val metadata = listOf(mistake.subject, mistake.questionType)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .joinToString("  ·  ")
                val metadataLines = wrapForPdf(metadata, metaPaint, contentWidth - 14f)
                return maxOf(titleLines.size * 19f + metadataLines.size * 13f + 6f, 46f)
            }

            fun estimateQuestionSpace(index: Int, mistake: MistakeEntity): Float {
                var height = estimateHeaderSpace(index, mistake) + 18f
                val questionBlocks = QuestionContentBlockCodec.decode(mistake.contentBlocks)
                    .filter { it.role == ContentBlockRole.QUESTION }
                val answerBlocks = QuestionContentBlockCodec.decode(mistake.contentBlocks)
                    .filter { it.role == ContentBlockRole.ANSWER }
                val explanationBlocks = QuestionContentBlockCodec.decode(mistake.contentBlocks)
                    .filter { it.role == ContentBlockRole.EXPLANATION }
                val questionImages = sourceImagePaths(mistake)
                if (exportOriginalImagesOnly) {
                    height += questionImages.sumOf { estimateImageSpace(it).toDouble() + if (estimateImageSpace(it) > 0f) 14.5 else 0.0 }.toFloat()
                    if (mode == PdfMode.QUESTIONS) height += 84f
                } else when (mode) {
                    PdfMode.QUESTIONS -> {
                        height += estimateSectionSpace(mistake.questionText)
                        height += questionImages.sumOf { estimateImageSpace(it).toDouble() + if (estimateImageSpace(it) > 0f) 14.5 else 0.0 }.toFloat()
                        height += questionBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += 84f
                    }
                    PdfMode.ANSWERS -> {
                        height += estimateSectionSpace(mistake.questionText)
                        height += questionBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += estimateSectionSpace(mistake.answerText)
                        if (estimateImageSpace(mistake.answerImagePath) > 0f) height += 14.5f + estimateImageSpace(mistake.answerImagePath)
                        height += answerBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += estimateSectionSpace(mistake.explanation)
                        if (estimateImageSpace(mistake.explanationImagePath) > 0f) height += 14.5f + estimateImageSpace(mistake.explanationImagePath)
                        height += explanationBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += estimateSectionSpace(mistake.note)
                    }
                    PdfMode.ALL -> {
                        height += estimateSectionSpace(mistake.questionText)
                        height += questionImages.sumOf { estimateImageSpace(it).toDouble() + if (estimateImageSpace(it) > 0f) 14.5 else 0.0 }.toFloat()
                        height += questionBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += estimateSectionSpace(mistake.answerText)
                        if (estimateImageSpace(mistake.answerImagePath) > 0f) height += 14.5f + estimateImageSpace(mistake.answerImagePath)
                        height += answerBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += estimateSectionSpace(mistake.explanation)
                        if (estimateImageSpace(mistake.explanationImagePath) > 0f) height += 14.5f + estimateImageSpace(mistake.explanationImagePath)
                        height += explanationBlocks.sumOf { estimateImageSpace(it.path).toDouble() + if (estimateImageSpace(it.path) > 0f) 14.5 else 0.0 }.toFloat()
                        height += estimateSectionSpace(mistake.note)
                    }
                }
                return height
            }

            drawHeader(firstPage = true)
            if (mistakes.isEmpty()) {
                drawWrappedText("没有可导出的题目。", bodyPaint, 19f)
            } else {
                mistakes.forEachIndexed { index, mistake ->
                    val freshPageCapacity = contentBottom - 67f
                    ensureSpace(minOf(estimateQuestionSpace(index, mistake), freshPageCapacity))
                    drawQuestionHeader(index, mistake)
                    if (exportOriginalImagesOnly) {
                        sourceImagePaths(mistake).forEachIndexed { sourceIndex, path ->
                            drawImageSection("", path, blackAndWhite = true)
                        }
                        if (mode == PdfMode.QUESTIONS) drawAnswerArea(index + 1)
                    } else when (mode) {
                        PdfMode.QUESTIONS -> {
                            if (mistake.includeSourceImageInPdf) sourceImagePaths(mistake).forEachIndexed { sourceIndex, path ->
                                drawImageSection("", path, blackAndWhite = true)
                            }
                            drawSection("题目", mistake.questionText)
                            drawContentBlockImages(mistake, ContentBlockRole.QUESTION, "题目图")
                            drawAnswerArea(index + 1)
                        }
                        PdfMode.ANSWERS -> {
                            if (mistake.includeSourceImageInPdf) sourceImagePaths(mistake).forEachIndexed { sourceIndex, path ->
                                drawImageSection("", path, blackAndWhite = true)
                            }
                            drawSection("题目", mistake.questionText)
                            drawContentBlockImages(mistake, ContentBlockRole.QUESTION, "题目图")
                            drawImageSection("答案图片", mistake.answerImagePath)
                            drawSection("答案", mistake.answerText)
                            drawContentBlockImages(mistake, ContentBlockRole.ANSWER, "答案图")
                            drawImageSection("解析图片", mistake.explanationImagePath)
                            drawSection("解析", mistake.explanation)
                            drawContentBlockImages(mistake, ContentBlockRole.EXPLANATION, "解析图")
                            drawSection("注释", mistake.note)
                        }
                        PdfMode.ALL -> {
                            if (mistake.includeSourceImageInPdf) sourceImagePaths(mistake).forEachIndexed { sourceIndex, path ->
                                drawImageSection("", path, blackAndWhite = true)
                            }
                            drawSection("题目", mistake.questionText)
                            drawContentBlockImages(mistake, ContentBlockRole.QUESTION, "题目图")
                            drawImageSection("答案图片", mistake.answerImagePath)
                            drawSection("答案", mistake.answerText)
                            drawContentBlockImages(mistake, ContentBlockRole.ANSWER, "答案图")
                            drawImageSection("解析图片", mistake.explanationImagePath)
                            drawSection("解析", mistake.explanation)
                            drawContentBlockImages(mistake, ContentBlockRole.EXPLANATION, "解析图")
                            drawSection("注释", mistake.note)
                        }
                    }
                    if (index != mistakes.lastIndex) {
                        ensureSpace(18f)
                        y += 2f
                        page.canvas.drawLine(left, y, right, y, dividerPaint)
                        y += 14f
                    }
                }
            }
            drawFooter()
            document.finishPage(page)
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                document.writeTo(output)
            }
        } finally {
            document.close()
        }
    }

    fun writeBackup(resolver: ContentResolver, uri: Uri, mistakes: List<MistakeEntity>): Result<Unit> = runCatching {
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output)
            ZipOutputStream(output).use { zip ->
                mistakes.groupBy { safeName(it.subject.ifBlank { "未分类" }) }.forEach { (subject, entries) ->
                    entries.sortedBy { it.uploadedAt }.forEachIndexed { index, mistake ->
                    val order = (index + 1).toString().padStart(3, '0')
                    val base = "$subject/$order-${safeName(mistake.title)}"
                    val json = JSONObject().apply {
                        put("id", mistake.id); put("title", mistake.title); put("questionText", mistake.questionText)
                        put("answerText", mistake.answerText); put("explanation", mistake.explanation); put("note", mistake.note)
                        put("subject", mistake.subject); put("tags", mistake.tags); put("difficulty", mistake.difficulty)
                        put("questionType", mistake.questionType); put("uploadedAt", mistake.uploadedAt)
                        put("mastery", mistake.mastery); put("ocrText", mistake.ocrText); put("createdAt", mistake.createdAt)
                        put("updatedAt", mistake.updatedAt); put("nextReviewAt", mistake.nextReviewAt); put("reviewCount", mistake.reviewCount)
                    }
                    zip.putNextEntry(ZipEntry("$base.json")); zip.write(json.toString(2).toByteArray()); zip.closeEntry()
                    listOf("题目" to mistake.imagePath, "答案" to mistake.answerImagePath, "解析" to mistake.explanationImagePath).forEach { (kind, path) ->
                        val file = path?.let(::File)
                        if (file?.isFile == true) {
                            zip.putNextEntry(ZipEntry("$base-$kind.jpg"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    }
                }
            }
        }
    }

    private fun wrapForPdf(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        return buildList {
            text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { paragraph ->
                if (paragraph.isBlank()) {
                    add("")
                } else {
                    var remaining = paragraph.trim()
                    while (remaining.isNotEmpty()) {
                        val measured = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                        var splitAt = measured
                        if (measured < remaining.length) {
                            val candidate = remaining.substring(0, measured)
                            val whitespace = candidate.indexOfLast(Char::isWhitespace)
                            if (whitespace > measured / 2) splitAt = whitespace + 1
                        }
                        add(remaining.substring(0, splitAt).trimEnd())
                        remaining = remaining.substring(splitAt).trimStart()
                    }
                }
            }
        }
    }

    /** Keep exported text selectable while making common LaTeX readable on paper. */
    private fun formatForPdf(source: String): String {
        var text = normalizeDelimitedFormulaSegments(source)
            .replace("$$", "")
            .replace("$", "")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace("\\[", "")
            .replace("\\]", "")
        val fraction = Regex("""\\(?:d|t)?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
        repeat(6) { text = fraction.replace(text) { "(${it.groupValues[1]})/(${it.groupValues[2]})" } }
        val replacements = mapOf(
            "\\infty" to "∞", "\\rightarrow" to "→", "\\leftarrow" to "←", "\\to" to "→",
            "\\neq" to "≠", "\\ne" to "≠", "\\geq" to "≥", "\\leq" to "≤",
            "\\times" to "×", "\\cdot" to "·", "\\pm" to "±", "\\div" to "÷",
            "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\sqrt" to "√",
            "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
            "\\theta" to "θ", "\\lambda" to "λ", "\\mu" to "μ", "\\pi" to "π",
            "\\sigma" to "σ", "\\phi" to "φ", "\\Delta" to "Δ", "\\Omega" to "Ω"
        )
        replacements.forEach { (latex, symbol) -> text = text.replace(latex, symbol) }
        text = Regex("""\^\{([^{}]+)\}""").replace(text, "^($1)")
        text = Regex("""_\{([^{}]+)\}""").replace(text, "_($1)")
        text = text.replace(Regex("""\{([^{}]+)\}"""), "$1")
        text = text.replace(Regex("""\\([A-Za-z]+)"""), "$1")
        return text.replace(Regex("[ \t]+"), " ").trim()
    }

    private fun normalizeTextbookPunctuation(value: String): String {
        val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$[^\$\n]+\$)""")
        fun prosePart(part: String): String = part
            .replace("...", "……")
            .replace(',', '，')
            .replace(';', '；')
            .replace(':', '：')
            .replace('!', '！')
            .replace('?', '？')
            .replace(Regex("""(?<!\d)\.(?!\d)"""), "．")
            .replace('。', '．')
            .replace(Regex("""（\s*([0-9]+|[A-Za-z])\s*）""")) { "(${it.groupValues[1]})" }

        return buildString {
            var cursor = 0
            delimiter.findAll(value).forEach { match ->
                append(prosePart(value.substring(cursor, match.range.first)))
                append(match.value)
                cursor = match.range.last + 1
            }
            append(prosePart(value.substring(cursor)))
        }
    }

    private fun normalizeDelimitedFormulaSegments(value: String): String {
        val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$[^\$\n]+\$)""")
        return delimiter.replace(normalizeTextbookPunctuation(value)) { match ->
            val token = match.value
            val (opening, closing, rawFormula) = when {
                token.startsWith("\\[") -> Triple("\\[", "\\]", token.substring(2, token.length - 2))
                token.startsWith("\\(") -> Triple("\\(", "\\)", token.substring(2, token.length - 2))
                token.startsWith("$$") -> Triple("$$", "$$", token.substring(2, token.length - 2))
                else -> Triple("$", "$", token.substring(1, token.length - 1))
            }
            var formula = rawFormula.trimEnd()
            var trailing = ""
            val last = formula.lastOrNull()
            if (last != null && last in "，。．；：！？,;:.!?") {
                formula = formula.dropLast(1).trimEnd()
                trailing = if (last in "。．.") "．" else last.toString()
            }
            val normalizedFormula = formula
                .replace('，', ',').replace('、', ',').replace('；', ';').replace('：', ':')
                .replace('。', '.').replace('．', '.').replace('！', '!').replace('？', '?')
                .replace('（', '(').replace('）', ')').replace('［', '[').replace('］', ']')
            opening + normalizedFormula + closing + trailing
        }
    }

    private fun safeName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "未命名" }
}

private fun sourceImagePaths(mistake: MistakeEntity): List<String> = runCatching {
    val array = JSONArray(mistake.sourceImagePaths.ifBlank { "[]" })
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}.getOrDefault(emptyList()).ifEmpty { listOfNotNull(mistake.imagePath) }
