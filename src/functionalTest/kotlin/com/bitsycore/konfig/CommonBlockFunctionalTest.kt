package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional tests for the `common {}` block inside a dimension.
 *
 * The common block provides fallback fields for all variants - a variant field
 * with the same name must take precedence over the common field.
 */
class CommonBlockFunctionalTest : FunctionalTestBase() {

    // ── Common field is present when variant has no override ──────────────────

    @Test fun `common field appears when active variant does not define it`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common { field("TIMEOUT", 30) }
                    variant("dev")  { field("URL", "https://dev.example.com") }
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("const val TIMEOUT: Int = 30"))
        assertTrue(text.contains("""const val URL: String = "https://dev.example.com""""))
    }

    @Test fun `common field appears for both variants`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common { field("RETRIES", 3) }
                    variant("dev")  { field("URL", "https://dev.example.com") }
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
            }
        """)
        // dev variant
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        assertTrue(dir.generatedFile().readText().contains("const val RETRIES: Int = 3"))

        // prod variant
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(dir.generatedFile().readText().contains("const val RETRIES: Int = 3"))
    }

    // ── Variant field overrides common field ─────────────────────────────────

    @Test fun `variant field overrides common field with same name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common  { field("TIMEOUT", 30) }
                    variant("dev")  { field("TIMEOUT", 5) }
                    variant("prod") { /* uses common TIMEOUT */ }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        assertTrue(dir.generatedFile().readText().contains("const val TIMEOUT: Int = 5"))

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(dir.generatedFile().readText().contains("const val TIMEOUT: Int = 30"))
    }

    // ── Common block supports build-type scopes ───────────────────────────────

    @Test fun `common debug scope applies for DEBUG build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common {
                        field("TIMEOUT", 30)
                        debug { field("TIMEOUT", 2) }
                    }
                    variant("dev") { field("URL", "https://dev.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG", "-Pkonfig.dimension.env=dev"))
        assertTrue(dir.generatedFile().readText().contains("const val TIMEOUT: Int = 2"))
    }

    @Test fun `common debug scope does not affect RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common {
                        field("TIMEOUT", 30)
                        debug { field("TIMEOUT", 2) }
                    }
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(dir.generatedFile().readText().contains("const val TIMEOUT: Int = 30"))
    }

    @Test fun `common release scope applies for RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common {
                        field("LOG", "info")
                        release { field("LOG", "error") }
                    }
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(dir.generatedFile().readText().contains("""const val LOG: String = "error""""))
    }

    // ── Common-only dimension (no variant-specific fields) ────────────────────

    @Test fun `dimension with only common fields and empty variant generates common fields`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common {
                        field("BASE", "https://base.example.com")
                        field("TIMEOUT", 10)
                    }
                    variant("any") { /* intentionally empty */ }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=any"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val BASE: String = "https://base.example.com""""))
        assertTrue(text.contains("const val TIMEOUT: Int = 10"))
    }

    // ── Common fields from multiple common{} calls ────────────────────────────

    @Test fun `multiple common blocks merge their fields`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common { field("A", "first") }
                    common { field("B", "second") }
                    variant("x") { /* empty */ }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=x"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val A: String = "first""""))
        assertTrue(text.contains("""const val B: String = "second""""))
    }

    // ── Common does not bleed into unrelated dimension ────────────────────────

    @Test fun `common fields in one dimension do not appear in another dimension`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    common { field("SHARED", "env-shared") }
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
                dimension("region", defaultTo = "eu") {
                    variant("eu") { field("CDN", "https://eu-cdn.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        val text = dir.generatedFile().readText()
        // SHARED should be under Env object
        val envBlock   = text.substringAfter("object Env")
        val regionBlock = if (text.contains("object Region")) text.substringAfter("object Region") else ""
        assertTrue(envBlock.contains("SHARED"))
        assertFalse(regionBlock.contains("SHARED"))
    }
}
