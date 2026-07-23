package com.euptron.billingcompat.core.handlers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.QueryPurchasesParams;
import com.euptron.billingcompat.core.BillingManager;
import com.euptron.billingcompat.core.model.SubscriptionPlan;
import com.euptron.billingcompat.core.products.SubscriptionProduct;
import com.euptron.billingcompat.core.utils.Compatibility;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public class SubscriptionHandler extends BaseProductHandler<SubscriptionProduct> {
  /**
   * Public prefs-key suffixes for {@link SubscriptionPlan#name()}, exposed so that reading
   * subscription state directly off {@code SharedPreferences} (no live {@code BillingManager}
   * required, e.g. before one has been built this process) stays a supported operation rather
   * than requiring callers to hardcode private string literals. The file itself is {@link
   * BillingManager#PREFS_HANDLER_SUBSCRIPTIONS}. See {@link
   * com.euptron.billingcompat.core.BillingCompat#isSubscribed(Context)}.
   */
  public static final String SUFFIX_ACTIVE = "_active";

  public static final String SUFFIX_EXPIRY = "_expiry";
  public static final String SUFFIX_SERVER_EXPIRY = "_server_expiry";

  private final Map<SubscriptionPlan, Purchase> activeSubscriptions = new HashMap<>();
  private final Map<SubscriptionPlan, Long> expiryTimes = new HashMap<>();

  public SubscriptionHandler(BillingClient billingClient, Context context) {
    super(billingClient, context);
    loadSubscriptions();
  }

  @Override
  protected String getPrefsName() {
    return BillingManager.PREFS_HANDLER_SUBSCRIPTIONS;
  }

  @Override
  public void onPurchaseSuccess(SubscriptionProduct product, Purchase purchase) {
    Log.d(TAG, "Subscription purchased: " + product.getPlan());

    // Acknowledge the purchase
    acknowledgePurchase(purchase);

    // Store subscription
    activeSubscriptions.put(product.getPlan(), purchase);
    long expiry = parseExpiryTime(purchase, product.getPlan());
    expiryTimes.put(product.getPlan(), expiry);

    // Save to prefs
    saveLong(product.getPlan().name() + SUFFIX_EXPIRY, expiry);
    saveBoolean(product.getPlan().name() + SUFFIX_ACTIVE, true);

    if (listener != null) {
      listener.onProductPurchased(product, purchase.getPurchaseToken());
    }
  }

  @Override
  public void onPurchaseFailure(SubscriptionProduct product, String error) {
    Log.e(TAG, "Subscription failed: " + error);
    if (listener != null) {
      listener.onPurchaseError(error, product);
    }
  }

  @Override
  public void onPurchasePending(SubscriptionProduct product, Purchase purchase) {
    Log.d(TAG, "Subscription pending: " + product.getPlan());
    if (listener != null) {
      listener.onPurchasePending(product);
    }
  }

  @Override
  public boolean isOwned(SubscriptionProduct product) {
    return isActive(product);
  }

  @Override
  public void sync() {
    if (!billingClient.isReady()) {
      Log.w(TAG, "Billing client not ready for sync");
      return;
    }

    QueryPurchasesParams params =
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build();

    billingClient.queryPurchasesAsync(
        params,
        new PurchasesResponseListener() {
          @Override
          public void onQueryPurchasesResponse(
              @NonNull BillingResult result, @NonNull List<Purchase> purchases) {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              activeSubscriptions.clear();
              Set<SubscriptionPlan> stillActive = new HashSet<>();
              for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                  for (String productId : purchase.getProducts()) {
                    SubscriptionPlan plan = SubscriptionPlan.fromProductId(productId);
                    if (plan != null) {
                      activeSubscriptions.put(plan, purchase);
                      stillActive.add(plan);
                      long expiry = parseExpiryTime(purchase, plan);
                      expiryTimes.put(plan, expiry);
                      saveLong(plan.name() + SUFFIX_EXPIRY, expiry);
                      saveBoolean(plan.name() + SUFFIX_ACTIVE, true);
                    }
                  }
                }
              }

              // Google Play no longer returns purchases that were canceled-and-refunded,
              // revoked, or otherwise invalidated (a plain user cancellation with auto-renew
              // off is still returned as PURCHASED until the paid period ends, so this only
              // fires on true revocation). Clear any locally-cached state for those plans so
              // isActive()/isAnyActive() stop reporting them as owned.
              for (SubscriptionPlan plan : SubscriptionPlan.values()) {
                if (!stillActive.contains(plan) && getBoolean(plan.name() + SUFFIX_ACTIVE, false)) {
                  revokePlan(plan);
                }
              }

              Log.d(TAG, "Synced " + activeSubscriptions.size() + " subscriptions");

              if (listener != null) {
                listener.onPurchasesSynced();
              }
            }
          }
        });
  }

  public boolean isActive(SubscriptionProduct product) {
    return isActive(product.getPlan());
  }

  public boolean isActive(SubscriptionPlan plan) {
    long serverExpiry = getLong(plan.name() + SUFFIX_SERVER_EXPIRY, 0);
    // Prefer server-confirmed expiry over locally calculated one
    long expiry =
        serverExpiry > 0 ? serverExpiry : Compatibility.getOrDefault(expiryTimes, plan, 0L);
    return System.currentTimeMillis() < expiry;
  }

  /**
   * True if the user currently has an active subscription on ANY plan (weekly, monthly,
   * quarterly, or yearly). Use this for a single "is the user premium" check that doesn't care
   * which specific plan was purchased.
   */
  public boolean isAnyActive() {
    for (SubscriptionPlan plan : SubscriptionPlan.values()) {
      if (isActive(plan)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the currently active {@link SubscriptionPlan}, or null if none is active. */
  public SubscriptionPlan getActivePlan() {
    for (SubscriptionPlan plan : SubscriptionPlan.values()) {
      if (isActive(plan)) {
        return plan;
      }
    }
    return null;
  }

  /** Clears all locally-cached state for a plan that Play no longer reports as owned. */
  private void revokePlan(SubscriptionPlan plan) {
    activeSubscriptions.remove(plan);
    expiryTimes.remove(plan);
    saveBoolean(plan.name() + SUFFIX_ACTIVE, false);
    prefs.edit()
        .remove(plan.name() + SUFFIX_EXPIRY)
        .remove(plan.name() + SUFFIX_SERVER_EXPIRY)
        .apply();
    Log.d(TAG, "Revoked subscription plan (canceled/refunded/expired): " + plan);
  }

  public long getTimeRemaining(SubscriptionPlan plan) {
    if (!expiryTimes.containsKey(plan)) return 0;
    Long val = expiryTimes.get(plan);
    long expiry = val != null ? val : 0L;
    long remaining = expiry - System.currentTimeMillis();
    return Math.max(0, remaining);
  }

  public long getExpiryTime(SubscriptionPlan plan) {
    return Compatibility.getOrDefault(expiryTimes, plan, 0L);
  }

  private int getDaysForPlan(SubscriptionPlan plan) {
    switch (plan) {
      case WEEKLY:
        return 7;
      case MONTHLY:
        return 30;
      case QUARTERLY:
        return 90;
      case YEARLY:
        return 365;
      default:
        return 30;
    }
  }

  private long parseExpiryTime(Purchase purchase, SubscriptionPlan plan) {
    long serverExpiry = getLong(plan.name() + SUFFIX_SERVER_EXPIRY, 0);

    if (serverExpiry > 0) {
      Log.d(TAG, "Using REAL server expiry: " + new Date(serverExpiry));
      return serverExpiry;
    }

    // fallback to local calculation
    long localExpiry = purchase.getPurchaseTime() + getDaysForPlan(plan) * 24 * 60 * 60 * 1000L;
    Log.d(TAG, "Using LOCAL expiry: " + new Date(localExpiry));
    return localExpiry;
  }

  /** Get SubscriptionPlan from product ID */
  public SubscriptionPlan getPlanFromProductId(String productId) {
    for (SubscriptionPlan plan : SubscriptionPlan.values()) {
      if (plan.getProductId().equals(productId)) {
        return plan;
      }
    }
    return null;
  }

  public void saveServerExpiry(SubscriptionPlan plan, long expiryMillis) {
    saveLong(plan.name() + SUFFIX_SERVER_EXPIRY, expiryMillis);
    // Also update the main expiry
    saveLong(plan.name() + SUFFIX_EXPIRY, expiryMillis);
    expiryTimes.put(plan, expiryMillis);
  }

  private void saveLong(String key, long value) {
    prefs.edit().putLong(key, value).apply();
  }

  private long getLong(String key, long defValue) {
    return prefs.getLong(key, defValue);
  }

  private void loadSubscriptions() {
    for (SubscriptionPlan plan : SubscriptionPlan.values()) {
      long expiry = getLong(plan.name() + SUFFIX_EXPIRY, 0);
      if (expiry > 0) {
        expiryTimes.put(plan, expiry);
        if (System.currentTimeMillis() < expiry) {
          activeSubscriptions.put(plan, null); // Placeholder
        }
      }
    }
  }
}
