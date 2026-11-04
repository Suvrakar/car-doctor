package com.garidoctor.app.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.garidoctor.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * AdMob in the free tier only: a small banner on the home screen, and a rewarded ad that unlocks
 * one free Vehicle Passport (or other Pro report) without buying Pro. Pro users never see ads.
 */
object Ads {
    private var rewarded: RewardedAd? = null
    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        initialised = true
        MobileAds.initialize(context) { preloadRewarded(context) }
    }

    fun preloadRewarded(context: Context) {
        if (rewarded != null) return
        RewardedAd.load(context, BuildConfig.ADMOB_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null }
        })
    }

    val rewardedReady: Boolean get() = rewarded != null

    /** Shows the rewarded ad; [onReward] runs only if the user watched it to the end. */
    fun showRewarded(activity: Activity, onReward: () -> Unit): Boolean {
        val ad = rewarded ?: run { preloadRewarded(activity); return false }
        rewarded = null
        ad.show(activity) { onReward() }
        preloadRewarded(activity)
        return true
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier.fillMaxWidth()) {
    AndroidView(modifier = modifier, factory = { ctx ->
        AdView(ctx).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER
            loadAd(AdRequest.Builder().build())
        }
    })
}
