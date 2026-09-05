package com.bitsycore.konfig

import com.bitsycore.konfig.types.BuildType
import com.bitsycore.konfig.types.Visibility
import com.bitsycore.konfig.types.isValidKotlinIdentifier
import com.bitsycore.konfig.types.toVariantConstName
import com.bitsycore.konfig.types.toKotlinStringLiteral
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Generates the `BuildKonfig` (or custom named) Kotlin object.
 *
 * Fields are stored in flat [MapProperty]<String, String> maps where each value is
 * type-encoded as `"<TYPE>:<literal>"` (e.g. `"String:hello"`, `"Int:42"`).
 * Dimension fields use keys of the form `"<dimName>|<fieldName>"`.
 * Collections use `"Value:<Kotlin type>\n<initializer>"`, with literals escaped by
 * the encoder. Only strings, not reflection types or collection instances, reach task inputs.
 * This collapses 6 separate per-type maps down to one per scope.
 */
@CacheableTask
abstract class GenerateKonfigTask : DefaultTask() {

	// ==========================================================
	// MARK: General Settings
	// ==========================================================

	@get:Input abstract val moduleName:       Property<String>
	@get:Input abstract val buildType:        Property<BuildType>

	// ==========================================================
	// MARK: Object Settings
	// ==========================================================

	@get:Input abstract val objectPackage:      Property<String>
	@get:Input abstract val objectName:       Property<String>
	@get:Input abstract val objectVisibility: Property<Visibility>

	/**
	 * Resolution log entries with the ERROR tag only (configuration errors), keyed by
	 * dimension name. Full logs (with task-name-dependent reasons) live on [KonfigInfoTask]
	 * so they don't bust this task's up-to-date check.
	 * Each value is tab-separated: `"<TAG>\t<variant>\t<reason>"`.
	 */
	@get:Input abstract val dimensionResolutionLog: MapProperty<String, String>

	// ==========================================================
	// MARK: Fields
	// ==========================================================

	/** Global (top-level) fields: `fieldName -> "TYPE:value"`. */
	@get:Input abstract val globalFields: MapProperty<String, String>

	// ==========================================================
	// MARK: Dimensions
	// ==========================================================

	/** Ordered list of active dimension names. */
	@get:Input abstract val activeDimensionNames: ListProperty<String>
	/** Names of dimensions declared with `flatDimension` (fields emitted at the root). */
	@get:Input abstract val flatDimensionNames: SetProperty<String>
	/** `dimName -> Kotlin object name`. */
	@get:Input abstract val dimensionObjectNames: MapProperty<String, String>
	/** `dimName -> selected variant`. */
	@get:Input abstract val dimensionActiveVariants: MapProperty<String, String>

	/** Dimension fields: `"<dimName>|<fieldName>" -> "TYPE:value"`. */
	@get:Input abstract val dimensionFields: MapProperty<String, String>

	// Register only the generated file as output: Gradle must never own neighboring files.
	@get:Internal abstract val outputDirectory: DirectoryProperty
	/** A directory view that carries generatedFile's producer without owning the directory. */
	@get:Internal abstract val sourceDirectory: DirectoryProperty
	@get:OutputFile abstract val ownershipFile: RegularFileProperty
	@get:OutputFile abstract val generatedFile: RegularFileProperty

	init {
		generatedFile.convention(outputDirectory.zip(objectPackage) { dir, pkg -> dir.dir(pkg.replace('.', '/')) }
			.zip(objectName) { dir, name -> dir.file("$name.kt") })
		sourceDirectory.convention(generatedFile.zip(outputDirectory) { _, directory -> directory })
		outputs.doNotCacheIf("Output ownership must be checked or a renamed output cleaned up") {
			val record = ownershipFile.get().asFile
			val target = generatedFile.get().asFile
			!target.canonicalFile.toPath().startsWith(outputDirectory.get().asFile.canonicalFile.toPath()) ||
				(target.exists() && !target.readText().contains(GENERATED_MARKER)) ||
				(record.exists() && record.readText() != "${objectPackage.get().replace('.', '/')}/${objectName.get()}.kt")
		}
	}

	@TaskAction
	fun generate() {
		val pkg       = objectPackage.get()
		val objName   = objectName.get()
		val btVal     = buildType.get()
		val mod       = moduleName.get()
		val vis       = objectVisibility.get()
		val visPrefix = when (vis) {
            Visibility.INTERNAL -> "internal "
            Visibility.PUBLIC -> "public "
        }

		val isDebug   = btVal == BuildType.DEBUG

		validate(mod, objName, pkg)

		val outDir = outputDirectory.get().asFile
		val pkgDir = outDir.resolve(pkg.replace('.', '/'))

		val gFields   = globalFields.get()
		val dFields   = dimensionFields.get()
		val dimNames  = activeDimensionNames.get()
		val dimObjN   = dimensionObjectNames.get()
		val dimVars   = dimensionActiveVariants.get()
		val flatDims  = flatDimensionNames.get()

		checkRootCollisions(mod, dimNames, gFields, dFields, dimVars)

		val content = buildString {
			appendLine("""@file:Suppress("RedundantVisibilityModifier")""")
			appendLine()
			appendLine("package $pkg")
			appendLine()
			appendLine("// ============= DO NOT EDIT =============")
			appendLine("// Generated by buildkonfig-gradle-plugin ")
			appendLine("// =======================================")
			appendLine()
			appendLine("${visPrefix}object $objName {")
			appendLine()
			appendLine("""    const val BUILD_TYPE: String = "${btVal.name.lowercase()}"""")
			appendLine("    const val MODULE_NAME: String = ${mod.toKotlinStringLiteral()}")
			if (isDebug)
				appendLine("    inline val IS_DEBUG: Boolean get() = true")
			else
				appendLine("    const val IS_DEBUG: Boolean = false")

			if (gFields.isNotEmpty()) {
				appendLine()
				appendEncodedFields("    ", gFields, btVal)
			}

			for (dimName in dimNames) {
				val activeVariant = dimVars[dimName] ?: continue
				val prefix        = "$dimName|"
				val fields        = dFields
					.filterKeys { it.startsWith(prefix) }
					.mapKeys    { (k, _) -> k.removePrefix(prefix) }

				if (dimName in flatDims) {
					// Flat dimension: fields live directly on the root object.
					appendLine()
					appendLine("    // dimension: ${dimName.safeComment()} (flat), variant: ${activeVariant.safeComment()}")
					appendLine("    const val ${dimName.toVariantConstName()}: String = ${activeVariant.toKotlinStringLiteral()}")
					if (fields.isNotEmpty()) {
						appendEncodedFields("    ", fields, btVal)
					}
				} else {
					val dimObjName = dimObjN[dimName] ?: continue
					appendLine()
					appendLine("    ${visPrefix}object $dimObjName /*${dimName.safeComment()}*/ {")
					appendLine()
					appendLine("        const val VARIANT: String = ${activeVariant.toKotlinStringLiteral()}")
					if (fields.isNotEmpty()) {
						appendLine()
						appendEncodedFields("        ", fields, btVal)
					}
					appendLine("    }")
				}

				logger.info("konfig [$mod]: dim '$dimName' fields: ${fields.keys.sorted().joinToString()}")
			}

			appendLine()
			appendLine("}")
		}

		val outFile = generatedFile.get().asFile
		val rootPath = outDir.canonicalFile.toPath()
		if (!outFile.canonicalFile.toPath().startsWith(rootPath))
			throw GradleException("konfig: generated file escapes outputDirectory through a symbolic link")
		if (outFile.exists() && !outFile.readText().contains(GENERATED_MARKER))
			throw GradleException("konfig: refusing to overwrite non-generated file '$outFile'")
		pkgDir.mkdirs()
		val temporary = Files.createTempFile(pkgDir.toPath(), ".konfig-", ".tmp")
		try {
			Files.writeString(temporary, content)
			Files.move(temporary, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
		} finally {
			Files.deleteIfExists(temporary)
		}
		val record = ownershipFile.get().asFile
		if (record.exists()) {
			val previous = outDir.resolve(record.readText())
			if (previous.canonicalFile != outFile.canonicalFile && previous.isFile &&
				previous.canonicalFile.toPath().startsWith(rootPath) && previous.readText().contains(GENERATED_MARKER)) {
				Files.delete(previous.toPath())
			}
		}
		record.parentFile.mkdirs()
		record.writeText("${pkg.replace('.', '/')}/$objName.kt")

		val dimSummary   = if (dimNames.isEmpty()) "no dimensions"
			else "${dimNames.size} dimension(s): ${dimNames.joinToString { "'$it'" }}"
		val fieldSummary = if (gFields.isEmpty()) "no global fields"
			else "${gFields.size} global field(s)"
		logger.lifecycle("konfig [$mod]: generated $objName.kt  ($fieldSummary, $dimSummary)")
		logger.info("konfig [$mod]: output -> ${outFile.absolutePath}")
	}

	// ==============================================================================
	// MARK: Validation
	// ==============================================================================

	private fun validate(mod: String, objName: String, pkg: String) {
		val errors = mutableListOf<String>()

		dimensionResolutionLog.get().forEach { (dimName, encoded) ->
			if (encoded.split("\t").first() == "ERROR")
				errors += "dimension '$dimName': ${encoded.split("\t", limit = 3).getOrElse(2) { "unknown error" }}"
		}

		if (!objName.isValidKotlinIdentifier())
			errors += "objectName '$objName' is not a valid Kotlin identifier"

		globalFields.get().keys.forEach { name ->
			if (!name.isValidKotlinIdentifier())
				errors += "global field name '$name' is not a valid Kotlin identifier"
		}

		dimensionFields.get().keys.forEach { key ->
			val fieldName = key.substringAfter("|")
			if (!fieldName.isValidKotlinIdentifier())
				errors += "field name '$fieldName' is not a valid Kotlin identifier"
		}

		pkg.split(".").forEach { segment ->
			if (!segment.isValidKotlinIdentifier())
				errors += "package segment '$segment' in '$pkg' is not valid"
		}
		val flat = flatDimensionNames.get()
		activeDimensionNames.get().forEach { dimension ->
			val name = if (dimension in flat) dimension.toVariantConstName() else dimensionObjectNames.get()[dimension].orEmpty()
			if (!name.isValidKotlinIdentifier()) errors += "dimension '$dimension' generates invalid Kotlin identifier '$name'"
		}

		if (errors.isNotEmpty()) {
			throw GradleException(buildString {
				appendLine("konfig [$mod]: configuration errors:")
				errors.forEach { appendLine("  - $it") }
			}.trimEnd())
		}
	}

	// ==============================================================================
	// MARK: Generated scope collision detection
	// ==============================================================================

	/**
	 * Checks built-in constants, global/flat fields, nested object names and VARIANT.
	 */
	private fun checkRootCollisions(
		mod: String,
		activeDims: List<String>,
		gFields: Map<String, String>,
		dFields: Map<String, String>,
		dimVars: Map<String, String>,
	) {
		val owners = mutableMapOf<String, String>()
		listOf("BUILD_TYPE", "MODULE_NAME", "IS_DEBUG").forEach { owners[it] = "built-in constant" }
		val errors = mutableListOf<String>()
		fun claim(name: String, owner: String) {
			val existing = owners.putIfAbsent(name, owner)
			if (existing != null) errors += "$owner generates '$name' which collides with $existing"
		}
		gFields.keys.forEach { claim(it, "global field") }
		val flatDims = flatDimensionNames.get()
		for (dimName in activeDims) {
			if (dimVars[dimName] == null) continue
			val prefix = "$dimName|"
			if (dimName !in flatDims) {
				val dimObject = dimensionObjectNames.get().getValue(dimName)
				claim(dimObject, "dimension '$dimName'")
				if (dimObject == objectName.get()) errors += "dimension '$dimName' reuses enclosing object name '$dimObject'"
				if (dFields.containsKey("${prefix}VARIANT")) errors += "dimension '$dimName' field 'VARIANT' collides with built-in constant"
				continue
			}
			val rootNames = buildList {
				add(dimName.toVariantConstName())
				addAll(dFields.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) })
			}
			for (name in rootNames) {
				claim(name, "flat dimension '$dimName'")
			}
		}

		if (errors.isNotEmpty()) {
			throw GradleException(buildString {
				appendLine("konfig [$mod]: name collisions:")
				errors.forEach { appendLine("  - $it") }
			}.trimEnd())
		}
	}

	private fun String.safeComment(): String = toKotlinStringLiteral().removeSurrounding("\"")
		.replace("/*", "/ *").replace("*/", "* /")

	private companion object {
		const val GENERATED_MARKER = "// Generated by buildkonfig-gradle-plugin"
	}

	// ==============================================================================
	// MARK: Helpers
	// ==============================================================================

	/**
	 * Appends `const val` lines for each entry in [fields].
	 * Values are type-encoded strings: `"String:..."`, `"Boolean:..."`, etc.
	 */
	private fun StringBuilder.appendEncodedFields(indent: String, fields: Map<String, String>, btVal: BuildType) {
		fields.forEach { (name, encoded) ->
			val colon = encoded.indexOf(':')
			val type  = encoded.substring(0, colon)
			val raw   = encoded.substring(colon + 1)
			val line  = when (type) {
				"String"  -> """${indent}const val $name: String = ${raw.toKotlinStringLiteral()}"""
				"Boolean" -> when(btVal) {
                    BuildType.DEBUG -> """${indent}inline val $name: Boolean get() = $raw"""
                    BuildType.RELEASE -> """${indent}const val $name: Boolean = $raw"""
                }
				"Int"     -> """${indent}const val $name: Int = $raw"""
				"Long"    -> """${indent}const val $name: Long = ${if (raw == Long.MIN_VALUE.toString()) "Long.MIN_VALUE" else "${raw}L"}"""
				"Float"   -> """${indent}const val $name: Float = ${raw.toFloat().toKotlinFloat()}"""
				"Double"  -> """${indent}const val $name: Double = ${raw.toDouble().toKotlinDouble()}"""
				"Value"   -> "${indent}val $name: ${raw.substringBefore('\n')} = ${raw.substringAfter('\n')}"
				"Getter"  -> "${indent}val $name: ${raw.substringBefore('\n')} get() = ${raw.substringAfter('\n')}"
				else      -> return@forEach // unknown type — skip
			}
			appendLine(line)
		}
	}

	private fun Float.toKotlinFloat(): String = when {
		isNaN()                         -> "Float.NaN"
		this == Float.POSITIVE_INFINITY -> "Float.POSITIVE_INFINITY"
		this == Float.NEGATIVE_INFINITY -> "Float.NEGATIVE_INFINITY"
		else -> toString().let {
			if (!it.contains('.') && !it.contains('E') && !it.contains('e')) "$it.0" else it
		} + "f"
	}

	private fun Double.toKotlinDouble(): String = when {
		isNaN()                          -> "Double.NaN"
		this == Double.POSITIVE_INFINITY -> "Double.POSITIVE_INFINITY"
		this == Double.NEGATIVE_INFINITY -> "Double.NEGATIVE_INFINITY"
		else -> toString().let {
			if (!it.contains('.') && !it.contains('E') && !it.contains('e')) "$it.0" else it
		}
	}

}
