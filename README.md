# Android-Purchase-Helper

Small Kotlin helper around Google Play Billing.

## Install from JitPack

[![](https://jitpack.io/v/mzgs/Android-Purchase-Helper.svg)](https://jitpack.io/#mzgs/Android-Purchase-Helper)
  

Add JitPack to your dependency repositories:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.github.mzgs:Android-Purchase-Helper:1.0.0")
}
```

Replace `1.0.0` with your GitHub release tag.

## Usage

```kotlin
PurchaseHelper.init(
    context = applicationContext,
    listener = object : PurchaseHelper.Listener {
        override fun onPurchaseCompleted(purchase: Purchase) {
            // Verify the purchase and grant the matching entitlement first.
            // Then acknowledge non-consumables/subscriptions.
            PurchaseHelper.acknowledgePurchase(purchase) { result ->
                // Check result.responseCode.
            }
        }

        override fun onPurchasePending(purchase: Purchase) {
            // Do not grant entitlement until the purchase becomes PURCHASED.
        }
    }
)
```

Initialize once, normally from your Application. The helper reports purchase states;
it does not verify purchases or grant benefits for you. Only grant benefits for a
verified `PURCHASED` purchase, and process each purchase token once.

Get products (always check the billing result):

```kotlin
PurchaseHelper.getSubscriptionProducts(listOf("premium_monthly")) { result ->
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        // Show a retry action; do not treat this as an empty catalog.
        return@getSubscriptionProducts
    }
    val product = result.products.firstOrNull()
}

PurchaseHelper.getInAppProducts(listOf("remove_ads")) { result ->
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        // Show a retry action.
        return@getInAppProducts
    }
    val product = result.products.firstOrNull()
}
```

Buy the offer shown on your paywall:

```kotlin
PurchaseHelper.getSubscriptionProducts(listOf("premium_monthly")) { result ->
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        return@getSubscriptionProducts
    }
    val product = result.products.firstOrNull() ?: return@getSubscriptionProducts
    val offer = product.subscriptionOfferDetails?.singleOrNull {
        it.basePlanId == "monthly" && it.offerId == null
    } ?: return@getSubscriptionProducts

    // Display this offer's pricingPhases on your paywall. When the user buys:
    PurchaseHelper.buyProduct(
        activity = this,
        productId = product.productId,
        productType = BillingClient.ProductType.SUBS,
        offerToken = offer.offerToken,
    ) { launchResult ->
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Handle cancellation, unavailability, or another launch error.
        }
        // This is only the launch result. The final purchase arrives in Listener.
    }
}
```

Choose your actual base plan ID instead of `monthly`. Pass the token for the exact
offer whose price and billing period you displayed. If multiple offers are available
and no token is supplied, purchasing returns `DEVELOPER_ERROR` instead of choosing
an arbitrary offer. Blank or unknown tokens are rejected too. A single eligible
offer can still be bought without supplying its token.

The callback overload (`productId`) can be called from any thread; it queries,
launches, and delivers its launch callback on the main thread. The synchronous
`ProductDetails` overload must be called on the main thread; background calls return
`DEVELOPER_ERROR` without opening the purchase screen. Product-query callbacks
otherwise retain the underlying BillingClient threading behavior.

Check active subscriptions:

```kotlin
PurchaseHelper.hasActiveSubscription { result ->
    if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        // Update your existing state: isPremium = result.hasActiveSubscription
    } else {
        // Keep the previous state and show a retry action.
    }
}
```

Get active purchases:

```kotlin
PurchaseHelper.getActiveInAppProducts { result ->
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        return@getActiveInAppProducts
    }
    val activeInAppProducts = result.purchases
}

PurchaseHelper.getActiveSubscriptions { result ->
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        return@getActiveSubscriptions
    }
    val activeSubscriptions = result.purchases
    // Process unacknowledged, verified purchases here as well.
}
```

Check one product:

```kotlin
PurchaseHelper.hasPurchasedInAppProduct("remove_ads") { result ->
    if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        // Update your existing state: hasRemoveAds = result.hasPurchased
    } else {
        // Keep the previous state and show a retry action.
    }
}

PurchaseHelper.hasPurchasedSubscription("premium_monthly") { result ->
    if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        // Update your existing state: isPremium = result.hasPurchased
    } else {
        // Keep the previous state and show a retry action.
    }
}
```

`hasActiveSubscription` checks any subscription. Use `hasPurchasedSubscription` or
filter `getActiveSubscriptions(productIds = setOf("premium_monthly"))` when only
specific products unlock a feature. A failed query is not proof that a purchase has
expired. Refresh purchases on startup and when the app resumes to recover completed
pending payments, purchases made elsewhere, and missed purchase callbacks.

Call `PurchaseHelper.clear()` when finished with the singleton. Closing always
releases the BillingClient, including while it is connecting, and completes waiting
connection requests with `SERVICE_DISCONNECTED`. A closed helper cannot be reused;
call `init()` to create a new one. Late events from the old client are ignored.

## Validation

Use JDK 17 to run the project's Gradle wrapper:

```sh
./gradlew :purchasehelper:testDebugUnitTest :purchasehelper:assembleRelease :purchasehelper:lintDebug
```

The unit tests mock the BillingClient and cover shutdown during connection,
disconnection and retry, exact offer selection, main-thread dispatch, failed
entitlement queries, pending/completed payments, and acknowledgement. Test actual
payment, renewal, cancellation, expiry, and restoration with a Google Play license
tester and a signed build on a testing track before publishing a release.
