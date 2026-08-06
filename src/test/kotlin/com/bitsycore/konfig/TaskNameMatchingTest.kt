package com.bitsycore.konfig

import com.bitsycore.konfig.types.containsWordCamelCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for camelCase-boundary word matching used by dimension variant detection.
 * The critical property: variants that are substrings of other variants
 * (prod / preprod) must never cross-match.
 */
class TaskNameMatchingTest {

	// ── Prod vs Preprod differentiation ─────────────────────────────────────────

	@Test fun `preprod task does not match prod variant`() {
		assertFalse("assemblePreprodRelease".containsWordCamelCase("prod"))
		assertFalse(":app:assemblePreprodDebug".containsWordCamelCase("prod"))
	}

	@Test fun `preprod task matches preprod variant`() {
		assertTrue("assemblePreprodRelease".containsWordCamelCase("preprod"))
		assertTrue(":app:assemblePreprodDebug".containsWordCamelCase("preprod"))
	}

	@Test fun `prod task matches prod variant only`() {
		assertTrue("assembleProdRelease".containsWordCamelCase("prod"))
		assertFalse("assembleProdRelease".containsWordCamelCase("preprod"))
	}

	// ── CamelCase variant names ─────────────────────────────────────────────────

	@Test fun `camelCase variant matches its camelCase segment`() {
		assertTrue("assemblePreProdRelease".containsWordCamelCase("preProd"))
		// "Prod" is a legitimate camelCase segment inside PreProd — the resolver's
		// longest-match rule (tested functionally) disambiguates this case.
		assertTrue("assemblePreProdRelease".containsWordCamelCase("prod"))
	}

	@Test fun `prefix variant does not match longer word`() {
		assertFalse("assembleDevelopRelease".containsWordCamelCase("dev"))
		assertTrue("assembleDevRelease".containsWordCamelCase("dev"))
	}

	// ── Boundaries ──────────────────────────────────────────────────────────────

	@Test fun `matches at string start and end`() {
		assertTrue("prodRelease".containsWordCamelCase("prod"))
		assertTrue("assembleProd".containsWordCamelCase("prod"))
		assertTrue("prod".containsWordCamelCase("prod"))
	}

	@Test fun `matches with non-letter separators`() {
		assertTrue("app:prod:assemble".containsWordCamelCase("prod"))
		assertTrue("assemble-prod-release".containsWordCamelCase("prod"))
	}

	@Test fun `empty word never matches`() {
		assertFalse("assembleProdRelease".containsWordCamelCase(""))
	}
}
