package com.bitsycore.konfig.configs

import com.bitsycore.konfig.types.KonfigDsl

/**
 * DSL scope for a dimension declaration.
 *
 * Only [variant] and [common] are available here.  Scoping (`debug {}`, `release {}`)
 * is done inside each [variant] or [common] block.
 *
 * ```kotlin
 * dimension("env", defaultTo = "prod") {
 *     common {
 *         field("TIMEOUT", 30)
 *         debug { field("TIMEOUT", 5) }
 *     }
 *     variant("prod") {
 *         field("URL", "https://prod.example.com")
 *     }
 *     variant("dev") {
 *         field("URL", "https://dev.example.com")
 *     }
 * }
 * ```
 */
@KonfigDsl
class DimensionConfig @PublishedApi internal constructor(
    val dimensionName: String,
    val objectNameOverride: String?,
    val defaultVariant: String?,
    /** When true, fields are generated at the root of the konfig object instead of a nested object. */
    val flat: Boolean = false,
) {
    /** Require a selected variant even when global strict resolution is disabled. */
    var required: Boolean = false

    /** Android flavor dimension to read; defaults to this Konfig dimension's name. */
    var androidDimension: String = dimensionName
        set(value) {
            require(value.isNotBlank() && value.none { it.isISOControl() }) { "konfig: Android dimension alias must not be blank or contain control characters" }
            field = value
        }
    /** All named variants. */
    @PublishedApi
    internal val variants: MutableMap<String, VariantConfig> = mutableMapOf()

    /**
     * Shared fields that apply to **all** variants as a fallback.
     * A variant field with the same name takes precedence over a common field.
     */
    internal val commonConfig: VariantConfig = VariantConfig($$"$common")

    // ==============================================================================
    // MARK: DSL
    // ==============================================================================

    /**
     * Declares a named variant.  Can be called multiple times for the same name
     * to merge additional fields.
     */
    fun variant(name: String, config: VariantConfig.() -> Unit) {
        require(name.isNotEmpty() && '\t' !in name) { "konfig: variant name must be nonempty and cannot contain tabs" }
        val v = variants.getOrPut(name) { VariantConfig(name) }
        config(v)
    }

    /**
     * Declares shared fields that act as fallback values for all variants.
     * Fields declared here are used when the active variant does not define
     * a field with the same name.
     *
     * ```kotlin
     * common {
     *     field("TIMEOUT", 30)
     *     debug { field("TIMEOUT", 5) }
     * }
     * ```
     */
    fun common(config: VariantConfig.() -> Unit) = config(commonConfig)

    /** Derived Kotlin object name: override or CamelCase of dimensionName. */
    fun objectName(): String = objectNameOverride
        ?: dimensionName
            .split(Regex("[-_\\s]+"))
            .joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
}
