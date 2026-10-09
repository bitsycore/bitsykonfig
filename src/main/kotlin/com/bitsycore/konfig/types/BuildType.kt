package com.bitsycore.konfig.types

import kotlin.enums.enumEntries
import org.gradle.api.GradleException

enum class BuildType(val value: String) {
	DEBUG("debug"),
	RELEASE("release");

	companion object {
		private val DEBUG_REGEX = Regex("""(?<![a-z])debug(?![a-z])|(?<![A-Z])Debug(?![a-z])""")
		private val RELEASE_REGEX = Regex("""(?<![a-z])release(?![a-z])|(?<![A-Z])Release(?![a-z])""")

		internal fun resolveTasks(names: List<String>): BuildType? {
			val tasks = names.map { it.substringAfterLast(':') }
			val debug = tasks.any { it == "DEBUG" || DEBUG_REGEX.containsMatchIn(it) }
			val release = tasks.any { it == "RELEASE" || RELEASE_REGEX.containsMatchIn(it) }
			if (debug && release) throw GradleException(
				"konfig: conflicting debug and release tasks $names cannot share one generated object. " +
					"Run separate builds or select -Pkonfig.buildtype explicitly."
			)
			return when { debug -> DEBUG; release -> RELEASE; else -> null }
		}

		/**
		 * Build type when no task names one. An IDE sync (no task requested, or `idea.sync.active`)
		 * gets DEBUG, so the IDE resolves the debug configuration; a real build keeps RELEASE.
		 */
		internal fun defaultFor(names: List<String>, ideSync: Boolean): BuildType =
			if (ideSync || names.isEmpty()) DEBUG else RELEASE

		fun resolve(value: String): BuildType? {
			enumEntries<BuildType>().firstOrNull { it.name == value }?.let {
				return it
			}

			val debugResult = DEBUG_REGEX.containsMatchIn(value)
			val releaseResult = RELEASE_REGEX.containsMatchIn(value)

			return when {
				debugResult && releaseResult.not() -> DEBUG
				releaseResult && debugResult.not() -> RELEASE
				else -> null
			}
		}
	}
}
