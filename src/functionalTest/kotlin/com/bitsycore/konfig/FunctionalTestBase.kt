package com.bitsycore.konfig

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Base class for all functional tests.
 *
 * Provides:
 * - [withProject] - creates a temp Gradle project, runs it, then cleans up.
 * - [File.generatedFile] - walks the output dir to find the generated `.kt` file.
 * - [File.writeBuildGradle] - shorthand for writing a `build.gradle.kts`.
 */
abstract class FunctionalTestBase {

    /**
     * Creates a fresh temp project directory, invokes [block] with it, then deletes it.
     *
     * @param block receives `(projectDir, run)` where `run(args)` executes Gradle and
     *   returns the [BuildResult] (throws on build failure by default).
     */
    protected fun withProject(block: (projectDir: File, run: (List<String>) -> BuildResult) -> Unit) {
        val projectDir = createTempDirectory("konfig-ft").toFile()
        try {
            projectDir.resolve("settings.gradle.kts")
                .writeText("""rootProject.name = "test-project"""")
            val run = { args: List<String> ->
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withPluginClasspath()
                    .withArguments(args)
                    .build()
            }
            block(projectDir, run)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    /**
     * Same as [withProject] but Gradle is invoked with `buildAndFail()` so a build
     * failure does NOT throw - the returned [BuildResult] carries the failed output.
     */
    protected fun withFailingProject(block: (projectDir: File, run: (List<String>) -> BuildResult) -> Unit) {
        val projectDir = createTempDirectory("konfig-ft-fail").toFile()
        try {
            projectDir.resolve("settings.gradle.kts")
                .writeText("""rootProject.name = "test-project"""")
            val run = { args: List<String> ->
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withPluginClasspath()
                    .withArguments(args)
                    .buildAndFail()
            }
            block(projectDir, run)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    /** Finds the single generated `.kt` file inside the project's output tree. */
    protected fun File.generatedFile(): File =
        walkTopDown().first { it.isFile && it.extension == "kt" }

    /** Writes a minimal `build.gradle.kts` applying the konfig plugin. */
    protected fun File.writeBuildGradle(content: String) =
        resolve("build.gradle.kts").writeText(content.trimIndent())
}
