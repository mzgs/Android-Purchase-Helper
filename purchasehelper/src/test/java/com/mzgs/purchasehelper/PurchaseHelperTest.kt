package com.mzgs.purchasehelper

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.android.billingclient.api.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito.*

class PurchaseHelperTest {
    private inline fun <reified T> mock(): T = mock(T::class.java)
    private val client = mock<BillingClient>()
    private val listener = mock<PurchaseHelper.Listener>()
    private val helper = PurchaseHelper(listener) { client }
    private val activity = mock<Activity>()
    private val mainLooper = mock<Looper>()
    private var onMainThread = true
    private lateinit var loopers: MockedStatic<Looper>
    private lateinit var textUtils: MockedStatic<TextUtils>
    private fun result(code: Int) = BillingResult.newBuilder().setResponseCode(code).build()
    private val ok get() = result(BillingClient.BillingResponseCode.OK)

    @Before fun prepareAndroidCalls() {
        loopers = mockStatic(Looper::class.java)
        loopers.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
        loopers.`when`<Looper?> { Looper.myLooper() }.thenAnswer { if (onMainThread) mainLooper else null }
        textUtils = mockStatic(TextUtils::class.java)
        textUtils.`when`<Boolean> { TextUtils.isEmpty(any()) }.thenAnswer { (it.arguments[0] as CharSequence?).isNullOrEmpty() }
        textUtils.`when`<Boolean> { TextUtils.equals(any(), any()) }.thenAnswer { it.arguments[0]?.toString() == it.arguments[1]?.toString() }
        `when`(client.isReady).thenReturn(true)
        `when`(client.launchBillingFlow(any(Activity::class.java), any(BillingFlowParams::class.java))).thenReturn(ok)
    }

    @After fun releaseAndroidCalls() { textUtils.close(); loopers.close() }

    private fun subscription(vararg tokens: String): ProductDetails {
        // Real ProductDetails supplies non-null strings, including SDK metadata.
        val product = mock(ProductDetails::class.java) { call ->
            if (call.method.returnType == String::class.java) "" else RETURNS_DEFAULTS.answer(call)
        }
        `when`(product.productId).thenReturn("premium")
        `when`(product.productType).thenReturn(BillingClient.ProductType.SUBS)
        val offers = tokens.map { token ->
            mock<ProductDetails.SubscriptionOfferDetails>().also { `when`(it.offerToken).thenReturn(token) }
        }
        `when`(product.subscriptionOfferDetails).thenReturn(offers)
        return product
    }

    @Test fun closingWhileConnectingFailsWaitingRequestsAndIgnoresLateEvents() {
        `when`(client.isReady).thenReturn(false)
        val responses = mutableListOf<PurchaseHelper.ProductQueryResult>()
        helper.getSubscriptionProducts(listOf("premium")) { responses += it }
        val setup = ArgumentCaptor.forClass(BillingClientStateListener::class.java)
        verify(client).startConnection(setup.capture())
        helper.endConnection()
        verify(client).endConnection()
        assertEquals(listOf(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED), responses.map { it.billingResult.responseCode })
        `when`(client.isReady).thenReturn(true)
        setup.value.onBillingSetupFinished(ok)
        setup.value.onBillingServiceDisconnected()
        helper.onPurchasesUpdated(ok, mutableListOf(mock<Purchase>()))
        assertFalse(helper.isReady)
        assertEquals(1, responses.size)
        verifyNoInteractions(listener)
        helper.getSubscriptionProducts(listOf("premium")) { responses += it }
        assertEquals(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, responses.last().billingResult.responseCode)
        helper.endConnection()
        verify(client, times(1)).endConnection()
        verify(client, never()).queryProductDetailsAsync(any(), any())
    }

    @Test fun reinitializingAndClearingCloseClientsEvenBeforeTheyConnect() {
        val context = mock<Context>()
        `when`(context.applicationContext).thenReturn(context)
        val replacement = mock<BillingClient>()
        `when`(client.isReady).thenReturn(false)
        val firstBuilder = mock(BillingClient.Builder::class.java, RETURNS_SELF)
        val secondBuilder = mock(BillingClient.Builder::class.java, RETURNS_SELF)
        `when`(firstBuilder.build()).thenReturn(client)
        `when`(secondBuilder.build()).thenReturn(replacement)
        mockStatic(BillingClient::class.java).use { clients ->
            clients.`when`<BillingClient.Builder> { BillingClient.newBuilder(context) }.thenReturn(firstBuilder, secondBuilder)
            try {
                val first = PurchaseHelper.init(context)
                var code: Int? = null
                first.getSubscriptionProducts(listOf("premium")) { code = it.billingResult.responseCode }
                val second = PurchaseHelper.init(context)
                assertSame(second, PurchaseHelper.requireInstance())
                verify(client).endConnection()
                assertEquals(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, code)
                assertFalse(first.isReady)
                PurchaseHelper.clear()
                verify(replacement).endConnection()
                assertFalse(PurchaseHelper.isInitialized())
            } finally { PurchaseHelper.clear() }
        }
    }

    @Test fun multipleOneTimeOffersAlsoRequireAnExplicitToken() {
        val product = mock<ProductDetails>()
        `when`(product.productType).thenReturn(BillingClient.ProductType.INAPP)
        val offers = listOf("standard", "discount").map { token ->
            mock<ProductDetails.OneTimePurchaseOfferDetails>().also { `when`(it.offerToken).thenReturn(token) }
        }
        `when`(product.oneTimePurchaseOfferDetailsList).thenReturn(offers)
        assertEquals(BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            helper.buyProduct(activity, product).responseCode)
        verify(client, never()).launchBillingFlow(any(), any())
    }

    @Test fun disconnectionCompletesQueuedRequestsAndAllowsRetry() {
        `when`(client.isReady).thenReturn(false)
        val codes = mutableListOf<Int>()
        helper.startConnection { codes += it.responseCode }
        helper.startConnection { codes += it.responseCode }
        val setup = ArgumentCaptor.forClass(BillingClientStateListener::class.java)
        verify(client).startConnection(setup.capture())
        setup.value.onBillingServiceDisconnected()
        assertEquals(List(2) { BillingClient.BillingResponseCode.SERVICE_DISCONNECTED }, codes)
        helper.startConnection { codes += it.responseCode }
        verify(client, times(2)).startConnection(setup.capture())
        setup.value.onBillingSetupFinished(ok)
        assertEquals(BillingClient.BillingResponseCode.OK, codes.last())
    }

    @Test fun ambiguousOrInvalidOffersCannotLaunchAPurchase() {
        val product = subscription("weekly", "yearly")
        for (token in listOf(null, "", "unknown")) {
            assertEquals(BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                helper.buyProduct(activity, product, token).responseCode)
        }
        assertEquals(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            helper.buyProduct(activity, subscription()).responseCode)
        verify(client, never()).launchBillingFlow(any(), any())
    }

    @Test fun explicitPlanAndSingleOfferUseTheirExactTokens() {
        val builder = spy(BillingFlowParams.ProductDetailsParams.newBuilder())
        mockStatic(BillingFlowParams.ProductDetailsParams::class.java).use { builders ->
            builders.`when`<BillingFlowParams.ProductDetailsParams.Builder> { BillingFlowParams.ProductDetailsParams.newBuilder() }.thenReturn(builder)
            assertEquals(BillingClient.BillingResponseCode.OK,
                helper.buyProduct(activity, subscription("weekly", "yearly"), "yearly").responseCode)
            verify(builder).setOfferToken("yearly")
            assertEquals(BillingClient.BillingResponseCode.OK,
                helper.buyProduct(activity, subscription("weekly")).responseCode)
            verify(builder).setOfferToken("weekly")
        }
        verify(client, times(2)).launchBillingFlow(any(), any())
    }

    @Test fun synchronousPurchaseRejectsBackgroundCalls() {
        onMainThread = false
        assertEquals(BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            helper.buyProduct(activity, subscription("yearly")).responseCode)
        verify(client, never()).launchBillingFlow(any(), any())
    }

    @Test fun asynchronousPurchaseDispatchesQueryAndLaunchToMainThread() {
        onMainThread = false
        val posted = mutableListOf<Runnable>()
        var launchResult: BillingResult? = null
        mockConstruction(Handler::class.java) { handler, _ ->
            `when`(handler.post(any(Runnable::class.java))).thenAnswer { posted += it.arguments[0] as Runnable; true }
        }.use {
            helper.buyProduct(activity, "premium", BillingClient.ProductType.SUBS, "yearly") { launchResult = it }
            verify(client, never()).queryProductDetailsAsync(any(), any())
            assertEquals(1, posted.size)
            onMainThread = true
            posted.removeAt(0).run()
            val query = ArgumentCaptor.forClass(ProductDetailsResponseListener::class.java)
            verify(client).queryProductDetailsAsync(any(), query.capture())
            onMainThread = false
            query.value.onProductDetailsResponse(ok, QueryProductDetailsResult.create(listOf(subscription("yearly")), emptyList()))
            verify(client, never()).launchBillingFlow(any(), any())
            assertEquals(1, posted.size)
            onMainThread = true
            posted.removeAt(0).run()
            assertEquals(BillingClient.BillingResponseCode.OK, launchResult?.responseCode)
            verify(client).launchBillingFlow(any(), any())
        }
    }

    @Test fun failedQueriesPreserveAccessAndPendingPaymentsDoNotGrantIt() {
        var premium = true
        var response: PurchaseHelper.HasPurchasedProductResult? = null
        helper.hasPurchasedSubscription("premium") { result ->
            response = result
            // The README pattern: change access only after a successful query.
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) premium = result.hasPurchased
        }
        val query = ArgumentCaptor.forClass(PurchasesResponseListener::class.java)
        verify(client).queryPurchasesAsync(any(), query.capture())
        query.value.onQueryPurchasesResponse(result(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE), emptyList())
        assertTrue(premium)
        assertEquals(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, response?.billingResult?.responseCode)
        query.value.onQueryPurchasesResponse(ok, emptyList())
        assertFalse(premium)
        val purchase = mock<Purchase>()
        `when`(purchase.products).thenReturn(listOf("premium"))
        `when`(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PENDING)
        query.value.onQueryPurchasesResponse(ok, listOf(purchase))
        assertFalse(premium)
        `when`(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        query.value.onQueryPurchasesResponse(ok, listOf(purchase))
        assertTrue(premium)
        `when`(purchase.products).thenReturn(listOf("another_product"))
        query.value.onQueryPurchasesResponse(ok, listOf(purchase))
        assertFalse(premium)
    }

    @Test fun purchaseEventsSeparatePendingCompletedAndFailedPayments() {
        val pending = mock<Purchase>()
        val purchased = mock<Purchase>()
        `when`(pending.purchaseState).thenReturn(Purchase.PurchaseState.PENDING)
        `when`(purchased.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        helper.onPurchasesUpdated(ok, mutableListOf(pending, purchased))
        verify(listener).onPurchasePending(pending)
        verify(listener).onPurchaseCompleted(purchased)
        clearInvocations(listener)
        val error = result(BillingClient.BillingResponseCode.USER_CANCELED)
        helper.onPurchasesUpdated(error, mutableListOf(purchased))
        verify(listener).onPurchaseError(error)
        verify(listener, never()).onPurchaseCompleted(purchased)
    }

    @Test fun acknowledgementRejectsPendingAndSkipsAlreadyAcknowledgedPurchases() {
        val purchase = mock<Purchase>()
        var code: Int? = null
        `when`(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PENDING)
        helper.acknowledgePurchase(purchase) { code = it.responseCode }
        assertEquals(BillingClient.BillingResponseCode.DEVELOPER_ERROR, code)
        `when`(purchase.purchaseState).thenReturn(Purchase.PurchaseState.PURCHASED)
        `when`(purchase.isAcknowledged).thenReturn(true)
        helper.acknowledgePurchase(purchase) { code = it.responseCode }
        assertEquals(BillingClient.BillingResponseCode.OK, code)
        verify(client, never()).acknowledgePurchase(any(), any())
        `when`(purchase.isAcknowledged).thenReturn(false)
        `when`(purchase.purchaseToken).thenReturn("purchase-token")
        helper.acknowledgePurchase(purchase) { code = it.responseCode }
        val ack = ArgumentCaptor.forClass(AcknowledgePurchaseResponseListener::class.java)
        verify(client).acknowledgePurchase(any(), ack.capture())
        ack.value.onAcknowledgePurchaseResponse(ok)
        assertEquals(BillingClient.BillingResponseCode.OK, code)
    }
}
