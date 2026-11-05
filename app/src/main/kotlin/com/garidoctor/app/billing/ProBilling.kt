package com.garidoctor.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.garidoctor.app.BuildConfig
import com.garidoctor.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One-time "Car Doctor AI Pro" unlock through Google Play Billing (in-app product [BuildConfig.PRO_PRODUCT_ID],
 * priced ৳299–499 in Play Console). Restores the unlock on every start. Promo codes are a separate path.
 */
class ProBilling(context: Context, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var product: ProductDetails? = null
    private val _price = MutableStateFlow<String?>(null)
    /** Localised price from Play, null until Play responds (or if Play is unavailable). */
    val price: StateFlow<String?> = _price

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.let { handle(it) } }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) { Log.i(TAG, "billing unavailable: ${result.debugMessage}"); return }
                scope.launch { loadProduct(); restore() }
            }
            override fun onBillingServiceDisconnected() { /* reconnect lazily on next purchase */ }
        })
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(BuildConfig.PRO_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build(),
        )).build()
        val res = client.queryProductDetails(params)
        product = res.productDetailsList?.firstOrNull()
        _price.value = product?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    private suspend fun restore() {
        val res = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        handle(res.purchasesList)
    }

    private fun handle(purchases: List<Purchase>) {
        purchases.filter { BuildConfig.PRO_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
            prefs.setPro(true, "play:${p.orderId}")
            if (!p.isAcknowledged) scope.launch {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
            }
        }
    }

    /** Returns false if Play Billing isn't ready (e.g. sideloaded build); the UI then offers promo codes only. */
    fun buy(activity: Activity): Boolean {
        val pd = product ?: run { start(); return false }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build(),
        )).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    companion object { private const val TAG = "ProBilling" }
}
