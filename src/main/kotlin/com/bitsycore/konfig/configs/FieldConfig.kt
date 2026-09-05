package com.bitsycore.konfig.configs

import com.bitsycore.konfig.types.BuildType
import com.bitsycore.konfig.types.FieldValueType
import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import java.util.function.BiFunction

/**
 * Holds the resolved configuration for a single generated field.
 *
 * Values are stored as [Provider]<T> so lazy sources (e.g. Gradle property providers)
 * are supported alongside plain constants.  [org.gradle.api.provider.ProviderFactory]
 * is intentionally NOT stored here — it is not configuration-cache serializable and
 * must never flow into the object graph captured by task input providers.
 */
class FieldConfig<T : Any> @PublishedApi internal constructor(
    val fieldName: String,
    internal val type: Class<T>,
    /**
     * The unconditional default value, or `null` if this field was declared only inside
     * a scope block (debug/release) and has no fallback outside that scope.
     */
    internal val default: Provider<T>?,
    @PublishedApi internal val valueType: FieldValueType? = null,
) {
	@PublishedApi
	internal val buildTypeOverrides: MutableMap<BuildType, Provider<T>> = mutableMapOf()

    // ── BuildType overrides ───────────────────────────────────────────────────

    fun debug(value: T)             { buildTypeOverrides[BuildType.DEBUG]   = constantProvider(value) }
    fun debug(value: Provider<T>)   { buildTypeOverrides[BuildType.DEBUG]   = value }
    fun release(value: T)           { buildTypeOverrides[BuildType.RELEASE] = constantProvider(value) }
    fun release(value: Provider<T>) { buildTypeOverrides[BuildType.RELEASE] = value }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves the effective value for [buildType].
     * Returns `null` if no applicable value exists (field is absent for this context).
     */
    internal fun resolve(buildType: BuildType): Provider<T>? =
        buildTypeOverrides[buildType] ?: default
}

/**
 * Returns a minimal [Provider] that always returns [value].
 * Avoids storing [org.gradle.api.provider.ProviderFactory] anywhere in the DSL
 * object graph (not config-cache serializable).
 */
@PublishedApi
internal fun <T : Any> constantProvider(value: T): Provider<T> = ConstantProvider(value)

private class ConstantProvider<T : Any>(private val value: T) : Provider<T> {
    override fun get(): T = value
    override fun getOrNull(): T = value
    override fun getOrElse(defaultValue: T): T = value
    override fun isPresent(): Boolean = true

    override fun <S : Any> map(transformer: Transformer<out S?, in T>): Provider<S> =
        ConstantProvider(transformer.transform(value)!!)

    override fun <S : Any> flatMap(transformer: Transformer<out Provider<out S>?, in T>): Provider<S> =
        @Suppress("UNCHECKED_CAST")
        (transformer.transform(value) as Provider<S>)

    override fun filter(spec: Spec<in T>): Provider<T> =
        if (spec.isSatisfiedBy(value)) this else throw NoSuchElementException("Provider has no value")

    override fun orElse(value: T): Provider<T> = this
    override fun orElse(provider: Provider<out T>): Provider<T> = this

    @Suppress("unused") // called reflectively by older Gradle versions
    fun forUseAtConfigurationTime(): Provider<T> = this

    override fun <B : Any, R : Any> zip(
        right: Provider<B>,
        combiner: BiFunction<in T, in B, out R?>
    ): Provider<R> = ConstantProvider(combiner.apply(value, right.get())!!)
}
