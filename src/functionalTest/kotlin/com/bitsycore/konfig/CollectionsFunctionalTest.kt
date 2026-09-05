package com.bitsycore.konfig

import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionsFunctionalTest : FunctionalTestBase() {
    @Test fun `collections compile and preserve values including empty nullable and nested types`() = withProject { dir, run ->
        dir.writeCompilingProject("""
            konfig {
                field("WORDS", listOf("quote\"", "line\n", "\\path"))
                field("EMPTY_LIST", emptyList<String>())
                field("EMPTY_MAP", emptyMap<String, Int>())
                field("NESTED", mapOf("items" to listOf(1, 2)))
                field("NULLS", listOf<String?>(null, "present"))
                field("MIXED", listOf<Any>("text", 42, true, listOf(1), (-128).toByte(), 7.toShort()))
                field("KEYS", mapOf(1 to "one", 2 to "two"))
                field("STRINGS", arrayOf("a", "b"))
                field("EMPTY_ARRAY", emptyArray<String>())
                field("BOXED", arrayOf(1, 2))
                field("NULL_ARRAY", arrayOf<Int?>(1, null))
                field("ARRAYS", listOf(arrayOf(1, 2)))
                field("MIN", Long.MIN_VALUE)
            }
        """, """
            import com.example.BuildKonfig as K
            fun main() {
                check(K.WORDS == listOf("quote\"", "line\n", "\\path"))
                val emptyList: List<String> = K.EMPTY_LIST
                val emptyMap: Map<String, Int> = K.EMPTY_MAP
                check(emptyList.isEmpty() && emptyMap.isEmpty())
                check(K.NESTED == mapOf("items" to listOf(1, 2)))
                check(K.NULLS == listOf(null, "present"))
                check(K.MIXED == listOf("text", 42, true, listOf(1), (-128).toByte(), 7.toShort()))
                check(K.KEYS == mapOf(1 to "one", 2 to "two"))
                check(K.STRINGS.contentEquals(arrayOf("a", "b")))
                val emptyArray: Array<String> = K.EMPTY_ARRAY
                check(emptyArray.isEmpty())
                val optimized: IntArray = K.BOXED
                check(optimized.contentEquals(intArrayOf(1, 2)))
                check(K.NULL_ARRAY.contentEquals(arrayOf<Int?>(1, null)))
                val nested: List<IntArray> = K.ARRAYS
                check(nested.single().contentEquals(intArrayOf(1, 2)))
                check(K.MIN == Long.MIN_VALUE)
            }
        """)
        assertEquals(TaskOutcome.SUCCESS, run(listOf("verifyGenerated")).task(":verifyGenerated")?.outcome)
    }

    @Test fun `all primitive arrays compile with primitive factories and retain boundary values`() = withProject { dir, run ->
        dir.writeCompilingProject("""
            konfig {
                field("BOOLS", booleanArrayOf(true, false))
                field("BYTES", byteArrayOf(-128, 127))
                field("SHORTS", shortArrayOf(-32768, 32767))
                field("CHARS", charArrayOf('\'', '\\', '\n', '\u0000', '\uD800'))
                field("INTS", intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE))
                field("LONGS", longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE))
                field("FLOATS", floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -0.0f))
                field("DOUBLES", doubleArrayOf(Double.NaN, Double.NEGATIVE_INFINITY, -0.0))
                field("EMPTY", intArrayOf())
            }
        """, """
            import com.example.BuildKonfig as K
            fun main() {
                check(K.BOOLS.contentEquals(booleanArrayOf(true, false)))
                check(K.BYTES.contentEquals(byteArrayOf(-128, 127)))
                check(K.SHORTS.contentEquals(shortArrayOf(-32768, 32767)))
                check(K.CHARS.contentEquals(charArrayOf('\'', '\\', '\n', '\u0000', '\uD800')))
                check(K.INTS.contentEquals(intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE)))
                check(K.LONGS.contentEquals(longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE)))
                check(K.FLOATS.contentEquals(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -0.0f)))
                check(K.DOUBLES.contentEquals(doubleArrayOf(Double.NaN, Double.NEGATIVE_INFINITY, -0.0)))
                check(K.EMPTY.isEmpty())
            }
        """)
        run(listOf("verifyGenerated"))
        val text = dir.resolve("build/generated/konfig/com/example/BuildKonfig.kt").readText()
        listOf("boolean", "byte", "short", "char", "int", "long", "float", "double").forEach {
            assertTrue(text.contains("${it}ArrayOf("), text)
        }
    }

    @Test fun `collections support handles scopes common fields and flat dimensions`() = withProject { dir, run ->
        dir.writeCompilingProject("""
            konfig {
                field("GLOBAL", listOf("release")).debug(listOf("debug"))
                release { field("SCOPED", mapOf("mode" to "release")) }
                debug { field("SCOPED", mapOf("mode" to "debug")) }
                dimension("env", defaultTo = "prod") {
                    common { field("FALLBACK", listOf(1, 2)) }
                    variant("prod") { field("HOSTS", arrayOf("prod")) }
                }
                flatDimension("region", defaultTo = "eu") {
                    common { field("REGIONS", mapOf("id" to 1)) }
                    variant("eu") { field("PORTS", intArrayOf(443)) }
                }
            }
        """, """
            import com.example.BuildKonfig as K
            fun main() {
                val mode = if (K.IS_DEBUG) "debug" else "release"
                check(K.GLOBAL == listOf(mode))
                check(K.SCOPED == mapOf("mode" to mode))
                check(K.Env.FALLBACK == listOf(1, 2))
                check(K.Env.HOSTS.contentEquals(arrayOf("prod")))
                check(K.REGIONS == mapOf("id" to 1))
                check(K.PORTS.contentEquals(intArrayOf(443)))
            }
        """)
        run(listOf("verifyGenerated", "-Pkonfig.buildtype=DEBUG"))
        run(listOf("verifyGenerated", "-Pkonfig.buildtype=RELEASE"))
    }

    @Test fun `provider collections reuse configuration cache and react to changed inputs`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig {
                objectPackage = "com.example"
                field("VALUES", providers.gradleProperty("items").map { it.split(",") })
                field("MISSING", providers.gradleProperty("missing").map { listOf(it) })
                field("PORTS", providers.gradleProperty("port").map { intArrayOf(it.toInt()) })
                field("MAPPING", providers.gradleProperty("items").map { mapOf(it to listOf(1, 2)) })
                field("LITERAL", mapOf("nested" to listOf(arrayOf(1, 2))))
            }
        """)
        val args = listOf("generateKonfig", "--configuration-cache", "-Pitems=a,b", "-Pport=443")
        run(args)
        val second = run(args)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
        val changed = run(args.filterNot { it.startsWith("-Pitems=") } + "-Pitems=c")
        assertEquals(TaskOutcome.SUCCESS, changed.task(":generateKonfig")?.outcome)
        val text = dir.resolve("build/generated/konfig/com/example/BuildKonfig.kt").readText()
        assertTrue(text.contains("listOf<String>(\"c\")"))
        assertFalse(text.contains("MISSING"))
    }

    @Test fun `unsupported collection element type fails with useful error`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig { field("FILES", listOf(java.io.File("example"))) }
        """)
        assertTrue(run(listOf("generateKonfig")).output.contains("unsupported field type"))
    }

    @Test fun `scope override cannot change a collection element type`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig {
                field("VALUES", listOf(1))
                debug { field("VALUES", listOf("one")) }
            }
        """)
        assertTrue(run(listOf("generateKonfig")).output.contains("must use the same type"))
    }

    /** Compile and execute the generated code using the compiler bundled with TestKit's Gradle. */
    private fun File.writeCompilingProject(dsl: String, assertions: String) {
        resolve("Check.kt").writeText(assertions.trimIndent())
        writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig { objectPackage = "com.example" }
            ${dsl.trimIndent()}
            val compilerLib = gradle.gradleHomeDir!!.resolve("lib")
            val stdlib = compilerLib.listFiles()!!.first { it.name.startsWith("kotlin-stdlib-") }
            val generated = layout.buildDirectory.dir("generated/konfig")
            val classes = layout.buildDirectory.dir("verified-classes")
            val compileGenerated = tasks.register<JavaExec>("compileGenerated") {
                dependsOn("generateKonfig")
                classpath = files(fileTree(compilerLib) { include("*.jar") })
                mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                args("-no-stdlib", "-no-reflect", "-classpath", stdlib.absolutePath,
                    "-d", classes.get().asFile.absolutePath,
                    generated.get().asFile.absolutePath, file("Check.kt").absolutePath)
            }
            tasks.register<JavaExec>("verifyGenerated") {
                dependsOn(compileGenerated)
                classpath = files(classes, stdlib)
                mainClass.set("CheckKt")
            }
        """)
    }
}
