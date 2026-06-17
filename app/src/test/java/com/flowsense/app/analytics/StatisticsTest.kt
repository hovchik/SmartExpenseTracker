package com.flowsense.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsTest {

    @Test
    fun median_oddAndEven() {
        assertEquals(3.0, Statistics.median(listOf(1.0, 3.0, 2.0)), 1e-9)
        assertEquals(2.5, Statistics.median(listOf(1.0, 2.0, 3.0, 4.0)), 1e-9)
        assertEquals(0.0, Statistics.median(emptyList()), 1e-9)
    }

    @Test
    fun robustZScore_isResistantToOutliers() {
        val baseline = listOf(10.0, 11.0, 9.0, 10.0, 12.0, 11.0)
        // A value near the median should score low...
        assertTrue(Statistics.robustZScore(11.0, baseline) < 1.0)
        // ...while a 10x outlier should score very high.
        assertTrue(Statistics.robustZScore(120.0, baseline) > 5.0)
    }

    @Test
    fun robustZScore_fallsBackToStdDevWhenMadIsZero() {
        val identical = listOf(5.0, 5.0, 5.0, 5.0)
        // MAD is 0 here; an outlier must still register via the std-dev fallback.
        assertTrue(Statistics.robustZScore(50.0, identical) > 1.0)
    }

    @Test
    fun linearSlope_detectsDirection() {
        assertTrue(Statistics.linearSlope(listOf(1.0, 2.0, 3.0, 4.0)) > 0)
        assertTrue(Statistics.linearSlope(listOf(4.0, 3.0, 2.0, 1.0)) < 0)
        assertEquals(0.0, Statistics.linearSlope(listOf(2.0, 2.0, 2.0)), 1e-9)
    }

    @Test
    fun ewma_weightsRecentMoreHeavily() {
        val rising = listOf(10.0, 10.0, 10.0, 100.0)
        // EWMA should sit well above the old steady-state of 10 because the last
        // point jumped, but below the simple last value.
        val ewma = Statistics.ewma(rising, alpha = 0.5)
        assertTrue(ewma > 10.0 && ewma < 100.0)
    }

    @Test
    fun clamp_bounds() {
        assertEquals(0.0, Statistics.clamp(-5.0, 0.0, 1.0), 1e-9)
        assertEquals(1.0, Statistics.clamp(5.0, 0.0, 1.0), 1e-9)
        assertEquals(0.5, Statistics.clamp(0.5, 0.0, 1.0), 1e-9)
    }
}
