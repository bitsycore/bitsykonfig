package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional tests for `flatDimension` - fields generated at the root of the
 * konfig object, with hard failure on root-level name collisions.
 */
class FlatDimensionFunctionalTest : FunctionalTestBase() {

	@Test fun `flat dimension fields are generated at root without nested object`() = withProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				flatDimension("env", defaultTo = "prod") {
					variant("prod") { field("API_URL", "https://prod.example.com") }
					variant("dev")  { field("API_URL", "https://dev.example.com") }
				}
			}
		""")
		run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		val content = dir.generatedFile().readText()

		assertTrue(content.contains("""const val ENV_VARIANT: String = "prod""""), "root variant const missing:\n$content")
		assertTrue(content.contains("""const val API_URL: String = "https://prod.example.com""""), "root field missing:\n$content")
		assertFalse(content.contains("object Env"), "flat dimension must not generate a nested object:\n$content")
	}

	@Test fun `flat dimension colliding with global field fails the build`() = withFailingProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				field("API_URL", "global")
				flatDimension("env", defaultTo = "prod") {
					variant("prod") { field("API_URL", "https://prod.example.com") }
				}
			}
		""")
		val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		assertTrue(result.output.contains("collides"), "expected collision error, got:\n${result.output}")
		assertTrue(result.output.contains("API_URL"))
	}

	@Test fun `flat dimension colliding with built-in constant fails the build`() = withFailingProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				flatDimension("env", defaultTo = "prod") {
					variant("prod") { field("BUILD_TYPE", "oops") }
				}
			}
		""")
		val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		assertTrue(result.output.contains("collides"), "expected collision error, got:\n${result.output}")
		assertTrue(result.output.contains("BUILD_TYPE"))
	}

	@Test fun `two flat dimensions with colliding fields fail the build`() = withFailingProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				flatDimension("env", defaultTo = "prod") {
					variant("prod") { field("URL", "a") }
				}
				flatDimension("region", defaultTo = "eu") {
					variant("eu") { field("URL", "b") }
				}
			}
		""")
		val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		assertTrue(result.output.contains("collides"), "expected collision error, got:\n${result.output}")
	}

	@Test fun `flat and nested dimensions can coexist`() = withProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				flatDimension("env", defaultTo = "prod") {
					variant("prod") { field("API_URL", "https://prod.example.com") }
				}
				dimension("region", defaultTo = "eu") {
					variant("eu") { field("DC", "fra") }
				}
			}
		""")
		run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		val content = dir.generatedFile().readText()

		assertTrue(content.contains("const val ENV_VARIANT"))
		assertTrue(content.contains("object Region"))
		assertTrue(content.contains("""const val DC: String = "fra""""))
	}
}
