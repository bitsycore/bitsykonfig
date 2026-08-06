package com.bitsycore.konfig

import com.bitsycore.konfig.types.BuildType
import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Logs the resolved build type and dimension variants on EVERY build.
 *
 * [GenerateKonfigTask] only logs when it actually executes, so a cached /
 * up-to-date build would silently hide which variant is active. This task is
 * untracked (never up-to-date, never cached) and `generateKonfig` depends on
 * it, guaranteeing the selection is always visible in the logs — even on a
 * fully cached build with the configuration cache enabled.
 */
@DisableCachingByDefault(because = "pure logging task, must run on every build")
abstract class KonfigInfoTask : DefaultTask() {

	@get:Internal abstract val moduleName:      Property<String>
	@get:Internal abstract val buildType:       Property<BuildType>
	@get:Internal abstract val buildTypeSource: Property<String>

	/** Resolution log per dimension: `"<TAG>\t<variant>\t<reason>"`. */
	@get:Internal abstract val dimensionResolutionLog: MapProperty<String, String>

	init {
		// Never skipped: no declared outputs + explicit upToDateWhen false.
		outputs.upToDateWhen { false }
	}

	@TaskAction
	fun report() {
		val mod = moduleName.get()
		logger.lifecycle("konfig [$mod]: BUILD_TYPE = ${buildType.get().name.lowercase()}  (${buildTypeSource.get()})")

		val resolutionLog = dimensionResolutionLog.get()
		if (resolutionLog.isEmpty()) {
			logger.info("konfig [$mod]: no dimensions declared")
			return
		}
		val maxDimLen = resolutionLog.keys.maxOf { it.length }
		resolutionLog.entries
			.sortedBy { (n, enc) -> "${if (enc.startsWith("OK")) "0" else "1"}_$n" }
			.forEach { (dimName, encoded) ->
				val parts   = encoded.split("\t", limit = 3)
				val tag     = parts[0]
				val variant = parts.getOrElse(1) { "" }
				val reason  = parts.getOrElse(2) { "" }
				val padded  = dimName.padEnd(maxDimLen)
				when (tag) {
					"OK"   -> logger.lifecycle("konfig [$mod]: dim '$padded' -> '$variant'  ($reason)")
					"SKIP" -> logger.lifecycle("konfig [$mod]: dim '$padded' -> skipped  ($reason)")
					"WARN_UNKNOWN", "WARN_AMBIGUOUS" -> logger.warn("konfig [$mod]: dim '$dimName' -- $reason")
					"ERROR" -> logger.error("konfig [$mod]: dim '$dimName' -- ERROR: $reason")
				}
			}
	}
}
