package com.euptron.billingcompat.core;

import android.content.Context;
import android.content.SharedPreferences;
import com.euptron.billingcompat.core.handlers.SubscriptionHandler;
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
 *
 * <p>The {@code Context}-taking overloads ({@link #isSubscribed(Context)}, {@link
 * #isSubscribed(Context, SubscriptionPlan)}, {@link #getActiveSubscriptionPlan(Context)}) read
 * persisted subscription state directly from {@code SharedPreferences} and don't need {@link
 * #attach} to have been called at all — they work even before a {@link BillingManager} has been
 * built anywhere in the current process, since the underlying data survives process death.
 * They're only as fresh as the last purchase or {@link BillingManager#syncPurchases()} that
 * actually ran somewhere in the app — these overloads don't themselves talk to Play.
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

  /**
   * {@code Context}-only version of {@link #isSubscribed()} — reads persisted state directly, no
   * {@link #attach} required. See the class doc for when to prefer this over the no-arg version.
   */
  public static boolean isSubscribed(Context context) {
    return getActiveSubscriptionPlan(context) != null;
  }

  /**
   * {@code Context}-only version of {@link #isSubscribed(SubscriptionPlan)} — reads persisted
   * state directly, no {@link #attach} required.
   */
  public static boolean isSubscribed(Context context, SubscriptionPlan plan) {
    if (context == null || plan == null) return false;
    return readExpiry(context, plan) > System.currentTimeMillis();
  }

  /**
   * {@code Context}-only version of {@link #getActiveSubscriptionPlan()} — reads persisted state
   * directly, no {@link #attach} required.
   */
  public static SubscriptionPlan getActiveSubscriptionPlan(Context context) {
    if (context == null) return null;
    long now = System.currentTimeMillis();
    for (SubscriptionPlan plan : SubscriptionPlan.values()) {
      if (readExpiry(context, plan) > now) {
        return plan;
      }
    }
    return null;
  }

  private static long readExpiry(Context context, SubscriptionPlan plan) {
    SharedPreferences prefs =
        context
            .getApplicationContext()
            .getSharedPreferences(BillingManager.PREFS_HANDLER_SUBSCRIPTIONS, Context.MODE_PRIVATE);
    long serverExpiry = prefs.getLong(plan.name() + SubscriptionHandler.SUFFIX_SERVER_EXPIRY, 0);
    return serverExpiry > 0 ? serverExpiry : prefs.getLong(plan.name() + SubscriptionHandler.SUFFIX_EXPIRY, 0);
  }
}
