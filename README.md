# BillingCompat

A Secure Google Play Billing Wrapper for Android

[![JitPack](https://jitpack.io/v/euptron/billing-compat.svg)](https://jitpack.io/#euptron/billing-compat)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-23%2B-brightgreen.svg)](https://developer.android.com/studio/releases/platforms)
[![Play Billing](https://img.shields.io/badge/Play%20Billing-9.x-orange.svg)](https://developer.android.com/google/play/billing/release-notes)

BillingCompat wraps Google Play Billing Library 9.x with a clean builder API, multi-layered runtime security, and a server-side verification client ready to point at your own backend. Define your products, build the manager, launch a purchase — the parts that usually take a week of integration work are already done.

## Why use this?

Google Play Billing is correct but low-level: you write your own product registries, persist ownership state by hand, reconcile `SharedPreferences` against `queryPurchasesAsync`, and stand up a backend to verify purchase tokens before trusting anything the client tells you. None of that is optional if you want a paywall that survives refunds, patched APKs, and rooted devices — it's just usually built from scratch, once per app.

BillingCompat is that integration done once, properly. One builder handles non-consumables, consumables, subscriptions, and pending purchases instead of four separate code paths. Security checks for Lucky Patcher, Frida, Xposed, root, debuggers, emulators, and APK re-signing are built in, not a follow-up ticket. `SSVClient` gives you a ready async client for server-side verification — wire it to any backend that calls the Google Play Developer API. An optional themeable paywall UI is included for when you don't want to build that screen either.

This is meant to be production infrastructure, not a sample project.

## Quick Start

```java
// 1. Define a product
Purchasable removeAds = new NonConsumableProduct.Builder()
    .id("remove_ads")
    .name("Remove Ads")
    .price(2.99)
    .build();

// 2. Build the manager
BillingManager billingManager = new BillingConfigBuilder(this)
    .addProduct(removeAds)
    .setListener(purchaseEventListener)
    .autoConnect(true)
    .build();

billingManager.registerProduct(removeAds);

// 3. Launch a purchase
new PurchaseBuilder(activity, billingManager)
    .nonConsumable("remove_ads")
    .execute();
```

Full setup, listener wiring, and integration details: [core README](core/README.md).

## Features

- Non-consumable, consumable, subscription, and pending purchase support in one unified API
- Builder-based configuration (`BillingConfigBuilder`) and purchase flow (`PurchaseBuilder`)
- Subscription upgrades and downgrades with proration, base plans, and configurable offer selection
- Local ownership and balance persistence per product type, synced against Google Play
- `IntegrityGuard` for runtime detection of patchers, Frida, Xposed, debuggers, root, and emulators
- `SecurityGuard` for APK signature verification, to catch re-signed builds
- `PurchaseFraudGuard` for local-cache-vs-Play purchase state comparison
- `SSVClient`, an async server-side verification client — bring your own backend
- Optional `ui` module: a themeable, backend-agnostic paywall `DialogFragment`
- Built on Google Play Billing Library 9.x
- MIT licensed

## Installation

BillingCompat is distributed via [JitPack](https://jitpack.io/#euptron/Billing-Compat).

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
// app/build.gradle
dependencies {
    implementation 'com.github.euptron.billing-compat:core:0.0.1'

    // Optional — themeable paywall UI
    implementation 'com.github.euptron.billing-compat:ui:0.0.1'
}
```

## Modules

BillingCompat is split into two independent modules. Use either on its own, or both together.

| Module | What it is | Documentation |
|---|---|---|
| `core` | Billing logic: purchase flows, ownership state, security, and server-side verification. No UI dependencies. | [core/README.md](core/README.md) |
| `ui` | A standalone, themeable paywall `DialogFragment`. No billing logic — delegates every action to a callback, so it works with `core` or any backend you bring. | [ui/README.md](ui/README.md) |

## Security

BillingCompat treats client-side billing state as something to verify, not trust.

`IntegrityGuard` runs runtime environment checks for patching tools (Lucky Patcher, GameGuardian, Freedom, and others), instrumentation frameworks (Frida, Xposed), active debuggers, rooted devices, and emulators. `SecurityGuard` compares the running APK's signing certificate against your release signature to detect re-signed or tampered builds. `PurchaseFraudGuard` and `SSVClient` cross-check local purchase state and verify purchase tokens against the Google Play Developer API through a backend you control — the client-side contract and a reference implementation pattern are documented in the core README so you can stand up your own.

Full details and current implementation behavior: [core README — Security](core/README.md#security).

## Support this Project <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Hand%20gestures/Backhand%20Index%20Pointing%20Down.png" alt="Backhand Index Pointing Down" width="25" height="25" />

**Via Ko-fi:**

<a href='https://ko-fi.com/G0D720U4ID' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://storage.ko-fi.com/cdn/kofi1.png?v=6' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

**Don't have Ko-fi?**
[Click here](https://flutterwave.com/donate/pi2ia1mtwydm)

## License
This project is licensed under the [MIT](./LICENSE).