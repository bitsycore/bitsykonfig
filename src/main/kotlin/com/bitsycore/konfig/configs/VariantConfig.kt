package com.bitsycore.konfig.configs

import com.bitsycore.konfig.types.BuildType
import com.bitsycore.konfig.types.KonfigDsl
import com.bitsycore.konfig.types.FieldValueType
import org.gradle.api.provider.Provider
import kotlin.reflect.typeOf

// ==============================================================================
// MARK: FieldHandle with modifier
// ==============================================================================

/**
 * Returned by `field(name, default)` when called at the top level of a [VariantConfig].
 *
 * Allows fluent `.debug(value)` / `.release(value)` overrides:
 * ```kotlin
 * field("TIMEOUT", 30).debug(5)
 * field("URL", "https://prod.example.com").debug("https://dev.example.com")
 * ```
 * Not available inside `debug {}` / `release {}` blocks — those return [Unit].
 */
class FieldHandle<T> @PublishedApi internal constructor(
    @PublishedApi internal val field: FieldConfig<T>
) {
    fun debug(value: T): Unit = field.debug(value)
    fun debug(value: Provider<T & Any>): Unit = field.debug(value)
    fun release(value: T): Unit = field.release(value)
    fun release(value: Provider<T & Any>): Unit = field.release(value)
}

// ==============================================================================
// MARK: BuildType Scope
// ==============================================================================

/**
 * Receiver of `debug { ... }` and `release { ... }` blocks inside [VariantConfig].
 *
 * `field()` here returns [Unit] — no `.debug()`/`.release()` chaining is possible
 * because the build type is already fixed by the enclosing scope.
 */
@KonfigDsl
class BuildTypedFieldDeclScope @PublishedApi internal constructor(
    @PublishedApi internal val buildType: BuildType,
    @PublishedApi internal val owner: VariantConfig
) {
    inline fun <reified T> field(name: String, value: T) {
        owner.getOrCreateField<T>(name).buildTypeOverrides[buildType] = constantProvider(value ?: NullFieldValue)
    }

    inline fun <reified T> field(name: String, value: Provider<T & Any>) {
        owner.getOrCreateField<T>(name).buildTypeOverrides[buildType] = value
    }
}

// ==============================================================================
// MARK: Variant
// ==============================================================================

/**
 * DSL scope for a variant (or the `common {}` block inside a dimension).
 *
 * ```kotlin
 * variant("prod") {
 *     field("URL", "https://prod.example.com")
 *     field("TIMEOUT", 30).debug(5)
 *     debug   { field("LOG_LEVEL", "verbose") }
 *     release { field("LOG_LEVEL", "error")   }
 * }
 * ```
 */
@KonfigDsl
class VariantConfig @PublishedApi internal constructor(val variantName: String) {

    @PublishedApi
    internal val fields: MutableList<FieldConfig<*>> = mutableListOf()

    // ── Internal helpers ──────────────────────────────────────────────────────

    @PublishedApi
    internal inline fun <reified T> getOrCreateField(name: String): FieldConfig<T> {
        val existing = fields.firstOrNull { it.fieldName == name }
        if (existing != null) {
            require(existing.valueType == FieldValueType.from(typeOf<T>())) {
                "konfig: field '$name' must use the same type in every build-type scope"
            }
            @Suppress("UNCHECKED_CAST")
            return existing as FieldConfig<T>
        }
        val fc = FieldConfig(name, fieldClass<T>(), null, FieldValueType.from(typeOf<T>()))
        fields.add(fc)
        return fc
    }

    // ── Top-level field declarations ──────────────────────────────────────────

    /**
     * Declares a field with an unconditional default value.
     * Returns a [FieldHandle] to optionally set `.debug(value)` / `.release(value)` build-type overrides.
     */
    inline fun <reified T> field(name: String, default: T): FieldHandle<T> {
        require(fields.none { it.fieldName == name }) {
            "konfig: field '$name' is already declared in variant '$variantName'"
        }
        val fc = FieldConfig(name, fieldClass<T>(), constantProvider(default ?: NullFieldValue), FieldValueType.from(typeOf<T>()))
        fields.add(fc)
        return FieldHandle(fc)
    }

    inline fun <reified T> field(name: String, default: Provider<T & Any>): FieldHandle<T> {
        require(fields.none { it.fieldName == name }) {
            "konfig: field '$name' is already declared in variant '$variantName'"
        }
        val fc = FieldConfig(name, fieldClass<T>(), default, FieldValueType.from(typeOf<T>()))
        fields.add(fc)
        return FieldHandle(fc)
    }

    // ── Build-type scope blocks ───────────────────────────────────────────────

    fun debug(block: BuildTypedFieldDeclScope.() -> Unit) =
        BuildTypedFieldDeclScope(BuildType.DEBUG, this).block()

    fun release(block: BuildTypedFieldDeclScope.() -> Unit) =
        BuildTypedFieldDeclScope(BuildType.RELEASE, this).block()
}
