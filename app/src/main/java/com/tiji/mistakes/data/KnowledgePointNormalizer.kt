package com.tiji.mistakes.data

import java.util.Locale
import java.util.UUID

/** Compatibility parser for the old free-form tags column. */
object KnowledgePointNormalizer {
    fun parseTags(raw: String): List<String> = raw
        .split(',', '，', ';', '；', '|', '、')
        .map(::cleanName)
        .filter(String::isNotBlank)
        .distinctBy(::normalizeName)

    fun cleanName(value: String): String = value.trim().replace(WHITESPACE, " ")

    fun normalizeName(value: String): String = cleanName(value).lowercase(Locale.ROOT)

    fun stableId(subject: String, normalizedName: String): String = UUID.nameUUIDFromBytes(
        "knowledge-point:${subject.trim()}:${normalizeName(normalizedName)}".toByteArray(Charsets.UTF_8)
    ).toString()

    private val WHITESPACE = Regex("\\s+")
}
