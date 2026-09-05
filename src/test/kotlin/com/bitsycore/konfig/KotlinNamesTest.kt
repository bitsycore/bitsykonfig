package com.bitsycore.konfig

import com.bitsycore.konfig.types.isValidKotlinIdentifier
import com.bitsycore.konfig.types.BuildType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinNamesTest {
    @Test fun `hard keywords numeric starts and underscore-only identifiers are invalid`() {
        listOf("class", "when", "true", "typeof", "_", "___", "1bad", "", "a-b").forEach {
            assertFalse(it.isValidKotlinIdentifier(), it)
        }
        listOf("_valid", "Env", "VALUE", "value2", "café").forEach { assertTrue(it.isValidKotlinIdentifier(), it) }
    }

    @Test fun `project paths do not select build types`() {
        assertNull(BuildType.resolveTasks(listOf(":debug:compileKotlin", ":release:compileKotlin")))
    }
}
