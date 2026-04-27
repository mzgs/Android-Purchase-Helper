# Android-Purchase-Helper

Small Kotlin helper around Google Play Billing.

## Usage

```kotlin
PurchaseHelper.init(
    context = applicationContext,
    listener = object : PurchaseHelper.Listener {
        override fun onPurchaseCompleted(purchase: Purchase) {
            // Grant entitlement, then acknowledge non-consumables/subscriptions.
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

Get products:

```kotlin
PurchaseHelper.getSubscriptionProducts(listOf("premium_monthly")) { result ->
    val product = result.products.firstOrNull()
}

PurchaseHelper.getInAppProducts(listOf("remove_ads")) { result ->
    val product = result.products.firstOrNull()
}
```

Buy a product:

```kotlin
PurchaseHelper.buyProduct(
    activity = this,
    productId = "premium_monthly",
    productType = BillingClient.ProductType.SUBS,
) { launchResult ->
    // This is only the launch result. The final purchase arrives in Listener.
}
```

Check active subscriptions:

```kotlin
PurchaseHelper.hasActiveSubscription { result ->
    val isPremium = result.hasActiveSubscription
}
```

Get active purchases:

```kotlin
PurchaseHelper.getActiveInAppProducts { result ->
    val activeInAppProducts = result.purchases
}

PurchaseHelper.getActiveSubscriptions { result ->
    val activeSubscriptions = result.purchases
}
```

Check one product:

```kotlin
PurchaseHelper.hasPurchasedInAppProduct("remove_ads") { result ->
    val hasRemoveAds = result.hasPurchased
}

PurchaseHelper.hasPurchasedSubscription("premium_monthly") { result ->
    val hasPremium = result.hasPurchased
}
```

Call `PurchaseHelper.clear()` from the application cleanup path if you need to release the static instance.
