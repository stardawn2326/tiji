package com.tiji.mistakes.domain

/**
 * The only product definition of difficulty. Persistence keeps an Int for
 * backwards compatibility, but every user-facing read and write goes through
 * this five-level mapping.
 */
enum class Difficulty(val value: Int, val label: String) {
    VERY_EASY(1, "极简"),
    EASY(2, "简单"),
    NORMAL(3, "一般"),
    HARD(4, "困难"),
    VERY_HARD(5, "极难");

    companion object {
        fun fromValue(value: Int): Difficulty? = entries.firstOrNull { it.value == value }

        /** Keeps the legacy 0 sentinel and clamps invalid positive input to 1..5. */
        fun normalize(value: Int): Int = when {
            value <= 0 -> 0
            value > VERY_HARD.value -> VERY_HARD.value
            else -> value
        }

        fun labelFor(value: Int): String = fromValue(value)?.label ?: "未设置"
    }
}

val difficultyLevels: List<Difficulty> = Difficulty.entries.toList()
