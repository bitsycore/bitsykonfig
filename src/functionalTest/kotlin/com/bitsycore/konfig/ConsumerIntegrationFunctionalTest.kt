package com.bitsycore.konfig

import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConsumerIntegrationFunctionalTest : FunctionalTestBase() {
    private val kotlinVersion = System.getProperty("konfig.test.kotlinVersion", "2.3.0")
    private val androidVersion = System.getProperty("konfig.test.androidVersion", "9.3.2")

    @Test fun `JVM compilation and source publication carry generation dependencies`() = withProject { dir, _ ->
        val run = consumerRunner(dir)
        dir.consumerSettings()
        dir.resolve("src/main/kotlin/Use.kt").apply { parentFile.mkdirs(); writeText("val value = com.example.BuildKonfig.VALUE") }
        dir.writeConsumerBuildGradle("""
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("com.bitsycore.konfig")
            }
            repositories { mavenCentral() }
            java { withSourcesJar() }
            konfig { objectPackage = "com.example"; field("VALUE", listOf("ok")) }
        """)
        val args = listOf("compileKotlin", "sourcesJar", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, run(args).task(":generateKonfig")?.outcome)
        val second = run(args)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
        ZipFile(dir.resolve("build/libs/test-project-sources.jar")).use { jar ->
            assertNotNull(jar.getEntry("com/example/BuildKonfig.kt"))
        }
        run(listOf("clean"))
        assertNotNull(run(listOf("sourcesJar")).task(":generateKonfig"))
    }

    @Test fun `KMP commonMain compilation and source publication carry generation dependencies`() = withProject { dir, _ ->
        val run = consumerRunner(dir)
        dir.consumerSettings()
        dir.resolve("src/commonMain/kotlin/Use.kt").apply { parentFile.mkdirs(); writeText("val value = com.example.BuildKonfig.VALUE") }
        dir.writeConsumerBuildGradle("""
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("com.bitsycore.konfig")
            }
            repositories { mavenCentral() }
            kotlin { jvm() }
            konfig { objectPackage = "com.example"; field("VALUE", 42) }
        """)
        val args = listOf("compileKotlinJvm", "allMetadataJar", "jvmSourcesJar", "--configuration-cache")
        assertNotNull(run(args).task(":generateKonfig"))
        assertTrue(run(args).output.contains("Configuration cache entry reused"))
    }

    @Test fun `Android variants compile independently and wire lint and sources from a clean build`() = withProject { dir, _ ->
        val run = consumerRunner(dir, android = true)
        val sdk = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"),
            "${System.getProperty("user.home")}/AppData/Local/Android/Sdk").filterNotNull().map(::File)
            .firstOrNull { it.resolve("platforms/android-35/android.jar").isFile }
        assumeTrue("Android SDK platform 35 is required for Android integration tests", sdk != null)
        dir.consumerSettings()
        dir.resolve("local.properties").writeText("sdk.dir=${sdk!!.invariantSeparatorsPath}")
        dir.resolve("src/main/AndroidManifest.xml").apply { parentFile.mkdirs(); writeText("<manifest />") }
        dir.resolve("src/main/kotlin/Use.kt").apply { parentFile.mkdirs(); writeText("val value = com.example.BuildKonfig.Env.URL") }
        val externalKotlin = if (androidVersion.startsWith("8.")) "kotlin(\"android\") version \"$kotlinVersion\"" else ""
        dir.writeConsumerBuildGradle("""
            plugins {
                id("com.android.library") version "$androidVersion"
                $externalKotlin
                id("com.bitsycore.konfig")
            }
            repositories { google(); mavenCentral() }
            android {
                namespace = "com.example"
                compileSdk = 35
                defaultConfig { minSdk = 21 }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                flavorDimensions += "environment"
                productFlavors {
                    create("prod") { dimension = "environment" }
                    create("preProd") { dimension = "environment" }
                }
                publishing { singleVariant("prodRelease") { withSourcesJar() } }
            }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
            konfig {
                objectPackage = "com.example"
                dimension("env") {
                    androidDimension = "environment"
                    required = true
                    variant("prod") { field("URL", "production") }
                    variant("preProd") { field("URL", "preproduction") }
                }
            }
        """)
        val args = listOf("assembleProdDebug", "assemblePreProdRelease", "--configuration-cache")
        val first = run(args)
        assertNotNull(first.task(":generateProdDebugKonfig"))
        assertNotNull(first.task(":generatePreProdReleaseKonfig"))
        assertEquals(null, first.task(":generateKonfig"))
        dir.assertAndroidConstants("prod-debug", "debug", "prod", "production")
        dir.assertAndroidConstants("preProd-release", "release", "preProd", "preproduction")
        assertTrue(run(args).output.contains("Configuration cache entry reused"))
        run(listOf("clean"))
        val analysis = run(listOf("lintProdDebug", "sourceProdReleaseJar"))
        assertNotNull(analysis.task(":generateProdDebugKonfig"))
        assertNotNull(analysis.task(":generateProdReleaseKonfig"))
        val sourceJars = dir.resolve("build").walkTopDown().filter { it.isFile && it.name.endsWith("sources.jar") }.toList()
        assertTrue(sourceJars.any { file -> ZipFile(file).use { it.getEntry("com/example/BuildKonfig.kt") != null } })
        val aggregate = run(listOf("generateKonfig", "assembleProdDebug", "assemblePreProdRelease", "--configuration-cache"))
        listOf("ProdDebug", "ProdRelease", "PreProdDebug", "PreProdRelease").forEach {
            assertNotNull(aggregate.task(":generate${it}Konfig"))
        }
        val explicitArgs = listOf("generateProdDebugKonfig", "-Pkonfig.dimension.env=preProd", "--configuration-cache")
        run(explicitArgs)
        assertTrue(run(explicitArgs).output.contains("Configuration cache entry reused"))
        assertTrue(dir.resolve("build/generated/konfig/prodDebug/com/example/BuildKonfig.kt").readText()
            .contains("const val VARIANT: String = \"preProd\""))
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=prod")
        run(listOf("generatePreProdReleaseKonfig"))
        assertTrue(dir.resolve("build/generated/konfig/preProdRelease/com/example/BuildKonfig.kt").readText()
            .contains("const val VARIANT: String = \"prod\""))
    }

    private fun File.consumerSettings() {
        writePluginRepository()
        resolve("settings.gradle.kts").writeText("""
            pluginManagement { repositories { maven { url = uri("repo") }; google(); mavenCentral(); gradlePluginPortal() } }
            rootProject.name = "test-project"
        """.trimIndent())
    }

    // Resolve a real plugin artifact instead of TestKit's isolated injected classloader.
    private fun consumerRunner(dir: File, android: Boolean = false): (List<String>) -> org.gradle.testkit.runner.BuildResult = { args ->
        val runner = GradleRunner.create().withProjectDir(dir).withArguments(args + "--stacktrace")
        if (android && !androidVersion.startsWith("8.")) runner.withGradleVersion("9.5.0")
        runner.build()
    }

    private fun File.writeConsumerBuildGradle(script: String) {
        writeBuildGradle(script.replace("id(\"com.bitsycore.konfig\")", "id(\"com.bitsycore.konfig\") version \"0.0-test\""))
    }

    private fun File.writePluginRepository() {
        val metadata = Properties().apply {
            this@ConsumerIntegrationFunctionalTest.javaClass.classLoader
                .getResourceAsStream("plugin-under-test-metadata.properties")!!.use { load(it) }
        }
        val artifact = resolve("repo/com/bitsycore/test-plugin/0.0-test").apply { mkdirs() }
        JarOutputStream(artifact.resolve("test-plugin-0.0-test.jar").outputStream()).use { jar ->
            metadata.getProperty("implementation-classpath").split(File.pathSeparator).map(::File).filter { it.isDirectory }.forEach { root ->
                root.walkTopDown().filter { it.isFile }.forEach { file ->
                    jar.putNextEntry(JarEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }
        }
        val pomPrefix = "<project><modelVersion>4.0.0</modelVersion>"
        artifact.resolve("test-plugin-0.0-test.pom").writeText(
            "$pomPrefix<groupId>com.bitsycore</groupId><artifactId>test-plugin</artifactId><version>0.0-test</version></project>")
        val marker = resolve("repo/com/bitsycore/konfig/com.bitsycore.konfig.gradle.plugin/0.0-test").apply { mkdirs() }
        marker.resolve("com.bitsycore.konfig.gradle.plugin-0.0-test.pom").writeText("""
            $pomPrefix<groupId>com.bitsycore.konfig</groupId><artifactId>com.bitsycore.konfig.gradle.plugin</artifactId>
            <version>0.0-test</version><packaging>pom</packaging><dependencies><dependency>
            <groupId>com.bitsycore</groupId><artifactId>test-plugin</artifactId><version>0.0-test</version>
            </dependency></dependencies></project>
        """.trimIndent())
    }

    private fun File.assertAndroidConstants(variant: String, buildType: String, flavor: String, url: String) {
        val aar = resolve("build/outputs/aar/test-project-$variant.aar")
        assertTrue(aar.isFile, aar.path)
        val classes = resolve("$variant-classes.jar")
        ZipFile(aar).use { archive -> classes.writeBytes(archive.getInputStream(archive.getEntry("classes.jar")).readBytes()) }
        URLClassLoader(arrayOf(classes.toURI().toURL()), javaClass.classLoader).use { loader ->
            val root = loader.loadClass("com.example.BuildKonfig")
            val env = loader.loadClass("com.example.BuildKonfig\$Env")
            assertEquals(buildType, root.getField("BUILD_TYPE").get(null))
            assertEquals(flavor, env.getField("VARIANT").get(null))
            assertEquals(url, env.getField("URL").get(null))
        }
    }
}
