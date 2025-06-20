package io.noties.markwon.app.sample

import android.os.Bundle
import android.view.Window
import android.window.OnBackInvokedCallback
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.noties.debug.Debug
import io.noties.markwon.app.App
import io.noties.markwon.app.sample.ui.SampleFragment
import io.noties.markwon.app.sample.ui.SampleListFragment

class MainActivity : FragmentActivity() {

    private val mOnBackPressedCallback = object : OnBackPressedCallback(true){
        override fun handleOnBackPressed() {
            supportFragmentManager.popBackStack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        enableEdgeToEdge()

        if (supportFragmentManager.findFragmentById(Window.ID_ANDROID_CONTENT) == null) {

            supportFragmentManager.beginTransaction()
                    .add(Window.ID_ANDROID_CONTENT, SampleListFragment.init())
                    .commitNowAllowingStateLoss()

            // process deeplink if we are not restored
            val deeplink = Deeplink.parse(intent.data)

            val deepLinkFragment: Fragment? = if (deeplink != null) {
                when (deeplink) {
                    is Deeplink.Sample -> App.sampleManager.sample(deeplink.id)
                            ?.let { SampleFragment.init(it) }
                    is Deeplink.Search -> SampleListFragment.init(deeplink.search)
                }
            } else null

            if (deepLinkFragment != null) {
                supportFragmentManager.beginTransaction()
                        .replace(Window.ID_ANDROID_CONTENT, deepLinkFragment)
                        .addToBackStack(null)
                        .commit()
            }
        }
    }

    fun stopBackPress(stop: Boolean){
        mOnBackPressedCallback.isEnabled = stop
    }
}