package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.tracker.util.FontScale

/**
 * Settings section that provides accessibility controls: high contrast mode,
 * font scale, screen reader optimized layout, and voice input.
 */
@Composable
fun AccessibilitySettingsSection(
    fontScale: Float,
    highContrast: Boolean,
    screenReaderMode: Boolean,
    voiceInputEnabled: Boolean,
    onFontScaleChange: (Float) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onScreenReaderModeChange: (Boolean) -> Unit,
    onVoiceInputChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Accessibility",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // High contrast toggle
        ToggleRow(
            label = "High contrast mode",
            description = "Increases color contrast for better visibility",
            checked = highContrast,
            onCheckedChange = onHighContrastChange,
            semanticsLabel = if (highContrast) "High contrast mode, on" else "High contrast mode, off"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Font scale selector
        Text(
            text = "Font size",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Adjust text size throughout the app",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        val selectedFontScale = FontScale.fromValue(fontScale)

        Column(modifier = Modifier.selectableGroup()) {
            FontScale.entries.forEach { scale ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = scale == selectedFontScale,
                            onClick = { onFontScaleChange(scale.value) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = scale == selectedFontScale,
                        onClick = null // handled by Row's selectable
                    )
                    Text(
                        text = scale.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Screen reader optimized layout toggle
        ToggleRow(
            label = "Screen reader optimized layout",
            description = "Simplifies the UI for TalkBack and other screen readers",
            checked = screenReaderMode,
            onCheckedChange = onScreenReaderModeChange,
            semanticsLabel = if (screenReaderMode) "Screen reader mode, on" else "Screen reader mode, off"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Voice input toggle
        ToggleRow(
            label = "Voice input",
            description = "Enable voice commands to add expenses and navigate",
            checked = voiceInputEnabled,
            onCheckedChange = onVoiceInputChange,
            semanticsLabel = if (voiceInputEnabled) "Voice input, on" else "Voice input, off"
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    semanticsLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
