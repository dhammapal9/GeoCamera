package com.idp.geostampcamera.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.idp.geostampcamera.BuildConfig
import com.idp.geostampcamera.R
import com.idp.geostampcamera.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralised AdMob manager. All ad unit IDs are read from strings.xml so you can swap them
 * via a resource override. Ads are only loaded/shown when:
 *   - `BuildConfig.ADS_ENABLED` is true
 *   - the user is not Premium (`SettingsRepository.isProFlow`)
 *
 * Request a banner via [installBanner]. Request an interstitial / rewarded via [maybeShowInterstitial]
 * / [showRewarded]. The app-open ad is wired automatically through [startAppOpenObserver] called
 * from [GeoStampApp.onCreate].
 */
@Singleton
class AdsManager @Inject constructor(
    private val context: Context,
    private val settings: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var rewardedInterstitial: RewardedInterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private val nativeAdPool = java.util.ArrayDeque<NativeAd>()
    private var nativeLoading = false
    private val maxNativePool = 3
    private var appOpenLoading = false
    private var appOpenShownOnce = false
    private var mobileAdsInitialised = false

    fun init() {
        if (!BuildConfig.ADS_ENABLED) return
        if (!mobileAdsInitialised) {
            runCatching {
                MobileAds.initialize(context) { }
                mobileAdsInitialised = true
            }.onFailure { Log.w(TAG, "MobileAds init failed", it) }
        }
        // Track pro state so we can suppress ads live.
        settings.isProFlow().onEach { _isPro.value = it }.launchIn(scope)
        preloadInterstitial()
        preloadRewarded()
        preloadRewardedInterstitial()
        preloadNative()
        preloadAppOpen()
    }

    // ---------- REWARDED INTERSTITIAL ----------

    private fun preloadRewardedInterstitial() {
        if (!shouldShowAds() || rewardedInterstitial != null) return
        RewardedInterstitialAd.load(
            context,
            context.getString(R.string.admob_rewarded_interstitial),
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) { rewardedInterstitial = ad }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    rewardedInterstitial = null; Log.w(TAG, "RewardedInterstitial load failed: $err")
                }
            }
        )
    }

    /** Show a rewarded-interstitial. Falls through to [onUnavailable] when nothing is cached. */
    fun showRewardedInterstitial(
        activity: Activity,
        onRewardGranted: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedInterstitial
        if (!shouldShowAds()) { onRewardGranted(); return }
        if (ad == null) { onUnavailable(); preloadRewardedInterstitial(); return }
        var granted = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedInterstitial = null; preloadRewardedInterstitial()
                if (granted) onRewardGranted() else onUnavailable()
            }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                rewardedInterstitial = null; preloadRewardedInterstitial(); onUnavailable()
            }
        }
        ad.show(activity) { granted = true }
    }

    // ---------- NATIVE ----------

    private fun preloadNative() {
        if (!shouldShowAds() || nativeLoading) return
        val need = (maxNativePool - nativeAdPool.size).coerceAtLeast(0)
        if (need == 0) return
        nativeLoading = true
        AdLoader.Builder(context, context.getString(R.string.admob_native))
            .forNativeAd { ad -> synchronized(nativeAdPool) { nativeAdPool.add(ad) } }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(err: LoadAdError) {
                    nativeLoading = false
                    Log.w(TAG, "Native load failed: $err")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAds(AdRequest.Builder().build(), need)
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ nativeLoading = false }, 5_000)
    }

    /** Returns a cached native ad (if any) and tops up the pool in the background. */
    fun takeNativeAd(): NativeAd? {
        val ad = synchronized(nativeAdPool) { nativeAdPool.pollFirst() }
        if (nativeAdPool.size < maxNativePool / 2) preloadNative()
        return ad
    }

    // ---------- BANNER ----------

    fun installBanner(container: ViewGroup) {
        if (!shouldShowAds()) {
            Log.d(TAG, "installBanner skipped — ADS_ENABLED=${BuildConfig.ADS_ENABLED}, isPro=${_isPro.value}")
            container.visibility = android.view.View.GONE
            return
        }
        // Make sure MobileAds is initialised. Safe to call repeatedly.
        if (!mobileAdsInitialised) {
            runCatching { MobileAds.initialize(container.context.applicationContext) {} }
            mobileAdsInitialised = true
        }
        val unitId = container.context.getString(R.string.admob_banner)
        // Reveal the container first so it participates in layout, then install the AdView
        // once we actually have a measured width. For includes, the FrameLayout starts GONE
        // (0×0); without this doOnLayout the ad size would be computed from a width of 0.
        container.visibility = android.view.View.VISIBLE
        container.doOnLayout {
            if (container.childCount > 0) return@doOnLayout  // already installed
            Log.d(TAG, "installBanner → unit=$unitId, containerWidth=${container.width}")
            val adView = AdView(container.context).apply {
                setAdSize(adaptiveSize(container.context, container.width))
                adUnitId = unitId
                adListener = object : com.google.android.gms.ads.AdListener() {
                    override fun onAdLoaded() { Log.d(TAG, "Banner loaded ($unitId)") }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        Log.w(TAG, "Banner failed to load ($unitId): $err")
                    }
                }
            }
            container.addView(
                adView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
            )
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    // ---------- INTERSTITIAL ----------

    private fun preloadInterstitial() {
        if (!shouldShowAds() || interstitial != null) return
        InterstitialAd.load(
            context,
            context.getString(R.string.admob_interstitial),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    interstitial = null; Log.w(TAG, "Interstitial load failed: $err")
                }
            }
        )
    }

    /**
     * Show an interstitial if one is loaded. Pass [frequencyThrottleMs] to avoid showing more than
     * once per that interval. The onDone callback fires whether we show the ad or not — it is used
     * to continue the user's workflow (e.g. navigate to the viewer).
     */
    fun maybeShowInterstitial(
        activity: Activity,
        frequencyThrottleMs: Long = 60_000L,
        onDone: () -> Unit
    ) {
        val ad = interstitial
        val now = System.currentTimeMillis()
        if (!shouldShowAds() || ad == null || now - lastInterstitialShownAt < frequencyThrottleMs) {
            onDone(); preloadInterstitial(); return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis()
                interstitial = null
                preloadInterstitial()
                onDone()
            }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                interstitial = null; preloadInterstitial(); onDone()
            }
        }
        ad.show(activity)
    }
    private var lastInterstitialShownAt: Long = 0L

    // ---------- REWARDED ----------

    private fun preloadRewarded() {
        if (!shouldShowAds() || rewarded != null) return
        RewardedAd.load(
            context,
            context.getString(R.string.admob_rewarded),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    rewarded = null; Log.w(TAG, "Rewarded load failed: $err")
                }
            }
        )
    }

    /** Show a rewarded ad. [onRewardGranted] fires only after the user earns the reward. */
    fun showRewarded(activity: Activity, onRewardGranted: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded
        if (!shouldShowAds()) { onRewardGranted(); return }
        if (ad == null) { onUnavailable(); preloadRewarded(); return }
        var rewarded = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                this@AdsManager.rewarded = null
                preloadRewarded()
                if (rewarded) onRewardGranted() else onUnavailable()
            }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                this@AdsManager.rewarded = null; preloadRewarded(); onUnavailable()
            }
        }
        ad.show(activity) { rewarded = true }
    }

    // ---------- APP OPEN ----------

    fun startAppOpenObserver(app: Application) {
        if (!BuildConfig.ADS_ENABLED) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityStarted(a: Activity) { currentActivity = a }
            override fun onActivityResumed(a: Activity) { currentActivity = a }
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { showAppOpenIfAvailable() }
        })
    }

    private var currentActivity: Activity? = null

    private fun preloadAppOpen() {
        if (!shouldShowAds() || appOpenLoading || appOpenAd != null) return
        appOpenLoading = true
        AppOpenAd.load(
            context,
            context.getString(R.string.admob_app_open),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) { appOpenAd = ad; appOpenLoading = false }
                override fun onAdFailedToLoad(err: LoadAdError) {
                    appOpenAd = null; appOpenLoading = false
                    Log.w(TAG, "AppOpen load failed: $err")
                }
            }
        )
    }

    private fun showAppOpenIfAvailable() {
        val act = currentActivity ?: return
        val ad = appOpenAd ?: run { preloadAppOpen(); return }
        if (!shouldShowAds() || appOpenShownOnce) return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                appOpenShownOnce = true
                preloadAppOpen()
            }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                appOpenAd = null; preloadAppOpen()
            }
        }
        ad.show(act)
    }

    // ---------- helpers ----------

    private fun shouldShowAds(): Boolean =
        BuildConfig.ADS_ENABLED && !_isPro.value

    private fun adaptiveSize(ctx: Context, widthPx: Int): AdSize {
        val metrics: DisplayMetrics = ctx.resources.displayMetrics
        // If the container hasn't been laid out yet (width == 0), fall back to the display width.
        val resolvedPx = if (widthPx > 0) widthPx else metrics.widthPixels
        val widthDp = (resolvedPx / metrics.density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp)
    }

    companion object { private const val TAG = "AdsManager" }
}
