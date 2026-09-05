package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Functional tests for duplicate-name detection at configuration time.
 * The plugin must fail fast with a clear error when the same name is
 * declared more than once for dimensions, global fields, or variant fields.
 */
class DuplicateDetectionFunctionalTest : FunctionalTestBase() {

    // ── Duplicate dimension name ───────────────────────────────────────────────

    @Test fun `duplicate dimension name fails configuration with clear error`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") { variant("prod") { field("X", "y") } }
                dimension("env") { variant("dev")  { field("X", "z") } }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("env"))
    }

    // ── Duplicate global field name ───────────────────────────────────────────

    @Test fun `duplicate global field name fails configuration`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("API_URL", "https://prod.example.com")
                field("API_URL", "https://dev.example.com")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("API_URL"))
    }

    @Test fun `duplicate global field of different types fails configuration`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("FLAG", true)
                field("FLAG", "stringNow")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("FLAG"))
    }

    // ── Duplicate variant field name ──────────────────────────────────────────

    @Test fun `duplicate field name within variant fails configuration`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") {
                        field("URL", "https://a.example.com")
                        field("URL", "https://b.example.com")
                    }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(result.output.contains("URL"))
    }

    @Test fun `same field name in different variants is allowed`() = withProject { dir, run ->
        // Different variants may each define the same field name - that is the whole point
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("URL", "https://prod.example.com") }
                    variant("dev")  { field("URL", "https://dev.example.com") }
                }
            }
        """)
        // Should succeed without any exception
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        assertTrue(result.output.contains("generateKonfig"))
    }

    @Test fun `same field name in different dimensions is allowed`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
                dimension("region", defaultTo = "eu") {
                    variant("eu") { field("URL", "https://eu.example.com") }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("generateKonfig"))
    }

    // ── Duplicate dimension name - error message ──────────────────────────────

    @Test fun `duplicate dimension error message contains dimension name`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("my-dim") { variant("a") { field("X", "1") } }
                dimension("my-dim") { variant("b") { field("X", "2") } }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("my-dim"))
    }

    // ── Duplicate global field - error message ────────────────────────────────

    @Test fun `duplicate global field error message contains field name`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("UNIQUE_KEY", "first")
                field("UNIQUE_KEY", "second")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("UNIQUE_KEY"))
    }
}
