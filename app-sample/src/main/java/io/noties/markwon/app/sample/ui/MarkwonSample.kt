package io.noties.markwon.app.sample.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

abstract class MarkwonSample {

    lateinit var fragment: Fragment

    fun createView(inflater: LayoutInflater, container: ViewGroup?): View {
        return inflater.inflate(layoutResId, container, false)
    }

    abstract fun onViewCreated(view: View)

    protected abstract val layoutResId: Int
}