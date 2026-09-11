package com.tiji.mistakes.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** Builds a bind-parameter-only search query while retaining substring semantics. */
internal object MistakeSearchQuery {
    private val searchableColumns = listOf(
        "title",
        "questionText",
        "userAnswer",
        "answerText",
        "explanation",
        "note",
        "subject",
        "questionType",
        "tags",
        "errorReason",
        "ocrText"
    )

    internal data class Spec(val sql: String, val bindArgs: List<String>)

    fun build(keywords: List<String>): SupportSQLiteQuery = buildSpec(keywords).let { spec ->
        SimpleSQLiteQuery(spec.sql, spec.bindArgs.toTypedArray())
    }

    internal fun buildSpec(keywords: List<String>): Spec {
        val normalized = keywords.map(String::trim).filter(String::isNotBlank)
        if (normalized.isEmpty()) {
            return Spec(
                sql = activeSelect + orderBy,
                bindArgs = emptyList()
            )
        }

        val keywordClauses = normalized.map { keyword ->
            searchableColumns.joinToString(
                separator = " OR ",
                prefix = "(",
                postfix = ")"
            ) { column -> "$column LIKE ? ESCAPE '\\'" }
        }
        val args = normalized.flatMap { keyword ->
            val pattern = "%${escapeLike(keyword)}%"
            searchableColumns.map { pattern }
        }
        return Spec(
            sql = "$activeSelect AND ${keywordClauses.joinToString(" AND ")} $orderBy",
            bindArgs = args
        )
    }

    internal fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private const val activeSelect =
        "SELECT * FROM mistakes WHERE deletedAt IS NULL AND archived = 0"
    private const val orderBy = "ORDER BY uploadedAt DESC, updatedAt DESC"
}
