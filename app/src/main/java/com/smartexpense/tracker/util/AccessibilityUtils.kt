package com.smartexpense.tracker.util

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Font scale presets for accessibility settings.
 */
enum class FontScale(val value: Float, val label: String) {
    SMALL(0.85f, "Small"),
    NORMAL(1.0f, "Normal"),
    LARGE(1.3f, "Large"),
    EXTRA_LARGE(1.6f, "Extra Large");

    companion object {
        fun fromValue(value: Float): FontScale =
            entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: NORMAL
    }
}

/**
 * Utility helpers for screen reader support and accessibility enhancements.
 */
object AccessibilityUtils {

    /**
     * Announces a message to TalkBack / screen readers via the accessibility service.
     */
    fun announceForAccessibility(context: Context, message: String) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager != null && manager.isEnabled) {
            val event = android.view.accessibility.AccessibilityEvent.obtain(
                android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT
            ).apply {
                text.add(message)
                className = AccessibilityUtils::class.java.name
                packageName = context.packageName
            }
            manager.sendAccessibilityEvent(event)
        }
    }

    /**
     * Checks whether a screen reader (e.g. TalkBack) is currently active.
     */
    fun isScreenReaderActive(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return manager?.isTouchExplorationEnabled == true
    }
}

/**
 * Extension function on [Modifier] that adds a content description for screen readers.
 */
fun Modifier.semanticsDescription(description: String): Modifier = this.semantics {
    contentDescription = description
}

/**
 * Returns a high-contrast [ColorScheme] suitable for users who need increased visibility.
 * Uses pure black/white backgrounds with vivid foreground colors.
 */
fun highContrastColors(): ColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF005662),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFFF00),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF626200),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFF6E40),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFFF1744),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color.White,
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFF757575)
)

/**
 * A composable wrapper that provides proper accessibility semantics for clickable content.
 *
 * @param contentDescription Description read by screen readers.
 * @param clickActionLabel   Label for the click action (e.g. "Open", "Toggle").
 * @param role               The semantic role (e.g. [Role.Button], [Role.Switch]).
 * @param enabled            Whether the clickable is enabled.
 * @param onClick            Callback invoked when the element is clicked.
 * @param modifier           Additional modifiers.
 * @param content            The composable content to wrap.
 */
@Composable
fun AccessibleClickable(
    contentDescription: String,
    clickActionLabel: String,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.role = role
                onClick(label = clickActionLabel) {
                    onClick()
                    true
                }
            }
    ) {
        content()
    }
}
