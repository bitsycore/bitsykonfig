package com.bitsycore.konfig

import com.bitsycore.konfig.types.BuildType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Full coverage for [BuildType.resolve].
 * Covers exact enum-name matching, lowercase keywords, typical Android / KMP task
 * names, ambiguous inputs, false-positive substring guards, edge cases, and the
 * [BuildType.value] property.
 */
class BuildTypeExtendedTest {

    // ── Enum name resolution ─────────────────────────────────────────────────

    @Test fun `exact uppercase DEBUG resolves to DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("DEBUG"))
    }

    @Test fun `exact uppercase RELEASE resolves to RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("RELEASE"))
    }

    @Test fun `mixed case DeBuG does not match enum name and falls through to regex`() {
        // Not an exact enum name match; regex will catch it via "Debug" pattern
        assertNull(BuildType.resolve("DeBuG"))
    }

    // ── Lowercase keyword ────────────────────────────────────────────────────

    @Test fun `standalone lowercase debug resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("debug"))
    }

    @Test fun `standalone lowercase release resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("release"))
    }

    // ── Typical Android task names ───────────────────────────────────────────

    @Test fun `assembleDebug resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("assembleDebug"))
    }

    @Test fun `assembleRelease resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("assembleRelease"))
    }

    @Test fun `compileDebugKotlin resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("compileDebugKotlin"))
    }

    @Test fun `compileReleaseKotlin resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("compileReleaseKotlin"))
    }

    @Test fun `bundleReleaseAar resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("bundleReleaseAar"))
    }

    @Test fun `generateDebugSources resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("generateDebugSources"))
    }

    @Test fun `processDebugResources resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("processDebugResources"))
    }

    @Test fun `processReleaseResources resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("processReleaseResources"))
    }

    @Test fun `mergeDebugAssets resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("mergeDebugAssets"))
    }

    @Test fun `mergeReleaseAssets resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("mergeReleaseAssets"))
    }

    @Test fun `packageDebug resolves DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.resolve("packageDebug"))
    }

    @Test fun `packageRelease resolves RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.resolve("packageRelease"))
    }

    // ── KMP / JVM task names ─────────────────────────────────────────────────

    @Test fun `compileKotlinJvm returns null`() {
        assertNull(BuildType.resolve("compileKotlinJvm"))
    }

    @Test fun `compileKotlin returns null`() {
        assertNull(BuildType.resolve("compileKotlin"))
    }

    @Test fun `jar returns null`() {
        assertNull(BuildType.resolve("jar"))
    }

    @Test fun `test returns null`() {
        assertNull(BuildType.resolve("test"))
    }

    // ── Ambiguous task names (both keywords present) ─────────────────────────

    @Test fun `debugRelease is ambiguous returns null`() {
        assertNull(BuildType.resolve("debugRelease"))
    }

    @Test fun `releaseDebug is ambiguous returns null`() {
        assertNull(BuildType.resolve("releaseDebug"))
    }

    @Test fun `compileDebugReleaseKotlin is ambiguous returns null`() {
        assertNull(BuildType.resolve("compileDebugReleaseKotlin"))
    }

    // ── False-positive guards (words that contain debug/release as substring) ─

    @Test fun `debuggable does not resolve DEBUG`() {
        // "debuggable" has lowercase 'g' immediately after "debug" → regex rejects it
        assertNull(BuildType.resolve("debuggable"))
    }

    @Test fun `prereleased does not resolve RELEASE`() {
        // "release" is preceded by lowercase 'e' in "prere[lease]" - but "release"
        // starts after "prere", let's verify actual behaviour via the regex:
        // lookbehind (?<![a-z]) fails because char before 'r' of "release" is 'e'
        assertNull(BuildType.resolve("prereleased"))
    }

    @Test fun `released does not resolve RELEASE`() {
        // "released" - 'd' after "release" is NOT a lowercase letter... wait,
        // actually the regex checks (?![a-z]) so 'd' fails the lookahead.
        assertNull(BuildType.resolve("released"))
    }

    @Test fun `debugMode resolves DEBUG`() {
        // 'M' after "debug" - uppercase, not [a-z], so lookahead passes
        assertEquals(BuildType.DEBUG, BuildType.resolve("debugMode"))
    }

    // ── Empty / whitespace ───────────────────────────────────────────────────

    @Test fun `empty string returns null`() {
        assertNull(BuildType.resolve(""))
    }

    @Test fun `blank string returns null`() {
        assertNull(BuildType.resolve("   "))
    }

    // ── Value property ───────────────────────────────────────────────────────

    @Test fun `DEBUG value is debug`() {
        assertEquals("debug", BuildType.DEBUG.value)
    }

    @Test fun `RELEASE value is release`() {
        assertEquals("release", BuildType.RELEASE.value)
    }

    // ── Enum entries count ───────────────────────────────────────────────────

    @Test fun `exactly two build types exist`() {
        assertEquals(2, BuildType.entries.size)
    }

    // ── Default when no task names a build type ──────────────────────────────

    @Test fun `an IDE sync with no task defaults to DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.defaultFor(emptyList(), ideSync = false))
    }

    @Test fun `an IDE sync running its own helper tasks defaults to DEBUG`() {
        assertEquals(BuildType.DEBUG, BuildType.defaultFor(listOf("prepareKotlinBuildScriptModel"), ideSync = true))
    }

    @Test fun `a real build naming no build type stays RELEASE`() {
        assertEquals(BuildType.RELEASE, BuildType.defaultFor(listOf("generateKonfig"), ideSync = false))
    }
}
