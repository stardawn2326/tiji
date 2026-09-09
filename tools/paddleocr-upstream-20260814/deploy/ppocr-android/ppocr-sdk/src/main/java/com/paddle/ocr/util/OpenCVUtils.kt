// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.util

import android.content.Context
import android.util.Log

object OpenCVUtils {

    private var initialized = false

    fun init(context: Context): Boolean {
        if (initialized) return true
        try {
            // The host app loads OpenCV from its downloaded OCR package. Keep a
            // fallback for the upstream demo/tests, where the library is bundled.
            runCatching {
                val runtime = Class.forName("com.tiji.mistakes.service.OcrNativeRuntime")
                runtime.getMethod("loadLibrary", String::class.java)
                    .invoke(null, "opencv_java4")
            }.getOrElse {
                System.loadLibrary("opencv_java4")
            }
            initialized = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OpenCVUtils", "Failed to initialize OpenCV: ${e.message}")
        }
        return initialized
    }
}
