package com.tiji.mistakes

import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in diagnosis: never exports configuration or keys, never changes user records. */
class AuthorizedFormulaDiagnosisTest {
    @Test fun captureComplexSolveAndFollowUp() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tijiFormulaDiagnosis") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "formula-diagnosis").apply { mkdirs() }
        AiChatStateStore(context).read().messages.lastOrNull()?.let {
            File(folder, "saved-followup.txt").writeText(it.reply)
        }
        val question = "求 x(t)=e^t/n! * d^n[t^n e^{-2t}u(t)]/dt^n 的双边拉普拉斯变换及收敛域，n 为正整数。请完整展示性质和推导。"
        val profiles = AppPreferences(context).aiProfiles.first().filter {
            it.model.startsWith("qwen", true) || it.model.startsWith("deepseek", true)
        }.distinctBy { if (it.model.startsWith("qwen", true)) "qwen" else "deepseek" }
        for (profile in profiles) {
            val family = if (profile.model.startsWith("qwen", true)) "qwen" else "deepseek"
            val service = AiVisionService(appContext = context)
            val key = SecureKeyStore(context).read(profile.id)
            if (key.isBlank()) continue
            if (family == "qwen") {
                val solution = service.streamSolve(profile.endpoint, profile.model, key, question = question, onDelta = {}).getOrThrow()
                File(folder, "$family-solve.txt").writeText(solution)
            }
            val reply = service.answerFollowUp(profile.endpoint, profile.model, key, question,
                "请核对：先 t^n e^{-2t}u(t) 变换为 n!/(s+2)^(n+1)，再求 n 阶导数，最后乘 e^t/n!，结果是否为 (s-1)^n/(s+1)^(n+1)？详细列出每步公式与收敛域。",
                onDelta = {}).getOrThrow()
            File(folder, "$family-followup.txt").writeText(reply)
            File(folder, "$family-display.txt").writeText(followUpReplyForDisplay(reply))
        }
    }
}
