package com.flowsense.app.analytics

import com.flowsense.app.data.model.Budget
import com.flowsense.app.data.model.Category
import com.flowsense.app.data.model.Transaction
import com.flowsense.app.data.model.TransactionSource
import com.flowsense.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAnalyticsEngineTest {

    // Fixed clock: 2026-06-17 (matches the task date). All test data is relative to this.
    private val now = 1_781_000_000_000L // arbitrary fixed instant
    private val day = 24L * 60 * 60 * 1000
    private val engine = FinancialAnalyticsEngine(now = { now })

    private fun expense(
        amount: Double,
        daysAgo: Long,
        category: String = "Shopping",
        merchant: String = "",
        description: String = "tx"
    ) = Transaction(
        amount = amount,
        description = description,
        category = category,
        type = TransactionType.EXPENSE,
        source = TransactionSource.MANUAL,
        timestamp = now - daysAgo * day,
        merchantName = merchant
    )

    private fun income(amount: Double, daysAgo: Long) = Transaction(
        amount = amount,
        description = "salary",
        category = "Salary",
        type = TransactionType.INCOME,
        source = TransactionSource.MANUAL,
        timestamp = now - daysAgo * day
    )

    @Test
    fun emptyInput_returnsEmptyAnalysis() {
        assertEquals(FinancialAnalysis.EMPTY, engine.analyze(emptyList()))
    }

    @Test
    fun savingsRate_computedOverTrailing30Days() {
        val txns = listOf(
            income(2000.0, 5),
            expense(500.0, 4),
            expense(300.0, 3)
        )
        val analysis = engine.analyze(txns)
        // (2000 - 800) / 2000 = 0.6
        assertEquals(0.6, analysis.savingsRate, 1e-6)
    }

    @Test
    fun anomalyDetection_flagsOutlierWithinCategory() {
        val txns = mutableListOf<Transaction>()
        // A realistic baseline of small coffee purchases (varied, as real data is)...
        val baseline = listOf(4.0, 5.0, 6.0, 5.0, 4.5, 5.5, 6.0, 4.0)
        baseline.forEachIndexed { i, amt ->
            txns.add(expense(amt, (i + 1).toLong(), category = "Coffee", merchant = "Cafe"))
        }
        // ...plus one wildly out-of-line charge.
        txns.add(expense(200.0, 2, category = "Coffee", merchant = "Cafe"))

        val anomalies = engine.analyze(txns).anomalies
        assertTrue(anomalies.isNotEmpty())
        assertEquals(200.0, anomalies.first().amount, 1e-9)
        assertEquals("Coffee", anomalies.first().category)
    }

    @Test
    fun recurringDetection_findsMonthlySubscription() {
        // Charges ~30 days apart from the same merchant => monthly subscription.
        val txns = (0..3).map { expense(15.99, (it * 30 + 1).toLong(), category = "Entertainment", merchant = "Netflix") }
        val recurring = engine.detectRecurringCharges(txns)
        assertTrue(recurring.isNotEmpty())
        val sub = recurring.first()
        assertEquals(Cadence.MONTHLY, sub.cadence)
        assertEquals(4, sub.occurrences)
        assertTrue(sub.estimatedMonthlyCost in 14.0..18.0)
    }

    @Test
    fun recurringDetection_ignoresIrregularSpend() {
        // Random one-off purchases at the same shop, irregular intervals.
        val txns = listOf(
            expense(20.0, 1, merchant = "Corner Shop"),
            expense(45.0, 9, merchant = "Corner Shop"),
            expense(5.0, 40, merchant = "Corner Shop")
        )
        assertTrue(engine.detectRecurringCharges(txns).isEmpty())
    }

    @Test
    fun healthScore_lowSavingsScoresWorseThanHighSavings() {
        val thrifty = listOf(income(3000.0, 5), expense(300.0, 4))
        val spendy = listOf(income(3000.0, 5), expense(2900.0, 4))
        val thriftyScore = engine.analyze(thrifty).health.score
        val spendyScore = engine.analyze(spendy).health.score
        assertTrue("thrifty ($thriftyScore) should beat spendy ($spendyScore)", thriftyScore > spendyScore)
    }

    @Test
    fun budgetAdherence_isScoredWhenBudgetsExist() {
        val cat = Category(name = "Shopping")
        val budget = Budget(categoryId = cat.id, monthlyLimit = 1000.0)
        val txns = listOf(income(3000.0, 2), expense(50.0, 1, category = "Shopping"))
        val health = engine.analyze(txns, budgets = listOf(budget), categories = listOf(cat)).health
        assertTrue(health.components.any { it.name == "Budget adherence" })
    }

    @Test
    fun forecast_hasNonNegativeConfidence() {
        val txns = (1..6).map { expense(100.0, (it * 30).toLong()) }
        val forecast = engine.analyze(txns).forecast
        assertTrue(forecast.confidence in 0.0..1.0)
        assertTrue(forecast.projectedMonthlySpend > 0.0)
    }
}
