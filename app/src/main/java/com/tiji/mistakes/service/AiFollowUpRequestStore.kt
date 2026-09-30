package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/** Keep arbitrarily long question/answer text out of Binder transactions. No credentials are stored. */
internal class AiFollowUpRequestStore(private val context: Context) {
    fun write(baseContext: String, prompt: String): String {
        val token = UUID.randomUUID().toString()
        store(token).write("request", JSONObject().put("context", baseContext).put("prompt", prompt).toString())
        return token
    }

    fun take(token: String): Pair<String, String> {
        require(token.matches(Regex("[a-f0-9-]{36}")))
        val storage = store(token)
        val raw = storage.read("request") ?: error("追问草稿已失效，请重新发送")
        val data = JSONObject(raw)
        storage.clear()
        return data.getString("context") to data.getString("prompt")
    }

    private fun store(token: String) = DurableTextStore(context, "followup-requests/$token")
}
