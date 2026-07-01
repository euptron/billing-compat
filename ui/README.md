# BillingCompat — UI Module

A standalone, fully themeable paywall `DialogFragment` for Android. Displays a subscription plan selection screen with hero imagery, feature highlights, and plan cards. Contains zero billing logic — all purchase actions are delegated to the caller via a callback interface, making it compatible with any billing backend.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [PaywallConfig](#paywallconfig)
    - [Builder Reference](#builder-reference)
- [Subscription Plans](#subscription-plans)
    - [SubscriptionPlanUiModel](#subscriptionplanuimodel)
- [Feature Items](#feature-items)
    - [FeatureItemUiModel](#featureitempuimodel)
- [Showing the Dialog](#showing-the-dialog)
- [Handling Callbacks](#handling-callbacks)
    - [PaywallCallback](#paywallcallback)
- [Wiring to the Core Module](#wiring-to-the-core-module)
- [Theming & Colors](#theming--colors)
    - [Light Theme Tokens](#light-theme-tokens)
    - [Dark Theme Tokens](#dark-theme-tokens)
    - [Overriding Colors](#overriding-colors)
- [Plan Orientation](#plan-orientation)
- [Edge-to-Edge & Inset Handling](#edge-to-edge--inset-handling)
- [Dialog Animations](#dialog-animations)
- [Edge Cases & Pitfalls](#edge-cases--pitfalls)
- [Accessibility](#accessibility)

---

## Requirements

| Requirement | Minimum |
|---|---|
| Android minSdk | 21 |
| Java | 1.8 |
| Material Components | Required (`libs.material`) |
| AndroidX AppCompat | Required (`libs.appcompat`) |
| AndroidX Fragment | Required (for `DialogFragment`) |

---

## Installation

Add the module to your app's `build.gradle`:

```groovy
dependencies {
    implementation project(':ui')
}
```

Your app's theme must be a descendant of a Material3 theme for the plan cards and buttons to render correctly:

```xml
<style name="AppTheme" parent="Theme.Material3.Light.NoActionBar">
    ...
</style>
```

---

## Quick Start

```java
// 1. Build subscription plan models
SubscriptionPlanUiModel monthly = new SubscriptionPlanUiModel.Builder()
    .planId("subs_monthly")
    .label("Monthly")
    .price("$9.99")
    .period("/ month")
    .build();

SubscriptionPlanUiModel yearly = new SubscriptionPlanUiModel.Builder()
    .planId("subs_yearly")
    .label("Yearly")
    .price("$49.99")
    .period("/ year")
    .discountBadge("Save 58%")
    .recommended(true)
    .build();

// 2. Build the config
PaywallConfig config = new PaywallConfig.Builder()
    .appName("MyApp")
    .heroImage(ContextCompat.getDrawable(this, R.drawable.ic_premium_hero))
    .ctaTitle("Unlock Everything")
    .ctaSubtitle("Get unlimited access with a single subscription.")
    .featuresCardHeader("What you get")
    .addFeature(new FeatureItemUiModel("Ad-free experience"))
    .addFeature(new FeatureItemUiModel("Unlimited downloads", "Movies, music & more"))
    .addFeature(new FeatureItemUiModel("Priority support"))
    .addPlan(monthly)
    .addPlan(yearly)
    .defaultSelectedIndex(1)   // Pre-select Yearly
    .planOrientation(PaywallConfig.Orientation.VERTICAL)
    .subscribeCta("Start Free Trial")
    .restoreLabel("Restore Purchase")
    .cancelNote("Cancel anytime. No hidden fees.")
    .build();

// 3. Show the dialog
PaywallDialog.newInstance(config)
    .setCallback(paywallCallback)
    .show(getSupportFragmentManager(), "paywall");
```

---

## PaywallConfig

`PaywallConfig` is an immutable value object that drives every piece of content in the dialog. Build one with `PaywallConfig.Builder`.

### Builder Reference

| Method | Type | Required | Default | Description |
|---|---|---|---|---|
| `appName(String)` | `String` | No | `""` | App name shown in the top bar |
| `heroImage(Drawable)` | `Drawable` | No | `null` | Banner image at the top of the paywall. Pass `null` to hide the image view entirely. |
| `ctaTitle(String)` | `String` | No | `""` | Large headline below the hero image |
| `ctaSubtitle(String)` | `String` | No | `""` | Supporting copy below the CTA title |
| `featuresCardHeader(String)` | `String` | No | `"What you get"` | Heading text on the features card |
| `addFeature(FeatureItemUiModel)` | — | No | — | Appends one feature row |
| `features(List<FeatureItemUiModel>)` | — | No | — | Replaces the entire feature list |
| `addPlan(SubscriptionPlanUiModel)` | — | **Yes** | — | Appends one plan card. At least one plan is required. |
| `plans(List<SubscriptionPlanUiModel>)` | — | **Yes** | — | Replaces the entire plan list |
| `planOrientation(Orientation)` | `Orientation` | No | `VERTICAL` | Layout direction of the plan cards |
| `defaultSelectedIndex(int)` | `int` | No | `0` | Zero-based index of the plan selected on open. Out-of-range values are clamped to `0`. |
| `subscribeCta(String)` | `String` | No | `"Subscribe Now"` | Label on the primary subscribe button |
| `restoreLabel(String)` | `String` | No | `"Restore Purchase"` | Label on the restore purchases button |
| `cancelNote(String)` | `String` | No | `"Cancel anytime. No commitments."` | Fine-print text below the buttons |

> **Immutability:** `PaywallConfig` is fully immutable after `build()`. To update the dialog content, dismiss the current instance and show a new one with a fresh config.

---

## Subscription Plans

### SubscriptionPlanUiModel

Each plan is rendered as a tappable card. The currently selected plan is highlighted in the primary color.

```java
SubscriptionPlanUiModel plan = new SubscriptionPlanUiModel.Builder()
    .planId("subs_yearly")          // Must match the product ID in your billing backend
    .label("Yearly")                // Card title e.g. "Weekly", "Monthly", "Yearly"
    .price("$49.99")                // Formatted price string — no currency conversion is performed
    .period("/ year")               // Billing period string
    .discountBadge("Save 58%")      // Optional chip shown on the card. Null = hidden.
    .recommended(true)              // Shows a "Most Popular" chip when true
    .build();
```

| Field | Required | Notes |
|---|---|---|
| `planId` | Yes | Used in `PaywallCallback.onSubscribeClicked()` to identify which plan was tapped |
| `label` | Yes | — |
| `price` | Yes | Pre-format this string. The library performs no currency conversion. |
| `period` | Yes | — |
| `discountBadge` | No | `null` hides the chip entirely |
| `recommended` | No | Defaults to `false` |

> **Currency formatting:** Prices are pure display strings. Format them using `BillingClient`'s `ProductDetails.OneTimePurchaseOfferDetails.getFormattedPrice()` or equivalent before passing them to this model to ensure correct locale-aware formatting.

---

## Feature Items

### FeatureItemUiModel

Each item renders as a checkmark icon + title + optional subtitle row inside the "What you get" card.

```java
// Title only
new FeatureItemUiModel("Ad-free experience")

// Title + subtitle
new FeatureItemUiModel("Unlimited downloads", "Movies, music, podcasts & more")
```

- The `text` field is required and must not be empty — an `IllegalArgumentException` is thrown otherwise.
- The subtitle row is hidden entirely (`View.GONE`) when `subtitle` is `null` or empty.

---

## Showing the Dialog

`PaywallDialog` extends `DialogFragment`. Use the `FragmentManager` to display it:

```java
PaywallDialog.newInstance(config)
    .setCallback(callback)
    .show(getSupportFragmentManager(), "paywall");
```

To dismiss programmatically:

```java
PaywallDialog dialog = (PaywallDialog) getSupportFragmentManager()
    .findFragmentByTag("paywall");
if (dialog != null) dialog.dismiss();
```

> **Fragment back stack:** `PaywallDialog` does not add itself to the back stack. Dismissal is handled by the Cancel button, the system back gesture, and programmatic `dismiss()`.

---

## Handling Callbacks

### PaywallCallback

The dialog fires three events. Wire them to your billing logic:

```java
PaywallCallback callback = new PaywallCallback() {

    @Override
    public void onSubscribeClicked(SubscriptionPlanUiModel selectedPlan) {
        // selectedPlan.getPlanId() gives you the product ID to purchase
        // Dismiss the dialog and launch the billing flow:
        dismiss(); // optional — dismiss before or after purchase
        startBillingFlow(selectedPlan.getPlanId());
    }

    @Override
    public void onRestoreClicked() {
        // Trigger a purchase sync with the billing backend
        billingManager.syncPurchases();
    }

    @Override
    public void onDismissed() {
        // Called for all dismissal paths: Cancel button, back gesture, programmatic dismiss
        // Log analytics or update UI state here
    }
};
```

> **onDismissed is always called.** Whether the user taps Cancel, completes a purchase and you dismiss the dialog, or presses the system back button — `onDismissed()` fires exactly once. Do not duplicate cleanup logic in `onDismissed` and `onSubscribeClicked`.

---

## Wiring to the Core Module

The UI module has no dependency on the core module. To connect them, map `planId` back to the core `SubscriptionPlan` enum in `onSubscribeClicked`:

```java
@Override
public void onSubscribeClicked(SubscriptionPlanUiModel selectedPlan) {
    // Map the UI planId to the core SubscriptionPlan enum
    SubscriptionPlan corePlan = SubscriptionPlan.fromProductId(selectedPlan.getPlanId());
    if (corePlan == null) {
        Log.e(TAG, "Unknown plan ID: " + selectedPlan.getPlanId());
        return;
    }

    new PurchaseBuilder(activity, billingManager)
        .subscribe(corePlan)
        .execute();
}

@Override
public void onRestoreClicked() {
    billingManager.syncPurchases();
}
```

Ensure the `planId` values in your `SubscriptionPlanUiModel` instances exactly match the product IDs defined in `SubscriptionPlan` (e.g. `"subs_monthly"`, `"subs_yearly"`).

---

## Theming & Colors

All colors are defined as named resources, making them straightforward to override in your app's `res/values/colors.xml`.

### Light Theme Tokens

| Token | Default | Usage |
|---|---|---|
| `paywall_primary` | `#0A0A0A` | Selected plan card background, checkmark icons, subscribe button |
| `paywall_on_primary` | `#FFFFFF` | Text/icons on primary-colored surfaces |
| `paywall_secondary` | `#16A34A` | Secondary accent (botanical teal-green) |
| `paywall_on_secondary` | `#FFFFFF` | Text on secondary surfaces |
| `paywall_surface` | `#F5F5F7` | Unselected plan cards, features card background |
| `paywall_on_surface` | `#171717` | Primary text on surfaces |
| `paywall_on_surface_variant` | `#737373` | Secondary/muted text (period, subtitle, cancel note) |
| `paywall_background` | `#FFFFFF` | Full-screen dialog background |
| `paywall_on_background` | `#0A0A0A` | Text on the background (CTA title, plan label) |
| `paywall_discount_chip_background` | `#2622C55E` | Discount badge chip fill (38% opacity green) |
| `paywall_discount_chip_stroke` | `#ED22C55E` | Discount badge chip border (93% opacity green) |
| `paywall_scrim_start` | `#FFFFFFFF` | Top bar scrim gradient start (opaque white) |
| `paywall_scrim_end` | `#00FFFFFF` | Top bar scrim gradient end (transparent) |

### Dark Theme Tokens

Defined in `res/values-night/colors.xml`. The dark palette uses OLED black (`#000000`) as the background and soft silver (`#E5E5E5`) as the primary color for high contrast on dark surfaces.

| Token | Dark Value |
|---|---|
| `paywall_primary` | `#E5E5E5` |
| `paywall_on_primary` | `#0A0A0A` |
| `paywall_background` | `#000000` |
| `paywall_surface` | `#121212` |
| `paywall_on_surface` | `#D4D4D4` |

### Overriding Colors

To rebrand the paywall with your app's brand color, override only the tokens you need in your app's color file:

```xml
<!-- res/values/colors.xml — app-level override -->
<color name="paywall_primary">#6200EE</color>
<color name="paywall_on_primary">#FFFFFF</color>
```

```xml
<!-- res/values-night/colors.xml — dark mode override -->
<color name="paywall_primary">#BB86FC</color>
<color name="paywall_on_primary">#000000</color>
```

---

## Plan Orientation

| Orientation | Behaviour |
|---|---|
| `Orientation.VERTICAL` | Plans stack top-to-bottom. Recommended for 2–4 plans. |
| `Orientation.HORIZONTAL` | Plans scroll horizontally. Better for 4+ plans or compact vertical space. |

```java
.planOrientation(PaywallConfig.Orientation.HORIZONTAL)
```

Both orientations use `nestedScrollingEnabled = false` to integrate cleanly with the outer `RecyclerView` scroll.

---

## Edge-to-Edge & Inset Handling

The dialog detects whether the host activity is running in edge-to-edge mode and applies `WindowInsets` automatically:

- **Top inset** → padding on the top bar, keeping the app name and Cancel button above the status bar.
- **Bottom inset** → additive padding on the scroll container, keeping the subscribe button above the gesture navigation bar.
- **Left/right insets** → applied to the root view for landscape display cutout support.

This covers:
- **API 35+** (Android 15+) — always edge-to-edge.
- **API 29–34** — detected heuristically via decor view insets when the host app has opted in.
- **API 21–28** — legacy `SYSTEM_UI_FLAG_LAYOUT_*` flags are applied on the dialog window.

No action is needed from the caller. If your app does **not** use edge-to-edge, inset handling is skipped automatically.

---

## Dialog Animations

The dialog slides up on enter and down on dismiss via `PaywallDialogAnimation`:

```xml
<style name="PaywallDialogAnimation">
    <item name="android:windowEnterAnimation">@anim/paywall_slide_up</item>
    <item name="android:windowExitAnimation">@anim/paywall_slide_down</item>
</style>
```

To override with a fade animation, add this to your app's `styles.xml`:

```xml
<style name="PaywallDialogAnimation">
    <item name="android:windowEnterAnimation">@android:anim/fade_in</item>
    <item name="android:windowExitAnimation">@android:anim/fade_out</item>
</style>
```

The style name must remain `PaywallDialogAnimation` for the override to take effect.

---

## Edge Cases & Pitfalls

**`build()` throws when no plans are added**
`PaywallConfig.Builder.build()` enforces that at least one `SubscriptionPlanUiModel` is added. Calling `build()` on an empty plan list throws `IllegalStateException`.

**`defaultSelectedIndex` out of range**
An index below `0` or at or above `plans.size()` is silently clamped to `0` during `build()`. No exception is thrown.

**`config` is null after process death**
`PaywallDialog` does not save `config` to a `Bundle` (it is not `Parcelable`). If the process is killed and the back stack is restored, the dialog will dismiss itself via `dismissAllowingStateLoss()` inside `onViewCreated`. Reconstruct and re-show the dialog if this matters for your flow.

**Hero image height is fixed at 220dp**
`item_paywall_header.xml` uses a fixed `220dp` height for the hero `ImageView`. If your hero image has a significantly different aspect ratio, override the layout or use `adjustViewBounds="true"` with a `wrap_content` height and a `maxHeight` constraint to avoid letterboxing.

**`onDismissed` fires on configuration change**
`DialogFragment` dismisses and recreates on rotation by default if not retained. If you track dialog state in your ViewModel, handle `onDismissed` idempotently.

**Duplicate plan IDs**
The adapter does not enforce uniqueness of `planId` across plans. Duplicate IDs in the same `PaywallConfig` will cause ambiguity in `onSubscribeClicked`. Ensure each plan has a unique `planId`.

**Plan card click in horizontal scroll**
When `Orientation.HORIZONTAL` is used, a horizontal swipe gesture on a plan card may conflict with the card's click listener on some touch environments. If users report missed taps, consider increasing the card width or switching to `VERTICAL`.

**Price strings are never formatted by the library**
`getPrice()` is returned exactly as provided. Passing a raw `double` (e.g. `"9.99"`) instead of a properly formatted locale string (e.g. `"$9.99"` or `"€9,99"`) will display incorrectly for users in regions with different decimal separators. Always format prices using the locale-aware string returned by the Play Billing Library's `ProductDetails` API.

---

## Accessibility

- The hero `ImageView` has `contentDescription` bound to `@string/paywall_hero_content_description`. Override this string in your app to provide a meaningful description of your specific hero image.
- Feature checkmark icons have `contentDescription="@null"` (decorative).
- Plan cards are fully tappable via `MaterialCardView.setOnClickListener`, which is accessible by default.
- The Cancel button uses `materialIconButtonOutlinedStyle`, which provides an accessible touch target.