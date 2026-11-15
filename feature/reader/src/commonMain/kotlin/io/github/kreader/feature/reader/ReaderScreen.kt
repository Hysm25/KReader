package io.github.kreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.kreader.core.engine.ReaderEngineState

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBackClick: () -> Unit,
    onToggleControls: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSeek: (Float) -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggleControls
                )
        ) {
            when (val engineState = state.engineState) {
                is ReaderEngineState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReaderEngineState.Ready -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Content: ${engineState.currentLocator.href} (${(engineState.totalProgression * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is ReaderEngineState.Error -> {
                    Text(
                        text = "Error: ${engineState.userFriendlyMessage}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                ReaderEngineState.Idle -> {
                    Text(
                        text = "Reader Idle",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Top Bar Overlay
            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = state.book?.title ?: "Reading",
                    onBackClick = onBackClick,
                    onTocClick = onTocClick,
                    onSettingsClick = onSettingsClick
                )
            }

            // Bottom Bar Overlay
            AnimatedVisibility(
                visible = state.isControlsVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val readyState = state.engineState as? ReaderEngineState.Ready
                val prog = readyState?.totalProgression ?: 0f
                val pageInfo = if (readyState?.totalPages != null && readyState.currentPageIndex != null) {
                    "${readyState.currentPageIndex + 1} / ${readyState.totalPages}"
                } else {
                    "${(prog * 100).toInt()}%"
                }

                ReaderBottomBar(
                    progression = prog,
                    pageInfo = pageInfo,
                    onSeek = onSeek,
                    onPreviousPage = onPreviousPage,
                    onNextPage = onNextPage
                )
            }
        }
    }
}
