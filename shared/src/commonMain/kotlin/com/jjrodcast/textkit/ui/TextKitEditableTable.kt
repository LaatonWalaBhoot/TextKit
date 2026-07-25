package com.jjrodcast.textkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jjrodcast.textkit.theme.TextKitTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * An **editable**, Notion-style rich table for a `table` embed.
 *
 * Where [TextKitEmbedPopup]'s `TableView` renders a table read-only, this component lets the user
 * mutate one: add/remove rows and columns, merge and split cells (true `colspan`/`rowspan`), toggle
 * header cells (styled with a more prominent color), and edit cell text inline. Nothing leaves the
 * component until the user taps **Sincronizar**, at which point [onSync] receives the table
 * serialized back to the same ProseMirror JSON shape carried by the embed's `rawJson`.
 *
 * The whole UI is themed through [TextKitTheme] so it adapts to light/dark automatically.
 *
 * @param rawJson The ProseMirror `table` JSON from the embed model (`EmbedInfo.rawJson`). A fresh
 *   editable state is built whenever this value changes. Malformed input falls back to a starter 3×3.
 * @param onSync Called with the updated ProseMirror `table` JSON when the user syncs their changes.
 * @param modifier Layout modifier for the root column.
 */
@Composable
fun TextKitEditableTable(
    rawJson: String,
    onSync: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(rawJson) { EditableTableState.from(rawJson) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TableToolbar(state = state, onSync = onSync)

        Box(
            modifier = Modifier
                .border(
                    BorderStroke(1.dp, TextKitTheme.colors.outlineVariant),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(1.dp),
        ) {
            TableGrid(state = state)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Toolbar
// ---------------------------------------------------------------------------------------------

@Composable
private fun TableToolbar(state: EditableTableState, onSync: (String) -> Unit) {
    // Read the structural version + selection so the toolbar re-evaluates enabled states.
    @Suppress("UNUSED_VARIABLE") val version = state.version
    val selectionCount = state.selected.size

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableActionButton(Icons.Rounded.Add, "Fila") { state.addRowRelative() }
            TableActionButton(Icons.Rounded.Add, "Columna") { state.addColumnRelative() }
            TableActionButton(
                icon = Icons.Rounded.Delete,
                label = "Eliminar fila",
                enabled = selectionCount > 0,
            ) { state.deleteSelectedRows() }
            TableActionButton(
                icon = Icons.Rounded.Delete,
                label = "Eliminar columna",
                enabled = selectionCount > 0,
            ) { state.deleteSelectedColumns() }
            TableActionButton(
                icon = Icons.Rounded.CallMerge,
                label = "Fusionar",
                enabled = state.canMerge(),
            ) { state.mergeSelection() }
            TableActionButton(
                icon = Icons.Rounded.CallSplit,
                label = "Dividir",
                enabled = state.canSplit(),
            ) { state.splitSelection() }
            TableActionButton(
                icon = Icons.Rounded.Title,
                label = "Encabezado",
                enabled = selectionCount > 0,
            ) { state.toggleHeaderSelected() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectionCount == 0) {
                    "Toca la barra superior de una celda para seleccionarla"
                } else {
                    "$selectionCount celda(s) seleccionada(s)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextKitTheme.colors.onSurfaceVariant,
            )
            Button(
                onClick = { onSync(state.toProseMirrorJson()) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = TextKitTheme.colors.primary,
                    contentColor = TextKitTheme.colors.onPrimary,
                ),
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = "Sincronizar",
                    modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TableActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = TextKitTheme.colors.surfaceVariant,
        contentColor = if (enabled) {
            TextKitTheme.colors.onSurfaceVariant
        } else {
            TextKitTheme.colors.onSurfaceVariant.copy(alpha = 0.4f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Grid rendering (custom Layout so merged cells span multiple rows/columns correctly)
// ---------------------------------------------------------------------------------------------

private val ColumnWidth: Dp = 150.dp
private val MinRowHeight: Dp = 46.dp

@Composable
private fun TableGrid(state: EditableTableState, modifier: Modifier = Modifier) {
    // Read `version` so structural mutations trigger a recomposition + re-measure.
    @Suppress("UNUSED_VARIABLE") val version = state.version
    val anchors = state.anchors()
    val rowCount = state.rows()
    val colCount = state.cols()

    Layout(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        content = {
            anchors.forEach { anchor ->
                key(anchor.id) {
                    Box(
                        Modifier.cellSpan(
                            CellSpan(anchor.row, anchor.col, anchor.rowSpan, anchor.colSpan),
                        ),
                    ) {
                        TableCellView(state = state, id = anchor.id)
                    }
                }
            }
        },
    ) { measurables, _ ->
        val colPx = ColumnWidth.roundToPx()
        val minRowPx = MinRowHeight.roundToPx()
        val spans = measurables.map { it.parentData as CellSpan }

        // Pass 1: measure each cell at its final width with natural (unbounded) height.
        val natural = measurables.mapIndexed { i, m ->
            val s = spans[i]
            m.measure(Constraints(minWidth = s.colSpan * colPx, maxWidth = s.colSpan * colPx))
        }

        // Row heights: single-row cells set them, multi-row cells grow the last spanned row.
        val rowH = IntArray(rowCount) { minRowPx }
        natural.forEachIndexed { i, p ->
            val s = spans[i]
            if (s.rowSpan == 1) rowH[s.row] = maxOf(rowH[s.row], p.height)
        }
        natural.forEachIndexed { i, p ->
            val s = spans[i]
            if (s.rowSpan > 1) {
                val have = (s.row until s.row + s.rowSpan).sumOf { rowH[it] }
                if (p.height > have) rowH[s.row + s.rowSpan - 1] += p.height - have
            }
        }

        val rowY = IntArray(rowCount)
        for (r in 1 until rowCount) rowY[r] = rowY[r - 1] + rowH[r - 1]

        // Pass 2: re-measure each cell at the exact spanned size so backgrounds/borders fill it.
        val placeables = measurables.mapIndexed { i, m ->
            val s = spans[i]
            val h = (s.row until s.row + s.rowSpan).sumOf { rowH[it] }
            m.measure(Constraints.fixed(s.colSpan * colPx, h))
        }

        val totalW = colCount * colPx
        val totalH = rowH.sum()
        layout(totalW, totalH) {
            placeables.forEachIndexed { i, p ->
                val s = spans[i]
                p.place(s.col * colPx, rowY[s.row])
            }
        }
    }
}

@Composable
private fun TableCellView(state: EditableTableState, id: Long) {
    val content = state.cells[id] ?: return
    val isSelected = id in state.selected
    val isHeader = content.isHeader

    val background = if (isHeader) TextKitTheme.colors.primaryContainer else TextKitTheme.colors.surface
    val textColor = if (isHeader) TextKitTheme.colors.onPrimaryContainer else TextKitTheme.colors.onSurface
    val border = if (isSelected) {
        BorderStroke(2.dp, TextKitTheme.colors.primary)
    } else {
        BorderStroke(1.dp, TextKitTheme.colors.outlineVariant)
    }
    val handleColor = when {
        isSelected -> TextKitTheme.colors.primary
        isHeader -> TextKitTheme.colors.onPrimaryContainer.copy(alpha = 0.18f)
        else -> TextKitTheme.colors.surfaceVariant
    }

    Column(modifier = Modifier.background(background).border(border)) {
        // Selection handle: a slim, always-available bar so selecting never fights with editing text.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(handleColor)
                .clickable { state.toggleSelect(id) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "•  •  •",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) TextKitTheme.colors.onPrimary else TextKitTheme.colors.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = TextKitTheme.colors.outlineVariant)
        BasicTextField(
            value = content.text,
            onValueChange = { content.text = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = textColor,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            ),
            cursorBrush = SolidColor(TextKitTheme.colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** Parent data attaching a cell's grid position + span so the [Layout] can place/size it. */
private data class CellSpan(val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int)

private class CellSpanModifier(private val span: CellSpan) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = span
}

private fun Modifier.cellSpan(span: CellSpan): Modifier = this.then(CellSpanModifier(span))

// ---------------------------------------------------------------------------------------------
// Editable model — the grid of anchor ids is the single source of truth; spans are derived.
// ---------------------------------------------------------------------------------------------

/** Mutable content of one cell. Text/header are Compose state so edits recompose only that cell. */
private class CellContent(text: String, isHeader: Boolean) {
    var text by mutableStateOf(text)
    var isHeader by mutableStateOf(isHeader)
}

/** A cell's top-left position and how many rows/columns it spans (derived from the grid). */
private data class Anchor(
    val id: Long,
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
)

/**
 * Holds the editable table. `grid[r][c]` stores the id of the cell occupying that logical slot; a
 * merged cell simply has its id in every slot it covers. Spans are always *derived* from the grid
 * (see [anchors]), which keeps every mutation (add/remove/merge/split) a simple grid edit that can't
 * leave the model inconsistent. The rectangle is kept dense (all rows share the same width).
 */
private class EditableTableState(
    private val grid: MutableList<MutableList<Long>>,
    val cells: MutableMap<Long, CellContent>,
    startId: Long,
) {
    private var nextId = startId

    /** Bumped on structural changes (rows/cols/spans) to invalidate the grid layout. */
    var version by mutableIntStateOf(0)
        private set

    /** Currently selected cell ids (for merge/split/delete/header actions). */
    val selected = mutableStateListOf<Long>()

    private fun newId(): Long = nextId++
    private fun bump() { version++ }

    fun rows(): Int = grid.size
    fun cols(): Int = grid.firstOrNull()?.size ?: 0

    /** Derive every anchor (top-left + span) from the grid, sorted row-major for stable rendering. */
    fun anchors(): List<Anchor> {
        val box = HashMap<Long, IntArray>() // id -> [minR, minC, maxR, maxC]
        for (r in grid.indices) {
            val row = grid[r]
            for (c in row.indices) {
                val id = row[c]
                val b = box[id]
                if (b == null) {
                    box[id] = intArrayOf(r, c, r, c)
                } else {
                    if (r < b[0]) b[0] = r
                    if (c < b[1]) b[1] = c
                    if (r > b[2]) b[2] = r
                    if (c > b[3]) b[3] = c
                }
            }
        }
        return box.entries
            .map { (id, b) -> Anchor(id, b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1) }
            .sortedWith(compareBy({ it.row }, { it.col }))
    }

    fun toggleSelect(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    // --- structural mutations ---------------------------------------------------------------

    private fun addRow(at: Int) {
        val n = cols()
        val newRow = ArrayList<Long>(n)
        for (c in 0 until n) {
            val above = if (at > 0) grid[at - 1][c] else -1L
            val below = if (at < grid.size) grid[at][c] else -1L
            // Inserting *inside* a vertically-merged cell grows that cell (Notion behavior);
            // otherwise the new slot is a fresh empty single cell.
            if (at in 1 until grid.size && above == below) {
                newRow.add(above)
            } else {
                val id = newId()
                cells[id] = CellContent("", false)
                newRow.add(id)
            }
        }
        grid.add(at, newRow)
        bump()
    }

    private fun addColumn(at: Int) {
        for (row in grid) {
            val left = if (at > 0) row[at - 1] else -1L
            val right = if (at < row.size) row[at] else -1L
            if (at in 1 until row.size && left == right) {
                row.add(at, left) // grow a horizontally-merged cell
            } else {
                val id = newId()
                cells[id] = CellContent("", false)
                row.add(at, id)
            }
        }
        bump()
    }

    fun deleteRows(indices: List<Int>) {
        val distinct = indices.distinct()
        if (rows() - distinct.size < 1) return // always keep at least one row
        distinct.sortedDescending().forEach { if (it in grid.indices) grid.removeAt(it) }
        purge()
        selected.clear()
        bump()
    }

    fun deleteColumns(indices: List<Int>) {
        val distinct = indices.distinct()
        if (cols() - distinct.size < 1) return // always keep at least one column
        val desc = distinct.sortedDescending()
        for (row in grid) for (c in desc) if (c < row.size) row.removeAt(c)
        purge()
        selected.clear()
        bump()
    }

    // --- merge / split ----------------------------------------------------------------------

    /** The bounding box of the current selection if it forms a fully-covered rectangle, else null. */
    private fun mergeBox(): IntArray? {
        if (selected.size < 2) return null
        val picked = anchors().filter { it.id in selected }
        if (picked.isEmpty()) return null
        val minR = picked.minOf { it.row }
        val minC = picked.minOf { it.col }
        val maxR = picked.maxOf { it.row + it.rowSpan - 1 }
        val maxC = picked.maxOf { it.col + it.colSpan - 1 }
        for (r in minR..maxR) for (c in minC..maxC) {
            if (grid[r][c] !in selected) return null
        }
        return intArrayOf(minR, minC, maxR, maxC)
    }

    fun canMerge(): Boolean = mergeBox() != null

    fun mergeSelection() {
        val b = mergeBox() ?: return
        val target = grid[b[0]][b[1]] // keep the top-left cell's content
        for (r in b[0]..b[2]) for (c in b[1]..b[3]) grid[r][c] = target
        purge()
        selected.clear()
        selected.add(target)
        bump()
    }

    fun canSplit(): Boolean {
        val id = selected.singleOrNull() ?: return false
        val a = anchors().firstOrNull { it.id == id } ?: return false
        return a.rowSpan > 1 || a.colSpan > 1
    }

    fun splitSelection() {
        val id = selected.singleOrNull() ?: return
        val a = anchors().firstOrNull { it.id == id } ?: return
        val header = cells[id]?.isHeader == true
        var first = true
        for (r in a.row until a.row + a.rowSpan) for (c in a.col until a.col + a.colSpan) {
            if (first) { first = false; continue } // top-left keeps the id + content
            val nid = newId()
            cells[nid] = CellContent("", header)
            grid[r][c] = nid
        }
        bump()
    }

    // --- selection-relative helpers ---------------------------------------------------------

    private fun selectedAnchors(): List<Anchor> = anchors().filter { it.id in selected }
    private fun selectedRows(): List<Int> =
        selectedAnchors().flatMap { it.row until it.row + it.rowSpan }.distinct()
    private fun selectedCols(): List<Int> =
        selectedAnchors().flatMap { it.col until it.col + it.colSpan }.distinct()

    fun addRowRelative() = addRow((selectedRows().maxOrNull()?.plus(1)) ?: rows())
    fun addColumnRelative() = addColumn((selectedCols().maxOrNull()?.plus(1)) ?: cols())
    fun deleteSelectedRows() { if (selected.isNotEmpty()) deleteRows(selectedRows()) }
    fun deleteSelectedColumns() { if (selected.isNotEmpty()) deleteColumns(selectedCols()) }

    fun toggleHeaderSelected() {
        if (selected.isEmpty()) return
        val allHeader = selected.all { cells[it]?.isHeader == true }
        selected.forEach { cells[it]?.isHeader = !allHeader }
    }

    private fun purge() {
        val live = grid.flatten().toHashSet()
        cells.keys.retainAll(live)
        selected.removeAll { it !in live }
    }

    // --- serialization ----------------------------------------------------------------------

    /** Serialize back to the ProseMirror `table` JSON shape carried by the embed's `rawJson`. */
    fun toProseMirrorJson(): String {
        val anchorMap = anchors().associateBy { it.id }
        val root = buildJsonObject {
            put("type", "table")
            putJsonArray("content") {
                for (r in 0 until rows()) {
                    add(
                        buildJsonObject {
                            put("type", "tableRow")
                            putJsonArray("content") {
                                for (c in 0 until cols()) {
                                    val id = grid[r][c]
                                    val a = anchorMap[id] ?: continue
                                    if (a.row != r || a.col != c) continue // emit only the anchor slot
                                    val content = cells[id] ?: continue
                                    add(
                                        buildJsonObject {
                                            put("type", if (content.isHeader) "tableHeader" else "tableCell")
                                            putJsonObject("attrs") {
                                                put("colspan", a.colSpan)
                                                put("rowspan", a.rowSpan)
                                                put("colwidth", JsonNull)
                                            }
                                            putJsonArray("content") { add(paragraphJson(content.text)) }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
        return root.toString()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(rawJson: String): EditableTableState =
            runCatching { parse(rawJson) }.getOrNull() ?: default()

        private fun default(): EditableTableState {
            val grid = MutableList(3) { r -> MutableList(3) { c -> (r * 3 + c).toLong() } }
            val cells = HashMap<Long, CellContent>()
            for (r in 0..2) for (c in 0..2) {
                val id = (r * 3 + c).toLong()
                cells[id] = CellContent(if (r == 0) "Encabezado ${c + 1}" else "", r == 0)
            }
            return EditableTableState(grid, cells, 9L)
        }

        private fun parse(rawJson: String): EditableTableState {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            require(obj["type"]?.jsonPrimitive?.content == "table")
            val rowsJson = obj["content"]?.jsonArray ?: JsonArray(emptyList())

            val grid: MutableList<MutableList<Long>> = ArrayList()
            val cells = HashMap<Long, CellContent>()
            var nextId = 0L

            fun ensure(r: Int, c: Int) {
                while (grid.size <= r) grid.add(ArrayList())
                val row = grid[r]
                while (row.size <= c) row.add(-1L)
            }

            fun get(r: Int, c: Int): Long {
                if (r >= grid.size) return -1L
                val row = grid[r]
                if (c >= row.size) return -1L
                return row[c]
            }

            fun set(r: Int, c: Int, id: Long) { ensure(r, c); grid[r][c] = id }

            rowsJson.forEachIndexed { r, rowEl ->
                ensure(r, 0)
                val cs = rowEl.jsonObject["content"]?.jsonArray ?: JsonArray(emptyList())
                var c = 0
                cs.forEach { cellEl ->
                    val cell = cellEl.jsonObject
                    while (get(r, c) != -1L) c++
                    val attrs = cell["attrs"]?.jsonObject
                    val colspan = attrs?.get("colspan")?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val rowspan = attrs?.get("rowspan")?.jsonPrimitive?.content?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val isHeader = cell["type"]?.jsonPrimitive?.content == "tableHeader"
                    val id = nextId++
                    cells[id] = CellContent(cellText(cell["content"]?.jsonArray), isHeader)
                    for (dr in 0 until rowspan) for (dc in 0 until colspan) set(r + dr, c + dc, id)
                    c += colspan
                }
            }

            require(grid.isNotEmpty())
            // Rectangularize: pad every row to the widest and fill any holes with fresh single cells.
            val cols = grid.maxOf { it.size }.coerceAtLeast(1)
            for (r in grid.indices) {
                ensure(r, cols - 1)
                val row = grid[r]
                for (c in 0 until cols) {
                    if (row[c] == -1L) {
                        val id = nextId++
                        cells[id] = CellContent("", false)
                        row[c] = id
                    }
                }
            }
            return EditableTableState(grid, cells, nextId)
        }
    }
}

/** Concatenates all `text` leaves inside a cell's paragraph content. */
private fun cellText(paragraphs: JsonArray?): String {
    if (paragraphs == null) return ""
    val builder = StringBuilder()
    paragraphs.forEach { paragraph ->
        paragraph.jsonObject["content"]?.jsonArray?.forEach { inline ->
            inline.jsonObject["text"]?.jsonPrimitive?.content?.let { builder.append(it) }
        }
    }
    return builder.toString()
}

private fun paragraphJson(text: String): JsonObject = buildJsonObject {
    put("type", "paragraph")
    putJsonArray("content") {
        if (text.isNotEmpty()) {
            add(buildJsonObject { put("type", "text"); put("text", text) })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private const val SampleTableJson = """
{
  "type": "table",
  "content": [
    { "type": "tableRow", "content": [
      { "type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Producto"}]}] },
      { "type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Región"}]}] },
      { "type": "tableHeader", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Ventas"}]}] }
    ]},
    { "type": "tableRow", "content": [
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Laptop"}]}] },
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Norte"}]}] },
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"1200"}]}] }
    ]},
    { "type": "tableRow", "content": [
      { "type": "tableCell", "attrs": {"colspan": 2, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"Total parcial"}]}] },
      { "type": "tableCell", "attrs": {"colspan": 1, "rowspan": 1, "colwidth": null}, "content": [{"type":"paragraph","content":[{"type":"text","text":"1200"}]}] }
    ]}
  ]
}
"""

@Preview(showBackground = true, backgroundColor = 0xFFF5FBF7, widthDp = 560, heightDp = 620)
@Composable
private fun TextKitEditableTablePreview() {
    var synced by remember { mutableStateOf("") }

    TextKitTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(TextKitTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextKitEditableTable(
                rawJson = SampleTableJson,
                onSync = { synced = it },
            )
            if (synced.isNotEmpty()) {
                HorizontalDivider(color = TextKitTheme.colors.outlineVariant)
                Text(
                    text = "JSON sincronizado",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextKitTheme.colors.onBackground,
                )
                Text(
                    text = synced,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextKitTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1513, widthDp = 560, heightDp = 460)
@Composable
private fun TextKitEditableTableDarkPreview() {
    TextKitTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(TextKitTheme.colors.background)
                .padding(16.dp),
        ) {
            TextKitEditableTable(rawJson = SampleTableJson, onSync = {})
        }
    }
}
