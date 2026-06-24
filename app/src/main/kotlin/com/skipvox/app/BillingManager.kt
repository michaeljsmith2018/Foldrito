package com.skipvox.app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchaseHistoryParams
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing for SkipVox subscriptions.
 *
 * SKUs defined in Google Play Console:
 * - Monthly: skipvox_premium_monthly ($2.99/mo)
 * - Yearly:  skipvox_premium_yearly  ($19.99/yr)
 */
class BillingManager(private val context: Context) {

    companion object {
        private const val TAG = "SkipVoxBilling"

        // Subscription product IDs — must match Google Play Console
        const val SKU_MONTHLY = "skipvox_premium_monthly"
        const val SKU_YEARLY = "skipvox_premium_yearly"
    }

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Product details fetched from Google Play
    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    // Whether user has an active subscription
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Active purchase tokens for acknowledgment
    private var activePurchases = mutableListOf<Purchase>()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            handlePurchasesUpdated(billingResult, purchases)
        }
        .enablePendingPurchases()
        .build()

    /** Start connection to Google Play Billing. Should be called early in Activity lifecycle. */
    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _isConnected.value = true
            onConnected?.invoke()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Billing client connected successfully")
                    _isConnected.value = true
                    queryProductDetails()
                    queryExistingPurchases()
                    onConnected?.invoke()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
                // Optionally retry connection after a delay
            }
        })
    }

    /** Disconnect from Google Play Billing. Call when Activity is destroyed. */
    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
        _isConnected.value = false
    }

    /**
     * Query Google Play for subscription product details (title, price, etc.)
     */
    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_MONTHLY)
                .setProductType(ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_YEARLY)
                .setProductType(ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, details ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = details
                Log.i(TAG, "Product details received: ${details.size} products")
                details.forEach { detail ->
                    detail.subscriptionOfferDetails?.forEach { offer ->
                        val basePlan = offer.pricingPhases.pricingPhaseList.firstOrNull()
                        Log.d(TAG, "  Product: ${detail.productId}, Price: ${basePlan?.formattedPrice}, " +
                                "Interval: ${basePlan?.billingPeriod}")
                    }
                }
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Query existing purchases to restore subscription status.
     */
    private fun queryExistingPurchases() {
        val purchasesResult = billingClient.queryPurchasesAsync(
            QueryPurchaseHistoryParams.newBuilder()
                .setProductType(ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            Log.d(TAG, "Purchase history query result: ${billingResult.responseCode}")
        }

        // Simpler: just check current purchases
        val result = billingClient.queryPurchasesAsync(
            com.android.billingclient.api.QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handleActivePurchases(purchases)
            } else {
                Log.e(TAG, "Failed to query purchases: ${billingResult.responseCode}")
            }
        }
    }

    /**
     * Process purchase update callback from BillingClient.
     */
    private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.i(TAG, "Purchases updated: ${purchases.size} purchase(s)")
            handleActivePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "Purchase flow canceled by user")
        } else {
            Log.e(TAG, "Purchase update error: ${billingResult.responseCode} ${billingResult.debugMessage}")
        }
    }

    /**
     * Validate and acknowledge active purchases, then update premium status.
     */
    private fun handleActivePurchases(purchases: List<Purchase>) {
        var hasValidSubscription = false

        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    // Only consider subscriptions that are auto-renewing or entitled
                    if (purchase.products.any { it == SKU_MONTHLY || it == SKU_YEARLY }) {
                        hasValidSubscription = true
                        activePurchases.add(purchase)

                        // Must acknowledge purchases within 3 days or they're refunded
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase.purchaseToken)
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.d(TAG, "Purchase pending for: ${purchase.products}")
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    Log.w(TAG, "Purchase in unspecified state: ${purchase.products}")
                }
            }
        }

        _isPremium.value = hasValidSubscription
        SkipVoxState.setPremium(hasValidSubscription)
        Log.i(TAG, "Premium status updated: active=$hasValidSubscription")
    }

    /**
     * Acknowledge a purchase so Google does not auto-refund.
     */
    private fun acknowledgePurchase(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Purchase acknowledged successfully")
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.responseCode}")
            }
        }
    }

    /**
     * Launch the purchase flow for a subscription.
     * @param activity The calling Activity (required for BillingFlow)
     * @param productId The SKU to purchase (SKU_MONTHLY or SKU_YEARLY)
     */
    fun launchPurchaseFlow(activity: Activity, productId: String) {
        // Check billing is ready
        if (!billingClient.isReady) {
            Log.e(TAG, "Billing client not ready — reconnecting...")
            startConnection {
                launchPurchaseFlow(activity, productId)
            }
            return
        }

        // Find the ProductDetails for the requested product
        val details = _productDetails.value.find { it.productId == productId }
        if (details == null) {
            Log.e(TAG, "Product details not found for: $productId")
            // Re-query and try again
            queryProductDetails()
            return
        }

        // Get the base offer token from subscriptionOfferDetails
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "No subscription offer found for: $productId")
            return
        }

        val productDetailsParams = ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "Billing flow launched for: $productId")
        } else {
            Log.e(TAG, "Failed to launch billing flow: ${billingResult.responseCode} ${billingResult.debugMessage}")
        }
    }

    /**
     * Restore purchases by re-querying Google Play.
     */
    fun restorePurchases() {
        Log.i(TAG, "Restoring purchases...")
        queryExistingPurchases()
    }

    /**
     * Get the formatted price for a product.
     */
    fun getFormattedPrice(productId: String): String {
        return _productDetails.value
            .find { it.productId == productId }
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
            ?: if (productId == SKU_MONTHLY) "$2.99" else "$19.99"
    }

    /**
     * Get the billing period label for a product (e.g. "month", "year").
     */
    fun getBillingPeriod(productId: String): String {
        return _productDetails.value
            .find { it.productId == productId }
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.billingPeriod
            ?: if (productId == SKU_MONTHLY) "P1M" else "P1Y"
    }
}