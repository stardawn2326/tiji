package com.tiji.mistakes.service

import android.os.Build
import java.io.File

/** Loads user-downloaded OCR native libraries from the app-private OCR package. */
object OcrNativeRuntime {
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    private val loadedLibraries = mutableSetOf<String>()

    @Volatile
    private var runtimeDirectory: File? = null

    @Synchronized
    fun prepare(packageDirectory: File) {
        val abi = Build.SUPPORTED_ABIS.firstOrNull(supportedAbis::contains)
            ?: error("当前设备架构不受本地 OCR 支持：${Build.SUPPORTED_ABIS.joinToString()}")
        val directory = File(packageDirectory, "native/$abi")
        check(directory.isDirectory) { "本地 OCR 运行库不完整，请重新下载 OCR 包" }
        runtimeDirectory = directory
    }

    /** Loads one OCR native library from the downloaded, app-private package. */
    @JvmStatic
    @Synchronized
    fun loadLibrary(name: String) {
        if (name in loadedLibraries) return
        val directory = runtimeDirectory
            ?: error("本地 OCR 运行库尚未准备，请重新下载 OCR 包")
        val library = File(directory, System.mapLibraryName(name))
        check(library.isFile && library.length() > 0L) {
            "本地 OCR 运行库不完整（缺少 ${library.name}），请重新下载 OCR 包"
        }
        System.load(library.absolutePath)
        loadedLibraries += name
    }

    /** ONNX Runtime natively supports an explicit private library directory. */
    @Synchronized
    fun ensureOnnxLoaded(packageDirectory: File) {
        prepare(packageDirectory)
        // libc++_shared is no longer packaged in the APK. It is part of the
        // staged OCR runtime and must be loaded before OpenCV/ONNX.
        loadLibrary("c++_shared")
        loadLibrary("onnxruntime")
        val directory = checkNotNull(runtimeDirectory)
        System.setProperty("onnxruntime.native.path", directory.absolutePath)
    }

    /** OpenCV is downloaded with the OCR package instead of being shipped in the APK. */
    @Synchronized
    fun ensureOpenCvLoaded(packageDirectory: File) {
        prepare(packageDirectory)
        loadLibrary("c++_shared")
        loadLibrary("opencv_java4")
    }
}
