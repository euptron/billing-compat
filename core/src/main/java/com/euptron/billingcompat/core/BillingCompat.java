package com.euptron.billingcompat.core;

import com.euptron.billingcompat.core.model.SubscriptionPlan;

/**
 * Static entry point for checking entitlement state from anywhere in the app without holding a
 * reference to {@link BillingManager}.
 *
 * <p>{@link com.euptron.billingcompat.core.builders.BillingConfigBuilder#build()} attaches the
 * built {@link BillingManager} automatically, so in the common single-manager setup no extra
 * wiring is needed. If you construct a {@link BillingManager} yourself, call {@link
 * #attach(BillingManager)} once (e.g. in {@code Application.onCreate()}).
 *
 * <p>Every method here is a no-op-safe passthrough: if no manager has been attached yet, they
 * return {@code false}/{@code null} rather than throwing.
 */
public final class BillingCompat {
  private static volatile BillingManager manager;

  private BillingCompat() {}

  /** Registers the {@link BillingManager} instance that static calls will delegate to. */
  public static void attach(BillingManager billingManager) {
    manager = billingManager;
  }

  /** Clears the attached manager. Mainly useful for tests. */
  public static void detach() {
    manager = null;
  }

  /** The manager currently backing the static calls, or null if none has been attached. */
  public static BillingManager getManager() {
    return manager;
  }

  /**
   * True if the user has bought or is subscribed to the given registered product. Covers
   * subscriptions, non-consumables, and consumables with a positive balance, and correctly
   * reflects cancellations/refunds once {@link BillingManager#syncPurchases()} has run.
   */
  public static boolean hasPurchased(String productId) {
    return manager != null && manager.hasPurchased(productId);
  }

  /**
   * True if the user has an active subscription on ANY configured plan (weekly, monthly,
   * quarterly, or yearly).
   */
  public static boolean isSubscribed() {
    return manager != null && manager.isSubscribed();
  }

  /** True if the user has an active subscription on the given plan specifically. */
  public static boolean isSubscribed(SubscriptionPlan plan) {
    return manager != null && manager.isSubscribed(plan);
  }

  /** Returns the user's currently active {@link SubscriptionPlan}, or null if none is active. */
  public static SubscriptionPlan getActiveSubscriptionPlan() {
    return manager != null ? manager.getActiveSubscriptionPlan() : null;
  }
}
