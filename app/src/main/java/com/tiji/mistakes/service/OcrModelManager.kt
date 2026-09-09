package com.tiji.mistakes.service

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile

data class OcrModelStatus(
    val enabled: Boolean = false,
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val version: String = "",
    val error: String? = null,
    val resumable: Boolean = false
)

/** Manages the single PaddleOCR + Pix2Text package used by the app. */
class OcrModelManager(context: Context) {
    private val appContext = context.applicationContext
    internal val context: Context get() = appContext
    private val root = File(appContext.filesDir, "ocr-models")
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val operationMutex = Mutex()
    private val combinedRoot = File(root, "combined")
    private val downloadGeneration = AtomicLong(0L)
    @Volatile private var activeConnection: HttpURLConnection? = null
    private val _combinedState = MutableStateFlow(loadCombinedState())
    val combinedState: StateFlow<OcrModelStatus> = _combinedState.asStateFlow()

    init {
        // Remove the pre-combined OCR package on upgrade. The current package
        // contains both text and formula recognition and must be the only OCR
        // source consulted by the app.
        cleanupLegacyOcrStorage()
    }

    /** The single user-facing OCR package: Chinese/English text plus math symbols. */
    suspend fun enableCombined(): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            preferences.edit().putBoolean(COMBINED_ENABLED, true).apply()
            if (isCombinedInstalled()) {
                publishCombined { it.copy(enabled = true, downloading = false, error = null) }
                Result.success(Unit)
            } else {
                downloadCombined(force = false)
            }
        }
    }

    fun disableCombined() {
        preferences.edit().putBoolean(COMBINED_ENABLED, false).apply()
        publishCombined { it.copy(enabled = false, error = null) }
    }

    suspend fun updateCombined(): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock { downloadCombined(force = true) }
    }

    /** Stops network I/O but keeps every verified or partial file for Continue. */
    fun stopCombinedDownload() {
        if (!combinedState.value.downloading) return
        downloadGeneration.incrementAndGet()
        activeConnection?.disconnect()
        publishPausedState(error = null)
    }

    fun clearCombined() {
        downloadGeneration.incrementAndGet()
        activeConnection?.disconnect()
        combinedRoot.deleteRecursively()
        File(root, LEGACY_FORMULA_DIRECTORY).deleteRecursively()
        activeConnection = null
        preferences.edit()
            .putBoolean(COMBINED_ENABLED, false)
            .remove("enabled_text")
            .remove("enabled_formula")
            .apply()
        publishCombined { OcrModelStatus(enabled = false, installed = false) }
    }

    fun isCombinedReady(): Boolean = combinedState.value.enabled && isCombinedInstalled()

    /** Returns the private Pix2Text package only after every model file is complete. */
    fun formulaDataPath(): File? {
        val combinedFormulaDir = combinedDataPath()?.let { File(it, "formula") }
        if (combinedFormulaDir != null &&
            FORMULA_SPEC.files.all { spec ->
                val actual = File(combinedFormulaDir, spec.name)
                actual.isFile && actual.length() >= spec.size
            }
        ) {
            return combinedFormulaDir
        }
        return null
    }

    fun isFormulaReady(): Boolean =
        formulaDataPath() != null && combinedState.value.enabled

    /** Returns the completed PaddleOCR + formula model directory. */
    fun combinedDataPath(): File? {
        val versionDir = File(combinedRoot, COMBINED_SPEC.version)
        val abi = supportedRuntimeAbiOrNull() ?: return null
        val markerReady = File(versionDir, COMPLETE_MARKER).isFile &&
            File(versionDir, "models/det").isDirectory &&
            File(versionDir, "models/rec").isDirectory
        val filesReady = COMBINED_SPEC.files.all { spec ->
            val actual = File(versionDir, spec.name)
            actual.isFile && actual.length() >= spec.size
        }
        val runtimeReady = RUNTIME_LIBRARY_NAMES.all { name ->
            val actual = File(versionDir, "native/$abi/$name")
            actual.isFile && actual.length() > MIN_NATIVE_LIBRARY_SIZE
        }
        return versionDir.takeIf { markerReady && filesReady && runtimeReady }
    }

    /** Exact manifest download size shown in settings, including OCR, formula and runtimes. */
    fun downloadPackageSizeBytes(): Long = totalDownloadBytes()

    fun downloadPackageSizeLabel(): String =
        "约${(downloadPackageSizeBytes() + 500_000L) / 1_000_000L}MB"

    private fun isCombinedInstalled(): Boolean = combinedDataPath() != null

    private fun loadCombinedState(): OcrModelStatus {
        val installed = isCombinedInstalled()
        val downloaded = stagedDownloadBytes()
        val total = totalDownloadBytes()
        val resumable = stagingDirectory().isDirectory
        return OcrModelStatus(
            enabled = preferences.getBoolean(COMBINED_ENABLED, false),
            installed = installed,
            progress = if (total > 0L) downloaded.toFloat() / total else 0f,
            downloadedBytes = downloaded,
            totalBytes = if (resumable) total else 0L,
            version = if (installed) COMBINED_SPEC.version else "",
            resumable = resumable
        )
    }

    private fun publishCombined(transform: (OcrModelStatus) -> OcrModelStatus) {
        _combinedState.value = transform(_combinedState.value)
    }

    private fun cleanupLegacyOcrStorage() {
        File(root, LEGACY_FORMULA_DIRECTORY).deleteRecursively()
        preferences.edit()
            .remove("enabled_text")
            .remove("enabled_formula")
            .apply()
    }

    private fun stagingDirectory(): File = File(combinedRoot, ".${COMBINED_SPEC.version}.download")

    private fun totalDownloadBytes(): Long =
        COMBINED_SPEC.files.sumOf { it.size } + RUNTIME_ARCHIVES.sumOf { it.size }

    private fun stagedDownloadBytes(): Long {
        val stagingDir = stagingDirectory()
        val modelBytes = COMBINED_SPEC.files.sumOf { spec ->
            File(stagingDir, spec.name).length().coerceIn(0L, spec.size)
        }
        val archiveDir = File(stagingDir, ".runtime-downloads")
        val runtimeBytes = RUNTIME_ARCHIVES.sumOf { archive ->
            File(archiveDir, archive.name).length().coerceIn(0L, archive.size)
        }
        return modelBytes + runtimeBytes
    }

    private fun publishPausedState(error: String?) {
        val downloaded = stagedDownloadBytes()
        val total = totalDownloadBytes()
        val resumable = stagingDirectory().isDirectory
        publishCombined {
            it.copy(
                installed = isCombinedInstalled(),
                downloading = false,
                progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
                downloadedBytes = downloaded,
                totalBytes = if (resumable) total else 0L,
                version = if (isCombinedInstalled()) COMBINED_SPEC.version else "",
                error = error,
                resumable = resumable
            )
        }
    }

    private suspend fun downloadCombined(force: Boolean): Result<Unit> {
        val versionDir = File(combinedRoot, COMBINED_SPEC.version)
        if (!force && isCombinedInstalled()) {
            publishCombined { it.copy(enabled = true, installed = true, downloading = false, version = COMBINED_SPEC.version, error = null) }
            return Result.success(Unit)
        }
        val sessionId = downloadGeneration.incrementAndGet()
        val stagingDir = stagingDirectory()
        File(stagingDir, "models/det").mkdirs()
        File(stagingDir, "models/rec").mkdirs()
        File(stagingDir, "formula").mkdirs()
        val runtimeAbi = supportedRuntimeAbi()
        val total = totalDownloadBytes()
        val staged = stagedDownloadBytes()
        var completed = 0L
        publishCombined {
            it.copy(
                enabled = true,
                installed = isCombinedInstalled(),
                downloading = true,
                progress = (staged.toFloat() / total).coerceIn(0f, 1f),
                downloadedBytes = staged,
                totalBytes = total,
                version = if (isCombinedInstalled()) COMBINED_SPEC.version else "",
                error = null,
                resumable = staged > 0L
            )
        }
        return try {
            COMBINED_SPEC.files.forEach { file ->
                ensureDownloadActive(sessionId)
                val destination = File(stagingDir, file.name)
                destination.parentFile?.mkdirs()
                if (destination.length() > file.size) destination.delete()
                if (destination.length() != file.size &&
                    destination.length() == 0L &&
                    !force
                ) {
                    copyReusableModelFile(file, destination)
                }
                if (destination.length() != file.size) {
                    downloadFile(downloadSources(file.url), destination, file.size, sessionId) { downloaded ->
                        publishDownloadProgress(completed + downloaded, total)
                    }
                }
                check(destination.length() == file.size) { "OCR 模型文件下载不完整" }
                try {
                    file.sha256?.let { expected -> verifySha256(destination, expected) }
                } catch (error: Throwable) {
                    destination.delete()
                    throw error
                }
                completed += file.size
                publishDownloadProgress(completed, total)
            }
            val archiveDirectory = File(stagingDir, ".runtime-downloads").apply { mkdirs() }
            RUNTIME_ARCHIVES.forEach { archive ->
                ensureDownloadActive(sessionId)
                val destination = File(archiveDirectory, archive.name)
                if (destination.length() > archive.size) destination.delete()
                if (destination.length() != archive.size) {
                    downloadFile(downloadSources(archive.url), destination, archive.size, sessionId) { downloaded ->
                        publishDownloadProgress(completed + downloaded, total)
                    }
                }
                try {
                    verifySha256(destination, archive.sha256)
                } catch (error: Throwable) {
                    destination.delete()
                    throw error
                }
                completed += archive.size
                publishDownloadProgress(completed, total)
                extractRuntimeArchive(destination, stagingDir, runtimeAbi, archive)
            }
            archiveDirectory.deleteRecursively()
            File(stagingDir, COMPLETE_MARKER).writeText(COMBINED_SPEC.version)
            versionDir.deleteRecursively()
            combinedRoot.mkdirs()
            if (!stagingDir.renameTo(versionDir)) error("无法提交 OCR 模型更新")
            pruneOldVersions(combinedRoot, COMBINED_SPEC.version)
            publishCombined {
                it.copy(
                    enabled = true,
                    installed = true,
                    downloading = false,
                    progress = 1f,
                    downloadedBytes = completed,
                    totalBytes = completed,
                    version = COMBINED_SPEC.version,
                    error = null,
                    resumable = false
                )
            }
            Result.success(Unit)
        } catch (stopped: DownloadStoppedException) {
            publishPausedState(error = null)
            Result.failure(stopped)
        } catch (cancelled: CancellationException) {
            downloadGeneration.incrementAndGet()
            activeConnection?.disconnect()
            publishPausedState(error = null)
            throw cancelled
        } catch (error: Throwable) {
            val hasResumableSession = stagingDirectory().isDirectory
            val message = if (hasResumableSession) {
                "OCR包下载中断，已保留进度，请检查网络后点击继续。"
            } else {
                "OCR包下载失败，请检查网络后重试。"
            }
            publishPausedState(error = message)
            Result.failure(error)
        }
    }

    private fun publishDownloadProgress(downloaded: Long, total: Long) {
        publishCombined {
            it.copy(
                downloadedBytes = downloaded,
                totalBytes = total,
                progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
            )
        }
    }

    private fun copyReusableModelFile(spec: ModelFile, destination: File): Boolean {
        val previousCombined = combinedRoot.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") && it.name != COMBINED_SPEC.version }
            .sortedByDescending(File::lastModified)
            .map { File(it, spec.name) }
        val source = previousCombined
            .firstOrNull { it.isFile && it.length() == spec.size }
            ?: return false
        source.copyTo(destination, overwrite = true)
        return destination.isFile && destination.length() == spec.size
    }

    private fun verifySha256(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) { "OCR包文件校验失败，请继续下载" }
    }

    private fun extractRuntimeArchive(
        archiveFile: File,
        stagingDirectory: File,
        abi: String,
        archive: RuntimeArchive
    ) {
        val targetDirectory = File(stagingDirectory, "native/$abi").apply { mkdirs() }
        ZipFile(archiveFile).use { zip ->
            archive.libraryNames.forEach { libraryName ->
                val entry = zip.getEntry("jni/$abi/$libraryName")
                    ?: error("OCR 运行库不支持当前设备架构：$abi")
                val destination = File(targetDirectory, libraryName)
                zip.getInputStream(entry).use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                check(entry.size <= 0L || destination.length() == entry.size) {
                    "OCR 运行库解压不完整：$libraryName"
                }
                destination.setReadable(true, true)
                destination.setExecutable(true, true)
                destination.setWritable(false, false)
            }
        }
    }

    private suspend fun downloadFile(
        sourceUrls: List<String>,
        destination: File,
        expectedSize: Long,
        sessionId: Long,
        onProgress: (Long) -> Unit
    ) {
        var lastError: Throwable? = null
        sourceUrls.distinct().forEach { sourceUrl ->
            repeat(DOWNLOAD_ATTEMPTS_PER_SOURCE) { attempt ->
                ensureDownloadActive(sessionId)
                try {
                    downloadFileOnce(sourceUrl, destination, expectedSize, sessionId, onProgress)
                    return
                } catch (stopped: DownloadStoppedException) {
                    throw stopped
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    lastError = error
                    Log.w(
                        LOG_TAG,
                        "OCR download source failed: ${runCatching { URL(sourceUrl).host }.getOrDefault("unknown")} " +
                            "attempt ${attempt + 1}/$DOWNLOAD_ATTEMPTS_PER_SOURCE",
                        error
                    )
                    if (destination.length() > expectedSize) destination.delete()
                    if (attempt < DOWNLOAD_ATTEMPTS_PER_SOURCE - 1) {
                        delay(1_000L * (attempt + 1))
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("OCR 模型下载失败")
    }

    private suspend fun downloadFileOnce(
        sourceUrl: String,
        destination: File,
        expectedSize: Long,
        sessionId: Long,
        onProgress: (Long) -> Unit
    ) {
        ensureDownloadActive(sessionId)
        if (destination.length() > expectedSize) destination.delete()
        val existingBytes = destination.length().coerceAtLeast(0L)
        if (existingBytes == expectedSize) {
            onProgress(existingBytes)
            return
        }
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Tiji-Android-OCR/1")
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }
        activeConnection = connection
        try {
            val responseCode = connection.responseCode
            ensureDownloadActive(sessionId)
            if (responseCode == 416 &&
                destination.length() == expectedSize
            ) {
                onProgress(expectedSize)
                return
            }
            if (responseCode !in 200..299) {
                error("下载 OCR 模型失败（HTTP $responseCode）")
            }
            val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            val initialBytes = if (append) existingBytes else 0L
            if (!append && existingBytes > 0L) destination.delete()
            onProgress(initialBytes)
            connection.inputStream.use { input ->
                FileOutputStream(destination, append).buffered().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var downloaded = initialBytes
                    while (true) {
                        ensureDownloadActive(sessionId)
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded)
                    }
                    output.flush()
                }
            }
            check(destination.length() == expectedSize) { "OCR 模型文件下载不完整" }
        } finally {
            if (activeConnection === connection) activeConnection = null
            connection.disconnect()
        }
    }

    private suspend fun ensureDownloadActive(sessionId: Long) {
        currentCoroutineContext().ensureActive()
        if (downloadGeneration.get() != sessionId) throw DownloadStoppedException()
    }

    private fun pruneOldVersions(targetDir: File, currentVersion: String) {
        targetDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != currentVersion }
            .forEach { it.deleteRecursively() }
    }

    private class DownloadStoppedException : Exception("OCR download stopped")

    private data class ModelFile(
        val name: String,
        val url: String,
        val size: Long,
        val sha256: String? = null
    )
    private data class ModelSpec(val version: String, val files: List<ModelFile>)
    private data class RuntimeArchive(
        val name: String,
        val url: String,
        val size: Long,
        val sha256: String,
        val libraryNames: List<String>
    )

    companion object {
        @Volatile private var sharedInstance: OcrModelManager? = null

        /** Shared by the UI and background download service in the app process. */
        fun getInstance(context: Context): OcrModelManager =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: OcrModelManager(context.applicationContext).also { sharedInstance = it }
            }

        private const val PREFERENCES = "ocr_models"
        private const val LOG_TAG = "OcrModelManager"
        private const val COMBINED_ENABLED = "combined_enabled"
        private const val LEGACY_FORMULA_DIRECTORY = "formula"
        private const val COMPLETE_MARKER = ".complete"
        private const val HF = "https://huggingface.co"
        private const val HF_CHINA_FRIENDLY = "https://hf-mirror.com"
        private const val MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2"
        private const val TENCENT_MAVEN = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"
        private const val ALIYUN_MAVEN = "https://maven.aliyun.com/repository/public"
        private const val MIN_NATIVE_LIBRARY_SIZE = 32_000L
        private const val DOWNLOAD_ATTEMPTS_PER_SOURCE = 2
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private val SUPPORTED_RUNTIME_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        private val RUNTIME_LIBRARY_NAMES = listOf(
            "libonnxruntime.so",
            "libonnxruntime4j_jni.so",
            "libc++_shared.so"
        )

        private fun supportedRuntimeAbiOrNull(): String? =
            Build.SUPPORTED_ABIS.firstOrNull(SUPPORTED_RUNTIME_ABIS::contains)

        private fun supportedRuntimeAbi(): String = supportedRuntimeAbiOrNull()
            ?: error("当前设备架构不受本地 OCR 支持：${Build.SUPPORTED_ABIS.joinToString()}")

        /** Prefer mainland-friendly mirrors, while retaining the official upstream fallback. */
        private fun downloadSources(primaryUrl: String): List<String> = when {
            primaryUrl.startsWith("$HF/") -> listOf(
                primaryUrl.replaceFirst(HF, HF_CHINA_FRIENDLY),
                primaryUrl
            )
            primaryUrl.startsWith("$MAVEN_CENTRAL/") -> buildList {
                add(primaryUrl.replaceFirst(MAVEN_CENTRAL, TENCENT_MAVEN))
                add(primaryUrl.replaceFirst(MAVEN_CENTRAL, ALIYUN_MAVEN))
                add(primaryUrl)
            }
            else -> listOf(primaryUrl)
        }

        private val FORMULA_SPEC = ModelSpec(
            version = "pix2text-1.5",
            files = listOf(
                ModelFile(
                    "mfd.onnx",
                    "$HF/breezedeus/pix2text-mfd-1.5/resolve/main/pix2text-mfd-1.5.onnx",
                    80_311_115L,
                    "40d4fc852d99bcbf25a9478897d2f49fbbb8f7fdd6569c088cd1c31386293bd7"
                ),
                ModelFile(
                    "mfr-encoder.onnx",
                    "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/encoder_model.onnx",
                    87_510_770L,
                    "080a3f660f08bc9ebcacdd96e34be6b6400f8c7e62d7cd0dd8251badc37f610b"
                ),
                ModelFile(
                    "mfr-decoder.onnx",
                    "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/decoder_model.onnx",
                    32_026_253L,
                    "917deb98e91a0453c5f234f58a0f32f9fb037de8527c7eb4ed394daf9e692f2a"
                ),
                ModelFile("mfr-config.json", "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/config.json", 1_573L),
                ModelFile("mfr-generation.json", "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/generation_config.json", 211L),
                ModelFile("mfr-preprocessor.json", "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/preprocessor_config.json", 450L),
                ModelFile("mfr-special-tokens.json", "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/special_tokens_map.json", 964L),
                ModelFile("mfr-tokenizer.json", "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/tokenizer.json", 113_168L),
                ModelFile("mfr-tokenizer-config.json", "$HF/breezedeus/pix2text-mfr-1.5/resolve/main/tokenizer_config.json", 1_244L)
            )
        )

        private val COMBINED_SPEC = ModelSpec(
            version = "paddleocr-v5-pix2text-1.1",
            files = listOf(
                ModelFile(
                    "models/det/inference.onnx",
                    "$HF/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx?download=true",
                    4_826_518L,
                    "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d"
                ),
                ModelFile(
                    "models/rec/inference.onnx",
                    "$HF/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx?download=true",
                    16_534_782L,
                    "da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092"
                ),
                ModelFile(
                    "models/rec/inference.yml",
                    "$HF/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml?download=true",
                    148_345L,
                    "5dfeb2777f6d0db8177d8128a8acfcf6e6276dc4ac73ea3bf0dc06d6a5e85d8e"
                )
            ) + FORMULA_SPEC.files.map { spec ->
                ModelFile("formula/" + spec.name, spec.url, spec.size, spec.sha256)
            }
        )

        private val RUNTIME_ARCHIVES = listOf(
            RuntimeArchive(
                name = "onnxruntime-android-1.21.1.aar",
                url = "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.21.1/onnxruntime-android-1.21.1.aar",
                size = 27_944_395L,
                sha256 = "30e594a4b9246fe3ca25768570e90f71e6d33ceb7b7dd72f92dcd7c267611d3f",
                libraryNames = listOf("libonnxruntime.so", "libonnxruntime4j_jni.so")
            ),
            RuntimeArchive(
                // QuickBird documents runtime issues in 4.5.3 and recommends 4.5.3.0.
                name = "opencv-4.5.3.0.aar",
                url = "https://repo.maven.apache.org/maven2/com/quickbirdstudios/opencv/4.5.3.0/opencv-4.5.3.0.aar",
                size = 53_990_420L,
                sha256 = "5736e3c7a23153478b62c60937fd5224eee267c59e077c053957d90c6736469e",
                libraryNames = listOf("libc++_shared.so", "libopencv_java4.so")
            )
        )
    }
}
