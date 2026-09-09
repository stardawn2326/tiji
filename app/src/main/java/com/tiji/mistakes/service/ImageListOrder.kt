package com.tiji.mistakes.service

/** Replace one processed source without changing the user's submission order. */
internal fun replaceImageAtSamePosition(
    paths: List<String>,
    original: String?,
    processed: String
): List<String> = if (original == null) {
    (paths + processed).distinct()
} else {
    paths.map { if (it == original) processed else it }.distinct()
}
