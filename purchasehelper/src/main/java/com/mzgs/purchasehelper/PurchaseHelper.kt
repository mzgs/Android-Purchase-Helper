package com.mzgs.purchasehelper

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct

class PurchaseHelper(
    context: Context,
    private val listener: Listener? = null,
    enablePendingPrepaidPlans: Boolean = false,
) : PurchasesUpdatedListener {

    interface Listener {
        fun onBillingDisconnected() = Unit
        fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) = Unit
        fun onPurchaseCompleted(purchase: Purchase) = Unit
        fun onPurchasePending(purchase: Purchase) = Unit
        fun onPurchaseError(result: BillingResult) = Unit
    }

    data class ProductQueryResult(
        val billingResult: BillingResult,
        val products: List<ProductDetails>,
        val unfetchedProducts: List<UnfetchedProduct>,
    )

    data class PurchasesQueryResult(
        val billingResult: BillingResult,
        val purchases: List<Purchase>,
    )

    data class ActiveProductsResult(
        val billingResult: BillingResult,
        val purchases: List<Purchase>,
        val inAppPurchases: List<Purchase>,
        val subscriptionPurchases: List<Purchase>,
    )

    data class HasPurchasedProductResult(
        val billingResult: BillingResult,
        val hasPurchased: Boolean,
        val purchases: List<Purchase>,
    )

    data class ActiveSubscriptionResult(
        val billingResult: BillingResult,
        val hasActiveSubscription: Boolean,
        val purchases: List<Purchase>,
    )

    companion object {
        @Volatile
        private var instance: PurchaseHelper? = null

        @JvmStatic
        @JvmOverloads
        fun init(
            context: Context,
            listener: Listener? = null,
            enablePendingPrepaidPlans: Boolean = false,
        ): PurchaseHelper = synchronized(this) {
            val helper = PurchaseHelper(
                context = context,
                listener = listener,
                enablePendingPrepaidPlans = enablePendingPrepaidPlans,
            )
            instance?.endConnection()
            instance = helper
            helper.startConnection()
            helper
        }

        @JvmStatic
        fun getInstance(): PurchaseHelper? = instance

        @JvmStatic
        fun requireInstance(): PurchaseHelper = instance
            ?: error("PurchaseHelper.init(context, listener) must be called before using static methods.")

        @JvmStatic
        fun isInitialized(): Boolean = instance != null

        @JvmStatic
        fun clear() {
            synchronized(this) {
                instance?.endConnection()
                instance = null
            }
        }

        val isReady: Boolean
            get() = requireInstance().isReady

        fun startConnection(callback: (BillingResult) -> Unit = {}) {
            requireInstance().startConnection(callback)
        }

        fun endConnection() {
            requireInstance().endConnection()
        }

        fun getProducts(
            productIds: List<String>,
            @BillingClient.ProductType productType: String,
            callback: (ProductQueryResult) -> Unit,
        ) {
            requireInstance().getProducts(productIds, productType, callback)
        }

        fun getInAppProducts(
            productIds: List<String>,
            callback: (ProductQueryResult) -> Unit,
        ) {
            requireInstance().getInAppProducts(productIds, callback)
        }

        fun getSubscriptionProducts(
            productIds: List<String>,
            callback: (ProductQueryResult) -> Unit,
        ) {
            requireInstance().getSubscriptionProducts(productIds, callback)
        }

        fun buyProduct(
            activity: Activity,
            productDetails: ProductDetails,
            offerToken: String? = null,
            obfuscatedAccountId: String? = null,
            obfuscatedProfileId: String? = null,
        ): BillingResult {
            val helper = requireInstance()
            return helper.buyProduct(
                activity = activity,
                productDetails = productDetails,
                offerToken = offerToken ?: helper.run { productDetails.defaultOfferToken() },
                obfuscatedAccountId = obfuscatedAccountId,
                obfuscatedProfileId = obfuscatedProfileId,
            )
        }

        fun buyProduct(
            activity: Activity,
            productId: String,
            @BillingClient.ProductType productType: String,
            offerToken: String? = null,
            obfuscatedAccountId: String? = null,
            obfuscatedProfileId: String? = null,
            callback: (BillingResult) -> Unit,
        ) {
            requireInstance().buyProduct(
                activity = activity,
                productId = productId,
                productType = productType,
                offerToken = offerToken,
                obfuscatedAccountId = obfuscatedAccountId,
                obfuscatedProfileId = obfuscatedProfileId,
                callback = callback,
            )
        }

        fun queryPurchases(
            @BillingClient.ProductType productType: String,
            includeSuspendedSubscriptions: Boolean = false,
            callback: (PurchasesQueryResult) -> Unit,
        ) {
            requireInstance().queryPurchases(productType, includeSuspendedSubscriptions, callback)
        }

        fun getActiveProducts(
            productIds: Set<String> = emptySet(),
            includeInAppProducts: Boolean = true,
            includeSubscriptions: Boolean = true,
            includeSuspendedSubscriptions: Boolean = false,
            callback: (ActiveProductsResult) -> Unit,
        ) {
            requireInstance().getActiveProducts(
                productIds = productIds,
                includeInAppProducts = includeInAppProducts,
                includeSubscriptions = includeSubscriptions,
                includeSuspendedSubscriptions = includeSuspendedSubscriptions,
                callback = callback,
            )
        }

        fun getActiveInAppProducts(
            productIds: Set<String> = emptySet(),
            callback: (PurchasesQueryResult) -> Unit,
        ) {
            requireInstance().getActiveInAppProducts(productIds, callback)
        }

        fun getActiveSubscriptions(
            productIds: Set<String> = emptySet(),
            includeSuspendedSubscriptions: Boolean = false,
            callback: (PurchasesQueryResult) -> Unit,
        ) {
            requireInstance().getActiveSubscriptions(productIds, includeSuspendedSubscriptions, callback)
        }

        fun hasPurchasedProduct(
            productId: String,
            includeInAppProducts: Boolean = true,
            includeSubscriptions: Boolean = true,
            includeSuspendedSubscriptions: Boolean = false,
            callback: (HasPurchasedProductResult) -> Unit,
        ) {
            requireInstance().hasPurchasedProduct(
                productId = productId,
                includeInAppProducts = includeInAppProducts,
                includeSubscriptions = includeSubscriptions,
                includeSuspendedSubscriptions = includeSuspendedSubscriptions,
                callback = callback,
            )
        }

        fun hasPurchasedInAppProduct(
            productId: String,
            callback: (HasPurchasedProductResult) -> Unit,
        ) {
            requireInstance().hasPurchasedInAppProduct(productId, callback)
        }

        fun hasPurchasedSubscription(
            productId: String,
            includeSuspendedSubscriptions: Boolean = false,
            callback: (HasPurchasedProductResult) -> Unit,
        ) {
            requireInstance().hasPurchasedSubscription(productId, includeSuspendedSubscriptions, callback)
        }

        fun hasActiveSubscription(
            callback: (ActiveSubscriptionResult) -> Unit,
        ) {
            requireInstance().hasActiveSubscription(callback)
        }

        fun acknowledgePurchase(
            purchase: Purchase,
            callback: (BillingResult) -> Unit,
        ) {
            requireInstance().acknowledgePurchase(purchase, callback)
        }

        fun consumePurchase(
            purchase: Purchase,
            callback: (BillingResult, purchaseToken: String) -> Unit,
        ) {
            requireInstance().consumePurchase(purchase, callback)
        }
    }

    private val lock = Any()
    private val connectionCallbacks = mutableListOf<(BillingResult) -> Unit>()
    private var isConnecting = false

    private val billingClient: BillingClient

    init {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .apply {
                if (enablePendingPrepaidPlans) {
                    enablePrepaidPlans()
                }
            }
            .build()

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .enableAutoServiceReconnection()
            .build()
    }

    val isReady: Boolean
        get() = billingClient.isReady

    fun startConnection(callback: (BillingResult) -> Unit = {}) {
        if (billingClient.isReady) {
            callback(billingResult(BillingClient.BillingResponseCode.OK, "Billing client is already connected."))
            return
        }

        var shouldStartConnection = false
        synchronized(lock) {
            connectionCallbacks.add(callback)
            if (!isConnecting) {
                isConnecting = true
                shouldStartConnection = true
            }
        }

        if (!shouldStartConnection) {
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val callbacks = synchronized(lock) {
                    isConnecting = false
                    connectionCallbacks.toList().also { connectionCallbacks.clear() }
                }
                callbacks.forEach { it(billingResult) }
            }

            override fun onBillingServiceDisconnected() {
                synchronized(lock) {
                    isConnecting = false
                }
                listener?.onBillingDisconnected()
            }
        })
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
        synchronized(lock) {
            isConnecting = false
            connectionCallbacks.clear()
        }
    }

    fun getProducts(
        productIds: List<String>,
        @BillingClient.ProductType productType: String,
        callback: (ProductQueryResult) -> Unit,
    ) {
        if (productIds.isEmpty()) {
            callback(
                ProductQueryResult(
                    billingResult(BillingClient.BillingResponseCode.OK, "No products requested."),
                    emptyList(),
                    emptyList(),
                )
            )
            return
        }

        executeWhenReady(
            onConnectionFailed = { billingResult ->
                callback(ProductQueryResult(billingResult, emptyList(), emptyList()))
            }
        ) {
            val products = productIds.distinct().map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(productType)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                callback(
                    ProductQueryResult(
                        billingResult = billingResult,
                        products = productDetailsResult.productDetailsList,
                        unfetchedProducts = productDetailsResult.unfetchedProductList,
                    )
                )
            }
        }
    }

    fun getInAppProducts(
        productIds: List<String>,
        callback: (ProductQueryResult) -> Unit,
    ) = getProducts(productIds, BillingClient.ProductType.INAPP, callback)

    fun getSubscriptionProducts(
        productIds: List<String>,
        callback: (ProductQueryResult) -> Unit,
    ) = getProducts(productIds, BillingClient.ProductType.SUBS, callback)

    fun buyProduct(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String? = productDetails.defaultOfferToken(),
        obfuscatedAccountId: String? = null,
        obfuscatedProfileId: String? = null,
    ): BillingResult {
        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (!offerToken.isNullOrBlank()) {
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))

        if (!obfuscatedAccountId.isNullOrBlank()) {
            billingFlowParamsBuilder.setObfuscatedAccountId(obfuscatedAccountId)
        }
        if (!obfuscatedProfileId.isNullOrBlank()) {
            billingFlowParamsBuilder.setObfuscatedProfileId(obfuscatedProfileId)
        }

        return billingClient.launchBillingFlow(activity, billingFlowParamsBuilder.build())
    }

    fun buyProduct(
        activity: Activity,
        productId: String,
        @BillingClient.ProductType productType: String,
        offerToken: String? = null,
        obfuscatedAccountId: String? = null,
        obfuscatedProfileId: String? = null,
        callback: (BillingResult) -> Unit,
    ) {
        getProducts(listOf(productId), productType) { result ->
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                callback(result.billingResult)
                return@getProducts
            }

            val productDetails = result.products.firstOrNull()
            if (productDetails == null) {
                callback(billingResult(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE, "Product not found: $productId"))
                return@getProducts
            }

            callback(
                buyProduct(
                    activity = activity,
                    productDetails = productDetails,
                    offerToken = offerToken ?: productDetails.defaultOfferToken(),
                    obfuscatedAccountId = obfuscatedAccountId,
                    obfuscatedProfileId = obfuscatedProfileId,
                )
            )
        }
    }

    fun queryPurchases(
        @BillingClient.ProductType productType: String,
        includeSuspendedSubscriptions: Boolean = false,
        callback: (PurchasesQueryResult) -> Unit,
    ) {
        executeWhenReady(
            onConnectionFailed = { billingResult ->
                callback(PurchasesQueryResult(billingResult, emptyList()))
            }
        ) {
            val paramsBuilder = QueryPurchasesParams.newBuilder()
                .setProductType(productType)

            if (productType == BillingClient.ProductType.SUBS) {
                paramsBuilder.includeSuspendedSubscriptions(includeSuspendedSubscriptions)
            }

            billingClient.queryPurchasesAsync(paramsBuilder.build()) { billingResult, purchases ->
                callback(PurchasesQueryResult(billingResult, purchases))
            }
        }
    }

    fun getActiveProducts(
        productIds: Set<String> = emptySet(),
        includeInAppProducts: Boolean = true,
        includeSubscriptions: Boolean = true,
        includeSuspendedSubscriptions: Boolean = false,
        callback: (ActiveProductsResult) -> Unit,
    ) {
        if (!includeInAppProducts && !includeSubscriptions) {
            val result = billingResult(BillingClient.BillingResponseCode.OK, "No product types requested.")
            callback(ActiveProductsResult(result, emptyList(), emptyList(), emptyList()))
            return
        }

        fun complete(
            inAppResult: PurchasesQueryResult?,
            subscriptionResult: PurchasesQueryResult?,
        ) {
            val inAppPurchases = inAppResult?.purchases.orEmpty()
            val subscriptionPurchases = subscriptionResult?.purchases.orEmpty()
            callback(
                ActiveProductsResult(
                    billingResult = mergeBillingResults(inAppResult?.billingResult, subscriptionResult?.billingResult),
                    purchases = inAppPurchases + subscriptionPurchases,
                    inAppPurchases = inAppPurchases,
                    subscriptionPurchases = subscriptionPurchases,
                )
            )
        }

        if (includeInAppProducts) {
            getActiveInAppProducts(productIds) { inAppResult ->
                if (includeSubscriptions) {
                    getActiveSubscriptions(productIds, includeSuspendedSubscriptions) { subscriptionResult ->
                        complete(inAppResult, subscriptionResult)
                    }
                } else {
                    complete(inAppResult, null)
                }
            }
        } else {
            getActiveSubscriptions(productIds, includeSuspendedSubscriptions) { subscriptionResult ->
                complete(null, subscriptionResult)
            }
        }
    }

    fun getActiveInAppProducts(
        productIds: Set<String> = emptySet(),
        callback: (PurchasesQueryResult) -> Unit,
    ) {
        getActivePurchases(
            productType = BillingClient.ProductType.INAPP,
            productIds = productIds,
            callback = callback,
        )
    }

    fun getActiveSubscriptions(
        productIds: Set<String> = emptySet(),
        includeSuspendedSubscriptions: Boolean = false,
        callback: (PurchasesQueryResult) -> Unit,
    ) {
        getActivePurchases(
            productType = BillingClient.ProductType.SUBS,
            productIds = productIds,
            includeSuspendedSubscriptions = includeSuspendedSubscriptions,
            callback = callback,
        )
    }

    fun hasPurchasedProduct(
        productId: String,
        includeInAppProducts: Boolean = true,
        includeSubscriptions: Boolean = true,
        includeSuspendedSubscriptions: Boolean = false,
        callback: (HasPurchasedProductResult) -> Unit,
    ) {
        getActiveProducts(
            productIds = setOf(productId),
            includeInAppProducts = includeInAppProducts,
            includeSubscriptions = includeSubscriptions,
            includeSuspendedSubscriptions = includeSuspendedSubscriptions,
        ) { result ->
            callback(
                HasPurchasedProductResult(
                    billingResult = result.billingResult,
                    hasPurchased = result.billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        result.purchases.isNotEmpty(),
                    purchases = result.purchases,
                )
            )
        }
    }

    fun hasPurchasedInAppProduct(
        productId: String,
        callback: (HasPurchasedProductResult) -> Unit,
    ) {
        getActiveInAppProducts(setOf(productId)) { result ->
            callback(result.toHasPurchasedProductResult())
        }
    }

    fun hasPurchasedSubscription(
        productId: String,
        includeSuspendedSubscriptions: Boolean = false,
        callback: (HasPurchasedProductResult) -> Unit,
    ) {
        getActiveSubscriptions(setOf(productId), includeSuspendedSubscriptions) { result ->
            callback(result.toHasPurchasedProductResult())
        }
    }

    fun hasActiveSubscription(
        callback: (ActiveSubscriptionResult) -> Unit,
    ) {
        getActiveSubscriptions { result ->
            callback(
                ActiveSubscriptionResult(
                    billingResult = result.billingResult,
                    hasActiveSubscription = result.billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        result.purchases.isNotEmpty(),
                    purchases = result.purchases,
                )
            )
        }
    }

    fun acknowledgePurchase(
        purchase: Purchase,
        callback: (BillingResult) -> Unit,
    ) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            callback(billingResult(BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Only purchased items can be acknowledged."))
            return
        }

        if (purchase.isAcknowledged) {
            callback(billingResult(BillingClient.BillingResponseCode.OK, "Purchase is already acknowledged."))
            return
        }

        executeWhenReady(callback) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(params, callback)
        }
    }

    fun consumePurchase(
        purchase: Purchase,
        callback: (BillingResult, purchaseToken: String) -> Unit,
    ) {
        executeWhenReady({ callback(it, purchase.purchaseToken) }) {
            val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.consumeAsync(params, callback)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val purchaseList = purchases.orEmpty()
        listener?.onPurchasesUpdated(billingResult, purchaseList)

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            listener?.onPurchaseError(billingResult)
            return
        }

        purchaseList.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> listener?.onPurchaseCompleted(purchase)
                Purchase.PurchaseState.PENDING -> listener?.onPurchasePending(purchase)
                else -> Unit
            }
        }
    }

    private fun executeWhenReady(
        onConnectionFailed: (BillingResult) -> Unit,
        block: () -> Unit,
    ) {
        if (billingClient.isReady) {
            block()
            return
        }

        startConnection { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                block()
            } else {
                onConnectionFailed(billingResult)
            }
        }
    }

    private fun getActivePurchases(
        @BillingClient.ProductType productType: String,
        productIds: Set<String>,
        includeSuspendedSubscriptions: Boolean = false,
        callback: (PurchasesQueryResult) -> Unit,
    ) {
        queryPurchases(productType, includeSuspendedSubscriptions) { result ->
            callback(
                PurchasesQueryResult(
                    billingResult = result.billingResult,
                    purchases = result.purchases.filter { purchase ->
                        purchase.isActive(productIds)
                    },
                )
            )
        }
    }

    private fun Purchase.isActive(productIds: Set<String>): Boolean =
        purchaseState == Purchase.PurchaseState.PURCHASED &&
            (productIds.isEmpty() || products.any(productIds::contains))

    private fun PurchasesQueryResult.toHasPurchasedProductResult(): HasPurchasedProductResult =
        HasPurchasedProductResult(
            billingResult = billingResult,
            hasPurchased = billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                purchases.isNotEmpty(),
            purchases = purchases,
        )

    private fun mergeBillingResults(
        first: BillingResult?,
        second: BillingResult?,
    ): BillingResult {
        val results = listOfNotNull(first, second)
        return results.firstOrNull { it.responseCode != BillingClient.BillingResponseCode.OK }
            ?: results.firstOrNull()
            ?: billingResult(BillingClient.BillingResponseCode.OK, "No product types requested.")
    }

    private fun ProductDetails.defaultOfferToken(): String? {
        val oneTimeOfferToken = oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?: oneTimePurchaseOfferDetails?.offerToken

        return oneTimeOfferToken
            ?: subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
    }

    private fun billingResult(
        responseCode: Int,
        debugMessage: String,
    ): BillingResult = BillingResult.newBuilder()
        .setResponseCode(responseCode)
        .setDebugMessage(debugMessage)
        .build()
}
