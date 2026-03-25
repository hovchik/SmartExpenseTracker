package com.smartexpense.tracker.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Broadcast receiver that Android uses to manage the home-screen widget lifecycle.
 * Delegates all rendering to [ExpenseTrackerWidget] (Glance).
 */
class ExpenseTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseTrackerWidget()
}
