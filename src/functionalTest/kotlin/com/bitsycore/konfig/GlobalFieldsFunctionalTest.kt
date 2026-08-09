package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional tests for top-level (global) field declarations:
 * String, Boolean, Int - defaults, debug overrides, release overrides,
 * and the debug/release scope block syntax.
 */
class GlobalFieldsFunctionalTest : FunctionalTestBase() {

    // ── String fields ────────────────────────────────────────────────────────

    @Test fun `string field is generated with correct const val`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("API_URL", "https://api.example.com")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val API_URL: String = "https://api.example.com""""))
    }

    @Test fun `string field with quotes is escaped correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("APP_NAME", "My \"App\"")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val APP_NAME: String = "My \"App\"""""))
    }

    @Test fun `string field with backslash is escaped correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("PATH", "C:\\Users\\dev")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("const val PATH: String = "))
        assertTrue(text.contains("\\\\"))
    }

    @Test fun `string field with dollar sign is escaped correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("TEMPLATE", "price: ${'$'}99")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        // Dollar in string literals must be escaped as \$
        assertTrue(text.contains("""\${'$'}""") || text.contains("""\$"""))
    }

    @Test fun `empty string field is generated`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("EMPTY", "")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("""const val EMPTY: String = """""))
    }

    // ── Boolean fields ────────────────────────────────────────────────────────

    @Test fun `boolean false field emits const val in RELEASE`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("LOGGING", false)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val LOGGING: Boolean = false"))
    }

    @Test fun `boolean true field emits const val in RELEASE`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("FLAG", true)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val FLAG: Boolean = true"))
    }

    @Test fun `boolean field emits inline val getter in DEBUG`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("LOGGING", false)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText().contains("inline val LOGGING: Boolean get() = false"))
    }

    // ── Int fields ────────────────────────────────────────────────────────────

    @Test fun `int field is generated with correct const val`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("TIMEOUT", 30)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val TIMEOUT: Int = 30"))
    }

    @Test fun `zero int field is generated`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("ZERO", 0)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val ZERO: Int = 0"))
    }

    @Test fun `negative int field is generated`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("NEG", -1)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val NEG: Int = -1"))
    }

    // ── Debug overrides ───────────────────────────────────────────────────────

    @Test fun `string debug override is applied for DEBUG build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("BASE_URL", "https://prod.example.com").debug("https://dev.example.com")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val BASE_URL: String = "https://dev.example.com""""))
    }

    @Test fun `string debug override is NOT applied for RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("BASE_URL", "https://prod.example.com").debug("https://dev.example.com")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val BASE_URL: String = "https://prod.example.com""""))
        assertFalse(text.contains("dev.example.com"))
    }

    @Test fun `boolean debug override emits inline val getter in DEBUG`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("LOGGING", false).debug(true)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText().contains("inline val LOGGING: Boolean get() = true"))
    }

    @Test fun `int debug override is applied for DEBUG build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("TIMEOUT", 30).debug(5)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText().contains("const val TIMEOUT: Int = 5"))
    }

    // ── Release overrides ─────────────────────────────────────────────────────

    @Test fun `string release override is applied for RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("URL", "https://default.example.com").release("https://prod.example.com")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val URL: String = "https://prod.example.com""""))
    }

    @Test fun `release override is NOT applied for DEBUG build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("URL", "https://default.example.com").release("https://prod.example.com")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val URL: String = "https://default.example.com""""))
    }

    // ── debug {} / release {} block syntax ───────────────────────────────────

    @Test fun `debug scope block sets field for DEBUG build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                debug {
                    field("LOG_LEVEL", "verbose")
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val LOG_LEVEL: String = "verbose""""))
    }

    @Test fun `debug scope block field is absent for RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                debug {
                    field("DEBUG_ONLY", "yes")
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertFalse(dir.generatedFile().readText().contains("DEBUG_ONLY"))
    }

    @Test fun `release scope block sets field for RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                release {
                    field("LOG_LEVEL", "error")
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val LOG_LEVEL: String = "error""""))
    }

    @Test fun `multiple global fields all appear in output`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("A", "alpha")
                field("B", 42)
                field("C", true)
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val A: String = "alpha""""))
        assertTrue(text.contains("const val B: Int = 42"))
        assertTrue(text.contains("const val C: Boolean = true"))
    }
}
