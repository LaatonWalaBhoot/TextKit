package com.jjrodcast.textkit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.theme.TextKitTheme
import com.jjrodcast.textkit.ui.TextKitEditorOutlined
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The outlined editor must fill the area its caller gives it (#143): the decoration Box measured
 * its children loosely, so under `Modifier.weight(1f)` — which hands the editor a minimum size —
 * the outlined container wrapped its placeholder in a corner of the allotted space. The inner
 * text field's layout constraints are the observable: when the decoration fills, the field is
 * measured against (nearly) the full weighted area; when it wraps, against a placeholder-sized box.
 */
@OptIn(ExperimentalComposeUiApi::class)
class OutlinedEditorSizingTest {

    @Test
    fun a_weighted_outlined_editor_fills_its_allotted_area() {
        val state = TextKitState("{}", createTextKitConfiguration()).apply { setup() }
        var time = 0L
        ImageComposeScene(width = 500, height = 860) {
            TextKitTheme {
                Column(Modifier.fillMaxSize()) {
                    TextKitEditorOutlined(
                        state = state,
                        modifier = Modifier.weight(1f).padding(16.dp)
                    )
                }
            }
        }.use { scene -> repeat(3) { time += 16_000_000; scene.render(time) } }

        // The propagated minimum reaches the inner field as a TIGHT width spanning the weighted
        // area (the un-fixed decoration measured it against a placeholder-sized box instead). The
        // field's height stays unbounded inside the decoration — the decoration itself clips.
        val constraints = state.textLayoutResult?.layoutInput?.constraints ?: error("no layout")
        assertTrue(constraints.minWidth > 300, "field must span the weighted width, got $constraints")
    }
}
