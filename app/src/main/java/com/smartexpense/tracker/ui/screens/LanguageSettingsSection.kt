package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.tracker.util.LocaleManager

/**
 * Settings section that displays all supported languages and lets the user
 * pick one. Each entry shows the native name and English name.
 */
@Composable
fun LanguageSettingsSection(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Language",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        LocaleManager.SUPPORTED_LANGUAGES.forEach { language ->
            val isSelected = language.code == currentLanguage
            val accessibilityLabel = if (language.englishName.isNotEmpty()) {
                "${language.nativeName}, ${language.englishName}" +
                    if (isSelected) ", selected" else ""
            } else {
                language.nativeName + if (isSelected) ", selected" else ""
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onLanguageChange(language.code) }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .semantics { contentDescription = accessibilityLabel },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null // handled by Row's clickable
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = language.nativeName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (language.englishName.isNotEmpty()) {
                        Text(
                            text = language.englishName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
