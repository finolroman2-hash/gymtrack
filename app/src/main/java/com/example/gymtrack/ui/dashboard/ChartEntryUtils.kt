package com.example.gymtrack.ui.dashboard

import com.patrykandpatrick.vico.core.entry.FloatEntry
import kotlin.math.roundToInt

/** Vico 1.14 admite como máximo 2 decimales en el eje X. */
fun Float.toVicoX(): Float = (this * 100f).roundToInt() / 100f

fun vicoEntry(x: Float, y: Float): FloatEntry = FloatEntry(x.toVicoX(), y)

fun List<FloatEntry>.withVicoXPrecision(): List<FloatEntry> =
    map { vicoEntry(it.x, it.y) }
