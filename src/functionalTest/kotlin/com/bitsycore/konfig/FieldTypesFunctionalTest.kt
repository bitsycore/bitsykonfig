package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional tests for every supported field type emitted by the plugin:
 * String, Boolean, Int, Long, Float, Double - including correct Kotlin
 * literal syntax (suffixes, special Float/Double values, escaping).
 */
class FieldTypesFunctionalTest : FunctionalTestBase() {

    // ── Long ─────────────────────────────────────────────────────────────────

    @Test fun `Long field emits L suffix`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("BIG", 9_000_000_000L)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val BIG: Long = 9000000000L"))
    }

    @Test fun `Long zero emits L suffix`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("Z", 0L) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val Z: Long = 0L"))
    }

    @Test fun `Long max value emits correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("MAX", Long.MAX_VALUE) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val MAX: Long = 9223372036854775807L"))
    }

    // ── Float ─────────────────────────────────────────────────────────────────

    @Test fun `Float field emits f suffix`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("RATIO", 1.5f) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("const val RATIO: Float = "))
        assertTrue(text.contains("f"))
    }

    @Test fun `Float NaN emits Float_NaN`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("NAN", Float.NaN) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val NAN: Float = Float.NaN"))
    }

    @Test fun `Float POSITIVE_INFINITY emits Float_POSITIVE_INFINITY`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("INF", Float.POSITIVE_INFINITY) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("const val INF: Float = Float.POSITIVE_INFINITY"))
    }

    @Test fun `Float NEGATIVE_INFINITY emits Float_NEGATIVE_INFINITY`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("NINF", Float.NEGATIVE_INFINITY) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("const val NINF: Float = Float.NEGATIVE_INFINITY"))
    }

    // ── Double ────────────────────────────────────────────────────────────────

    @Test fun `Double field emits no suffix`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("PI", 3.14159) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("const val PI: Double = 3.14159"))
        assertFalse(text.contains("3.14159f"))
    }

    @Test fun `Double NaN emits Double_NaN`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("NAN", Double.NaN) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val NAN: Double = Double.NaN"))
    }

    @Test fun `Double POSITIVE_INFINITY emits Double_POSITIVE_INFINITY`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("INF", Double.POSITIVE_INFINITY) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("const val INF: Double = Double.POSITIVE_INFINITY"))
    }

    @Test fun `Double NEGATIVE_INFINITY emits Double_NEGATIVE_INFINITY`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("NINF", Double.NEGATIVE_INFINITY) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("const val NINF: Double = Double.NEGATIVE_INFINITY"))
    }

    // ── Boolean (DEBUG inline val vs RELEASE const val) ───────────────────────

    @Test fun `Boolean true in RELEASE is const val`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("B", true) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val B: Boolean = true"))
    }

    @Test fun `Boolean false in DEBUG is inline val getter`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("B", false) }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText().contains("inline val B: Boolean get() = false"))
    }

    // ── All types together ────────────────────────────────────────────────────

    @Test fun `all field types appear correctly in a single object`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("STR",    "hello")
                field("BOOL",   true)
                field("INT",    42)
                field("LONG",   99L)
                field("FLOAT",  2.5f)
                field("DOUBLE", 1.23)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val STR: String = "hello""""))
        assertTrue(text.contains("const val BOOL: Boolean = true"))
        assertTrue(text.contains("const val INT: Int = 42"))
        assertTrue(text.contains("const val LONG: Long = 99L"))
        assertTrue(text.contains("const val FLOAT: Float = ") && text.contains("f"))
        assertTrue(text.contains("const val DOUBLE: Double = 1.23"))
    }

    // ── Types in dimension fields ─────────────────────────────────────────────

    @Test fun `Long field inside dimension variant is emitted correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("TOKEN_TTL", 3_600_000L) }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val TOKEN_TTL: Long = 3600000L"))
    }

    @Test fun `Double field inside dimension variant is emitted correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("THRESHOLD", 0.95) }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val THRESHOLD: Double = 0.95"))
    }
}
