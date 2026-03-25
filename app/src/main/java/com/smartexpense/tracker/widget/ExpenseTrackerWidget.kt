package com.smartexpense.tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.smartexpense.tracker.MainActivity
import com.smartexpense.tracker.util.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Glance-based home screen widget showing monthly expense summary,
 * today's spending, top categories, and budget alerts.
 */
class ExpenseTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadWidgetData(context) }
        provideContent {
            WidgetContent(data)
        }
    }
}

// ── Colors ──────────────────────────────────────────────────────────────

private val GreenPrimary = android.graphics.Color.parseColor("#10B981")
private val GreenDark = android.graphics.Color.parseColor("#059669")
private val RedExpense = android.graphics.Color.parseColor("#EF4444")
private val OrangeWarning = android.graphics.Color.parseColor("#F59E0B")
private val DarkBg = android.graphics.Color.parseColor("#0F172A")
private val DarkCard = android.graphics.Color.parseColor("#1E293B")
private val LightBg = android.graphics.Color.parseColor("#F8FAFC")
private val LightCard = android.graphics.Color.parseColor("#FFFFFF")
private val TextLight = android.graphics.Color.parseColor("#E2E8F0")
private val TextDark = android.graphics.Color.parseColor("#0F172A")
private val TextMuted = android.graphics.Color.parseColor("#94A3B8")

// ── Widget UI ───────────────────────────────────────────────────────────

@Composable
private fun WidgetContent(data: WidgetData) {
    val bgColor = ColorProvider(day = LightBg, night = DarkBg)
    val cardColor = ColorProvider(day = LightCard, night = DarkCard)
    val primaryText = ColorProvider(day = TextDark, night = TextLight)
    val mutedText = ColorProvider(day = android.graphics.Color.parseColor("#64748B"), night = TextMuted)
    val greenColor = ColorProvider(day = GreenPrimary, night = GreenPrimary)
    val redColor = ColorProvider(day = RedExpense, night = RedExpense)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ── Header: App name + month ──
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SmartExpense",
                    style = TextStyle(
                        color = greenColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = data.monthLabel,
                    style = TextStyle(color = mutedText, fontSize = 11.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // ── Balance card ──
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(cardColor)
                    .cornerRadius(14.dp)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "Monthly Balance",
                        style = TextStyle(color = mutedText, fontSize = 11.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.format(data.balance, data.currencyCode),
                        style = TextStyle(
                            color = if (data.balance >= 0) greenColor else redColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Income / Expense row
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        // Income
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Income",
                                style = TextStyle(color = mutedText, fontSize = 10.sp)
                            )
                            Text(
                                text = CurrencyUtils.formatCompact(data.totalIncome, data.currencyCode),
                                style = TextStyle(
                                    color = greenColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        // Expenses
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Expenses",
                                style = TextStyle(color = mutedText, fontSize = 10.sp)
                            )
                            Text(
                                text = CurrencyUtils.formatCompact(data.totalExpenses, data.currencyCode),
                                style = TextStyle(
                                    color = redColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        // Today
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Today",
                                style = TextStyle(color = mutedText, fontSize = 10.sp)
                            )
                            Text(
                                text = CurrencyUtils.formatCompact(data.todayExpenses, data.currencyCode),
                                style = TextStyle(
                                    color = ColorProvider(day = OrangeWarning, night = OrangeWarning),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // ── Top categories ──
            if (data.topCategories.isNotEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(cardColor)
                        .cornerRadius(14.dp)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Top Categories",
                            style = TextStyle(
                                color = mutedText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        data.topCategories.forEach { cat ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat.name,
                                    style = TextStyle(color = primaryText, fontSize = 11.sp),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Text(
                                    text = CurrencyUtils.formatCompact(cat.amount, data.currencyCode),
                                    style = TextStyle(color = redColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
            }

            // ── Budget alert ──
            if (data.worstPace != null) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                val pace = data.worstPace
                val alertColor = ColorProvider(day = RedExpense, night = RedExpense)
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ColorProvider(
                            day = android.graphics.Color.parseColor("#FEF2F2"),
                            night = android.graphics.Color.parseColor("#371717")
                        ))
                        .cornerRadius(10.dp)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠ ",
                            style = TextStyle(fontSize = 12.sp)
                        )
                        Text(
                            text = "${pace.categoryName}: ${CurrencyUtils.formatCompact(pace.spent, data.currencyCode)} / ${CurrencyUtils.formatCompact(pace.limit, data.currencyCode)}",
                            style = TextStyle(color = alertColor, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // ── Footer ──
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${data.transactionCount} transactions",
                    style = TextStyle(color = mutedText, fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "Tap to open →",
                    style = TextStyle(color = greenColor, fontSize = 10.sp)
                )
            }
        }
    }
}
