package com.jjrodcast.textkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.listlayout.TextKitViewerBlocks
import com.jjrodcast.textkit.ui.listlayout.withListLineMetrics
import com.jjrodcast.textkit.ui.state.TextKitState

@Composable
fun TextKitViewer(
    state: TextKitState,
    modifier: Modifier = Modifier
) {
    val highlightColor = TextKitTheme.colors.highlight
    SideEffect { state.setThemeHighlightColor(highlightColor) }

    TextKitViewerBlocks(
        blocks = state.viewerBlocks,
        textStyle = TextStyle(color = TextKitTheme.colors.onSurface).withListLineMetrics(),
        modifier = modifier,
    )
}
