package com.jjrodcast.textkit

import kotlin.test.Test

/**
 * The full seeded sweep, kept on the JVM target only.
 *
 * It runs the same operations and invariants as the cross-platform [EditingStressTest], just far
 * more of them: 400 seeds × 250 operations. That is a single synchronous run of a hundred thousand
 * edits, which starves the browser test runner's event loop — Karma kills a tab that cannot answer
 * a ping within two seconds — so the wide search runs here and the other targets keep the smoke run.
 *
 * Reverting any of #87, #95, #97 or #99 makes this fail with the exact seed, step and operation.
 */
class EditingStressSweepTest {

    @Test
    fun the_full_sweep_keeps_the_document_consistent() {
        EditingStress.run(0 until 400, 250)
    }
}
