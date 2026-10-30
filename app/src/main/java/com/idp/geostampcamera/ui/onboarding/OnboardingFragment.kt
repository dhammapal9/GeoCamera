package com.idp.geostampcamera.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.repository.SettingsRepository
import com.idp.geostampcamera.databinding.FragmentOnboardingBinding
import com.idp.geostampcamera.databinding.ItemOnboardingPageBinding
import com.idp.geostampcamera.util.setDebouncedClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    @Inject lateinit var settings: SettingsRepository

    private data class Page(
        @StringRes val title: Int,
        @StringRes val body: Int,
        @DrawableRes val icon: Int,
        @DrawableRes val heroBg: Int,
    )

    private val pages = listOf(
        Page(R.string.onboarding_1_title, R.string.onboarding_1_body,
            R.drawable.ic_location, R.drawable.bg_onboarding_hero_1),
        Page(R.string.onboarding_2_title, R.string.onboarding_2_body,
            R.drawable.ic_templates, R.drawable.bg_onboarding_hero_2),
        Page(R.string.onboarding_3_title, R.string.onboarding_3_body,
            R.drawable.ic_check, R.drawable.bg_onboarding_hero_3),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Onboarding is a fullscreen destination (bottom nav hidden), so the ad container
        // is constraint-anchored to the parent bottom. Pad the root for the system nav bar
        // so the ad sits above the gesture/3-button bar on edge-to-edge Android 15+.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
        // Apply synchronously too — the parent activity consumed the initial inset dispatch
        // before this fragment attached, so the listener alone would not fire until the next
        // config change and the ad would overlap the nav bar on first launch.
        ViewCompat.getRootWindowInsets(binding.root)?.let { insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                bars.bottom
            )
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.onboardingPager.adapter = Adapter()
        binding.onboardingPager.offscreenPageLimit = 1
        binding.onboardingPager.setPageTransformer(HeroParallaxTransformer())

        buildDots()

        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.btnNext.setText(
                    if (position == pages.lastIndex) R.string.onboarding_get_started
                    else R.string.onboarding_next
                )
                updateDots(position)
                animateActions()
            }
        })
        updateDots(0)

        binding.btnNext.setDebouncedClickListener {
            val current = binding.onboardingPager.currentItem
            if (current < pages.lastIndex) {
                binding.onboardingPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
        binding.btnSkip.setDebouncedClickListener { finishOnboarding() }
    }

    private fun buildDots() {
        val density = resources.displayMetrics.density
        pages.forEachIndexed { i, _ ->
            val dot = View(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    (8 * density).toInt(), (8 * density).toInt()
                ).apply { marginEnd = (if (i < pages.lastIndex) 10 * density else 0f).toInt() }
                background = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), R.drawable.bg_onboarding_dot_inactive
                )
            }
            binding.dotsIndicator.addView(dot)
        }
    }

    private fun updateDots(selected: Int) {
        val density = resources.displayMetrics.density
        for (i in 0 until binding.dotsIndicator.childCount) {
            val dot = binding.dotsIndicator.getChildAt(i)
            val isActive = i == selected
            val targetWidth = (if (isActive) 26 else 8).toFloat() * density
            dot.animate().cancel()
            // Animate width via layoutParams; this is cheap for 3 items.
            val from = dot.layoutParams.width
            val animator = android.animation.ValueAnimator.ofInt(from, targetWidth.toInt())
            animator.duration = 260
            animator.interpolator = android.view.animation.DecelerateInterpolator()
            animator.addUpdateListener { a ->
                dot.layoutParams = dot.layoutParams.apply { width = a.animatedValue as Int }
            }
            animator.start()
            dot.background = androidx.core.content.ContextCompat.getDrawable(
                requireContext(),
                if (isActive) R.drawable.bg_onboarding_dot_active
                else R.drawable.bg_onboarding_dot_inactive
            )
        }
    }

    private fun animateActions() {
        binding.btnNext.animate()
            .scaleX(1.06f).scaleY(1.06f).setDuration(140)
            .withEndAction {
                binding.btnNext.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }.start()
    }

    private fun finishOnboarding() {
        viewLifecycleOwner.lifecycleScope.launch {
            settings.setOnboardingComplete(true)
            findNavController().popBackStack()
        }
    }

    override fun onViewStateRestored(savedInstanceState: android.os.Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = pages.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = pages[position]
            holder.b.pageTitle.setText(page.title)
            holder.b.pageBody.setText(page.body)
            holder.b.pageIcon.setImageResource(page.icon)
            holder.b.heroPanel.setBackgroundResource(page.heroBg)
            holder.b.stepChip.text = holder.b.root.context.getString(R.string.onboarding_step, position + 1, pages.size)
            // Entrance animations when the page is bound (first show or returning to it).
            holder.b.heroIconHolder.startAnimation(
                AnimationUtils.loadAnimation(holder.b.root.context, R.anim.onboarding_hero_in)
            )
            holder.b.pageTitle.startAnimation(
                AnimationUtils.loadAnimation(holder.b.root.context, R.anim.onboarding_text_in)
            )
            holder.b.pageBody.startAnimation(
                AnimationUtils.loadAnimation(holder.b.root.context, R.anim.onboarding_text_in).apply {
                    startOffset = 120
                }
            )
        }
    }

    /** Parallax effect: the hero shifts half the swipe distance and the text fades. */
    private class HeroParallaxTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val hero = page.findViewById<View>(R.id.heroPanel)
            val text = page.findViewById<View>(R.id.textZone)
            val icon = page.findViewById<View>(R.id.heroIconHolder)
            val pageWidth = page.width.toFloat()
            if (pageWidth <= 0f) return
            when {
                position < -1f || position > 1f -> {
                    page.alpha = 0f
                }
                else -> {
                    val absPos = abs(position)
                    page.alpha = 1f - absPos * 0.25f
                    // Hero slides slower → feels deeper.
                    hero?.translationX = -position * pageWidth * 0.35f
                    icon?.translationX = -position * pageWidth * 0.15f
                    icon?.scaleX = 1f - absPos * 0.08f
                    icon?.scaleY = 1f - absPos * 0.08f
                    // Text fades out and slides down slightly as it leaves.
                    text?.translationY = absPos * 40f
                    text?.alpha = 1f - absPos
                }
            }
        }
    }
}
