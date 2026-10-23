package com.idp.geostampcamera.ui.main

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.MaterialColors
import com.idp.geostampcamera.R
import com.idp.geostampcamera.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()
    private val bottomNav: View get() = binding.root.findViewById(R.id.bottomNav)

    private data class NavSlot(
        val destinationId: Int,
        val container: LinearLayout,
        val pill: View,
        val icon: ImageView,
        val label: TextView,
        val isCamera: Boolean = false,
    )

    private lateinit var navSlots: List<NavSlot>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GeoStampCamera_NoActionBar)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val host = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = host.navController

        setupCustomBottomNav()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val fullScreen = destination.id == R.id.cameraFragment ||
                destination.id == R.id.qrFragment ||
                destination.id == R.id.photoViewerFragment ||
                destination.id == R.id.onboardingFragment
            bottomNav.visibility = if (fullScreen) View.GONE else View.VISIBLE
            if (!fullScreen) highlightSlot(destination.id)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, 0)
            insets
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigateToOnboarding.collect { shouldShow ->
                    if (shouldShow) {
                        navController.navigate(R.id.onboardingFragment)
                        viewModel.onboardingShown()
                    }
                }
            }
        }
    }

    private fun setupCustomBottomNav() {
        val root = bottomNav
        fun slot(destId: Int, itemId: Int, pillId: Int, iconId: Int, labelId: Int, isCamera: Boolean = false) = NavSlot(
            destinationId = destId,
            container = root.findViewById(itemId),
            pill = root.findViewById(pillId),
            icon = root.findViewById(iconId),
            label = root.findViewById(labelId),
            isCamera = isCamera,
        )
        navSlots = listOf(
            slot(R.id.homeFragment,      R.id.navHome,      R.id.navHomePill,      R.id.navHomeIcon,      R.id.navHomeLabel),
            slot(R.id.galleryFragment,   R.id.navGallery,   R.id.navGalleryPill,   R.id.navGalleryIcon,   R.id.navGalleryLabel),
            slot(R.id.cameraFragment,    R.id.navCamera,    R.id.navCameraPill,    R.id.navCameraIcon,    R.id.navCameraLabel, isCamera = true),
            slot(R.id.mapViewFragment,   R.id.navMap,       R.id.navMapPill,       R.id.navMapIcon,       R.id.navMapLabel),
            slot(R.id.templatesFragment, R.id.navTemplates, R.id.navTemplatesPill, R.id.navTemplatesIcon, R.id.navTemplatesLabel),
        )
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.startDestinationId, false, true)
            .build()
        navSlots.forEach { s ->
            s.container.setOnClickListener {
                // Little scale-pop on tap for feedback, bigger for the camera FAB.
                val popScale = if (s.isCamera) 1.12f else 1.08f
                s.pill.animate().cancel()
                s.pill.animate().scaleX(popScale).scaleY(popScale).setDuration(90)
                    .withEndAction {
                        s.pill.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    }.start()
                if (navController.currentDestination?.id != s.destinationId) {
                    navController.navigate(s.destinationId, null, navOptions)
                }
            }
        }
        navController.currentDestination?.id?.let(::highlightSlot)
    }

    private fun highlightSlot(currentDestId: Int) {
        val onSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val onPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
        val primary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        navSlots.forEach { s ->
            val selected = s.destinationId == currentDestId
            if (s.isCamera) {
                // Camera FAB stays lit; only the label color + weight change on selection.
                s.icon.setColorFilter(onPrimary)
                s.label.setTextColor(if (selected) primary else onSurfaceVariant)
                s.label.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            } else {
                // Gradient pill + brand-tinted icon + bold label when selected.
                s.pill.background = if (selected)
                    ContextCompat.getDrawable(this, R.drawable.bg_nav_pill_gradient)
                else null
                val iconTint = if (selected) MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSecondaryContainer) else onSurfaceVariant
                s.icon.setColorFilter(iconTint)
                s.label.setTextColor(if (selected) primary else onSurfaceVariant)
                s.label.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                // Scale the icon up a notch when selected.
                val target = if (selected) 1.15f else 1f
                s.icon.animate().cancel()
                s.icon.animate().scaleX(target).scaleY(target).setDuration(220)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .start()
            }
        }
    }
}
