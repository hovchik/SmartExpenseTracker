package com.flowsense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowsense.app.ui.theme.GreenPrimary

// ── Shared AI response rich-text rendering ──────────────────────

/**
 * Detects whether a line is a section heading.
 * Matches patterns like:
 *   - "## Heading" or "### Heading"  (markdown)
 *   - "1. HEADING" or "2. Heading"   (numbered)
 *   - "FINANCIAL HEALTH OVERVIEW"     (all-caps)
 *   - "**Heading**"                   (bold markdown)
 */
fun isSectionHeading(line: String): Boolean {
    val t = line.trim()
    if (t.length < 3) return false
    if (t.startsWith("#")) return true
    if (t.startsWith("**") && t.endsWith("**") && t.length > 4) return true
    if (t.matches(Regex("^\\d+\\.\\s+[A-Z].*"))) return true
    val stripped = t.replace(Regex("[^A-Za-z ]"), "").trim()
    if (stripped.length > 2 && stripped == stripped.uppercase() && stripped.any { it.isLetter() }) return true
    return false
}

/** Strips markdown heading markers and bold markers for display. */
fun cleanHeading(line: String): String {
    return line.trim()
        .replace(Regex("^#+\\s*"), "")
        .replace(Regex("^\\d+\\.\\s*"), "")
        .removeSurrounding("**")
        .trim()
}

/** Strips leading bullet/dash markers for display. */
fun cleanBullet(line: String): String {
    return line.trim()
        .removePrefix("-").removePrefix("•").removePrefix("*")
        .replace(Regex("^\\d+[.):]\\s*"), "")
        .trim()
}

/** Returns true if the line starts with a bullet marker. */
fun isBulletLine(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("-") || t.startsWith("•") || t.startsWith("*") ||
            t.matches(Regex("^\\d+[.)]\\s+.*"))
}

/** Renders inline bold (**text**) using AnnotatedString. */
@Composable
fun RichText(
    text: String,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 20.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val annotated = remember(text) {
        buildRichAnnotatedString(text)
    }
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color
    )
}

/** Builds an AnnotatedString with bold spans for **text** patterns. */
fun buildRichAnnotatedString(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var remaining = text
    val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
    while (remaining.isNotEmpty()) {
        val match = boldPattern.find(remaining)
        if (match != null) {
            builder.append(remaining.substring(0, match.range.first))
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(match.groupValues[1])
            builder.pop()
            remaining = remaining.substring(match.range.last + 1)
        } else {
            builder.append(remaining)
            break
        }
    }
    return builder.toAnnotatedString()
}

/**
 * Renders AI response text with rich formatting:
 * section headings, bullet points, inline bold, and paragraphs.
 *
 * @param accentColor Color for the heading accent bar (defaults to GreenPrimary).
 */
@Composable
fun ColumnScope.RichAiResponseContent(
    text: String,
    accentColor: Color = GreenPrimary
) {
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()

        when {
            trimmed.isBlank() -> {
                i++
            }

            isSectionHeading(trimmed) -> {
                val heading = cleanHeading(trimmed)
                if (heading.isNotBlank()) {
                    if (i > 0) Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            heading.uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                i++
            }

            isBulletLine(trimmed) -> {
                val bulletText = cleanBullet(trimmed)
                if (bulletText.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        RichText(
                            text = bulletText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                i++
            }

            else -> {
                RichText(
                    text = trimmed,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                i++
            }
        }
    }
}
