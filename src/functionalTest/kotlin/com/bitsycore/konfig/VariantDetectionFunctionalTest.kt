package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Functional tests for task-name variant detection edge cases:
 * prod vs preprod differentiation, camelCase flavors, and the konfigInfo
 * task always logging the selection (even on cached builds).
 */
class VariantDetectionFunctionalTest : FunctionalTestBase() {

	private val buildScript = """
		plugins { id("com.bitsycore.konfig") }
		group = "com.example"
		konfig {
			dimension("env") {
				variant("prod")    { field("URL", "https://prod.example.com") }
				variant("preprod") { field("URL", "https://preprod.example.com") }
			}
		}
		tasks.register("assemblePreprodRelease") { dependsOn("generateKonfig") }
		tasks.register("assembleProdRelease")    { dependsOn("generateKonfig") }
	"""

	@Test fun `preprod task selects preprod not prod`() = withProject { dir, run ->
		dir.writeBuildGradle(buildScript)
		val result = run(listOf("assemblePreprodRelease"))
		assertTrue(result.output.contains("dim 'env' -> 'preprod'"), "expected preprod selection:\n${result.output}")
		val content = dir.generatedFile().readText()
		assertTrue(content.contains("https://preprod.example.com"), "wrong URL generated:\n$content")
	}

	@Test fun `prod task selects prod not ambiguous`() = withProject { dir, run ->
		dir.writeBuildGradle(buildScript)
		val result = run(listOf("assembleProdRelease"))
		assertTrue(result.output.contains("dim 'env' -> 'prod'"), "expected prod selection:\n${result.output}")
		val content = dir.generatedFile().readText()
		assertTrue(content.contains("https://prod.example.com"), "wrong URL generated:\n$content")
	}

	@Test fun `camelCase flavor preProd wins longest match over prod`() = withProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				dimension("env") {
					variant("prod")    { field("URL", "https://prod.example.com") }
					variant("preProd") { field("URL", "https://preprod.example.com") }
				}
			}
			tasks.register("assemblePreProdRelease") { dependsOn("generateKonfig") }
		""")
		val result = run(listOf("assemblePreProdRelease"))
		assertTrue(result.output.contains("dim 'env' -> 'preProd'"), "expected preProd selection:\n${result.output}")
	}

	@Test fun `selection is logged even when generateKonfig is up-to-date`() = withProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				field("X", "y")
			}
		""")
		run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		// Second run: generateKonfig is UP-TO-DATE, but konfigInfo must still log.
		val second = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
		assertTrue(second.output.contains("UP-TO-DATE"), "expected cached generate task:\n${second.output}")
		assertTrue(second.output.contains("BUILD_TYPE = release"), "selection must be logged on cached builds:\n${second.output}")
	}

	@Test fun `isDebug and getCurrentDimension are usable in build scripts`() = withProject { dir, run ->
		dir.writeBuildGradle("""
			plugins { id("com.bitsycore.konfig") }
			group = "com.example"
			konfig {
				dimension("env", defaultTo = "prod") {
					variant("prod") { field("X", "y") }
				}
			}
			tasks.register("konfigQuery") {
				dependsOn("generateKonfig")
				val debug = konfig.isDebug
				val env   = konfig.getCurrentDimension("env")
				val missing = konfig.getCurrentDimension("nope")
				doLast {
					println("QUERY isDebug=" + debug)
					println("QUERY env=" + env)
					println("QUERY missing=" + missing)
				}
			}
		""")
		val result = run(listOf("konfigQuery", "-Pkonfig.buildtype=DEBUG"))
		assertTrue(result.output.contains("QUERY isDebug=true"), result.output)
		assertTrue(result.output.contains("QUERY env=prod"), result.output)
		assertTrue(result.output.contains("QUERY missing=null"), result.output)
	}
}
