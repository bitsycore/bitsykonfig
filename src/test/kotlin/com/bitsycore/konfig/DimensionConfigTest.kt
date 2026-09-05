package com.bitsycore.konfig

import com.bitsycore.konfig.configs.DimensionConfig
import com.bitsycore.konfig.types.BuildType
import kotlin.test.*

/**
 * Unit tests for [DimensionConfig].
 *
 * Covers:
 * - Construction: dimensionName, objectNameOverride, defaultVariant
 * - objectName() derivation (camelCase, override, separators)
 * - variant() DSL: creation, retrieval, merging
 * - common() block: shared fallback fields
 * - Interaction between common and variant fields (variant wins)
 */
class DimensionConfigTest {

    private fun makeDim(
        name: String,
        objectNameOverride: String? = null,
        defaultTo: String? = null,
    ) = DimensionConfig(name, objectNameOverride, defaultTo)

    // ── Construction ─────────────────────────────────────────────────────────

    @Test fun `dimensionName is stored`() {
        val d = makeDim("env")
        assertEquals("env", d.dimensionName)
    }

    @Test fun `objectNameOverride is stored when provided`() {
        val d = makeDim("env", objectNameOverride = "Environment")
        assertEquals("Environment", d.objectNameOverride)
    }

    @Test fun `objectNameOverride is null when not provided`() {
        val d = makeDim("env")
        assertNull(d.objectNameOverride)
    }

    @Test fun `defaultVariant is stored`() {
        val d = makeDim("env", defaultTo = "prod")
        assertEquals("prod", d.defaultVariant)
    }

    @Test fun `defaultVariant is null when not provided`() {
        val d = makeDim("env")
        assertNull(d.defaultVariant)
    }

    // ── objectName() ─────────────────────────────────────────────────────────

    @Test fun `objectName returns override when set`() {
        val d = makeDim("env", objectNameOverride = "MyEnv")
        assertEquals("MyEnv", d.objectName())
    }

    @Test fun `objectName capitalises simple lowercase name`() {
        val d = makeDim("env")
        assertEquals("Env", d.objectName())
    }

    @Test fun `objectName capitalises each part split by dash`() {
        val d = makeDim("build-env")
        assertEquals("BuildEnv", d.objectName())
    }

    @Test fun `objectName capitalises each part split by underscore`() {
        val d = makeDim("my_env")
        assertEquals("MyEnv", d.objectName())
    }

    @Test fun `objectName capitalises each part split by space`() {
        val d = makeDim("my env")
        assertEquals("MyEnv", d.objectName())
    }

    @Test fun `objectName handles mixed separators`() {
        val d = makeDim("build_my-env")
        assertEquals("BuildMyEnv", d.objectName())
    }

    @Test fun `objectName for single uppercase word keeps first letter uppercase`() {
        val d = makeDim("REGION")
        assertEquals("Region", d.objectName())
    }

    @Test fun `objectName for already camel-case single word`() {
        val d = makeDim("region")
        assertEquals("Region", d.objectName())
    }

    // ── variant() ────────────────────────────────────────────────────────────

    @Test fun `variant creates new VariantConfig`() {
        val d = makeDim("env")
        d.variant("prod") { field("URL", "https://prod.example.com") }
        assertEquals(1, d.variants.size)
        assertNotNull(d.variants["prod"])
    }

    @Test fun `variant stores correct variantName`() {
        val d = makeDim("env")
        d.variant("dev") {}
        assertEquals("dev", d.variants["dev"]!!.variantName)
    }

    @Test fun `multiple variants are stored`() {
        val d = makeDim("env")
        d.variant("prod") {}
        d.variant("dev")  {}
        d.variant("staging") {}
        assertEquals(3, d.variants.size)
    }

    @Test fun `calling variant with same name merges fields`() {
        val d = makeDim("env")
        d.variant("prod") { field("URL", "https://prod.example.com") }
        d.variant("prod") { field("KEY", "secret")                   }
        val v = d.variants["prod"]!!
        // Same VariantConfig instance reused - two fields total
        assertEquals(2, v.fields.size)
    }

    @Test fun `variant fields are accessible via VariantConfig`() {
        val d = makeDim("env")
        d.variant("prod") { field("URL", "https://prod.example.com") }
        val url = d.variants["prod"]!!.fields[0].resolve(BuildType.RELEASE)!!.get()
        assertEquals("https://prod.example.com", url)
    }

    // ── common() ─────────────────────────────────────────────────────────────

    @Test fun `common block adds fields to commonConfig`() {
        val d = makeDim("env")
        d.common { field("TIMEOUT", 30) }
        assertEquals(1, d.commonConfig.fields.size)
        assertEquals("TIMEOUT", d.commonConfig.fields[0].fieldName)
    }

    @Test fun `common block can be called multiple times and merges`() {
        val d = makeDim("env")
        d.common { field("TIMEOUT", 30) }
        d.common { field("RETRIES", 3)  }
        assertEquals(2, d.commonConfig.fields.size)
    }

    @Test fun `common block supports debug scope`() {
        val d = makeDim("env")
        d.common {
            field("TIMEOUT", 30)
            debug { field("TIMEOUT", 5) }
        }
        val fc = d.commonConfig.fields.first { it.fieldName == "TIMEOUT" }
        assertEquals(5,  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals(30, fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `common block supports release scope`() {
        val d = makeDim("env")
        d.common {
            field("LOG", "info")
            release { field("LOG", "error") }
        }
        val fc = d.commonConfig.fields.first { it.fieldName == "LOG" }
        assertEquals("info",  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("error", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Fresh dimension has empty collections ─────────────────────────────────

    @Test fun `fresh DimensionConfig has no variants`() {
        val d = makeDim("env")
        assertTrue(d.variants.isEmpty())
    }

    @Test fun `fresh DimensionConfig commonConfig has no fields`() {
        val d = makeDim("env")
        assertTrue(d.commonConfig.fields.isEmpty())
    }

    // ── Dimension with only common (no variants) ──────────────────────────────

    @Test fun `dimension with only common block and no variants is valid`() {
        val d = makeDim("env")
        d.common { field("BASE_URL", "https://default.example.com") }
        assertEquals(0, d.variants.size)
        assertEquals(1, d.commonConfig.fields.size)
    }

    // ── objectName for dimension used as nested object ────────────────────────

    @Test fun `objectName is consistent on repeated calls`() {
        val d = makeDim("my-env")
        assertEquals(d.objectName(), d.objectName())
    }
}
