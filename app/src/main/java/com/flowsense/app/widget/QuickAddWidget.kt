package com.flowsense.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
import com.flowsense.app.MainActivity
import com.flowsense.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smart Quick-Add Transaction widget.
 *
 * Displays up to 4 category buttons ranked by the user's recent usage
 * patterns and time of day. Tapping a category opens the app's Add
 * Transaction screen with that category pre-selected.
 * A central "+" button opens the full Add Transaction screen.
 */
class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadQuickAddData(context) }
        provideContent {
            QuickAddContent(data)
        }
    }
}

// ── Colors ───────────────────────────────────────────────────────────────

private val GreenPrimary = Color(0xFF10B981)
private val WhiteText = Color(0xFFFFFFFF)

// ── Category icon mapping ────────────────────────────────────────────────

/** Maps Material icon names used in categories to Unicode emoji for Glance. */
private fun categoryEmoji(icon: String): String = when (icon) {
    "restaurant" -> "\uD83C\uDF7D\uFE0F"   // fork & knife
    "directions_car" -> "\uD83D\uDE97"       // car
    "shopping_bag" -> "\uD83D\uDECD\uFE0F"  // shopping bags
    "movie" -> "\uD83C\uDFAC"               // clapper board
    "receipt_long" -> "\uD83D\uDCDC"         // scroll
    "local_hospital" -> "\uD83C\uDFE5"      // hospital
    "school" -> "\uD83C\uDF93"              // graduation cap
    "local_grocery_store" -> "\uD83D\uDED2" // shopping cart
    "home" -> "\uD83C\uDFE0"               // house
    "payments" -> "\uD83D\uDCB0"            // money bag
    "work" -> "\uD83D\uDCBC"               // briefcase
    "trending_up" -> "\uD83D\uDCC8"         // chart increasing
    "more_horiz" -> "\u2699\uFE0F"          // gear
    else -> "\uD83D\uDCB3"                  // credit card
}

// ── Action Parameters ────────────────────────────────────────────────────

private val CategoryNameKey = ActionParameters.Key<String>("category_name")
private val IsExpenseKey = ActionParameters.Key<Boolean>("is_expense")

// ── Actions ──────────────────────────────────────────────────────────────

/**
 * Opens the main app's Add Transaction screen.
 * The category is passed as an Intent extra so MainActivity can pre-select it.
 */
class OpenAddTransactionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val category = parameters[CategoryNameKey] ?: ""
        val isExpense = parameters[IsExpenseKey] ?: true

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "add")
            putExtra("preset_category", category)
            putExtra("preset_type", if (isExpense) "EXPENSE" else "INCOME")
        }
        context.startActivity(intent)
    }
}

/**
 * Opens the full Add Transaction screen without a preset category.
 */
class OpenAddScreenAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "add")
        }
        context.startActivity(intent)
    }
}

// ── Widget UI ────────────────────────────────────────────────────────────

@Composable
private fun QuickAddContent(data: QuickAddWidgetData) {
    val bgColor = ColorProvider(R.color.widget_bg)
    val cardColor = ColorProvider(R.color.widget_card)
    val primaryText = ColorProvider(R.color.widget_text_primary)
    val mutedText = ColorProvider(R.color.widget_text_muted)
    val greenColor = ColorProvider(GreenPrimary)
    val whiteColor = ColorProvider(WhiteText)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(20.dp)
            .padding(12.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u26A1",
                    style = TextStyle(fontSize = 14.sp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Quick Add",
                    style = TextStyle(
                        color = greenColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "FlowSense",
                    style = TextStyle(color = mutedText, fontSize = 10.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // ── Suggestion label ──
            Text(
                text = "Suggested for you",
                style = TextStyle(color = mutedText, fontSize = 10.sp)
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // ── Category buttons (2x2 grid) ──
            val categories = data.suggestedCategories
            if (categories.isNotEmpty()) {
                // First row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 0 until minOf(2, categories.size)) {
                        if (i > 0) Spacer(modifier = GlanceModifier.width(8.dp))
                        CategoryButton(
                            category = categories[i],
                            cardColor = cardColor,
                            primaryText = primaryText,
                            mutedText = mutedText,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    // Fill empty slot if only 1 category
                    if (categories.size < 2) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Second row
                if (categories.size > 2) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (i in 2 until minOf(4, categories.size)) {
                            if (i > 2) Spacer(modifier = GlanceModifier.width(8.dp))
                            CategoryButton(
                                category = categories[i],
                                cardColor = cardColor,
                                primaryText = primaryText,
                                mutedText = mutedText,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                        // Fill empty slot if only 3 categories
                        if (categories.size < 4) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // ── Big "Add Transaction" button ──
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(greenColor)
                    .cornerRadius(12.dp)
                    .padding(vertical = 10.dp, horizontal = 16.dp)
                    .clickable(actionRunCallback<OpenAddScreenAction>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Add Transaction",
                    style = TextStyle(
                        color = whiteColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun CategoryButton(
    category: SmartCategory,
    cardColor: ColorProvider,
    primaryText: ColorProvider,
    mutedText: ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    val emoji = categoryEmoji(category.icon)

    Box(
        modifier = modifier
            .background(cardColor)
            .cornerRadius(12.dp)
            .padding(8.dp)
            .clickable(
                actionRunCallback<OpenAddTransactionAction>(
                    actionParametersOf(
                        CategoryNameKey to category.name,
                        IsExpenseKey to category.isExpense
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = emoji,
                style = TextStyle(fontSize = 20.sp)
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = category.name,
                style = TextStyle(
                    color = primaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}
