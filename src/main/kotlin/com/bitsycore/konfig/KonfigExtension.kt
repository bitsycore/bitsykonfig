package com.bitsycore.konfig

import com.bitsycore.konfig.configs.BuildTypedFieldDeclScope
import com.bitsycore.konfig.configs.DimensionConfig
import com.bitsycore.konfig.configs.FieldConfig
import com.bitsycore.konfig.configs.VariantConfig
import com.bitsycore.konfig.types.BuildType
import com.bitsycore.konfig.types.KonfigDsl
import com.bitsycore.konfig.types.isValidDimensionName
import com.bitsycore.konfig.types.Visibility
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

@KonfigDsl
abstract class KonfigExtension @Inject constructor(
    objects: ObjectFactory,
) {

    // ==============================================================================
    // MARK: Settings Internal
    // ==============================================================================

    internal val objectPackageProp:      Property<String>     = objects.property(String::class.java)
    internal val objectNameProp:       Property<String>     = objects.property(String::class.java)
    internal val objectVisibilityProp: Property<Visibility> = objects.property(Visibility::class.java)

    // ==============================================================================
    // MARK: User facing Settings
    // ==============================================================================

    /** Package for the generated object (e.g. `"com.example.app"`). */
    var objectPackage: String
        get()      = objectPackageProp.get()
        set(value) = objectPackageProp.set(value)

    /** Name of the generated Kotlin object (default: `"BuildKonfig"`). */
    var objectName: String
        get()      = objectNameProp.get()
        set(value) = objectNameProp.set(value)

    /** Visibility of the generated object (default: [Visibility.PUBLIC]). */
    var objectVisibility: Visibility
        get()      = objectVisibilityProp.get()
        set(value) = objectVisibilityProp.set(value)

    /** Output directory - kept as [DirectoryProperty] for full Gradle lazy semantics. */
    val outputDir: DirectoryProperty = objects.directoryProperty()

    /** Reject missing/unknown dimension selections and invalid explicit build types. */
    var strictResolution: Boolean = false

    /** Check effective field names/types across all variants and both build types. */
    var validateVariantSchema: Boolean = false

    /** Specialize Array<Int> and other non-null primitive arrays. Explicit IntArray stays primitive. */
    var specializeArrays: Boolean = true

    /** Recreate array-containing values on each access, including arrays nested in collections. */
    var copyArraysOnAccess: Boolean = false

    // ==============================================================================
    // MARK: DSL Internal
    // ==============================================================================

    @PublishedApi
    internal val dimensions: MutableList<DimensionConfig> = mutableListOf()

    /** Backing store for global fields - reuses [com.bitsycore.konfig.configs.VariantConfig] for its field/debug/release logic. */
    @PublishedApi
    internal val globalScope: VariantConfig = VariantConfig("\$global")

    internal val globalFields: List<FieldConfig<*>> get() = globalScope.fields

    // ==============================================================================
    // MARK: Top Level DSL
    // ==============================================================================

    fun dimension(
        name: String,
        objectNameOverride: String? = null,
        defaultTo: String? = null,
        config: DimensionConfig.() -> Unit
    ) {
        require(name.isValidDimensionName()) { "konfig: invalid dimension name '$name' (blank, delimiter, or control character)" }
        require(dimensions.none { it.dimensionName == name }) {
            "konfig: dimension '$name' is already declared"
        }
        val d = DimensionConfig(name, objectNameOverride, defaultTo)
        config(d)
        dimensions.add(d)
    }

    /**
     * Declares a dimension whose fields are generated directly at the root of the
     * konfig object instead of inside a nested `object`.
     *
     * The selected variant is exposed as `<NAME>_VARIANT` (dimension name uppercased).
     * Name collisions with global fields, base constants or other flat dimensions
     * fail the build.
     */
    fun flatDimension(
        name: String,
        defaultTo: String? = null,
        config: DimensionConfig.() -> Unit
    ) {
        require(name.isValidDimensionName()) { "konfig: invalid dimension name '$name' (blank, delimiter, or control character)" }
        require(dimensions.none { it.dimensionName == name }) {
            "konfig: dimension '$name' is already declared"
        }
        val d = DimensionConfig(name, objectNameOverride = null, defaultVariant = defaultTo, flat = true)
        config(d)
        dimensions.add(d)
    }

    inline fun <reified T> field(
        name: String,
        default: T,
    ) = globalScope.field(name, default)

    inline fun <reified T> field(
        name: String,
        default: Provider<T & Any>,
    ) = globalScope.field(name, default)

    fun debug(block: BuildTypedFieldDeclScope.() -> Unit)   = globalScope.debug(block)
    fun release(block: BuildTypedFieldDeclScope.() -> Unit) = globalScope.release(block)

    // ==============================================================================
    // MARK: Build-script queries
    // ==============================================================================

    /** Wired by the plugin at apply time - same resolution chain as the generated object. */
    internal lateinit var buildTypeProviderInternal: Provider<BuildType>

    /** Wired by the plugin at apply time - resolves a dimension with the same recognition logic. */
    internal lateinit var dimensionResolverInternal: (String) -> Provider<String>

    /** Resolved build type as a lazy provider (`"debug"` / `"release"`). */
    val currentBuildType: Provider<String>
        get() = buildTypeProviderInternal.map { it.value }

    /** Lazy provider variant of [isDebug], for provider-based wiring. */
    val isDebugProvider: Provider<Boolean>
        get() = buildTypeProviderInternal.map { it == BuildType.DEBUG }

    /**
     * True when the resolved build type is debug - uses the exact same recognition
     * as the generated object, so it can drive build-script decisions such as
     * KMP `debugImplementation`-style wiring:
     *
     * ```kotlin
     * dependencies {
     *     if (konfig.isDebug) implementation(project(":debugImpl"))
     *     else                implementation(project(":releaseImpl"))
     * }
     * ```
     */
    val isDebug: Boolean
        get() = isDebugProvider.get()

    /**
     * Lazy provider for the active variant of dimension [name].
     * The provider has no value when the dimension is skipped or unknown.
     */
    fun currentDimension(name: String): Provider<String> = dimensionResolverInternal(name)

    /** Active variant of dimension [name], or `null` when skipped/unknown. */
    fun getCurrentDimension(name: String): String? = currentDimension(name).orNull
}
