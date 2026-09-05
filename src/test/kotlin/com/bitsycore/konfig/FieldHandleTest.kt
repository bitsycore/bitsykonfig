package com.bitsycore.konfig

import com.bitsycore.konfig.configs.FieldConfig
import com.bitsycore.konfig.configs.FieldHandle
import com.bitsycore.konfig.configs.constantProvider
import com.bitsycore.konfig.types.BuildType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [FieldHandle].
 *
 * [FieldHandle] is a thin wrapper that delegates directly to the underlying
 * [FieldConfig].  These tests verify that every overload correctly mutates the
 * backing [FieldConfig] and that the resolver sees those changes.
 */
class FieldHandleTest {

    private fun stringHandle(default: String = "default"): Pair<FieldHandle<String>, FieldConfig<String>> {
        val fc = FieldConfig("F", String::class.java, constantProvider(default))
        return FieldHandle(fc) to fc
    }

    private fun intHandle(default: Int = 0): Pair<FieldHandle<Int>, FieldConfig<Int>> {
        val fc = FieldConfig("N", Int::class.javaObjectType, constantProvider(default))
        return FieldHandle(fc) to fc
    }

    // ── debug(literal) ───────────────────────────────────────────────────────

    @Test fun `debug literal sets DEBUG override`() {
        val (handle, fc) = stringHandle()
        handle.debug("devValue")
        assertEquals("devValue", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `debug literal does not touch RELEASE`() {
        val (handle, fc) = stringHandle("default")
        handle.debug("devValue")
        assertEquals("default", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `debug literal on Int handle sets DEBUG override`() {
        val (handle, fc) = intHandle(30)
        handle.debug(5)
        assertEquals(5,  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals(30, fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── release(literal) ─────────────────────────────────────────────────────

    @Test fun `release literal sets RELEASE override`() {
        val (handle, fc) = stringHandle()
        handle.release("prodValue")
        assertEquals("prodValue", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `release literal does not touch DEBUG`() {
        val (handle, fc) = stringHandle("default")
        handle.release("prodValue")
        assertEquals("default", fc.resolve(BuildType.DEBUG)!!.get())
    }

    // ── debug(provider) ──────────────────────────────────────────────────────

    @Test fun `debug provider sets DEBUG override`() {
        val (handle, fc) = stringHandle()
        handle.debug(constantProvider("fromProvider"))
        assertEquals("fromProvider", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `debug provider does not touch RELEASE`() {
        val (handle, fc) = stringHandle("default")
        handle.debug(constantProvider("fromProvider"))
        assertEquals("default", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── release(provider) ────────────────────────────────────────────────────

    @Test fun `release provider sets RELEASE override`() {
        val (handle, fc) = stringHandle()
        handle.release(constantProvider("relProv"))
        assertEquals("relProv", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `release provider does not touch DEBUG`() {
        val (handle, fc) = stringHandle("default")
        handle.release(constantProvider("relProv"))
        assertEquals("default", fc.resolve(BuildType.DEBUG)!!.get())
    }

    // ── Both overrides set ───────────────────────────────────────────────────

    @Test fun `debug and release both set resolve independently`() {
        val (handle, fc) = stringHandle("default")
        handle.debug("d")
        handle.release("r")
        assertEquals("d", fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("r", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Handle wraps correct FieldConfig ─────────────────────────────────────

    @Test fun `handle field property points to the same FieldConfig`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("x"))
        val handle = FieldHandle(fc)
        // Mutation through handle should be visible via fc
        handle.debug("changed")
        assertEquals("changed", fc.resolve(BuildType.DEBUG)!!.get())
    }

    // ── No-override scenarios ────────────────────────────────────────────────

    @Test fun `handle with no debug or release set returns null for scope-only field`() {
        val fc = FieldConfig<String>("F", String::class.java, null)
        @Suppress("UNUSED_VARIABLE") val handle = FieldHandle(fc)
        // No overrides set - resolve returns null
        assertNull(fc.resolve(BuildType.DEBUG))
        assertNull(fc.resolve(BuildType.RELEASE))
    }

    // ── Override replacement ─────────────────────────────────────────────────

    @Test fun `second debug call replaces first override`() {
        val (handle, fc) = stringHandle()
        handle.debug("first")
        handle.debug("second")
        assertEquals("second", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `second release call replaces first override`() {
        val (handle, fc) = stringHandle()
        handle.release("first")
        handle.release("second")
        assertEquals("second", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Provider and literal mixed ────────────────────────────────────────────

    @Test fun `literal debug then provider debug - provider wins`() {
        val (handle, fc) = stringHandle()
        handle.debug("literal")
        handle.debug(constantProvider("provider"))
        assertEquals("provider", fc.resolve(BuildType.DEBUG)!!.get())
    }
}
