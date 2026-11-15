package io.github.kreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kreader.core.engine.TocItem
import io.github.kreader.core.model.Locator
import io.github.kreader.core.model.ReaderTheme
import io.github.kreader.core.model.ReaderTypography
import io.github.kreader.core.model.ThemeMode

object ReaderThemePresets {
    val Light = ReaderTheme(
        mode = ThemeMode.LIGHT,
        backgroundColorHex = "#FFFFFF",
        textColorHex = "#121212"
    )
    val Sepia = ReaderTheme(
        mode = ThemeMode.SEPIA,
        backgroundColorHex = "#FBF0D9",
        textColorHex = "#5F4B32"
    )
    val Dark = ReaderTheme(
        mode = ThemeMode.DARK,
        backgroundColorHex = "#1E1E1E",
        textColorHex = "#E8E8E8"
    )
    val Amoled = ReaderTheme(
        mode = ThemeMode.DARK,
        backgroundColorHex = "#000000",
        textColorHex = "#FFFFFF"
    )
}

@Composable
fun ReaderTocSheet(
    isOpen: Boolean,
    tocItems: List<TocItem>,
    onItemClick: (Locator) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Table of Contents",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (tocItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No Table of Contents",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(flattenToc(tocItems)) { flatItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onItemClick(flatItem.item.locator)
                                        onDismiss()
                                    }
                                    .padding(
                                        start = (12 + flatItem.depth * 16).dp,
                                        end = 12.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = flatItem.item.title.ifBlank { "Untitled" },
                                    style = if (flatItem.depth == 0) {
                                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    } else {
                                        MaterialTheme.typography.bodySmall
                                    },
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FlatTocItem(
    val item: TocItem,
    val depth: Int
)

private fun flattenToc(items: List<TocItem>, depth: Int = 0): List<FlatTocItem> {
    val result = mutableListOf<FlatTocItem>()
    for (item in items) {
        result.add(FlatTocItem(item, depth))
        if (item.children.isNotEmpty()) {
            result.addAll(flattenToc(item.children, depth + 1))
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    isOpen: Boolean,
    theme: ReaderTheme,
    typography: ReaderTypography,
    isScrollMode: Boolean,
    onThemeChange: (ReaderTheme) -> Unit,
    onTypographyChange: (ReaderTypography) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reading Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider()

                // Theme Presets: Light, Sepia, Dark, AMOLED
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ThemeOption(
                            label = "Light",
                            bgColor = Color(0xFFFFFFFF),
                            textColor = Color(0xFF111111),
                            isSelected = theme.mode == ThemeMode.LIGHT,
                            onClick = { onThemeChange(ReaderThemePresets.Light) }
                        )
                        ThemeOption(
                            label = "Sepia",
                            bgColor = Color(0xFFFBF0D9),
                            textColor = Color(0xFF5F4B32),
                            isSelected = theme.mode == ThemeMode.SEPIA,
                            onClick = { onThemeChange(ReaderThemePresets.Sepia) }
                        )
                        ThemeOption(
                            label = "Dark",
                            bgColor = Color(0xFF1E1E1E),
                            textColor = Color(0xFFE8E8E8),
                            isSelected = theme.mode == ThemeMode.DARK && theme.backgroundColorHex != "#000000",
                            onClick = { onThemeChange(ReaderThemePresets.Dark) }
                        )
                        ThemeOption(
                            label = "AMOLED",
                            bgColor = Color(0xFF000000),
                            textColor = Color(0xFFFFFFFF),
                            isSelected = theme.mode == ThemeMode.DARK && theme.backgroundColorHex.equals("#000000", ignoreCase = true),
                            onClick = { onThemeChange(ReaderThemePresets.Amoled) }
                        )
                    }
                }

                // Reading Mode: Paginated vs Scroll
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isScrollMode,
                            onClick = { onScrollModeChange(false) },
                            label = { Text("Paginated") }
                        )
                        FilterChip(
                            selected = isScrollMode,
                            onClick = { onScrollModeChange(true) },
                            label = { Text("Continuous Scroll") }
                        )
                    }
                }

                // Progression Direction: LTR vs RTL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progression",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !typography.isRtl,
                            onClick = { onTypographyChange(typography.copy(isRtl = false)) },
                            label = { Text("LTR") }
                        )
                        FilterChip(
                            selected = typography.isRtl,
                            onClick = { onTypographyChange(typography.copy(isRtl = true)) },
                            label = { Text("RTL (العربية)") }
                        )
                    }
                }

                // Font Family: Default, Amiri (Arabic Serif), Cairo (Arabic Sans)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Font Family",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = typography.fontFamily.equals("Default", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Default")) },
                            label = { Text("Default") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.fontFamily.equals("Amiri", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Amiri")) },
                            label = { Text("Amiri (أميري)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.fontFamily.equals("Cairo", ignoreCase = true),
                            onClick = { onTypographyChange(typography.copy(fontFamily = "Cairo")) },
                            label = { Text("Cairo (كايرو)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Font Size adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Font Size",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val newSize = (typography.fontSizeSp - 2f).coerceAtLeast(12f)
                                onTypographyChange(typography.copy(fontSizeSp = newSize))
                            }
                        ) {
                            Text("A-", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "${typography.fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(
                            onClick = {
                                val newSize = (typography.fontSizeSp + 2f).coerceAtMost(36f)
                                onTypographyChange(typography.copy(fontSizeSp = newSize))
                            }
                        ) {
                            Text("A+", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                // Line Height Spacing (Tashkeel / Diacritics friendly >= 1.5)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Line Height (تباعد الأسطر والتشكيل)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = typography.lineHeightMultiplier <= 1.55f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 1.5f)) },
                            label = { Text("1.5x (عادي)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.lineHeightMultiplier > 1.55f && typography.lineHeightMultiplier <= 1.85f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 1.8f)) },
                            label = { Text("1.8x (مريح)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = typography.lineHeightMultiplier > 1.85f,
                            onClick = { onTypographyChange(typography.copy(lineHeightMultiplier = 2.0f)) },
                            label = { Text("2.0x (واسع)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aa",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
