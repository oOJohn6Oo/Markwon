package io.noties.markwon.app.utils

import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.animation.*
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.view.ViewCompat
import androidx.core.view.updatePaddingRelative
import io.noties.markwon.app.databinding.SampleSseCommonBinding

var View.hidden: Boolean
    get() = visibility == GONE
    set(value) {
        visibility = if (value) GONE else VISIBLE
    }

fun View.onPreDraw(action: () -> Unit) {
    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            val vto = viewTreeObserver
            if (vto.isAlive) {
                vto.removeOnPreDrawListener(this)
            }
            action()
            // do not block drawing
            return true
        }
    })
}

var View.active: Boolean
    get() = isActivated
    set(newValue) {
        isActivated = newValue

        (this as? ViewGroup)?.children?.forEach { it.active = newValue }
    }


fun slideInFromLeft(): Animation {
    val slide = TranslateAnimation(
        Animation.RELATIVE_TO_PARENT, -0.5f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f
    )
    val fade = AlphaAnimation(0f, 1f)

    return AnimationSet(true).apply {
        addAnimation(slide)
        addAnimation(fade)
        duration = 200
    }
}

fun slideInFromRight(): Animation {
    val slide = TranslateAnimation(
        Animation.RELATIVE_TO_PARENT, 0.5f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f
    )
    val fade = AlphaAnimation(0f, 1f)

    return AnimationSet(true).apply {
        addAnimation(slide)
        addAnimation(fade)
        duration = 200
    }
}

fun slideOutToLeft(): Animation {
    val slide = TranslateAnimation(
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, -0.5f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f
    )
    val fade = AlphaAnimation(1f, 0f)

    return AnimationSet(true).apply {
        addAnimation(slide)
        addAnimation(fade)
        duration = 200
    }
}

fun slideOutToRight(): Animation {
    val slide = TranslateAnimation(
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0.5f,
        Animation.RELATIVE_TO_PARENT, 0f,
        Animation.RELATIVE_TO_PARENT, 0f
    )
    val fade = AlphaAnimation(1f, 0f)

    return AnimationSet(true).apply {
        addAnimation(slide)
        addAnimation(fade)
        duration = 200
    }
}

fun SampleSseCommonBinding.setupEdge2Edge(){
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val safeDrawing = insets.safeDrawing(false)
        scrollView.updatePaddingRelative(start = safeDrawing.left, end = safeDrawing.right)
        seekContainerFgSse.updatePaddingRelative(start = safeDrawing.left, end = safeDrawing.right)
        speedBtnContainerFgSse.updatePaddingRelative(end = safeDrawing.right)
        insets
    }
}

fun SampleSseCommonBinding.setupSpeedAndSeeker(currentSpeed: Int, updateSpeed: (Int)->Unit){
    switcherFgSse.displayedChild = 0
    btnCollapseFgSse.setOnClickListener {
        switcherFgSse.apply {
            inAnimation = slideInFromLeft()
            outAnimation = slideOutToRight()
            displayedChild = 0
        }
        switcherFgSse.displayedChild = 1
    }
    btnExpandFgSse.setOnClickListener {
        switcherFgSse.apply {
            inAnimation = slideInFromRight()
            outAnimation = slideOutToLeft()
            displayedChild = 0
        }
    }
    seekBarFgSse.setOnSeekBarChangeListener(object : OnSeekBarChangeListener by noOpDelegate() {
        override fun onProgressChanged(seekbar: SeekBar, progress: Int, p2: Boolean) {
            val desireProgress = progress + 1
            updateSpeed(desireProgress)
            titleSpeedFgSse.text = "Speed: $desireProgress"
            btnExpandFgSse.text = desireProgress.toString()
        }
    })
    seekBarFgSse.progress = currentSpeed - 1
}