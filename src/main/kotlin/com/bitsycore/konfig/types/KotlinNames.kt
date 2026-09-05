package com.bitsycore.konfig.types

internal fun String.isValidKotlinIdentifier(): Boolean =
    isNotEmpty() && any { it != '_' } && this !in kotlinKeywords &&
        first().let { it.isLetter() || it == '_' } && all { it.isLetterOrDigit() || it == '_' }

private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
)

/** Dimensions are also used as property keys and delimiters in task input maps. */
internal fun String.isValidDimensionName(): Boolean =
    isNotBlank() && none { it == '|' || it.isISOControl() }

internal fun String.toVariantConstName(): String =
    uppercase().replace(Regex("[^A-Z0-9]"), "_") + "_VARIANT"
