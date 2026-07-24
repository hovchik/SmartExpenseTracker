package com.flowsense.app.service.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the categorization behaviour that used to funnel most transactions into
 * "Shopping". Regression coverage for the fix in [AiExpenseEngine.categorize]:
 * script and corporate-suffix heuristics no longer force "Shopping", and truly
 * unknown merchants fall through to "Other".
 */
class AiExpenseEngineCategorizeTest {

    private val engine = AiExpenseEngine()

    @Test
    fun `known merchant still maps to its category`() {
        assertEquals("Entertainment", engine.categorize("NETFLIX.COM"))
        assertEquals("Shopping", engine.categorize("Amazon Marketplace"))
    }

    @Test
    fun `keyword heuristics still classify obvious cases`() {
        assertEquals("Shopping", engine.categorize("Some Local Store 42"))
        assertEquals("Bills & Utilities", engine.categorize("Monthly service fee"))
    }

    @Test
    fun `unknown Armenian-script merchant is not forced into Shopping`() {
        // Previously any Armenian character forced "Shopping"; now it's uncategorised.
        assertEquals("Other", engine.categorize("Կաթսայատուն 7"))
    }

    @Test
    fun `unknown company with corporate suffix is not forced into Shopping`() {
        assertEquals("Other", engine.categorize("Acme Widgets LLC"))
        assertEquals("Other", engine.categorize("Zeta Holdings OJSC"))
    }

    @Test
    fun `completely unknown merchant falls through to Other`() {
        assertEquals("Other", engine.categorize("Zxqv 9931"))
    }
}
