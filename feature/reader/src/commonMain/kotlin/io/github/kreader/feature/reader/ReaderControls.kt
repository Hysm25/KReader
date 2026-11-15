package io.github.kreader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    isBookmarked: Boolean = false,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Text("🔍", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onBookmarkToggle) {
                Text(
                    text = if (isBookmarked) "🔖" else "🏷️",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onTocClick) {
                Text("☰", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onSettingsClick) {
                Text("Aa", style = MaterialTheme.typography.titleMedium)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        modifier = modifier
    )
}

@Composable
fun ReaderBottomBar(
    progression: Float,
    pageInfo: String,
    onSeek: (Float) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Slider(
            value = progression,
            onValueChange = onSeek,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⟨ Prev",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable(onClick = onPreviousPage)
                    .padding(8.dp)
            )
            Text(
                text = pageInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Next ⟩",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable(onClick = onNextPage)
                    .padding(8.dp)
            )
        }
    }
}
