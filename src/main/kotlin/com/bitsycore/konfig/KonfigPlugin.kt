@file:Suppress("unused")

package com.bitsycore.konfig

import com.android.build.api.variant.AndroidComponentsExtension
import com.bitsycore.konfig.configs.DimensionConfig
import com.bitsycore.konfig.configs.FieldConfig
import com.bitsycore.konfig.types.BuildType
import com.bitsycore.konfig.types.Visibility
import com.bitsycore.konfig.types.containsWordCamelCase
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import java.util.*

/** Bundles all inputs needed to resolve dimension variants in a config-cache-safe provider chain. */
private data class DimensionContext(
	val gradleProps: Map<String, String>,
	val fileProps: Map<String, String>,
	val flavorDetect: Boolean,
	val taskNames: List<String>,
)

class KonfigPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		val projectName  = project.name
		val projectGroup = project.providers.provider { project.group.toString() }
		val defaultPkgProvider = projectGroup.map { group -> defaultPackageName(projectName, group) }

		val extension = project.extensions.create("konfig", KonfigExtension::class.java).apply {
			objectPackageProp.convention(defaultPkgProvider)
			objectNameProp.convention(project.providers.provider { "BuildKonfig" })
			objectVisibilityProp.convention(project.providers.provider { Visibility.PUBLIC })
			outputDir.convention(project.layout.buildDirectory.dir("generated/konfig"))
		}

		// =========================================================================
		// MARK: konfig.properties
		// =========================================================================

		val konfigPropertiesFile = project.layout.projectDirectory.file("konfig.properties")
		val konfigPropertiesProvider: Provider<Map<String, String>> = project.providers
			.fileContents(konfigPropertiesFile)
			.asText
			.map { text ->
				val props = Properties()
				props.load(text.reader())
				@Suppress("UNCHECKED_CAST")
				(props as Map<*, *>).entries
					.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
					.toMap()
			}
			.orElse(emptyMap())

		// =========================================================================
		// MARK: Cache Safe Providers
		// =========================================================================

		val taskNamesList              = project.gradle.startParameter.taskNames
		val taskNamesProvider          = project.providers.provider { taskNamesList }
		val dimensionPropsProvider     = project.providers.gradlePropertiesPrefixedBy("konfig.dimension.")

		val buildTypeDetectionEnabled: Provider<Boolean> = project.providers
			.gradleProperty("konfig.android.buildtypedetection")
			.map { it != "false" }
			.orElse(project.providers.provider { true })

		val flavorDetectionEnabled: Provider<Boolean> = project.providers
			.gradleProperty("konfig.android.flavordetection")
			.map { it != "false" }
			.orElse(project.providers.provider { true })

		// =========================================================================
		// MARK: Build Type Provider
		// =========================================================================

		val rawBuildTypeProp = project.providers.gradleProperty("konfig.buildtype")
		val buildTypeProvider: Provider<BuildType> = rawBuildTypeProp
			.map { BuildType.resolve(it) ?: BuildType.RELEASE }
			.orElse(
				buildTypeDetectionEnabled.zip(taskNamesProvider) { enabled, names ->
					if (enabled) BuildType.resolve(names.joinToString(" ")) ?: BuildType.RELEASE
					else BuildType.RELEASE
				}
			)

		// =========================================================================
		// Encodes WHY the build type was chosen; stored as a task input for execution-time logging.
		// Format: plain descriptive string.
		// =========================================================================

		val buildTypeSourceProvider: Provider<String> = rawBuildTypeProp
			.map { raw ->
				val resolved = BuildType.resolve(raw)
				if (resolved != null) "explicit property -Pkonfig.buildtype=$raw"
				else                  "explicit property -Pkonfig.buildtype=$raw (unrecognized value, fell back to RELEASE)"
			}
			.orElse(
				buildTypeDetectionEnabled.zip(taskNamesProvider) { enabled, names ->
					when {
						!enabled -> "detection disabled by konfig.android.buildtypedetection=false, using RELEASE"
						names.isEmpty() -> "no tasks running, using RELEASE"
						else -> {
							val resolved = BuildType.resolve(names.joinToString(" "))
							if (resolved != null)
								"task-name detection matched ${resolved.name.lowercase()} in [${names.joinToString()}]"
							else
								"no debug/release pattern found in tasks [${names.joinToString()}], using RELEASE"
						}
					}
				}
			)

		// Combined provider for dimension resolvers
		// Packs: gradleProps, konfigFileProps, flavorDetectionEnabled, taskNames
		val combinedProps: Provider<DimensionContext> = dimensionPropsProvider
			.zip(konfigPropertiesProvider) { gradleProps, fileProps -> gradleProps to fileProps }
			.zip(flavorDetectionEnabled) { (gp, fp), fe -> Triple(gp, fp, fe) }
			.zip(taskNamesProvider) { (gp, fp, fe), names ->
				DimensionContext(gradleProps = gp, fileProps = fp, flavorDetect = fe, taskNames = names)
			}

		// Encodes resolution status for EVERY declared dimension (including skipped ones).
		// Format per entry: "<TAG>\t<variant>\t<reason>"
		//   TAG = OK | WARN_UNKNOWN | WARN_AMBIGUOUS | SKIP | ERROR
		val dimensionResolutionLogProvider: Provider<Map<String, String>> =
			buildTypeProvider.zip(combinedProps) { _, ctx ->
				extension.dimensions.associate { dim ->
					dim.dimensionName to resolveWithSource(dim, ctx.gradleProps, ctx.fileProps, ctx.flavorDetect, ctx.taskNames)
				}
			}

		// =========================================================================
		// MARK: Build-script query wiring (konfig.isDebug / konfig.getCurrentDimension)
		// =========================================================================

		extension.buildTypeProviderInternal = buildTypeProvider
		extension.dimensionResolverInternal = { dimName ->
			combinedProps.map { ctx ->
				extension.dimensions.firstOrNull { it.dimensionName == dimName }?.let { dim ->
					resolveActiveVariant(dim, ctx.gradleProps, ctx.fileProps, ctx.flavorDetect, ctx.taskNames)
				}
			}
		}

		// =========================================================================
		// MARK: Task Registration
		// =========================================================================

		val forceRegen = project.providers.gradleProperty("konfig.force").isPresent

		// Untracked logging task: runs on EVERY build (even fully cached ones) so the
		// selected build type / dimension variants always appear in the logs.
		val infoTask = project.tasks.register("konfigInfo", KonfigInfoTask::class.java) {
			description = "Prints the resolved konfig build type and dimension variants."
			group = "konfig"
			moduleName.set(project.name)
			buildType.set(buildTypeProvider)
			buildTypeSource.set(buildTypeSourceProvider)
			dimensionResolutionLog.set(dimensionResolutionLogProvider)
		}

		val generateTask = project.tasks.register("generateKonfig", GenerateKonfigTask::class.java).apply {
			configure {
				if (forceRegen) outputs.upToDateWhen { false }
				dependsOn(infoTask)

				moduleName.set(project.name)
				buildType.set(buildTypeProvider)
				// Only ERROR entries: full logs (task-name dependent) are on konfigInfo,
				// keeping this task's inputs stable across invocations.
				dimensionResolutionLog.set(
					dimensionResolutionLogProvider.map { log ->
						log.filterValues { it.startsWith("ERROR") }
					}
				)
				flatDimensionNames.set(
					project.providers.provider {
						extension.dimensions.filter { it.flat }.map { it.dimensionName }.toSet()
					}
				)
				outputDirectory.set(extension.outputDir)
				objectPackage.set(extension.objectPackageProp)
				objectName.set(extension.objectNameProp)
				objectVisibility.set(extension.objectVisibilityProp)

				// ── Global fields ─────────────────────────────────────────────
				globalFields.set(resolveFields(buildTypeProvider, extension.globalFields))

				// ── Dimension metadata ────────────────────────────────────────
				activeDimensionNames.set(
					buildTypeProvider.zip(combinedProps) { _, ctx ->
						extension.dimensions.mapNotNull { dim ->
							resolveActiveVariant(dim, ctx.gradleProps, ctx.fileProps, ctx.flavorDetect, ctx.taskNames)
								?: return@mapNotNull null
							dim.dimensionName
						}
					}
				)
				dimensionObjectNames.set(
					buildTypeProvider.map {
						extension.dimensions.associate { dim -> dim.dimensionName to dim.objectName() }
					}
				)
				dimensionActiveVariants.set(
					buildTypeProvider.zip(combinedProps) { _, ctx ->
						extension.dimensions.mapNotNull { dim ->
							val sv = resolveActiveVariant(dim, ctx.gradleProps, ctx.fileProps, ctx.flavorDetect, ctx.taskNames)
								?: return@mapNotNull null
							dim.dimensionName to sv
						}.toMap()
					}
				)

				// ── Dimension fields ──────────────────────────────────────────
				dimensionFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension))
			}
		}

		// =========================================================================
		// MARK: Auto Sourceset
		// =========================================================================

		project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
			project.extensions.findByType(KotlinMultiplatformExtension::class.java)
				?.sourceSets?.findByName("commonMain")?.kotlin?.srcDir(extension.outputDir)
		}
		project.plugins.withId("org.jetbrains.kotlin.jvm") {
			project.extensions.findByType(KotlinSingleTargetExtension::class.java)
				?.sourceSets?.findByName("main")?.kotlin?.srcDir(extension.outputDir)
		}
		project.plugins.withId("org.jetbrains.kotlin.android") {
			project.extensions.findByType(KotlinSingleTargetExtension::class.java)
				?.sourceSets?.findByName("main")?.kotlin?.srcDir(extension.outputDir)
		}
		listOf("com.android.application", "com.android.library").forEach { androidPluginId ->
			project.plugins.withId(androidPluginId) {
				// AGP 9.2 flips android.sourceset.disallowProvider to true: passing a
				// provider (like a DirectoryProperty) to the AndroidSourceSet DSL fails.
				// Register the generated directory through the variant Sources API instead.
				project.extensions.findByType(AndroidComponentsExtension::class.java)
					?.onVariants { variant ->
						val vOutDir = extension.outputDir.get().asFile
						vOutDir.mkdirs()
						variant.sources.kotlin?.addStaticSourceDirectory(vOutDir.absolutePath)
					}
			}
		}

		// ==============================================
		// MARK: Task Dependency
		// ==============================================

		project.tasks.configureEach {
			if (
				name.contains("sourcesJar") || name.contains("SourcesJar")
				|| name.contains("compileKotlin")
				|| (name.startsWith("compile") && name.contains("Kotlin"))
				|| (name.startsWith("compile") && name.contains("Main"))
			) {
				dependsOn(generateTask)
			}
		}
	}

	// ==============================================
	// MARK: Resolution helpers
	// ==============================================

	/**
	 * Resolves the active variant for a dimension (used for actual field resolution).
	 * Returns null if the dimension should be skipped.
	 */
	private fun resolveActiveVariant(
		dim: DimensionConfig,
		gradleProps: Map<String, String>,
		fileProps: Map<String, String>,
		flavorDetect: Boolean,
		taskNames: List<String>
	): String? {
		val encoded = resolveWithSource(dim, gradleProps, fileProps, flavorDetect, taskNames)
		val parts   = encoded.split("\t", limit = 3)
		return when (parts[0]) {
			"OK" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
			else -> null
		}
	}

	/**
	 * Resolves a dimension and encodes the result as a tab-separated string for logging.
	 *
	 * Format: `"<TAG>\t<variant>\t<reason>"`
	 * - TAG = `OK` - active, variant resolved successfully
	 * - TAG = `WARN_UNKNOWN` - property set but value is not a known variant
	 * - TAG = `WARN_AMBIGUOUS` - multiple variants matched task names
	 * - TAG = `SKIP` - no active variant could be determined
	 * - TAG = `ERROR` - configuration error (e.g. invalid defaultTo)
	 */
	private fun resolveWithSource(
		dim: DimensionConfig,
		gradleProps: Map<String, String>,
		fileProps: Map<String, String>,
		flavorDetect: Boolean,
		taskNames: List<String>
	): String {
		// Validate defaultTo at resolution time
		if (dim.defaultVariant != null && !dim.variants.containsKey(dim.defaultVariant)) {
			return "ERROR\t\tdefaultTo='${dim.defaultVariant}' is not a known variant " +
				"(known: ${dim.variants.keys.sorted().joinToString()})"
		}

		val propKey = "konfig.dimension.${dim.dimensionName}"

		// Priority 1: explicit Gradle property konfig.dimension.<name>=<value>
		val fromGradleProp = gradleProps[dim.dimensionName] ?: gradleProps[propKey]
		if (fromGradleProp != null) {
			return if (dim.variants.containsKey(fromGradleProp)) {
				"OK\t$fromGradleProp\tproperty -P$propKey=$fromGradleProp"
			} else {
				"WARN_UNKNOWN\t\tproperty -P$propKey=$fromGradleProp is not a known variant " +
					"(known: ${dim.variants.keys.sorted().joinToString()}) -- dimension skipped"
			}
		}

		// Priority 2: konfig.properties file
		val fromFile = fileProps[dim.dimensionName] ?: fileProps[propKey]
		if (fromFile != null) {
			return if (dim.variants.containsKey(fromFile)) {
				"OK\t$fromFile\tkonfig.properties $propKey=$fromFile"
			} else {
				"WARN_UNKNOWN\t\tkonfig.properties $propKey=$fromFile is not a known variant " +
					"(known: ${dim.variants.keys.sorted().joinToString()}) -- dimension skipped"
			}
		}

		// Priority 3: task-name detection
		if (flavorDetect && taskNames.isNotEmpty()) {
			val rawMatches = dim.variants.keys.filter { variant ->
				taskNames.any { task -> task.containsWordCamelCase(variant) }
			}
			// When every match is a substring of the longest one (e.g. 'prod' inside
			// 'preProd' for task assemblePreProdRelease), the longest is the real one.
			val matches = if (rawMatches.size > 1) {
				val longest = rawMatches.maxBy { it.length }
				if (rawMatches.all { it == longest || longest.contains(it, ignoreCase = true) }) listOf(longest)
				else rawMatches
			} else rawMatches
			when (matches.size) {
				1 -> return "OK\t${matches.first()}\ttask-name detection: '${matches.first()}' " +
					"found in [${taskNames.joinToString()}]"
				in 2..Int.MAX_VALUE -> return "WARN_AMBIGUOUS\t\ttask names matched multiple variants " +
					"${matches.sorted()} in [${taskNames.joinToString()}] -- dimension skipped"
			}
		}

		// Priority 4: defaultTo fallback
		if (dim.defaultVariant != null) {
			return "OK\t${dim.defaultVariant}\tdefaultTo='${dim.defaultVariant}'"
		}

		// Not set
		val hints = buildList {
			add("no -P$propKey property")
			add("no konfig.properties entry")
			if (!flavorDetect) add("flavor detection disabled")
			else if (taskNames.isEmpty()) add("no tasks running")
			else add("no variant name found in tasks [${taskNames.joinToString()}]")
			add("no defaultTo set")
		}
		return "SKIP\t\t${hints.joinToString(", ")}"
	}

	private fun resolveFields(
		buildType: Provider<BuildType>,
		fields: List<FieldConfig<*>>,
	): Provider<Map<String, String>> = buildType.map { bt ->
		fields.mapNotNull { field -> encodeField(field, bt) }.toMap()
	}

	private fun resolveDimensionFields(
		buildType: Provider<BuildType>,
		combined: Provider<DimensionContext>,
		extension: KonfigExtension,
	): Provider<Map<String, String>> =
		buildType.zip(combined) { bt, ctx ->
			buildMap {
				for (dim in extension.dimensions) {
					val sv = resolveActiveVariant(dim, ctx.gradleProps, ctx.fileProps, ctx.flavorDetect, ctx.taskNames) ?: continue
					val vc = dim.variants[sv] ?: continue
					val prefix = "${dim.dimensionName}|"
					for (field in mergeVariantFields(dim.commonConfig.fields, vc.fields)) {
						encodeField(field, bt)?.let { (name, value) -> put("$prefix$name", value) }
					}
				}
			}
		}

	/** Encodes a [FieldConfig] value for [buildType] as `"TYPE:rawValue"`, or `null` if absent. */
	private fun encodeField(field: FieldConfig<*>, buildType: BuildType): Pair<String, String>? {
		val value = field.resolve(buildType)?.orNull ?: return null
		val encoded = when (value) {
			is String  -> "String:$value"
			is Boolean -> "Boolean:$value"
			is Int     -> "Int:$value"
			is Long    -> "Long:$value"
			is Float   -> "Float:$value"
			is Double  -> "Double:$value"
			else       -> return null
		}
		return field.fieldName to encoded
	}

	// ==============================================================================
	// MARK: Fields Merge
	// ==============================================================================

	/**
	 * Returns a merged list of fields: [commonFields] as the base, with any field in
	 * [variantFields] taking precedence (variant fields win by name).
	 */
	private fun mergeVariantFields(
		commonFields: List<FieldConfig<*>>,
		variantFields: List<FieldConfig<*>>
	): List<FieldConfig<*>> {
		if (commonFields.isEmpty()) return variantFields
		val variantNames = variantFields.map { it.fieldName }.toHashSet()
		return commonFields.filter { it.fieldName !in variantNames } + variantFields
	}

	// ==============================================================================
	// MARK: Package Utilities
	// ==============================================================================

	private fun defaultPackageName(projectName: String, projectGroup: String): String {
		val group = projectGroup
			.lowercase()
			.replace("-", ".")
			.takeIf { it.isNotBlank() && it != "unspecified" }
		val artifact = projectName
			.replace("-", ".")
			.replace(Regex("[^A-Za-z0-9.]"), "")
		return if (group != null) "$group.$artifact" else artifact.ensureValidPackage()
	}

	private fun String.ensureValidPackage(): String = split(".")
		.filter { it.isNotBlank() }
		.joinToString(".") { segment ->
			val cleaned = segment.replace(Regex("[^A-Za-z0-9_]"), "")
			when {
				cleaned.isBlank()         -> "_"
				cleaned.first().isDigit() -> "_$cleaned"
				else                      -> cleaned
			}
		}.lowercase()
}
