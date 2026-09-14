package com.tiji.mistakes.data

/**
 * Resolves AI and user supplied knowledge-point names to a canonical point.
 *
 * Matching is deliberately conservative. A general substring replacement is
 * unsafe because concepts such as "函数" and "函数单调性" are distinct. The
 * only semantic candidates are explicit aliases and a small set of safe,
 * documented variants used by the product's existing data.
 */
object KnowledgePointCanonicalizer {
    enum class MatchKind { CANONICAL, ALIAS, NORMALIZED, KNOWN_ALIAS, SAFE_CANDIDATE, NEW }

    data class Resolution(
        val inputName: String,
        val canonicalName: String,
        val normalizedName: String,
        val matchKind: MatchKind,
        val shouldStoreAlias: Boolean
    )

    /** Names which are known to be alternate phrasings, not arbitrary keywords. */
    private val knownAliases = mapOf(
        "二重积分计算" to "二重积分",
        "二重积分的计算" to "二重积分",
        "计算二重积分" to "二重积分"
    ).mapKeys { normalizeForMatch(it.key) }.mapValues { normalizeForMatch(it.value) }

    fun resolve(
        subject: String,
        inputName: String,
        points: Iterable<KnowledgePointEntity>,
        aliases: Iterable<KnowledgePointAliasEntity>
    ): Resolution {
        val cleanInput = KnowledgePointNormalizer.cleanName(inputName)
        require(cleanInput.isNotBlank()) { "知识点名称不能为空" }
        val normalized = KnowledgePointNormalizer.normalizeName(cleanInput)
        val subjectKey = subject.trim().ifBlank { "未分类" }
        val subjectPoints = points.filter { it.subject.trim() == subjectKey }.toList()
        val subjectAliases = aliases.filter { it.subject.trim() == subjectKey }.toList()

        subjectPoints.firstOrNull { normalizeForMatch(it.normalizedName) == normalizeForMatch(normalized) }
            ?.let { point ->
                return Resolution(cleanInput, point.name, point.normalizedName, MatchKind.CANONICAL, false)
            }
        subjectAliases.firstOrNull { normalizeForMatch(it.normalizedAlias) == normalizeForMatch(normalized) }
            ?.let { alias ->
                val point = subjectPoints.firstOrNull { it.id == alias.knowledgePointId }
                if (point != null) {
                    return Resolution(cleanInput, point.name, point.normalizedName, MatchKind.ALIAS, false)
                }
            }

        knownAliases[normalizeForMatch(cleanInput)]?.let { target ->
            subjectPoints.firstOrNull { normalizeForMatch(it.normalizedName) == target }
                ?.let { point ->
                    return Resolution(cleanInput, point.name, point.normalizedName, MatchKind.KNOWN_ALIAS, true)
                }
            // If this is the first occurrence, create the known canonical name
            // instead of making the alias the active point.
            return Resolution(
                inputName = cleanInput,
                canonicalName = target,
                normalizedName = target,
                matchKind = MatchKind.KNOWN_ALIAS,
                shouldStoreAlias = true
            )
        }

        safeCandidate(cleanInput, subjectPoints)?.let { point ->
            return Resolution(cleanInput, point.name, point.normalizedName, MatchKind.SAFE_CANDIDATE, true)
        }

        return Resolution(cleanInput, cleanInput, normalized, MatchKind.NEW, false)
    }

    /**
     * The candidate rules only run when the shortened form already exists as a
     * canonical point and has at least four characters. This avoids broad
     * merges while covering common descriptive variants such as "二重积分计算".
     */
    private fun safeCandidate(
        inputName: String,
        subjectPoints: List<KnowledgePointEntity>
    ): KnowledgePointEntity? {
        val normalized = normalizeForMatch(inputName)
        if (normalized.length < 6) return null
        val suffixes = listOf("的计算", "计算")
        val prefixes = listOf("计算")
        val candidates = buildList {
            suffixes.filter(normalized::endsWith).forEach { suffix -> add(normalized.removeSuffix(suffix)) }
            prefixes.filter(normalized::startsWith).forEach { prefix -> add(normalized.removePrefix(prefix)) }
        }.filter { it.length >= 4 }.distinct()
        return candidates.asSequence()
            .mapNotNull { candidate ->
                subjectPoints.firstOrNull { normalizeForMatch(it.normalizedName) == candidate }
            }
            .firstOrNull()
    }

    private fun normalizeForMatch(value: String): String =
        KnowledgePointNormalizer.normalizeName(value).replace(" ", "")
}
