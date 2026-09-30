package com.tiji.mistakes.ui.library

import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.subjectCounts
import com.tiji.mistakes.ui.common.parseTagValues

internal class LibraryIndex(val items: List<MistakeListItem>) {
    val mistakes = items.map { it.mistake }
    val subjects = subjectCounts(mistakes).map { it.first }
    val tagsById = mistakes.associate { it.id to parseTagValues(it.tags).toSet() }
    val subjectById = mistakes.associate { it.id to normalizedSubject(it.subject) }
    val knowledgeBySubject = mistakes.groupBy { subjectById.getValue(it.id) }.mapValues { (_, rows) ->
        rows.flatMap { tagsById.getValue(it.id) }.distinct().sorted()
    }
    val allKnowledge = tagsById.values.flatten().distinct().sorted()
    val newest = items.sortedByDescending { it.mistake.uploadedAt }
    val oldest = items.sortedBy { it.mistake.uploadedAt }
    val updated = items.sortedByDescending { it.mistake.updatedAt }
}
