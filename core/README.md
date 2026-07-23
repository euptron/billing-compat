# BillingCompat — Core Module

A lightweight Android wrapper around the Google Play Billing Library that handles purchase flows,
ownership state, fraud detection, and server-side verification through a clean, builder-based API.

Built and verified against **Google Play Billing Library 9.1.0**. This is module documentation for
`core` only — `core` has no dependency on the sibling `ui` module and can be used entirely headless.

---

## Table of Contents

- [Requirements](#requirements)
- [Official Resources](#official-resources)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Architecture Overview](#architecture-overview)
- [SubscriptionPlan Configuration](#subscriptionplan-configuration)
- [Product Types](#product-types)
- [Defining Products](#defining-products)
- [Building the BillingManager](#building-the-billingmanager)
- [Offer Selection](#offer-selection)
- [Launching Purchases](#launching-purchases)
- [Listening to Purchase Events](#listening-to-purchase-events)
- [Checking Ownership](#checking-ownership)
- [BillingCompat (Static Access)](#billingcompat-static-access)
- [Subscription Handling](#subscription-handling)
- [Consumable Balances](#consumable-balances)
- [Syncing Purchases](#syncing-purchases)
- [Persisting Purchase State](#persisting-purchase-state)
- [Connection Lifecycle](#connection-lifecycle)
- [Threading Model](#threading-model)
- [Data Persistence Reference](#data-persistence-reference)
- [Android Auto Backup Rules](#android-auto-backup-rules)
- [Security](#security)
  - [Manifest Package Visibility](#manifest-package-visibility)
  - [IntegrityGuard](#integrityguard)
  - [SecurityGuard](#securityguard)
  - [PurchaseFraudGuard](#purchasefraudguard)
  - [Server-Side Verification (SSVClient)](#server-side-verification-ssvclient)
  - [SSV Backend Setup](#ssv-backend-setup)
- [Usage Patterns](#usage-patterns)
- [Play Console Setup Checklist](#play-console-setup-checklist)
- [Edge Cases & Pitfalls](#edge-cases--pitfalls)
- [Known Limitations](#known-limitations)
- [FAQ](#faq)
- [Migration Guide](#migration-guide)
- [Changelog](#changelog)
- [ProGuard / R8](#proguard--r8)
- [Contributing](#contributing)
- [License](#license)

---

## Requirements

| Requirement | Minimum |
|---|---|
| Android `minSdk` | 23 |
| Android `compileSdk` | 36 |
| Java source/target compatibility | 1.8 |
| Google Play Billing Library | 9.1.0 (`libs.billing`) |
| AndroidX Annotations | Required |

Google periodically mandates a minimum Billing Library version and sunsets older ones on a fixed
schedule (for example, all new apps and updates have historically been required to move to a newer
major version by a published deadline). Confirm the version pinned in your `libs.versions.toml`
is still inside Google's supported window before publishing — see
[Official Resources](#official-resources).

---

## Official Resources

This README documents what `BillingCompat — Core` does on top of the Play Billing Library. It does
not replace Google's own documentation, and Play's billing requirements change over time
(mandatory library upgrades, new product/offer rules, policy updates). Before integrating or
releasing, check:

| Resource | Purpose |
|---|---|
| [Integrate Google Play Billing](https://developer.android.com/google/play/billing/integrate) | Canonical integration guide for the underlying Billing Library |
| [Billing Library Deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq) | Mandatory upgrade deadlines and version sunset schedule |
| [Play Console Help](https://support.google.com/googleplay/android-developer) | Product/subscription setup, payments profile, policy requirements |
| [Test Google Play Billing](https://developer.android.com/google/play/billing/test) | License testing accounts, test cards, sandbox purchase behavior |
| [Google Play Developer API (Android Publisher)](https://developers.google.com/android-publisher) | The API your backend calls for [server-side verification](#server-side-verification-ssvclient) |

---

## Installation

### Local module (current)

```groovy
// settings.gradle
include ':core'

// app/build.gradle
dependencies {
    implementation project(':core')
}
```

### Published dependency (placeholder)

This module is not yet published to Maven Central or JitPack. Once it is, replace the block above
with the real coordinates:

```groovy
dependencies {
    // TODO: confirm final group/artifact coordinates once published
    implementation("io.github.euptron:billingcompat-core:0.0.4")
}
```

```kotlin
// If distributed via JitPack instead, add to settings.gradle.kts:
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

dependencies {
    implementation("com.github.euptron:Billing-Compat:0.0.4")
}
```

The `BILLING` permission is added automatically by the Play Billing Library's manifest merger.
Verify it is present in your merged manifest after building:

```xml
<uses-permission android:name="com.android.vending.BILLING" />
```

---

## Quick Start

```java
// 1. Configure subscription product IDs (once, in Application.onCreate)
SubscriptionPlan.configure(new SubscriptionPlan.Config()
    .monthly("com.myapp.pro_monthly")
    .yearly("com.myapp.pro_yearly"));

// 2. Define products
Purchasable removeAds = new NonConsumableProduct.Builder()
    .id("com.myapp.remove_ads")
    .name("Remove Ads")
    .price(1.99)
    .build();

Purchasable coins = new ConsumableProduct.Builder()
    .id("com.myapp.coins_100")
    .name("100 Coins")
    .price(0.99)
    .quantity(100)
    .build();

Purchasable pro = ProductFactory.createSubscription(
    "com.myapp.pro_monthly", "Pro Monthly", 9.99, SubscriptionPlan.MONTHLY);

// 3. Build and connect
BillingManager billingManager = new BillingConfigBuilder(this)
    .addProduct(removeAds)
    .addProduct(coins)
    .addProduct(pro)
    .setListener(purchaseEventListener)
    .autoConnect(true)
    .build();

// 4. Register the same products with the manager's own registry.
// BillingConfigBuilder registers products with each ProductHandler's internal
// cache, but does not populate BillingManager's separate productMap. Without
// this step, isFeatureUnlocked(), isUnlocked(), getProduct(), and
// getOwnedProducts() will not resolve any product, and
// PurchaseBuilder.productId() will throw IllegalArgumentException.
billingManager.registerProduct(removeAds);
billingManager.registerProduct(coins);
billingManager.registerProduct(pro);

// 5. Launch a purchase
new PurchaseBuilder(activity, billingManager)
    .nonConsumable("com.myapp.remove_ads")
    .execute();

// 6. Disconnect when done
billingManager.destroy();
```

See [Building the BillingManager](#building-the-billingmanager) and the
[FAQ](#faq) for the full explanation of step 4.

---

## Architecture Overview

```
BillingConfigBuilder          Single entry point for all configuration
      |
      v
BillingManager                Public API surface used by your app
      |
      v
GooglePlayProvider             Owns BillingClient, handles purchase flow & routing
      |
      |-- NonConsumableHandler    One-time permanent purchases
      |-- ConsumableHandler       Balance-tracked repeatable purchases
      |-- SubscriptionHandler     Recurring purchases with expiry tracking
      `-- PendingHandler          Deferred payments (bank transfer, kiosk, etc.)
```

Each handler:

- Owns its own `SharedPreferences` file (separate filenames, no cross-contamination — see
  [Data Persistence Reference](#data-persistence-reference))
- Implements `ProductHandler<T>` — `onPurchaseSuccess`, `onPurchaseFailure`, `onPurchasePending`,
  `isOwned`, `sync`, `registerProduct`, `getProduct`, `setListener`, `setOwned`
- Persists state independently via `BaseProductHandler`

---

## SubscriptionPlan Configuration

`SubscriptionPlan` is an enum with four values: `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`. By
default it maps to product IDs `subs_weekly`, `subs_monthly`, `subs_quarterly`, `subs_yearly`. If
your Play Console product IDs differ, configure the enum once before calling
`BillingConfigBuilder.build()`.

```java
// In Application.onCreate() — before any billing call
SubscriptionPlan.configure(new SubscriptionPlan.Config()
    .weekly("com.myapp.sub_weekly")
    .monthly("com.myapp.sub_monthly")
    .quarterly("com.myapp.sub_quarterly")
    .yearly("com.myapp.sub_yearly"));
```

You can also override the day counts used for the local expiry fallback (server verification is
always preferred):

```java
SubscriptionPlan.configure(new SubscriptionPlan.Config()
    .monthly("com.myapp.sub_monthly")
    .monthlyDays(28)   // override default of 30
    .yearly("com.myapp.sub_yearly")
    .yearlyDays(366)); // override default of 365
```

Only supply the fields you need — omitted values keep their defaults.

| Enum | Default Product ID | Default Days |
|---|---|---|
| `WEEKLY` | `subs_weekly` | 7 |
| `MONTHLY` | `subs_monthly` | 30 |
| `QUARTERLY` | `subs_quarterly` | 90 |
| `YEARLY` | `subs_yearly` | 365 |

`SubscriptionPlan.configure()` mutates static state and should be called exactly once, before
`build()`. Calling it after products are registered has no retroactive effect on IDs already baked
into those product objects.

---

## Product Types

| Type | Enum | SharedPreferences file | Description |
|---|---|---|---|
| Non-Consumable | `PurchaseType.NON_CONSUMABLE` | `non_consumable_purchases` | One-time permanent purchase |
| Consumable | `PurchaseType.CONSUMABLE` | `consumable_balances` | Repeatable, tracked by balance |
| Subscription | `PurchaseType.SUBSCRIPTION` | `subscriptions` | Recurring with expiry tracking |
| Pending | `PurchaseType.PENDING` | `pending_purchases` | Deferred payment, not yet confirmed |

`PENDING` requires custom integration: none of the built-in `Purchasable` implementations
(`NonConsumableProduct`, `ConsumableProduct`, `SubscriptionProduct`) return
`PurchaseType.PENDING` — `PendingHandler` only activates if you author your own `Purchasable`
that returns this type.

---

## Defining Products

### NonConsumableProduct

```java
NonConsumableProduct product = new NonConsumableProduct.Builder()
    .id("com.myapp.remove_ads")       // Required. Must match the Play Console product ID exactly.
    .name("Remove Ads")               // Required. Display name.
    .description("Clean experience")  // Optional.
    .price(1.99)                      // Optional, for display only — Play returns the real price.
    .currency("USD")                  // Optional. Defaults to "USD".
    .stockKeepingUnit("remove_ads")   // Optional. Defaults to id. Use when the SKU differs from id.
    .build();
```

### ConsumableProduct

```java
ConsumableProduct product = new ConsumableProduct.Builder()
    .id("com.myapp.coins_100")
    .name("100 Coins")
    .price(0.99)
    .quantity(100)    // Required. Amount credited to the LOCAL balance per single purchase.
    .unit("coins")    // Optional. Display unit label (e.g. "gems", "energy").
    .build();
```

`quantity` is the number of credits awarded locally when one purchase is consumed — it is not a
"buy N units in one transaction" selector. Google Play managed in-app products are always
purchased at quantity 1 per transaction.

### SubscriptionProduct

```java
SubscriptionProduct product = new SubscriptionProduct.Builder()
    .id("com.myapp.pro_yearly")       // Must match SubscriptionPlan.YEARLY.getProductId()
    .name("Yearly Pro")
    .price(49.99)
    .plan(SubscriptionPlan.YEARLY)    // Required. Links the product to its SubscriptionPlan enum.
    .trialDays(7)                     // Optional. Informational only — see note below.
    .build();
```

`trialDays` on `SubscriptionProduct` is informational metadata only. Free trial eligibility is
controlled entirely by the base plan / offer configuration in Play Console, selected at purchase
time via `OfferSelector` (see [Offer Selection](#offer-selection)). Use `trialDays` to drive UI
copy ("7-day free trial"), not to gate logic.

### ProductFactory (shorthand)

```java
NonConsumableProduct p1 = ProductFactory.createNonConsumable("com.myapp.remove_ads", "Remove Ads", 1.99);
ConsumableProduct    p2 = ProductFactory.createConsumable("com.myapp.coins_100", "100 Coins", 0.99, 100);
SubscriptionProduct  p3 = ProductFactory.createSubscription("com.myapp.pro_monthly", "Monthly", 9.99, SubscriptionPlan.MONTHLY);
SubscriptionProduct  p4 = ProductFactory.createSubscriptionWithTrial("com.myapp.pro_yearly", "Yearly", 49.99, SubscriptionPlan.YEARLY, 7);
```

---

## Building the BillingManager

`BillingConfigBuilder` is the single entry point for configuration.

```java
BillingManager billingManager = new BillingConfigBuilder(context)
    // Products
    .addProduct(product1)
    .addProducts(productList)

    // Events
    .setListener(listener)

    // Offer selection (subscriptions only — see Offer Selection)
    .setOfferSelector(new DefaultOfferSelector("trial"))

    // Connection
    .autoConnect(true)              // default: true. Calls connect() during build().

    // Disable unused handlers (optional)
    .disableConsumable()
    .disableNonConsumable()
    .disableSubscription()
    .disablePending()

    .build();

// Register the same products with the manager's own registry — required, see below.
for (Purchasable p : List.of(product1 /*, ... */)) {
    billingManager.registerProduct(p);
}
```

### What `build()` does internally

1. Creates `GooglePlayProvider` and `BillingClient`.
2. Registers the enabled handlers (`NonConsumableHandler`, `ConsumableHandler`, etc.).
3. Registers products with their respective handler's internal cache.
4. Stores products in `pendingProducts` — queried for `ProductDetails` after connection.
5. Creates `BillingManager` wrapping the provider.
6. If `autoConnect(true)`: calls `manager.connect()`, which starts `BillingClient.startConnection()`.
7. On `onBillingSetupFinished` (`OK`): queries product details, syncs all handlers, fires
   `onConnected`.

Two integration points are worth calling out explicitly:

- **Never call `execute()` on a `PurchaseBuilder` before `onConnected` fires.** Product details
  are fetched inside the connection callback, not synchronously during `build()`.
- **`build()` does not call `BillingManager.registerProduct(...)` for you.** Products registered
  via `addProduct()`/`addProducts()` populate each handler's internal cache (used for
  `ProductDetails` queries and purchase dispatch), but `BillingManager` keeps a separate
  `productMap` that backs `getProduct()`, `isUnlocked()`, `isFeatureUnlocked()`, and
  `getOwnedProducts()`. Register every product with the manager directly, as shown above, or those
  methods will behave as if no products exist. This is the most common integration mistake with
  the current API shape — see the [FAQ](#faq).

---

## Offer Selection

Google Play subscriptions can have multiple offers per product (for example, a base plan plus a
discounted introductory offer or free trial). `OfferSelector` lets you control exactly which offer
token is used at purchase time.

### OfferSelector interface

```java
public interface OfferSelector {
    /**
     * @param offers    All eligible offers Google Play returned for this product.
     * @param productId The product ID being purchased.
     * @return The offerToken to use, or null to abort the purchase.
     */
    String selectOffer(List<ProductDetails.SubscriptionOfferDetails> offers, String productId);
}
```

### DefaultOfferSelector

A built-in implementation that optionally matches by offer tag and falls back to index 0 (the base
plan).

```java
// Always use the base plan — no tag preference
new DefaultOfferSelector()

// Prefer an offer tagged "trial", fall back to base plan if not found
new DefaultOfferSelector("trial")

// Prefer an offer tagged "promo"
new DefaultOfferSelector("promo")
```

Set it on the builder:

```java
new BillingConfigBuilder(this)
    .setOfferSelector(new DefaultOfferSelector("trial"))
    .build();
```

### Custom OfferSelector

For full control — different tags per product, price-based logic, eligibility checks:

```java
// Lambda — different tag per product ID
new BillingConfigBuilder(this)
    .setOfferSelector((offers, productId) -> {
        String preferredTag = productId.contains("yearly") ? "trial" : "promo";
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            if (offer.getOfferTags().contains(preferredTag)) {
                return offer.getOfferToken();
            }
        }
        return offers.isEmpty() ? null : offers.get(0).getOfferToken();
    })
    .build();
```

```java
// Map-driven — configure tags per product ID at setup time
Map<String, String> tagMap = new HashMap<>();
tagMap.put("com.myapp.pro_monthly", "promo");
tagMap.put("com.myapp.pro_yearly", "trial");

new BillingConfigBuilder(this)
    .setOfferSelector((offers, productId) -> {
        String tag = tagMap.get(productId);
        if (tag != null) {
            for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                if (offer.getOfferTags().contains(tag)) return offer.getOfferToken();
            }
        }
        return offers.isEmpty() ? null : offers.get(0).getOfferToken();
    })
    .build();
```

Offer tags are configured in Play Console under your subscription's Offers. If you have no offers
beyond the base plan, `new DefaultOfferSelector()` with no arguments is sufficient.

If your selector returns `null`, `launchPurchase` logs an error and aborts without showing the Play
purchase sheet — make sure your selector always falls back to `offers.get(0).getOfferToken()`
rather than returning `null` unless you genuinely intend to block the purchase.

---

## Launching Purchases

Use `PurchaseBuilder` for a fluent purchase flow. All variants ultimately call
`BillingManager.purchase(activity, product, oldSku)`.

```java
// Non-consumable by registered ID
new PurchaseBuilder(activity, billingManager)
    .nonConsumable("com.myapp.remove_ads")
    .execute();

// Consumable
new PurchaseBuilder(activity, billingManager)
    .consumable("com.myapp.coins_100", 1)
    .execute();

// Subscription by SubscriptionPlan enum
new PurchaseBuilder(activity, billingManager)
    .subscribe(SubscriptionPlan.MONTHLY)
    .execute();

// Subscription upgrade — replaces an existing subscription
new PurchaseBuilder(activity, billingManager)
    .subscribe(SubscriptionPlan.YEARLY)
    .upgradeFrom("com.myapp.pro_monthly") // old product ID
    .execute();

// Pass a Purchasable object directly
new PurchaseBuilder(activity, billingManager)
    .product(myProduct)
    .execute();

// Look up a product already registered on the manager by ID
new PurchaseBuilder(activity, billingManager)
    .productId("com.myapp.remove_ads") // throws IllegalArgumentException if not registered
    .execute();
```

### Subscription upgrade replacement modes

For reference, the underlying Billing Library supports several replacement modes for subscription
upgrades/downgrades via `SubscriptionProductReplacementParams.ReplacementMode`:

| Mode | Behavior |
|---|---|
| `CHARGE_PRORATED_PRICE` | Immediate switch, remaining value from the old plan is prorated and credited. |
| `CHARGE_FULL_PRICE` | Immediate switch, full price charged. |
| `WITHOUT_PRORATION` | Immediate switch, no credit — charged on next renewal date. |
| `KEEP_EXISTING` | Old plan continues until renewal, then the new plan takes effect. |

`GooglePlayProvider` currently hardcodes `CHARGE_PRORATED_PRICE` for every `upgradeFrom(...)` call.
`PurchaseBuilder.withProration(String mode)` exists on the builder and stores a value, but that
value is not currently read anywhere downstream — there is no way to select a different mode
through the public API yet.

`execute()` throws `IllegalStateException` if no product has been set. `productId(String)` throws
`IllegalArgumentException` if the ID was never registered via `billingManager.registerProduct(...)`.

---

## Listening to Purchase Events

Implement `PurchaseEventListener`, or extend `PurchaseEventListener.SimpleListener` (no-op
defaults for every method), to receive purchase callbacks. Note the exact signature of
`onProductPurchased` — it takes two parameters, including the purchase token:

```java
PurchaseEventListener listener = new PurchaseEventListener.SimpleListener() {

    @Override
    public void onProductPurchased(Purchasable product, String purchaseToken) {
        // Purchase confirmed and acknowledged/consumed by the handler.
        // purchaseToken is available here for immediate server-side verification.
        if (product.getId().equals("com.myapp.remove_ads")) {
            hideAds();
        }

        // Optionally verify with your server
        new PurchaseFraudGuard(billingManager).verifyWithServer(
            "https://your-backend.example.com/verify",
            product,
            purchaseToken,
            (isValid, expiryMillis, autoRenewing) -> {
                if (isValid) unlockFeature(product);
            });
    }

    @Override
    public void onProductRestored(Purchasable product) {
        // See note below.
    }

    @Override
    public void onPurchasePending(Purchasable product) {
        // Payment initiated but not yet settled (e.g. cash at a kiosk, bank transfer).
        // Show a "pending" UI state. Do not unlock features yet.
        showPendingMessage(product);
    }

    @Override
    public void onPurchaseError(String error, Purchasable product) {
        // Show a user-facing error message.
        Log.e(TAG, "Purchase error for " + product.getId() + ": " + error);
    }

    @Override
    public void onPurchasesSynced() {
        // Fired after a sync round completes for a product type (INAPP or SUBS) with response OK.
        refreshUi();
    }
};
```

All of these callbacks originate from `BillingClient` async results, which the Play Billing
Library delivers on the main thread — see [Threading Model](#threading-model) for the full
breakdown including the one place this is not true (`SSVClient`).

Two gaps to be aware of in the current handler implementations:

- **`onProductRestored` is declared but not currently invoked.** It is clearly intended to fire
  when `sync()` discovers a previously-owned product the local cache didn't know about, but no
  built-in handler's `sync()` path calls it today — they update internal state and call
  `onPurchasesSynced()` only. Don't build UX that depends on this callback firing; drive
  "purchases restored" UX off `onPurchasesSynced()` plus your own before/after comparison of
  `getOwnedProducts()` if you need it.
- **Cancellations and generic billing errors don't reach `onPurchaseError`.**
  `GooglePlayProvider.onPurchasesUpdated()` only dispatches `onPurchaseSuccess` (for `PURCHASED`)
  and `onPurchasePending` (for `PENDING`). `USER_CANCELED` and other non-OK response codes are
  currently only logged, with no listener callback. `onPurchaseError` is reachable from other
  paths (for example `BillingManager.purchase()` when the provider isn't ready, or
  `ConsumableHandler` when `consumeAsync` fails), but not from the user closing the system
  purchase sheet. Treat "purchase sheet closed, nothing fired" as an implicit cancellation in your
  UI rather than waiting indefinitely for a callback.

---

## Checking Ownership

### Non-Consumable

```java
// Routes through NonConsumableHandler.isOwned() — in-memory state.
boolean owned = billingManager.isFeatureUnlocked("com.myapp.remove_ads");

// Currently routes through the exact same call as isFeatureUnlocked() above.
boolean cached = billingManager.isUnlocked("com.myapp.remove_ads");
```

`BillingManager.isFeatureUnlocked(String)` and `BillingManager.isUnlocked(String)` are intended to
represent two different sources of truth (Play-confirmed in-memory state vs. a locally persisted
cache), but in the current implementation both call the identical `ProductHandler.isOwned(product)`
path and will always agree. If you need the raw persisted cache independent of in-memory state, go
through the handler directly:

```java
NonConsumableHandler ncHandler = billingManager.getHandler(PurchaseType.NON_CONSUMABLE);
boolean rawCacheSaysOwned = ncHandler.isUnlocked("com.myapp.remove_ads"); // raw SharedPreferences
```

See [PurchaseFraudGuard](#purchasefraudguard) for why this distinction matters.

### Subscription

```java
SubscriptionHandler subHandler = billingManager.getHandler(PurchaseType.SUBSCRIPTION);
if (subHandler == null) return; // handler was disabled in BillingConfigBuilder

boolean active    = subHandler.isActive(SubscriptionPlan.MONTHLY);
long    expiry    = subHandler.getExpiryTime(SubscriptionPlan.MONTHLY);    // epoch millis, 0 if unknown
long    remaining = subHandler.getTimeRemaining(SubscriptionPlan.MONTHLY); // millis remaining, 0 if expired

// Look up a SubscriptionPlan from an arbitrary product ID string
SubscriptionPlan plan = subHandler.getPlanFromProductId("com.myapp.pro_monthly");
```

### Consumable Balance

```java
ConsumableHandler consumableHandler = billingManager.getHandler(PurchaseType.CONSUMABLE);
ConsumableProduct coinsProduct = (ConsumableProduct) billingManager.getProduct("com.myapp.coins_100");

int balance = consumableHandler.getBalance(coinsProduct);

boolean spent = consumableHandler.spend(coinsProduct, 10);
if (!spent) {
    showInsufficientCoinsDialog();
}
```

### All Owned Products

```java
Set<String> owned = billingManager.getOwnedProducts(); // product IDs
```

### Pending Purchases

```java
PendingHandler pendingHandler = billingManager.getHandler(PurchaseType.PENDING);
if (pendingHandler != null) {
    boolean hasPending = pendingHandler.hasPendingPurchases();
    int count = pendingHandler.getPendingCount();
}
```

### Any Subscription Plan / A Specific Product

`BillingManager` also exposes plan-aware helpers so you don't have to `OR` together every
`SubscriptionPlan` yourself:

```java
// True if the user owns/subscribes to a specific registered product, whatever its type.
boolean owned = billingManager.hasPurchased("com.myapp.pro_monthly");

// True if ANY subscription plan (weekly/monthly/quarterly/yearly) is currently active.
boolean subscribed = billingManager.isSubscribed();

// True if this specific plan is active.
boolean onYearly = billingManager.isSubscribed(SubscriptionPlan.YEARLY);

// The plan currently active, or null.
SubscriptionPlan plan = billingManager.getActiveSubscriptionPlan();
```

These read the same state as [Subscription](#subscription) above — `isSubscribed()` /
`isSubscribed(plan)` delegate to `SubscriptionHandler.isAnyActive()` / `isActive(plan)`, so they
inherit the same expiry-priority rules described in
[Subscription Handling](#subscription-handling) and the same cancellation/refund behavior
described there.

---

## BillingCompat (Static Access)

`BillingManager` is created once via `BillingConfigBuilder` and is usually threaded through your
app (Application class, DI graph, ViewModel, etc.) so any screen can check entitlement. If you'd
rather not carry that reference around, `BillingCompat` is a static facade over the same calls:

```java
// No BillingManager reference needed anywhere below.
boolean owned      = BillingCompat.hasPurchased("com.myapp.remove_ads");
boolean subscribed = BillingCompat.isSubscribed();
boolean onYearly   = BillingCompat.isSubscribed(SubscriptionPlan.YEARLY);
SubscriptionPlan plan = BillingCompat.getActiveSubscriptionPlan();
```

`BillingConfigBuilder.build()` calls `BillingCompat.attach(manager)` automatically, so in the
common single-manager setup there's nothing extra to wire up. If you construct `BillingManager`
some other way, call `BillingCompat.attach(billingManager)` once yourself (e.g.
`Application.onCreate()`) before any static call is made.

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        BillingManager billingManager = new BillingConfigBuilder(this)
            .addProducts(myProducts)
            .build(); // BillingCompat is attached here automatically
    }
}

// Anywhere else in the app, e.g. a Composable, Fragment, or plain util class:
if (BillingCompat.isSubscribed()) {
    unlockPremiumContent();
}
```

Every `BillingCompat` method is safe to call before a manager is attached — they return
`false`/`null` instead of throwing. `BillingCompat.getManager()` returns the attached instance (or
`null`) if you need to drop back down to the full `BillingManager` API. `BillingCompat.detach()`
clears the reference, which is mainly useful for tests.

> [!NOTE]
> `BillingCompat` holds a single static reference. If your app builds more than one
> `BillingManager` (uncommon — most apps have exactly one), the most recently attached one wins;
> use the instance methods on `BillingManager` directly in that case instead of the static facade.

### Checking without a manager ever having been built this session

The methods above still need `BillingCompat.attach()` — direct or via `BillingConfigBuilder.build()`
— to have run at least once **in the current process**. That's fine if you build the manager in
`Application.onCreate()`, but if you build it lazily (e.g. only when the user opens a specific
"Premium" screen), any other screen that checks entitlement *before* that screen has ever run this
session will get `false`, even if the user is actually subscribed and that's already recorded on
disk from a previous session.

For that case, use the `Context`-taking overloads. They read the persisted `SharedPreferences`
state directly and don't require `attach()`/a live manager at all — only that *some* purchase or
sync wrote the data at some point, in some session:

```java
// No BillingManager, no BillingCompat.attach() call required first — safe even
// on a cold app start before any billing connection exists this session.
boolean subscribed   = BillingCompat.isSubscribed(context);
boolean onYearly     = BillingCompat.isSubscribed(context, SubscriptionPlan.YEARLY);
SubscriptionPlan plan = BillingCompat.getActiveSubscriptionPlan(context);
```

These read the same `subscriptions` prefs file and the same `SubscriptionHandler.SUFFIX_ACTIVE` /
`SUFFIX_EXPIRY` / `SUFFIX_SERVER_EXPIRY` key suffixes that `SubscriptionHandler` itself writes, so
they stay in sync with the library rather than requiring you to hardcode key strings in app code.

> [!IMPORTANT]
> These overloads don't talk to Play themselves — they're exactly as fresh as the last purchase or
> `syncPurchases()` call that ran *somewhere* in the app (this session or a previous one). Whichever
> screen actually owns the `BillingManager` should still call `syncPurchases()` on `onResume()` (see
> [Syncing Purchases](#syncing-purchases)) so the persisted state these overloads read stays current.

---

## Subscription Handling

### Expiry priority

Expiry is resolved in this order:

1. **Server-confirmed expiry** — stored via `SubscriptionHandler.saveServerExpiry(plan, millis)`,
   written automatically by `PurchaseFraudGuard.verifyWithServer()` when the server returns a
   valid `expiry` value. This is the authoritative value and is what `isActive()` prefers.
2. **Local calculation fallback** — `purchase.getPurchaseTime() + plan.getDays() * 86400000L`,
   used only when no server expiry is available. Does not account for grace periods, billing
   retries, paused subscriptions, or free trial periods — do not rely on it as the sole gate for
   production billing.

```java
SubscriptionHandler handler = billingManager.getHandler(PurchaseType.SUBSCRIPTION);
if (handler != null) {
    handler.saveServerExpiry(SubscriptionPlan.MONTHLY, expiryEpochMillis);
}

// Equivalent to OR-ing isActive() across every SubscriptionPlan yourself.
private boolean hasActiveSubscription() {
    SubscriptionHandler h = billingManager.getHandler(PurchaseType.SUBSCRIPTION);
    return h != null && h.isAnyActive(); // or billingManager.isSubscribed()
}
```

### Cancellation and refund detection

A user can end a subscription two ways, and they behave differently:

- **Cancel auto-renew** (from the in-app Play Store deep link, or directly in the Play Store app)
  — Google Play keeps returning the purchase as `PURCHASED` from `queryPurchasesAsync` until the
  already-paid period ends. `isActive()` / `isSubscribed()` correctly stay `true` until the stored
  expiry time passes — this is expected, the user paid for that period.
- **Refund or immediate revocation** — the purchase stops being returned by
  `queryPurchasesAsync` right away, before the natural expiry. `SubscriptionHandler.sync()`
  detects any plan that was previously cached as active but is no longer present in the fresh
  query result, and clears its cached state (`_active`, `_expiry`, and `_server_expiry` prefs
  entries) immediately, rather than leaving a stale future expiry in place. This is what makes
  `isActive()` / `isSubscribed()` trustworthy after a Play Store-side cancellation, not just a
  local one.

`sync()` runs automatically on `connect()` and whenever you call `billingManager.syncPurchases()`
(see [Syncing Purchases](#syncing-purchases)) — call it on `onResume()` so revocations made outside
the app are picked up promptly.

---

## Consumable Balances

Balances persist in `SharedPreferences` file `consumable_balances`. The library handles the full
lifecycle automatically:

- **On purchase:** `consumeAsync` is called immediately. On success, `product.getQuantity()` is
  added to the balance and `onProductPurchased` fires.
- **On sync:** Any unconsumed purchases found via `queryPurchasesAsync` are consumed and the
  balance is credited — this covers crashes between purchase and consumption.
- **On spend:** Balance is decremented within a single synchronous call. Returns `false` without
  modifying state if the balance is insufficient.

```java
ConsumableHandler handler = billingManager.getHandler(PurchaseType.CONSUMABLE);
ConsumableProduct gems = (ConsumableProduct) billingManager.getProduct("com.myapp.gems_50");

int balance = handler.getBalance(gems);

if (handler.spend(gems, 5)) {
    grantPowerUp();
} else {
    showStoreDialog();
}
```

Consumables are not "owned" in the traditional sense — `isOwned()` returns `true` only when the
balance is greater than zero.

---

## Syncing Purchases

Call `syncPurchases()` on every `onResume` to catch purchases made outside the app (the user
subscribed via the Play Store app directly, or a pending payment was settled):

```java
@Override
protected void onResume() {
    super.onResume();
    if (billingManager != null && billingManager.isReady()) {
        billingManager.syncPurchases();
    }
}
```

`syncPurchases()` calls `queryPurchasesAsync` for both `INAPP` and `SUBS` product types, which
triggers each handler's own `sync()` method. `onPurchasesSynced()` fires once a round completes
with response code `OK`.

---

## Persisting Purchase State

`BillingManager.persistPurchaseState(productId, isUnlocked)` writes ownership state directly to
the correct handler's `SharedPreferences` file, without going through a full purchase flow. It
delegates to `handler.setOwned(productId, isUnlocked)`, which calls `saveBoolean` in
`BaseProductHandler`.

```java
// Manually unlock after server verification
billingManager.persistPurchaseState("com.myapp.remove_ads", true);

// Revoke after a refund detected server-side
billingManager.persistPurchaseState("com.myapp.remove_ads", false);
```

Use this sparingly — the primary way state gets written is through normal purchase and sync flows.
It's intended for post-verification callbacks or admin revocations. For subscriptions, prefer
`SubscriptionHandler.saveServerExpiry()` since subscriptions are gated by expiry time, not a
boolean flag.

---

## Connection Lifecycle

```java
billingManager.setConnectionListener(new BillingConnectionListener() {

    @Override
    public void onConnected() {
        // BillingClient is ready and product details are being fetched.
        // Safe to check ownership and launch purchases from here onward.
        checkSubscriptionStatus();
    }

    @Override
    public void onDisconnected() {
        // Transient disconnect — the library auto-reconnects via
        // enableAutoServiceReconnection(). No action needed.
    }

    @Override
    public void onConnectionError(String error) {
        // Connection could not be established (no Play Store, no network).
        showBillingUnavailableMessage();
    }
});

// Manual connection control (autoConnect(false) scenario)
billingManager.connect();
billingManager.disconnect();

// Check readiness before any purchase
if (!billingManager.isReady()) {
    Toast.makeText(this, "Connecting to Google Play...", Toast.LENGTH_SHORT).show();
    return;
}

// Always release in onDestroy
@Override
protected void onDestroy() {
    super.onDestroy();
    billingManager.destroy(); // disconnects and releases the BillingClient
}
```

---

## Threading Model

| Call / callback | Thread it runs on | Notes |
|---|---|---|
| `BillingManager.connect()` / `disconnect()` | Caller's thread (call from main thread) | Delegates to `BillingClient.startConnection()` |
| `BillingClientStateListener` callbacks (`onBillingSetupFinished`, `onBillingServiceDisconnected`) | Main thread | Delivered on main by the Play Billing Library itself |
| `queryPurchasesAsync`, `queryProductDetailsAsync`, `consumeAsync`, `acknowledgePurchase` result callbacks | Main thread | Same as above |
| `PurchasesUpdatedListener.onPurchasesUpdated` | Main thread | Triggers `ProductHandler.onPurchaseSuccess` / `onPurchasePending` synchronously on main |
| `PurchaseEventListener` callbacks | Main thread | Invoked from the handler methods above |
| `SSVClient.verifyPurchase(...)` -> `Callback.onVerified` / `onError` | Background (single-thread `ExecutorService`, or a worker `Thread` for the timeout overload) | You must hop to the main thread yourself before touching UI from this callback |
| `IntegrityGuard.detectViolation()` / the detection portion of `enforce()` | Caller's thread | Performs blocking file/process I/O — see note below |
| `IntegrityGuard`'s violation dialog | Main thread | `showViolationDialog()` wraps in `runOnUiThread` internally regardless of caller's thread |
| `BillingManager.isFeatureUnlocked()`, `isUnlocked()`, `getOwnedProducts()`, `getProduct()` | Caller's thread | Reads plain `HashMap`/`HashSet` fields — not synchronized |
| `ConsumableHandler.getBalance()`, `spend()` | Caller's thread | The read-check-write happens within one synchronous call on a single thread; this is not the same as being safe under concurrent multi-threaded access |
| `SubscriptionHandler.isActive()`, `getExpiryTime()`, `getTimeRemaining()` | Caller's thread | Same caveat — unsynchronized in-memory maps |

Practical guidance: treat all `BillingManager`/handler read methods as main-thread-only, matching
the fact that every write path into the same in-memory state arrives on the main thread via
Billing Library callbacks. `SSVClient` callbacks are the one exception — always wrap UI/Activity
work in `runOnUiThread(...)` or post to a main-thread `Handler` inside `onResult`/`onVerified`/
`onError`. If `IntegrityGuard.enforce()`'s blocking I/O (`Runtime.exec`, `/proc/net/tcp` reads)
causes a noticeable delay before first frame, call `IntegrityGuard.detectViolation(context)` on a
background thread first, then act on the result on main.

---

## Data Persistence Reference

All persistence is via standard `Context.getSharedPreferences(name, MODE_PRIVATE)`. No encryption
is applied by this library — treat these files as plaintext.

| Prefs file | Owning class | Key pattern | Type | Written by | Notes |
|---|---|---|---|---|---|
| `non_consumable_purchases` | `NonConsumableHandler` | `<productId>` | `boolean` | `onPurchaseSuccess()`, `sync()` | `true` = owned. Read directly via `NonConsumableHandler.isUnlocked(String)`. |
| `consumable_balances` | `ConsumableHandler` | `<productId>` | `int` | `consumePurchase()`, `spend()` | Running balance, credited per consumption. |
| `subscriptions` | `SubscriptionHandler` | `<PLAN_NAME>_expiry` | `long` (epoch ms) | `onPurchaseSuccess()`, `sync()`, `saveServerExpiry()` | Local-fallback or last-known expiry. |
| `subscriptions` | `SubscriptionHandler` | `<PLAN_NAME>_active` | `boolean` | `onPurchaseSuccess()`, `sync()` | Written on every purchase/sync but not currently read back — `isActive()` derives state from the expiry keys instead. |
| `subscriptions` | `SubscriptionHandler` | `<PLAN_NAME>_server_expiry` | `long` (epoch ms) | `saveServerExpiry()` | Authoritative when present; takes priority over the local-fallback expiry. |
| `pending_purchases` | `PendingHandler` | (none currently) | — | — | The prefs file is opened but no keys are written — pending state is tracked in-memory only (`pendingPurchases`, `pendingSince`) and is lost on process death. |
| `billing_preferences` | `BillingManager` | (none currently) | — | — | Constant `PREFS_NAME` is declared but the file is never opened anywhere in `BillingManager`. Reserved for future use. |

`SubscriptionHandler.loadSubscriptions()` (constructor-time) also populates an in-memory
`activeSubscriptions` map with a `null` placeholder `Purchase` for any plan whose stored expiry is
still in the future. Don't iterate `activeSubscriptions` directly expecting non-null `Purchase`
objects — use `isActive()`, `getExpiryTime()`, and `getTimeRemaining()` instead, which read the
dedicated `expiryTimes` map.

---

## Android Auto Backup Rules

Exclude all billing `SharedPreferences` files from Android Auto Backup and device-to-device
transfer. Without this, a user restoring a backup onto a new device gets stale local purchase
state (for example, a non-consumable marked "owned" from the old device) that hasn't been
reconciled with Google Play, and it will display as unlocked until the next successful
`syncPurchases()` call.

### Android 12+ (`dataExtractionRules`)

```xml
<!-- res/xml/data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="non_consumable_purchases.xml"/>
        <exclude domain="sharedpref" path="consumable_balances.xml"/>
        <exclude domain="sharedpref" path="subscriptions.xml"/>
        <exclude domain="sharedpref" path="pending_purchases.xml"/>
        <exclude domain="sharedpref" path="billing_preferences.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="non_consumable_purchases.xml"/>
        <exclude domain="sharedpref" path="consumable_balances.xml"/>
        <exclude domain="sharedpref" path="subscriptions.xml"/>
        <exclude domain="sharedpref" path="pending_purchases.xml"/>
        <exclude domain="sharedpref" path="billing_preferences.xml"/>
    </device-transfer>
</data-extraction-rules>
```

### Pre-Android 12 (`fullBackupContent`)

```xml
<!-- res/xml/full_backup_content.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="non_consumable_purchases.xml"/>
    <exclude domain="sharedpref" path="consumable_balances.xml"/>
    <exclude domain="sharedpref" path="subscriptions.xml"/>
    <exclude domain="sharedpref" path="pending_purchases.xml"/>
    <exclude domain="sharedpref" path="billing_preferences.xml"/>
</full-backup-content>
```

### Wire both into your manifest

```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/full_backup_content"
    ...>
```

This is also a useful reference for a logout / "clear local state" flow: wipe the same set of
files (plus call `billingManager.destroy()`) when implementing account switching, so a new account
on the same device doesn't inherit the previous account's locally cached entitlements before the
first sync completes.

---

## Security

### Manifest Package Visibility

The core module's `AndroidManifest.xml` declares `<queries>` entries for all known patcher and
root management packages. This is required on API 30+ (Android 11+) for `PackageManager` to return
results for those packages without `QUERY_ALL_PACKAGES`, which Google Play restricts to core
system apps. No action is needed from the app developer — the manifest merger handles this
automatically when `:core` is included as a dependency.

### IntegrityGuard

`IntegrityGuard` performs multi-layer runtime environment checks. Call `enforce()` in
`Activity.onCreate()` before `setContentView()`:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!BuildConfig.DEBUG) {
        IntegrityGuard.enforce(this);
    }

    setContentView(R.layout.activity_main);
}
```

When a violation is detected, a non-cancellable dialog is shown and the app terminates via
`finishAffinity()` + `System.exit(0)`.

#### Detection capabilities

| Violation | Detection method |
|---|---|
| `PATCHER_INSTALLED` | Package name scan — Lucky Patcher, GameGuardian, Freedom, HappyMod, Parallel Space, Magisk, SuperSU, and others |
| `FRIDA_DETECTED` | File path scan (`/data/local/tmp/frida-server`, etc.) plus `/proc/net/tcp` port 27042 scan |
| `XPOSED_DETECTED` | `ClassLoader.loadClass("de.robv.android.xposed.XposedBridge")` plus stack trace inspection |
| `APP_DEBUGGABLE` | `ApplicationInfo.FLAG_DEBUGGABLE` check |
| `DEBUGGER_ATTACHED` | `Debug.isDebuggerConnected()` plus `Debug.waitingForDebugger()` plus JDWP thread scan |
| `DEVICE_ROOTED` | `su` binary path scan plus `which su` shell execution plus `Build.TAGS` test-keys check (scored, threshold 3 or higher) |
| `EMULATOR_DETECTED` | `Build` field heuristics — `FINGERPRINT`, `MODEL`, `MANUFACTURER`, `BRAND`, `DEVICE`, `PRODUCT`, `HARDWARE` |

#### Selective enforcement

For cases where you need to allow certain environments (for example, allowing emulators in QA
builds):

```java
IntegrityGuard.enforce(this,
    IntegrityGuard.ViolationType.EMULATOR_DETECTED,
    IntegrityGuard.ViolationType.APP_DEBUGGABLE);
```

#### Query without terminating

```java
IntegrityGuard.ViolationType violation = IntegrityGuard.detectViolation(context);
if (violation != null) {
    analytics.logSecurityEvent(violation.name());
}

IntegrityGuard.ViolationType violation2 = IntegrityGuard.detectViolation(context,
    Arrays.asList(IntegrityGuard.ViolationType.EMULATOR_DETECTED));
```

#### Individual static checks

```java
IntegrityGuard.isPatcherInstalled(context);
IntegrityGuard.isFridaDetected();
IntegrityGuard.isXposedDetected();
IntegrityGuard.isDebuggable(context);
IntegrityGuard.isDebuggerAttached();
IntegrityGuard.isRooted();
IntegrityGuard.isEmulator();
```

#### Root detection scoring

`isRooted()` uses a scored approach to reduce false positives on legitimate devices:

| Check | Points |
|---|---|
| `su` binary found at a known path | +3 |
| `which su` returns a result | +3 |
| `Build.TAGS` contains `test-keys` (only checked on non-emulators) | +1 |

A score of 3 or higher is required to return `true` — a device with only `test-keys` in its build
tags alone is not flagged as rooted.

### SecurityGuard

`SecurityGuard` is a facade that combines `IntegrityGuard` with APK signature verification.

```java
// In Activity.onCreate, before setContentView
if (!BuildConfig.DEBUG) {
    SecurityGuard.validateEnvironment(this, "AA:BB:CC:DD:EE:FF:...");
}

// With selective violation ignoring
SecurityGuard.validateEnvironment(this, "AA:BB:CC:...",
    IntegrityGuard.ViolationType.EMULATOR_DETECTED);
```

`expectedSignatureHash` is your release keystore's SHA-256 fingerprint as a colon-separated hex
string — the format `keytool` outputs natively, no Base64 encoding needed:

```bash
keytool -list -v -keystore release.jks -alias your_alias
# Copy the "SHA256:" line from the output, e.g.:
# SHA256: AA:BB:CC:DD:EE:FF:...
```

```java
SecurityGuard.validateEnvironment(this, "AA:BB:CC:DD:EE:FF:00:11:22:...");
```

```java
// Purchase integrity audit
SecurityGuard.checkPurchaseIntegrity(billingManager, "com.myapp.remove_ads", knownProductIds);
SecurityGuard.checkPurchaseIntegrity(billingManager, null, knownProductIds); // full audit
```

If `expectedSignatureHash` is `null` or empty, `isSignatureTampered` returns `false` immediately
(no violation) without inspecting signatures — this is the supported way to disable the check
during development, not a bug to work around. Always pass your real release hash before
publishing.

### PurchaseFraudGuard

`PurchaseFraudGuard` is designed to cross-check Google-Play-confirmed in-memory state against a
locally persisted cache to detect injected or patched purchases:

```java
PurchaseFraudGuard guard = new PurchaseFraudGuard(billingManager);
PurchaseFraudGuard.FraudResult result = guard.validate(context, "com.myapp.remove_ads");

switch (result.getStatus()) {
    case LEGITIMATE:
        break;
    case FAKE_PURCHASE:
        result.remediate(context, billingManager); // clears local state + re-syncs
        revokeAccess();
        break;
    case SYNC_REQUIRED:
        result.remediate(context, billingManager); // re-syncs to rebuild local state
        break;
    case NOT_PURCHASED:
        break;
}
```

| Local cache | Google Play | Result |
|---|---|---|
| Unlocked | Owned | `LEGITIMATE` |
| Unlocked | Not owned | `FAKE_PURCHASE` |
| Locked | Owned | `SYNC_REQUIRED` |
| Locked | Not owned | `NOT_PURCHASED` |

`validate()` computes its two inputs from `billingManager.isUnlocked(productId)` and
`billingManager.isFeatureUnlocked(productId)`. As covered in
[Checking Ownership](#checking-ownership), these two manager-level methods currently execute the
identical code path and will always agree — as a direct consequence, `validate()` called this way
can in practice only resolve to `LEGITIMATE` or `NOT_PURCHASED`; `FAKE_PURCHASE` and
`SYNC_REQUIRED` are reachable in code but not currently producible through this call, because the
two inputs can never disagree. `validateAll()`'s additional orphan-detection pass has the same
characteristic, since it also compares in-memory state against itself.

If you need genuine local-cache-vs-Play divergence detection today, compare the handler-level raw
cache reader against the in-memory state yourself:

```java
NonConsumableHandler ncHandler = billingManager.getHandler(PurchaseType.NON_CONSUMABLE);
boolean rawCacheSaysOwned = ncHandler.isUnlocked("com.myapp.remove_ads");      // raw SharedPreferences
boolean playConfirmsOwned = billingManager.isFeatureUnlocked("com.myapp.remove_ads"); // in-memory
if (rawCacheSaysOwned && !playConfirmsOwned) {
    // genuine divergence — treat as suspicious
}
```

```java
// Full audit across known products
List<String> knownProducts = Arrays.asList(
    "com.myapp.remove_ads",
    "com.myapp.pro_monthly",
    "com.myapp.pro_yearly"
);
boolean allClean = guard.validateAll(context, knownProducts);
```

### Server-Side Verification (SSVClient)

`SSVClient` handles asynchronous HTTPS communication with your backend. It's used internally by
`PurchaseFraudGuard.verifyWithServer()` but can also be used directly. Client-side checks are
complementary, not authoritative — always verify purchase tokens server-side before permanently
unlocking paid features.

#### Via PurchaseFraudGuard (recommended)

```java
PurchaseFraudGuard guard = new PurchaseFraudGuard(billingManager);
Purchasable removeAds = billingManager.getProduct("com.myapp.remove_ads");

guard.verifyWithServer(
    "https://your-backend.example.com/verify-purchase",
    removeAds,                  // Purchasable — its type determines "subscription" vs "product"
    purchaseToken,
    new PurchaseFraudGuard.ServerVerificationCallback() {
        @Override
        public void onResult(boolean isValid, long expiryMillis, boolean autoRenewing) {
            if (isValid) {
                unlockFeature(removeAds);
                // For subscriptions, SubscriptionHandler.saveServerExpiry(...) is
                // already called internally when expiryMillis > 0.
            }
        }
    }
);

// With a custom timeout (default is 10 seconds)
guard.verifyWithServer(5_000L, serverUrl, removeAds, purchaseToken, callback);
```

#### Direct SSVClient usage

```java
SSVClient client = new SSVClient("https://your-backend.example.com/verify-purchase");

client.verifyPurchase(
    "com.myapp.remove_ads",
    purchaseToken,
    "com.myapp",
    false,      // isSubscription
    new SSVClient.Callback() {
        @Override
        public void onVerified(boolean isValid, long expiryMillis, boolean autoRenewing) {
            // handle result — arrives on a background thread, see Threading Model
        }
        @Override
        public void onError(String error) {
            // handle network failure
        }
    }
);

client.shutdown(); // release the executor thread when done
```

#### Request payload sent to your server

```json
{
    "productId": "com.myapp.pro_monthly",
    "purchaseToken": "<token from Google Play>",
    "packageName": "com.myapp",
    "type": "subscription"
}
```

For one-time products, `"type"` is `"product"`.

#### Expected server response

```json
{
    "valid": true,
    "expiry": 1751234567000,
    "autoRenewing": true
}
```

For one-time products, return `"expiry": 0` and `"autoRenewing": false`.

On network failure or timeout, the library falls back to the existing Google Play client-side
state (`billingManager.isFeatureUnlocked(productId)`).

### SSV Backend Setup

`SSVClient` only cares about the request/response contract above — the backend behind it can be
anything that can receive a POST and call the Google Play Developer API. This repository does not
ship a backend; below is a reference shape using Cloudflare Workers, since Workers don't have
Node's `crypto` module and the `googleapis` SDK isn't Workers-compatible out of the box, so the
JWT signing step looks different from a typical Node backend. Adapt freely — a traditional Node,
Go, or any other server that produces the same JSON response works identically from `SSVClient`'s
point of view.

#### 1 — Prerequisites

- A Google Cloud project linked to your Play Console app.
- A service account with access to the Google Play Android Developer API.
- The service account JSON key file.
- A Cloudflare account with Workers enabled, and `wrangler` installed (`npm install -g wrangler`).

```
Google Cloud Console
  -> APIs & Services -> Enable: "Google Play Android Developer API"
  -> IAM & Admin -> Service Accounts -> Create -> Download JSON key

Play Console
  -> Setup -> API access -> Link to your Cloud project
  -> Grant the service account appropriate permissions
```

#### 2 — Project setup

```bash
npm create cloudflare@latest ssv-worker -- --type=hello-world
cd ssv-worker
npm install jose
```

`jose` performs RS256 JWT signing via the Web Crypto API, which is what's available in the
Workers runtime in place of Node's `crypto` module.

#### 3 — Store the service account key as a secret

```bash
wrangler secret put SERVICE_ACCOUNT_KEY
# Paste the full contents of the service account JSON key file when prompted
```

#### 4 — Worker (`src/index.js`)

```javascript
import { SignJWT, importPKCS8 } from 'jose';

const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';

async function getAccessToken(env) {
    const credentials = JSON.parse(env.SERVICE_ACCOUNT_KEY);
    const privateKey = await importPKCS8(credentials.private_key, 'RS256');

    const jwt = await new SignJWT({ scope: SCOPE })
        .setProtectedHeader({ alg: 'RS256' })
        .setIssuer(credentials.client_email)
        .setAudience(TOKEN_URL)
        .setIssuedAt()
        .setExpirationTime('1h')
        .sign(privateKey);

    const response = await fetch(TOKEN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            assertion: jwt,
        }),
    });

    const data = await response.json();
    return data.access_token;
}

export default {
    async fetch(request, env) {
        if (request.method !== 'POST') {
            return new Response('Not found', { status: 404 });
        }

        const { productId, purchaseToken, packageName, type } = await request.json();

        if (!productId || !purchaseToken || !packageName) {
            return Response.json({ valid: false, error: 'Missing fields' }, { status: 400 });
        }

        const accessToken = await getAccessToken(env);

        const apiUrl = type === 'subscription'
            ? `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/subscriptionsv2/tokens/${purchaseToken}`
            : `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${productId}/tokens/${purchaseToken}`;

        const apiResponse = await fetch(apiUrl, {
            headers: { Authorization: `Bearer ${accessToken}` },
        });

        if (apiResponse.status === 404) {
            return Response.json({ valid: false, expiry: 0, autoRenewing: false });
        }

        const result = await apiResponse.json();

        if (type === 'subscription') {
            const lineItem = result.lineItems?.[0];
            // SUBSCRIPTION_STATE_ACTIVE = 2, SUBSCRIPTION_STATE_IN_GRACE_PERIOD = 3
            const isActive = [2, 3].includes(result.subscriptionState);
            const expiryMillis = lineItem?.expiryTime ? new Date(lineItem.expiryTime).getTime() : 0;
            const autoRenewing = lineItem?.autoRenewingPlan?.autoRenewEnabled ?? false;
            return Response.json({ valid: isActive, expiry: expiryMillis, autoRenewing });
        }

        // purchaseState: 0 = Purchased, 1 = Canceled, 2 = Pending
        const isValid = result.purchaseState === 0;
        return Response.json({ valid: isValid, expiry: 0, autoRenewing: false });
    },
};
```

#### 5 — Deploy and test

```bash
wrangler deploy

curl -X POST https://ssv-worker.<your-subdomain>.workers.dev \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "com.myapp.pro_monthly",
    "purchaseToken": "your_token_here",
    "packageName": "com.myapp",
    "type": "subscription"
  }'
```

Expected:

```json
{ "valid": true, "expiry": 1751234567000, "autoRenewing": true }
```

#### 6 — Production checklist

- Add authentication to the Worker (a shared secret header, or Cloudflare Access) so only your
  app can call it.
- Never commit the service account key — `wrangler secret put` keeps it out of source control and
  out of `wrangler.toml`.
- Cache the OAuth access token (Workers KV or the Cache API) instead of requesting a new one on
  every call — Google access tokens are valid for up to an hour.
- Consider Real-Time Developer Notifications (RTDN) via Pub/Sub, pushed to a second Worker route,
  for push-based subscription state changes — this library only supports pull-based verification.

---

## Usage Patterns

### Pattern 1 — Simple single-Activity app

```java
public class MainActivity extends AppCompatActivity {
    private BillingManager billingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Purchasable pro = ProductFactory.createSubscription(
            "com.myapp.pro_monthly", "Monthly", 9.99, SubscriptionPlan.MONTHLY);

        billingManager = new BillingConfigBuilder(this)
            .addProduct(pro)
            .setListener(new PurchaseEventListener.SimpleListener() {
                @Override
                public void onProductPurchased(Purchasable product, String purchaseToken) {
                    unlockPremium();
                }
            })
            .build();

        billingManager.registerProduct(pro);

        billingManager.setConnectionListener(new BillingConnectionListener() {
            @Override public void onConnected() { checkStatus(); }
            @Override public void onDisconnected() {}
            @Override public void onConnectionError(String e) { showRetry(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (billingManager.isReady()) billingManager.syncPurchases();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        billingManager.destroy();
    }
}
```

### Pattern 2 — ViewModel / Repository (recommended for production)

Keep `BillingManager` in a `ViewModel` so it survives rotation.

```java
// BillingRepository.java
public class BillingRepository {
    private final BillingManager billingManager;

    public BillingRepository(Application app) {
        Purchasable monthly = ProductFactory.createSubscription(
            "com.myapp.pro_monthly", "Monthly", 9.99, SubscriptionPlan.MONTHLY);
        Purchasable yearly = ProductFactory.createSubscription(
            "com.myapp.pro_yearly", "Yearly", 49.99, SubscriptionPlan.YEARLY);

        billingManager = new BillingConfigBuilder(app)
            .addProduct(monthly)
            .addProduct(yearly)
            .setListener(listener)
            .build();

        billingManager.registerProduct(monthly);
        billingManager.registerProduct(yearly);
    }

    public BillingManager get() { return billingManager; }
    public void destroy() { billingManager.destroy(); }
}

// BillingViewModel.java
public class BillingViewModel extends AndroidViewModel {
    private final BillingRepository repo;

    public BillingViewModel(Application app) {
        super(app);
        repo = new BillingRepository(app);
    }

    public BillingManager getBillingManager() { return repo.get(); }

    @Override
    protected void onCleared() {
        super.onCleared();
        repo.destroy(); // called only when the owning Activity is truly finishing
    }
}

// In your Activity or Fragment:
BillingViewModel vm = new ViewModelProvider(this).get(BillingViewModel.class);
BillingManager billing = vm.getBillingManager();
```

### Pattern 3 — Application-level singleton

For apps where premium state is needed across multiple screens simultaneously.

```java
public class MyApp extends Application {
    private BillingManager billingManager;

    @Override
    public void onCreate() {
        super.onCreate();

        SubscriptionPlan.configure(new SubscriptionPlan.Config()
            .monthly("com.myapp.pro_monthly")
            .yearly("com.myapp.pro_yearly"));

        Purchasable removeAds = ProductFactory.createNonConsumable(
            "com.myapp.remove_ads", "Remove Ads", 1.99);
        Purchasable pro = ProductFactory.createSubscription(
            "com.myapp.pro_monthly", "Monthly", 9.99, SubscriptionPlan.MONTHLY);

        billingManager = new BillingConfigBuilder(this)
            .addProduct(removeAds)
            .addProduct(pro)
            .setListener(listener)
            .build();

        billingManager.registerProduct(removeAds);
        billingManager.registerProduct(pro);
    }

    public BillingManager getBillingManager() { return billingManager; }
}

// Access from anywhere:
BillingManager billing = ((MyApp) getApplicationContext()).getBillingManager();
```

### Pattern 4 — Subscriptions only (minimal setup)

```java
Purchasable monthly = new SubscriptionProduct.Builder()
    .id("com.myapp.pro_monthly").name("Monthly").price(9.99)
    .plan(SubscriptionPlan.MONTHLY).build();
Purchasable yearly = new SubscriptionProduct.Builder()
    .id("com.myapp.pro_yearly").name("Yearly").price(49.99)
    .plan(SubscriptionPlan.YEARLY).build();

BillingManager billing = new BillingConfigBuilder(this)
    .addProduct(monthly)
    .addProduct(yearly)
    .disableConsumable()
    .disableNonConsumable()
    .disablePending()
    .setOfferSelector(new DefaultOfferSelector("trial"))
    .setListener(listener)
    .build();

billing.registerProduct(monthly);
billing.registerProduct(yearly);
```

### Pattern 5 — Gating content without launching a purchase

```java
private boolean isPremium() {
    return billingManager.isFeatureUnlocked("com.myapp.remove_ads");
}

private boolean hasActiveSubscription() {
    SubscriptionHandler h = billingManager.getHandler(PurchaseType.SUBSCRIPTION);
    if (h == null) return false;
    return h.isActive(SubscriptionPlan.MONTHLY)
        || h.isActive(SubscriptionPlan.YEARLY);
}
```

### Pattern 6 — Restore purchases button

Recommended (and, in some cases, required by Play policy) for apps with non-consumable or
subscription products.

```java
restoreButton.setOnClickListener(v -> {
    if (!billingManager.isReady()) {
        Toast.makeText(this, "Not connected to Google Play", Toast.LENGTH_SHORT).show();
        return;
    }
    billingManager.syncPurchases();
    // onPurchasesSynced() fires when the round completes.
});
```

---

## Play Console Setup Checklist

Code-complete integration will not work until the following is configured in Play Console. This is
the most common source of "nothing happens" / "ProductDetails not found" reports.

- [ ] App created in Play Console, with a payments/merchant profile configured.
- [ ] At least one build uploaded to an internal testing (or higher) track.
- [ ] License testers added under Setup -> License testing, so test purchases don't charge real
  money.
- [ ] In-app products and/or subscriptions created under Monetize -> Products, with product IDs
  that exactly match (case-sensitive) the IDs used in `NonConsumableProduct`,
  `ConsumableProduct`, `SubscriptionProduct`, or `SubscriptionPlan.configure(...)`.
- [ ] Products/subscriptions activated — draft products are not returned by
  `queryProductDetailsAsync`, which is why `launchPurchase()` silently no-ops.
- [ ] Subscriptions have at least one base plan (and any offers referenced via your
  `OfferSelector` tags) — base plans are mandatory for current Billing Library versions.
- [ ] The installed build's package name and signing certificate match what's associated with the
  app on Play.
- [ ] Google Play Developer API access enabled for server-side verification: link a Google Cloud
  project, create a service account under Setup -> API access, and grant it the appropriate
  permission in Play Console.
- [ ] (Recommended, not implemented by this library) Real-time developer notifications (RTDN)
  configured via Pub/Sub, so your backend learns about renewals, cancellations, and refunds
  without polling.

---

## Edge Cases & Pitfalls

**Product details queried after connection, not at build time.**
`setPendingProducts()` stores products during `build()`. `queryProductDetails()` is called inside
`onBillingSetupFinished`, after the `BillingClient` is ready. If `autoConnect(false)` is used,
product details aren't available until after you call `billingManager.connect()` and `onConnected`
fires. Never call `execute()` before `onConnected`.

**`SubscriptionPlan.configure()` must be called before `build()`.**
`configure()` mutates static fields on the enum. If called after `BillingConfigBuilder.build()`,
already-registered products keep the old product IDs and `SubscriptionPlan.fromProductId()` may
fail to resolve them during purchase handling.

**Pending purchases.**
Never unlock features in `onPurchasePending`. `PendingHandler` only signals a completed purchase
via `onProductPurchased` after the payment is confirmed on a subsequent `sync()`. Show a "payment
pending" UI state. `PendingHandler`'s state is also in-memory only (see
[Data Persistence Reference](#data-persistence-reference)) and won't survive process death — rely
on `sync()` after relaunch.

**Consumable re-consumption on crash recovery.**
If the app crashes after a purchase completes but before `consumeAsync` finishes, the unconsumed
purchase is detected on the next `sync()` and consumed again. The balance is credited correctly.
Ensure game logic triggered by `onProductPurchased` is idempotent.

**Subscription expiry without server verification.**
The local fallback (`purchaseTime + days`) does not account for grace periods, billing retries,
plan pauses, or trial end dates. Gate subscription features on `isActive()` backed by a
server-verified expiry stored via `saveServerExpiry()`.

**`NonConsumableHandler.sync()` is safe on failure.**
`sync()` only clears `ownedProducts` after confirming `BillingResponseCode.OK`. If the query
fails, the in-memory set is untouched and users retain their cached ownership state until the next
successful sync.

**Multiple product IDs in one `Purchase` object.**
Google Play can bundle multiple product IDs into a single `Purchase`. The library iterates
`purchase.getProducts()` and dispatches each ID to the correct handler individually.

**Handler not registered.**
If a `PurchaseType` is disabled via `disableConsumable()` etc. and a product of that type is
purchased anyway, `handlePurchase()` silently drops the event. Only disable handlers for product
types your app will never sell.

**SharedPreferences divergence after refund.**
After a refund, Google Play removes the purchase but `SharedPreferences` retains `true` until the
next `sync()`. Gate critical features on `isFeatureUnlocked()` rather than only the raw cache.

**`OfferSelector` returning null.**
If your custom `OfferSelector` returns `null`, `launchPurchase` logs an error and aborts without
showing the purchase sheet. Always fall back to `offers.get(0).getOfferToken()`.

**Silent failure on purchase launch and on cancellation.**
Both "product details not cached" and `USER_CANCELED` currently produce no listener callback (see
[Listening to Purchase Events](#listening-to-purchase-events)). Don't build a purchase-flow UI
that depends solely on `onPurchaseError` to dismiss a loading state.

**Signature hash format.**
`SecurityGuard.validateEnvironment` compares using `equalsIgnoreCase` against the colon-separated
hex SHA-256 output from `keytool`. Do not Base64-encode the hash.

---

## Known Limitations

This library deliberately, or in some cases currently, does not handle:

- **Multi-quantity purchases.** Google Play managed products are always purchased at quantity 1
  per transaction; `ConsumableProduct.quantity` is a local credit-award amount, not a purchase-time
  selector.
- **Subscription pause/resume.** No API surface for Play's subscription pause feature.
- **Installment plans.** Not exposed, even though the underlying Billing Library 9.x supports them.
- **Prepaid plans** beyond the basic upgrade/replacement flow (`upgradeFrom`).
- **Family sharing detection.**
- **Real-time developer notifications (RTDN).** Verification is pull-based only — there is no
  push/webhook handling in this library.
- **`PurchaseType.PENDING` requires a custom `Purchasable`.** No built-in product class returns
  this type by default.
- **`onProductRestored` is not currently invoked** by any built-in handler's `sync()` path.
- **`onPurchaseFailure` on `ProductHandler` is effectively unreachable** in normal operation —
  `GooglePlayProvider.onPurchasesUpdated()` never calls it; cancellations and generic error
  response codes only produce log output.
- **`isUnlocked()` and `isFeatureUnlocked()` on `BillingManager` currently delegate to the same
  code path** and will always agree — see [PurchaseFraudGuard](#purchasefraudguard) for the
  practical implication and a workaround.
- **`BillingManager`'s product registry (`productMap`) is not auto-populated by
  `BillingConfigBuilder`.** Call `registerProduct()` per product after `build()`.
- **`PurchaseBuilder.withProration(String)` is stored but not read** — subscription upgrades
  always use `ReplacementMode.CHARGE_PRORATED_PRICE`.
- **In-memory handler state is not synchronized** for multi-threaded access — see
  [Threading Model](#threading-model).
- **No built-in retry/backoff** for transient connection failures beyond the Billing Library's own
  `enableAutoServiceReconnection()`.
- **`pending_purchases` and `billing_preferences` SharedPreferences files are currently unused**
  for persistence — reserved for future use.

---

## FAQ

<details>
<summary>Why is isFeatureUnlocked() / isUnlocked() / getOwnedProducts() always false or empty, even after a successful purchase?</summary>

`BillingManager` keeps its own product registry (`productMap`), separate from the per-type caches
populated inside each `ProductHandler` by `BillingConfigBuilder`. If you never called
`billingManager.registerProduct(product)` for that product after `build()`, the manager-level
methods return `false`/empty regardless of actual ownership. See
[Building the BillingManager](#building-the-billingmanager).

</details>

<details>
<summary>Why does the subscribe/purchase button do nothing when tapped?</summary>

Almost always: `ProductDetails` for that product haven't been cached yet, either because the
billing client wasn't connected when `queryProductDetailsAsync` was meant to run, or the product/
offer isn't active in Play Console. `launchPurchase()` logs an error and returns without calling
any listener method in this case. Check Logcat for `GooglePlayProvider`, confirm
`billingManager.isReady()` is `true` before launching, and confirm the product is active in Play
Console — see [Play Console Setup Checklist](#play-console-setup-checklist).

</details>

<details>
<summary>Why is my purchase flagged as FAKE_PURCHASE by PurchaseFraudGuard when nothing is wrong?</summary>

Given the current implementation (see [PurchaseFraudGuard](#purchasefraudguard)), `validate()`
cannot actually produce `FAKE_PURCHASE` through normal use, because its two inputs read the same
in-memory state and can't disagree. If you're seeing unexpected behavior, you're more likely
hitting the `productMap` registration gap — the product genuinely shows as not-owned because it
was never registered, which is a `NOT_PURCHASED` result, not a fraud detection, but can look
similar from the UI side depending on how you've wired your own logic around these calls.

</details>

<details>
<summary>Can I use this library without subscriptions, or without any other product type?</summary>

Yes. Call `.disableSubscription()`, `.disableConsumable()`, `.disableNonConsumable()`, or
`.disablePending()` on `BillingConfigBuilder` for any types you don't sell. Just make sure you
never register a product of a disabled type — it will be silently dropped.

</details>

<details>
<summary>What happens if the user cancels the purchase sheet?</summary>

Nothing is currently surfaced through `PurchaseEventListener` — `USER_CANCELED` only produces a
log line. Treat "purchase sheet closed, no callback fired" as an implicit cancellation and reset
any loading state on a timeout, rather than waiting indefinitely for `onPurchaseError`.

</details>

<details>
<summary>Do I need a backend?</summary>

Not strictly, for basic gating — client-side state (`isFeatureUnlocked`) works standalone. For
anything resistant to tampering, refunds, chargebacks, or accurate subscription status, yes:
implement [server-side verification](#server-side-verification-ssvclient) against the Google Play
Developer API.

</details>

<details>
<summary>Is this library thread-safe?</summary>

Treat it as main-thread-only for all reads and writes, with one exception: `SSVClient` network
callbacks arrive on a background thread. See [Threading Model](#threading-model).

</details>

<details>
<summary>How do I let the user upgrade or downgrade between subscription tiers?</summary>

Use `PurchaseBuilder.subscribe(newPlan).upgradeFrom(oldProductId).execute()`. The replacement is
always charged with `CHARGE_PRORATED_PRICE` in the current version — see
[Launching Purchases](#launching-purchases) for the full mode reference and current limitation.

</details>


## Changelog

All notable changes to this module are documented here. This project does not yet formally follow
[Semantic Versioning](https://semver.org/) (it will, starting from a `1.0.0` API-stable release) —
treat all `0.0.x` releases as potentially containing breaking changes.

### [0.0.1] — 2026-06-30

Initial public release.

- `BillingConfigBuilder` / `PurchaseBuilder` fluent APIs for setup and purchase flows.
- Support for `NonConsumableProduct`, `ConsumableProduct`, `SubscriptionProduct`, and the
  `PENDING` purchase type (custom `Purchasable` required).
- `NonConsumableHandler`, `ConsumableHandler`, `SubscriptionHandler`, `PendingHandler` with local
  `SharedPreferences`-backed persistence.
- `GooglePlayProvider` wrapping Play Billing Library 9.1.0, including subscription
  upgrade/downgrade via `SubscriptionProductReplacementParams`.
- `OfferSelector` / `DefaultOfferSelector` for subscription offer/trial-tag selection.
- `IntegrityGuard` runtime environment checks (patchers, Frida, Xposed, debugger, root, emulator).
- `SecurityGuard` facade combining `IntegrityGuard` with APK signature verification.
- `PurchaseFraudGuard` local-vs-Play purchase state comparison, plus `SSVClient`-based
  server-side verification scaffolding.
- Documented integration gaps for this release — see [Known Limitations](#known-limitations).

---

## ProGuard / R8

The library's security effectiveness depends on code obfuscation. Ensure release builds enable
minification:

```groovy
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

Add to your app's `proguard-rules.pro`:

```pro
# Google Play Billing
-keep class com.android.billingclient.api.** { *; }

# BillingCompat — keep handler dispatch and product model classes
-keep class com.euptron.billingcompat.core.handlers.** { *; }
-keep class com.euptron.billingcompat.core.model.** { *; }
-keep class com.euptron.billingcompat.core.products.** { *; }
-keep class com.euptron.billingcompat.core.listeners.** { *; }
-keep class com.euptron.billingcompat.core.providers.** { *; }

# Security — obfuscate body but do not strip the class or its members
-keepclassmembers class com.euptron.billingcompat.core.security.IntegrityGuard { *; }
-keepclassmembers class com.euptron.billingcompat.core.security.SecurityGuard { *; }
-keepclassmembers class com.euptron.billingcompat.core.security.PurchaseFraudGuard { *; }
```

---

## Contributing

This module is currently maintained as a single-owner project (`euptron/Billing-Compat`). Issues
and pull requests are welcome — please include:

- The Play Billing Library version you're targeting.
- A minimal repro (handler type, product type, and the exact callback/behavior you expected vs.
  observed).
- Whether the issue reproduces against a license-testing account or a real purchase.