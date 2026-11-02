package n7.glyphpet.billing

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import n7.glyphpet.pet.PetStore

/**
 * The one-time "Supporter" unlock (Google Play in-app product [PRODUCT_ID], non-consumable):
 * a second egg every day, a golden egg and one instant hatch a day.
 * The unlock is remembered in [PetStore] and re-checked against Play on every start.
 */
class SupporterBilling(context: Context, private val store: PetStore) {

    companion object {
        const val PRODUCT_ID = "supporter"
        private const val TAG = "SupporterBilling"
    }

    private val _price = MutableStateFlow<String?>(null)
    /** Localised price from Play, or null while unknown (product not set up yet, offline). */
    val price: StateFlow<String?> = _price

    private val _owned = MutableStateFlow(false)
    val owned: StateFlow<Boolean> = _owned

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) { refresh(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else Log.w(TAG, "Billing setup: ${result.debugMessage}")
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun disconnect() = client.endConnection()

    /** Opens the Google Play purchase sheet. */
    fun buy(activity: Activity) {
        val product = details ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun refresh() {
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(query) { _, result ->
            details = result.productDetailsList.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val supporter = purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            store.setSupporter(supporter)
            _owned.value = supporter
            purchases.forEach(::handle)
        }
    }

    private fun handle(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        store.setSupporter(true)
        _owned.value = true
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r ->
                Log.d(TAG, "Acknowledge: ${r.responseCode}")
            }
        }
    }
}
