package com.euptron.billingcompat.core.handlers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.QueryPurchasesParams;
import com.euptron.billingcompat.core.model.SubscriptionPlan;
import com.euptron.billingcompat.core.products.SubscriptionProduct;
import com.euptron.billingcompat.core.utils.Compatibility;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class SubscriptionHandler extends BaseProductHandler<SubscriptionProduct> {
  private static final String KEY_SERVER_EXPIRY = "_server_expiry";
  private final Map<SubscriptionPlan, Purchase> activeSubscriptions = new HashMap<>();
  private final Map<SubscriptionPlan, Long> expiryTimes = new HashMap<>();

  public SubscriptionHandler(BillingClient billingClient, Context context) {
    super(billingClient, context);
    loadSubscriptions();
  }

  @Override
  protected String getPrefsName() {
    return "subscriptions";
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
    saveLong(product.getPlan().name() + "_expiry", expiry);
    saveBoolean(product.getPlan().name() + "_active", true);

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
              for (Purchase purchase : purchases) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                  for (String productId : purchase.getProducts()) {
                    SubscriptionPlan plan = SubscriptionPlan.fromProductId(productId);
                    if (plan != null) {
                      activeSubscriptions.put(plan, purchase);
                      long expiry = parseExpiryTime(purchase, plan);
                      expiryTimes.put(plan, expiry);
                      saveLong(plan.name() + "_expiry", expiry);
                      saveBoolean(plan.name() + "_active", true);
                    }
                  }
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
    long serverExpiry = getLong(plan.name() + KEY_SERVER_EXPIRY, 0);
    // Prefer server-confirmed expiry over locally calculated one
    long expiry =
        serverExpiry > 0 ? serverExpiry : Compatibility.getOrDefault(expiryTimes, plan, 0L);
    return System.currentTimeMillis() < expiry;
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
    long serverExpiry = getLong(plan.name() + KEY_SERVER_EXPIRY, 0);

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
    saveLong(plan.name() + KEY_SERVER_EXPIRY, expiryMillis);
    // Also update the main expiry
    saveLong(plan.name() + "_expiry", expiryMillis);
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
      long expiry = getLong(plan.name() + "_expiry", 0);
      if (expiry > 0) {
        expiryTimes.put(plan, expiry);
        if (System.currentTimeMillis() < expiry) {
          activeSubscriptions.put(plan, null); // Placeholder
        }
      }
    }
  }
}
