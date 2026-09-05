package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome

class FeatureOptionsFunctionalTest : FunctionalTestBase() {
    @Test fun `strict and required selections reject missing and unknown values`() = withProject { dir, run ->
        fun script(setting: String) = """
            plugins { id("com.bitsycore.konfig") }
            konfig {
                $setting
                dimension("env") { variant("prod") { field("URL", "ok") } }
            }
        """
        dir.writeBuildGradle(script("strictResolution = true"))
        assertTrue(runner(dir, listOf("generateKonfig")).buildAndFail().output.contains("selection is required"))
        assertTrue(runner(dir, listOf("generateKonfig", "-Pkonfig.dimension.env=typo")).buildAndFail().output.contains("not a known variant"))
        assertTrue(runner(dir, listOf("generateKonfig", "-Pkonfig.dimension.env=prod", "-Pkonfig.dimension.typo=prod")).buildAndFail().output.contains("unknown dimension"))
        assertTrue(runner(dir, listOf("generateKonfig", "-Pkonfig.buildtype=typo")).buildAndFail().output.contains("unknown build type"))
        val args = listOf("generateKonfig", "-Pkonfig.dimension.env=prod", "--configuration-cache")
        run(args)
        assertTrue(run(args).output.contains("Configuration cache entry reused"))
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=typo")
        assertTrue(runner(dir, listOf("generateKonfig")).buildAndFail().output.contains("not a known variant"))
        run(args) // explicit selection overrides the invalid file value
        dir.resolve("konfig.properties").writeText("")
        dir.writeBuildGradle(script("").replace("dimension(\"env\") {", "dimension(\"env\") { required = true;"))
        assertTrue(runner(dir, listOf("generateKonfig")).buildAndFail().output.contains("selection is required"))
        dir.writeBuildGradle(script(""))
        run(listOf("generateKonfig")) // optional remains the default
    }

    @Test fun `schema validation includes inactive variants common fields and build types`() = withProject { dir, run ->
        val base = """
            plugins { id("com.bitsycore.konfig") }
            konfig {
                validateVariantSchema = true
                dimension("env", defaultTo = "prod") {
                    common { field("SHARED", 1) }
                    variant("prod") { field("URL", "ok") }
                    variant("dev") { REPLACEMENT }
                }
            }
        """
        for (replacement in listOf("", "field(\"URL\", 42)", "debug { field(\"URL\", \"dev\") }")) {
            dir.writeBuildGradle(base.replace("REPLACEMENT", replacement))
            val failed = runner(dir, listOf("generateKonfig")).buildAndFail()
            assertTrue(failed.output.contains("schema"), failed.output)
        }
        dir.writeBuildGradle(base.replace("REPLACEMENT", "field(\"URL\", \"dev\")"))
        run(listOf("generateKonfig", "--configuration-cache"))
        assertTrue(run(listOf("generateKonfig", "--configuration-cache")).output.contains("Configuration cache entry reused"))
        dir.writeBuildGradle(base.replace("REPLACEMENT", "field(\"URL\", providers.gradleProperty(\"devUrl\"))"))
        assertTrue(runner(dir, listOf("generateKonfig")).buildAndFail().output.contains("missing=[URL]"))
        run(listOf("generateKonfig", "-PdevUrl=dev", "--configuration-cache"))
        dir.writeBuildGradle(base.replace("REPLACEMENT", "").replace("validateVariantSchema = true", "validateVariantSchema = false"))
        run(listOf("generateKonfig"))
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig { validateVariantSchema = true; debug { field("ONLY_DEBUG", 1) } }
        """)
        assertTrue(runner(dir, listOf("generateKonfig")).buildAndFail().output.contains("global fields differ"))
    }

    @Test fun `nullable set enum and unsigned values compile and survive configuration caching`() = withProject { dir, run ->
        dir.writeCompilingProject("""
            konfig {
                field<String?>("NULL_TEXT", null)
                field<Int?>("NUMBER", 7).debug(null)
                field<String?>("PROVIDED", providers.provider { "provided" })
                field<String?>("ABSENT", providers.gradleProperty("missing"))
                field("CONST_TEXT", providers.provider { "constant" })
                field("INLINE_FLAG", true)
                field<Set<String?>>("NAMES", setOf("a", null, "a"))
                field("EMPTY", emptySet<Int>())
                field("DAY", java.time.DayOfWeek.MONDAY)
                field("DAYS", setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY))
                field("UB", UByte.MAX_VALUE)
                field("US", UShort.MAX_VALUE)
                field("UI", UInt.MAX_VALUE)
                field("UL", ULong.MAX_VALUE)
                field("UNSIGNED", listOf(0uL, ULong.MAX_VALUE))
                dimension("env", defaultTo = "prod") {
                    common { field<String?>("OPTIONAL", null) }
                    variant("prod") { debug { field<List<Int>?>("ITEMS", null) } }
                }
            }
        """, """
            import com.example.BuildKonfig
            const val copiedConstant: String = BuildKonfig.CONST_TEXT
            fun main() {
                check(copiedConstant == "constant" && BuildKonfig.INLINE_FLAG)
                check(BuildKonfig.NULL_TEXT == null)
                check(BuildKonfig.NUMBER == null)
                check(BuildKonfig.PROVIDED == "provided")
                check(BuildKonfig.NAMES == setOf("a", null))
                check(BuildKonfig.EMPTY.isEmpty())
                check(BuildKonfig.DAY == java.time.DayOfWeek.MONDAY)
                check(BuildKonfig.DAYS.contains(java.time.DayOfWeek.FRIDAY))
                check(BuildKonfig.UB == UByte.MAX_VALUE && BuildKonfig.US == UShort.MAX_VALUE)
                check(BuildKonfig.UI == UInt.MAX_VALUE && BuildKonfig.UL == ULong.MAX_VALUE)
                check(BuildKonfig.UNSIGNED.last() == ULong.MAX_VALUE)
                check(BuildKonfig.Env.OPTIONAL == null && BuildKonfig.Env.ITEMS == null)
            }
        """)
        val args = listOf("verifyGenerated", "-Pkonfig.buildtype=DEBUG", "--configuration-cache")
        run(args)
        assertTrue(!dir.generatedFile().readText().contains("ABSENT"))
        assertTrue(dir.generatedFile().readText().contains("inline val INLINE_FLAG: Boolean get() = true"))
        val reused = run(args)
        assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":generateKonfig")?.outcome)
    }

    @Test fun `boxed arrays and fresh nested arrays preserve declared runtime types`() = withProject { dir, run ->
        dir.writeCompilingProject("""
            konfig {
                specializeArrays = false
                copyArraysOnAccess = true
                field("BOXED", arrayOf(1, 2))
                field("PRIMITIVE", intArrayOf(3, 4))
                field("NESTED", mapOf("items" to listOf(arrayOf(5))))
                field("DYNAMIC", listOf<Any>(intArrayOf(6)))
                field<Array<Int>?>("NULL_ARRAY", null)
            }
        """, """
            import com.example.BuildKonfig
            fun main() {
                val boxed: Array<Int> = BuildKonfig.BOXED
                boxed[0] = 99
                check(BuildKonfig.BOXED[0] == 1)
                val primitive: IntArray = BuildKonfig.PRIMITIVE
                primitive[0] = 99
                check(BuildKonfig.PRIMITIVE[0] == 3)
                BuildKonfig.NESTED.getValue("items")[0][0] = 99
                check(BuildKonfig.NESTED.getValue("items")[0][0] == 5)
                check(BuildKonfig.DYNAMIC[0] is IntArray)
                check(BuildKonfig.NULL_ARRAY == null)
            }
        """)
        run(listOf("verifyGenerated", "--configuration-cache"))
        assertTrue(run(listOf("verifyGenerated", "--configuration-cache")).output.contains("Configuration cache entry reused"))
    }

    @Test fun `array policy changes invalidate generation while defaults preserve shared arrays`() = withProject { dir, run ->
        fun script(settings: String) = """
            plugins { id("com.bitsycore.konfig") }
            konfig {
                $settings
                field("VALUES", arrayOf(1, 2))
            }
        """
        val args = listOf("generateKonfig", "--configuration-cache")
        dir.writeBuildGradle(script(""))
        run(args)
        assertTrue(dir.generatedFile().readText().contains("val VALUES: IntArray = intArrayOf(1, 2)"))
        dir.writeBuildGradle(script("specializeArrays = false; copyArraysOnAccess = true"))
        assertEquals(TaskOutcome.SUCCESS, run(args).task(":generateKonfig")?.outcome)
        assertTrue(dir.generatedFile().readText().contains("val VALUES: Array<Int> get() = arrayOf<Int>(1, 2)"))
        assertEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }
}
