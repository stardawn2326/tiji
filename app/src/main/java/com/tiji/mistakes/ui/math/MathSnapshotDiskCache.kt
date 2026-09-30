package com.tiji.mistakes.ui.math

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App-private, bounded cache for fully rendered static formula previews. */
internal object MathSnapshotDiskCache {
    private const val DIRECTORY_NAME = "math-card-previews-v2"
    private const val MAX_DISK_BYTES = 128L * 1024L * 1024L
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()
    private val cacheLock = Any()
    private val ownerGenerations = MathSnapshotGenerations()
    private val resetGeneration = AtomicLong()
    private var legacyPruned = false // guarded by fileMutex

    fun keyFor(content: String, owner: MathSnapshotOwner? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return owner?.prefix?.plus(digest) ?: digest
    }

    fun generationFor(owner: MathSnapshotOwner?): Long =
        ownerGenerations.generationFor(owner)

    /** Reject a preview captured before its mistake was reviewed, deleted, or reset. */
    fun cacheIfCurrent(
        context: Context,
        key: String,
        snapshot: MathTextSnapshot,
        owner: MathSnapshotOwner?,
        expectedGeneration: Long,
        persist: Boolean
    ): Boolean = synchronized(cacheLock) {
        if (generation(owner) != expectedGeneration) return@synchronized false
        MathSnapshotMemoryCache.put(key, snapshot)
        if (persist) saveAsync(context, key, snapshot, owner)
        true
    }

    suspend fun load(context: Context, key: String, owner: MathSnapshotOwner? = null): MathTextSnapshot? {
        val ownerGeneration = generation(owner)
        val resetAtStart = resetGeneration.get()
        return withContext(Dispatchers.IO) {
            try {
                fileMutex.withLock {
                    if (!isCurrent(owner, ownerGeneration, resetAtStart)) return@withLock null
                    val (imageFile, heightFile) = files(context, key)
                    pruneLegacy(imageFile.parentFile ?: return@withLock null)
                    if (!imageFile.isFile || !heightFile.isFile) return@withLock null
                    val heightDp = heightFile.readText().toFloatOrNull()
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (heightDp == null || heightDp <= 0f || bitmap == null) {
                        imageFile.delete()
                        heightFile.delete()
                        return@withLock null
                    }
                    if (!isCurrent(owner, ownerGeneration, resetAtStart)) return@withLock null
                    imageFile.setLastModified(System.currentTimeMillis())
                    heightFile.setLastModified(System.currentTimeMillis())
                    MathTextSnapshot(bitmap, heightDp)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("TijiMathRender", "snapshot_load_failed", error)
                null
            }
        }
    }

    fun saveAsync(context: Context, key: String, snapshot: MathTextSnapshot, owner: MathSnapshotOwner? = null) {
        val ownerGeneration = generation(owner)
        val resetAtStart = resetGeneration.get()
        writerScope.launch {
            try {
                fileMutex.withLock {
                    if (isCurrent(owner, ownerGeneration, resetAtStart)) {
                        pruneLegacy(directory(context))
                        save(context.applicationContext, key, snapshot)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("TijiMathRender", "snapshot_save_failed", error)
            }
        }
    }

    /** Invalidate only the previews owned by these mistakes after a committed review or deletion. */
    suspend fun invalidateOwners(context: Context, owners: Collection<MathSnapshotOwner>) {
        val distinctOwners = owners.toSet()
        if (distinctOwners.isEmpty()) return
        synchronized(cacheLock) {
            distinctOwners.forEach { owner ->
                ownerGenerations.invalidate(owner)
            }
            MathSnapshotMemoryCache.invalidate(distinctOwners)
        }
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val cacheDirectory = directory(context)
                pruneLegacy(cacheDirectory)
                cacheDirectory.listFiles()?.forEach { file ->
                    if (distinctOwners.any { owner -> file.name.startsWith(owner.prefix) }) file.delete()
                }
            }
        }
    }

    suspend fun clearAll(context: Context) {
        synchronized(cacheLock) {
            resetGeneration.incrementAndGet()
            ownerGenerations.clear()
            MathSnapshotMemoryCache.clear()
        }
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                directory(context).listFiles()?.forEach { it.delete() }
                legacyPruned = true
            }
        }
    }

    private fun save(context: Context, key: String, snapshot: MathTextSnapshot) {
        val (imageFile, heightFile) = files(context, key)
        val directory = imageFile.parentFile ?: return
        if (!directory.exists() && !directory.mkdirs()) return
        val imageTemp = File(directory, "$key.png.tmp")
        val heightTemp = File(directory, "$key.height.tmp")
        try {
            val imageSaved = FileOutputStream(imageTemp).use { output ->
                snapshot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output).also { output.flush() }
            }
            if (!imageSaved) return
            heightTemp.writeText(snapshot.heightDp.toString())
            imageFile.delete()
            heightFile.delete()
            if (!imageTemp.renameTo(imageFile)) return
            if (!heightTemp.renameTo(heightFile)) {
                imageFile.delete()
                return
            }
            trim(directory)
        } finally {
            imageTemp.delete()
            heightTemp.delete()
        }
    }

    private fun files(context: Context, key: String): Pair<File, File> {
        val cacheDirectory = directory(context)
        return File(cacheDirectory, "$key.png") to File(cacheDirectory, "$key.height")
    }

    private fun directory(context: Context): File = File(context.noBackupFilesDir, DIRECTORY_NAME)

    private fun generation(owner: MathSnapshotOwner?): Long = ownerGenerations.generationFor(owner)

    private fun isCurrent(owner: MathSnapshotOwner?, ownerGeneration: Long, resetAtStart: Long): Boolean =
        resetGeneration.get() == resetAtStart && generation(owner) == ownerGeneration

    private fun pruneLegacy(directory: File) {
        if (legacyPruned) return
        directory.listFiles()?.forEach { file ->
            val stem = file.name.substringBefore('.')
            if (stem.length == 64 && stem.all { it in "0123456789abcdef" }) file.delete()
        }
        legacyPruned = true
    }

    private fun trim(directory: File) {
        val images = directory.listFiles { file -> file.extension == "png" }.orEmpty()
            .sortedBy { it.lastModified() }
        var total = images.sumOf { image -> image.length() + File(image.parentFile, "${image.nameWithoutExtension}.height").length() }
        for (image in images) {
            if (total <= MAX_DISK_BYTES) break
            val height = File(image.parentFile, "${image.nameWithoutExtension}.height")
            total -= image.length() + height.length()
            image.delete()
            height.delete()
        }
    }
}
