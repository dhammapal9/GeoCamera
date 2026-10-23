package com.idp.geostampcamera.ui.common

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.idp.geostampcamera.R
import com.idp.geostampcamera.ads.AdsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Common ad entry point used by every fragment. Layouts drop in
 * `<include layout="@layout/view_ad_container" />` (or inline a FrameLayout with
 * `android:id="@+id/adContainer"`) and fragments call `AdBanner.install(this)` once.
 */
object AdBanner {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AdsEntryPoint {
        fun adsManager(): AdsManager
    }

    fun install(container: ViewGroup) {
        val mgr = EntryPointAccessors
            .fromApplication(container.context.applicationContext, AdsEntryPoint::class.java)
            .adsManager()
        mgr.installBanner(container)
    }

    /** Installs the shared banner into whichever `R.id.adContainer` the fragment exposes.
     *  No-op if the fragment's layout has no ad container or if the view is gone. */
    fun install(fragment: Fragment) {
        val root = fragment.view ?: return
        val container = root.findViewById<ViewGroup>(R.id.adContainer) ?: return
        install(container)
    }
}
