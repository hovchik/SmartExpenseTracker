package com.flowsense.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.flowsense.app.FlowSenseApp
import com.flowsense.app.MainActivity
import com.flowsense.app.R
import com.flowsense.app.util.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Glance-based home screen widget showing monthly expense summary,
 * today's spending, top categories, and budget alerts.
 * Supports hiding monetary amounts for privacy.
 */
class ExpenseTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadWidgetData(context) }
        provideContent {
            WidgetContent(data)
        }
    }
}

// ── Fixed colors (same in light & dark) ─────────────────────────────────

private val GreenPrimary = Color(0xFF10B981)
private val RedExpense = Color(0xFFEF4444)
private val OrangeWarning = Color(0xFFF59E0B)

/** Placeholder shown when amounts are hidden. */
private const val HIDDEN_AMOUNT = "••••"

/** Formats an amount or returns the hidden placeholder. */
private fun formatOrHide(amount: Double, currencyCode: String, hide: Boolean): String =
    if (hide) HIDDEN_AMOUNT else CurrencyUtils.format(amount, currencyCode)

/** Formats a compact amount or returns the hidden placeholder. */
private fun formatCompactOrHide(amount: Double, currencyCode: String, hide: Boolean): String =
    if (hide) HIDDEN_AMOUNT else CurrencyUtils.formatCompact(amount, currencyCode)

// ── Toggle hide/show action ─────────────────────────────────────────────

/**
 * ActionCallback that toggles the widgetHideAmounts setting and refreshes the widget.
 */
class ToggleHideAmountsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as? FlowSenseApp ?: return
        val repo = app.repository
        repo.awaitInitialization(5_000)

        val current = repo.appData.value.settings
        val updated = current.copy(widgetHideAmounts = !current.widgetHideAmounts)
        repo.updateSettings(updated)

        // Refresh this widget instance
        ExpenseTrackerWidget().update(context, glanceId)
    }
}

// ── Widget UI ───────────────────────────────────────────────────────────

@Composable
private fun WidgetContent(data: WidgetData) {
    // Day/night adaptive colors via resource IDs
    val bgColor = ColorProvider(R.color.widget_bg)
    val cardColor = ColorProvider(R.color.widget_card)
    val primaryText = ColorProvider(R.color.widget_text_primary)
    val mutedText = ColorProvider(R.color.widget_text_muted)
    val alertBg = ColorProvider(R.color.widget_alert_bg)

    // Fixed colors
    val greenColor = ColorProvider(GreenPrimary)
    val redColor = ColorProvider(RedExpense)
    val orangeColor = ColorProvider(OrangeWarning)

    val hide = data.hideAmounts

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ── Header: App name + eye toggle + month ──
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FlowSense",
                    style = TextStyle(
                        color = greenColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                // Eye toggle button
                Box(
                    modifier = GlanceModifier
                        .clickable(actionRunCallback<ToggleHideAmountsAction>())
                        .padding(2.dp)
                ) {
                    Text(
                        text = if (hide) "\uD83D\uDE48" else "\uD83D\uDC41",
                        style = TextStyle(fontSize = 14.sp)
                    )
                }
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
                        text = formatOrHide(data.balance, data.currencyCode, hide),
                        style = TextStyle(
                            color = if (data.balance >= 0) greenColor else redColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Income / Expense / Today row
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Income",
                                style = TextStyle(color = mutedText, fontSize = 10.sp)
                            )
                            Text(
                                text = formatCompactOrHide(data.totalIncome, data.currencyCode, hide),
                                style = TextStyle(
                                    color = greenColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Expenses",
                                style = TextStyle(color = mutedText, fontSize = 10.sp)
                            )
                            Text(
                                text = formatCompactOrHide(data.totalExpenses, data.currencyCode, hide),
                                style = TextStyle(
                                    color = redColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "Today",
                                style = TextStyle(color = mutedText, fontSize = 10.sp)
                            )
                            Text(
                                text = formatCompactOrHide(data.todayExpenses, data.currencyCode, hide),
                                style = TextStyle(
                                    color = orangeColor,
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
                                    text = formatCompactOrHide(cat.amount, data.currencyCode, hide),
                                    style = TextStyle(
                                        color = redColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
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
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(alertBg)
                        .cornerRadius(10.dp)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u26A0 ",
                            style = TextStyle(fontSize = 12.sp)
                        )
                        Text(
                            text = if (hide) {
                                "${pace.categoryName}: $HIDDEN_AMOUNT / $HIDDEN_AMOUNT"
                            } else {
                                "${pace.categoryName}: " +
                                        "${CurrencyUtils.formatCompact(pace.spent, data.currencyCode)} / " +
                                        CurrencyUtils.formatCompact(pace.limit, data.currencyCode)
                            },
                            style = TextStyle(
                                color = redColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
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
                    text = "Tap to open \u2192",
                    style = TextStyle(color = greenColor, fontSize = 10.sp)
                )
            }
        }
    }
}
