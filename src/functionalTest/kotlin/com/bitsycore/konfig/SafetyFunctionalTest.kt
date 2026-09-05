package com.bitsycore.konfig

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyFunctionalTest : FunctionalTestBase() {
    @Test fun `metadata escaping produces compilable Kotlin and preserves values`() = withProject { dir, run ->
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"module\\\$value\"")
        dir.writeCompilingProject("""
            val selected = "qa\"\\path\n${'$'}{'$'}value"
            konfig {
                dimension("env*/comment", objectNameOverride = "Env", defaultTo = selected) {
                    variant(selected) { field("VALUE", "ok") }
                }
                flatDimension("region", defaultTo = selected) { variant(selected) {} }
            }
        """, """
            import com.example.BuildKonfig as K
            fun main() {
                check(K.MODULE_NAME == "module${'$'}{'$'}value")
                check(K.Env.VARIANT == "qa\"\\path\n${'$'}{'$'}value")
                check(K.REGION_VARIANT == K.Env.VARIANT)
            }
        """)
        run(listOf("verifyGenerated"))
    }

    @Test fun `invalid Kotlin names fail before writing sources`() = withFailingProject { dir, run ->
        val cases = listOf(
            "objectName = \"when\"", "objectName = \"___\"",
            "objectPackage = \"com.123example\"", "objectPackage = \"com.class\"",
            "field(\"when\", 1)", "field(\"_\", 1)",
            "dimension(\"env\", objectNameOverride = \"Invalid-Name\", defaultTo = \"prod\") { variant(\"prod\") {} }",
            "flatDimension(\"123env\", defaultTo = \"prod\") { variant(\"prod\") {} }",
            "dimension(\"env|other\", defaultTo = \"prod\") { variant(\"prod\") {} }",
        )
        for (dsl in cases) {
            dir.writeBuildGradle("""
                plugins { id("com.bitsycore.konfig") }
                konfig { $dsl }
            """)
            val result = run(listOf("generateKonfig"))
            assertTrue(result.output.contains("not valid") || result.output.contains("invalid") || result.output.contains("not a valid"), result.output)
            assertFalse(dir.resolve("build/generated/konfig").walkTopDown().any { it.extension == "kt" })
        }
    }

    @Test fun `all generated scopes reject collisions`() = withFailingProject { dir, run ->
        val cases = listOf(
            "field(\"BUILD_TYPE\", \"custom\")",
            "field(\"MODULE_NAME\", \"custom\")",
            "field(\"IS_DEBUG\", false)",
            "dimension(\"env\", defaultTo = \"prod\") { variant(\"prod\") { field(\"VARIANT\", \"custom\") } }",
            "dimension(\"one\", \"Same\", \"prod\") { variant(\"prod\") {} }; dimension(\"two\", \"Same\", \"prod\") { variant(\"prod\") {} }",
            "dimension(\"env\", defaultTo = \"prod\") { variant(\"prod\") {} }; field(\"Env\", 1)",
            "dimension(\"env\", defaultTo = \"prod\") { variant(\"prod\") {} }; flatDimension(\"flat\", \"prod\") { variant(\"prod\") { field(\"Env\", 1) } }",
        )
        for (dsl in cases) {
            dir.writeBuildGradle("""
                plugins { id("com.bitsycore.konfig") }
                konfig { $dsl }
            """)
            assertTrue(run(listOf("generateKonfig")).output.contains("collides"))
        }
    }

    @Test fun `shared directory neighbors survive regeneration renaming and cache restoration`() = withProject { dir, run ->
        val neighbor = dir.resolve("shared/Keep.kt").apply { parentFile.mkdirs(); writeText("// user source") }
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig {
                objectPackage = "com.example"
                objectName = providers.gradleProperty("name").get()
                outputDir.set(layout.projectDirectory.dir("shared"))
                field("VALUE", 12)
            }
        """)
        val args = listOf("generateKonfig", "--build-cache", "--configuration-cache")
        run(args + "-Pname=Alpha")
        val alpha = dir.resolve("shared/com/example/Alpha.kt")
        val beta = dir.resolve("shared/com/example/Beta.kt")
        assertTrue(alpha.delete())
        assertEquals(TaskOutcome.FROM_CACHE, run(args + "-Pname=Alpha").task(":generateKonfig")?.outcome)
        assertEquals("// user source", neighbor.readText())
        run(args + "-Pname=Beta")
        assertTrue(beta.isFile)
        assertFalse(alpha.exists())
        run(args + "-Pname=Alpha")
        assertTrue(alpha.isFile)
        assertFalse(beta.exists())
        assertEquals("// user source", neighbor.readText())
    }

    @Test fun `validation failure preserves the previous generated source`() = withProject { dir, run ->
        val script = """
            plugins { id("com.bitsycore.konfig") }
            konfig { field("VALUE", 1) }
        """
        dir.writeBuildGradle(script)
        run(listOf("generateKonfig"))
        val generated = dir.generatedFile()
        val before = generated.readText()
        dir.writeBuildGradle(script + """
            konfig { flatDimension("env", "prod") { variant("prod") { field("VALUE", 2) } } }
        """)
        assertTrue(runner(dir, listOf("generateKonfig")).buildAndFail().output.contains("collides"))
        assertEquals(before, generated.readText())
    }

    @Test fun `generation never overwrites an existing user source`() = withFailingProject { dir, run ->
        val source = dir.resolve("shared/com/example/BuildKonfig.kt").apply { parentFile.mkdirs(); writeText("// user source") }
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig { objectPackage = "com.example"; outputDir.set(layout.projectDirectory.dir("shared")) }
        """)
        assertTrue(run(listOf("generateKonfig", "--no-build-cache")).output.contains("refusing to overwrite"))
        assertEquals("// user source", source.readText())
    }

    @Test fun `conflicting shared build types fail but an explicit selection works`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            tasks.register("assembleDebug") { dependsOn("generateKonfig") }
            tasks.register("assembleRelease") { dependsOn("generateKonfig") }
        """)
        val args = listOf("assembleDebug", "assembleRelease")
        assertTrue(runner(dir, args).buildAndFail().output.contains("conflicting debug and release"))
        run(args + "-Pkonfig.buildtype=DEBUG")
        assertTrue(dir.generatedFile().readText().contains("BUILD_TYPE: String = \"debug\""))
    }

    @Test fun `separate prod and preProd requests conflict instead of longest match winning`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            konfig { dimension("env") { variant("prod") {}; variant("preProd") {} } }
            tasks.register("assembleProdRelease") { dependsOn("generateKonfig") }
            tasks.register("assemblePreProdRelease") { dependsOn("generateKonfig") }
        """)
        val args = listOf("assembleProdRelease", "assemblePreProdRelease")
        assertTrue(runner(dir, args).buildAndFail().output.contains("conflicting variants"))
        run(args + "-Pkonfig.dimension.env=prod")
        assertTrue(dir.generatedFile().readText().contains("VARIANT: String = \"prod\""))
    }
}
