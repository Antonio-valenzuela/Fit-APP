package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.BuildConfig

data class PremiumOffer(
    val title: String,
    val price: String,
    val description: String
)

class PremiumBillingManager(
    context: Context,
    private val onPremiumChanged: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start(onOfferLoaded: (PremiumOffer?) -> Unit) {
        if (!isProductConfigured()) {
            post { onOfferLoaded(null) }
            post { onMessage("Configura PREMIUM_SUBSCRIPTION_ID en Play Console y .env.") }
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPremiumOffer(onOfferLoaded)
                    restorePurchases()
                } else {
                    post { onMessage("Billing no disponible: ${result.debugMessage}") }
                    post { onOfferLoaded(null) }
                }
            }

            override fun onBillingServiceDisconnected() {
                post { onMessage("Play Billing se desconecto. Intenta de nuevo.") }
            }
        })
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            onMessage("El producto Premium aun no esta disponible en Play Console.")
            return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (!offerToken.isNullOrBlank()) setOfferToken(offerToken) }
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    fun end() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::handlePurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> post { onMessage("Compra cancelada.") }
            else -> post { onMessage("No se pudo completar la compra: ${result.debugMessage}") }
        }
    }

    private fun queryPremiumOffer(onOfferLoaded: (PremiumOffer?) -> Unit) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PREMIUM_SUBSCRIPTION_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productResult ->
            val products = productResult.productDetailsList
            if (result.responseCode != BillingClient.BillingResponseCode.OK || products.isEmpty()) {
                post { onOfferLoaded(null) }
                post { onMessage("No encontre el producto Premium en Play Console.") }
                return@queryProductDetailsAsync
            }
            val details = products.first()
            productDetails = details
            val phase = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
            post {
                onOfferLoaded(
                    PremiumOffer(
                        title = details.title,
                        price = phase?.formattedPrice ?: "Precio en Play",
                        description = details.description
                    )
                )
            }
        }
    }

    private fun restorePurchases() {
        if (!billingClient.isReady || !isProductConfigured()) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any {
                    it.products.contains(BuildConfig.PREMIUM_SUBSCRIPTION_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                post { onPremiumChanged(active) }
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach(::acknowledgeIfNeeded)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(BuildConfig.PREMIUM_SUBSCRIPTION_ID)) return
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            post { onPremiumChanged(true) }
            acknowledgeIfNeeded(purchase)
            post { onMessage("Premium activado.") }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                post { onMessage("Compra recibida, pero falta confirmarla: ${result.debugMessage}") }
            }
        }
    }

    private fun isProductConfigured(): Boolean {
        val id = BuildConfig.PREMIUM_SUBSCRIPTION_ID
        return id.isNotBlank() && id != "MY_PREMIUM_SUBSCRIPTION_ID"
    }

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }
}
