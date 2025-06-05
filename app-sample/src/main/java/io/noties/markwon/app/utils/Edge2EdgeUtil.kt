package io.noties.markwon.app.utils

import android.content.res.Resources
import android.util.TypedValue
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

val Int.vdp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).roundToInt()

fun WindowInsetsCompat.safeDrawing(withIme: Boolean = true): Insets {
    val systemBars = this.getInsets(WindowInsetsCompat.Type.systemBars())
    val displayCutout = this.getInsets(WindowInsetsCompat.Type.displayCutout())
    var res = systemBars.union(displayCutout)
    if (withIme) {
        val ime = this.getInsets(WindowInsetsCompat.Type.ime())
        res = res.union(ime)
    }
    return res
}
fun WindowInsetsCompat.safeGestures(): Insets {
    val tappableElement = this.getInsets(WindowInsetsCompat.Type.tappableElement())
    val mandatorySystemGestures = this.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
    val systemGestures = this.getInsets(WindowInsetsCompat.Type.systemGestures())
    val waterfall = displayCutout?.waterfallInsets ?: Insets.NONE
    return tappableElement.union(mandatorySystemGestures).union(systemGestures).union(waterfall)
}

fun WindowInsetsCompat.safeContent(): Insets {
    return safeDrawing().union(safeGestures())
}

fun Insets.union(other: Insets): Insets {
    return Insets.of(
        maxOf(this.left, other.left),
        maxOf(this.top, other.top),
        maxOf(this.right, other.right),
        maxOf(this.bottom, other.bottom)
    )
}