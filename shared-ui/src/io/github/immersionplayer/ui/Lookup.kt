package io.github.immersionplayer.ui

import io.github.immersionplayer.dictionary.LookupResult

/** A lookup started by tapping a character in a subtitle line. */
data class ActiveLookup(
    val lineIndex: Int,
    val text: String,
    val start: Int,
    val result: LookupResult? = null,
)
