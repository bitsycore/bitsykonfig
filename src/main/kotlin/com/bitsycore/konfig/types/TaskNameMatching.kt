package com.bitsycore.konfig.types

/**
 * Case-insensitive word match respecting camelCase segment boundaries.
 *
 * A candidate occurrence only counts when it starts AND ends on a word segment
 * boundary, so `"assemblePreprodRelease"` matches variant `"preprod"` but NOT
 * variant `"prod"` (which is a plain substring of `Preprod`), and
 * `"assembleDevelopRelease"` does not match variant `"dev"`.
 *
 * Boundary rules for an occurrence at index `i` of length `n` in [this]:
 * - start: `i == 0`, or the previous char is not a letter/digit, or `this[i]` is uppercase
 * - end:   the match reaches the end, or the next char is not a letter/digit, or is uppercase
 */
internal fun String.containsWordCamelCase(word: String): Boolean {
	if (word.isEmpty()) return false
	var vIndex = indexOf(word, 0, ignoreCase = true)
	while (vIndex >= 0) {
		val vStartOk = vIndex == 0 || !this[vIndex - 1].isLetterOrDigit() || this[vIndex].isUpperCase()
		val vEnd     = vIndex + word.length
		val vEndOk   = vEnd >= length || !this[vEnd].isLetterOrDigit() || this[vEnd].isUpperCase()
		if (vStartOk && vEndOk) return true
		vIndex = indexOf(word, vIndex + 1, ignoreCase = true)
	}
	return false
}
