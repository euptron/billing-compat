package com.euptron.billingcompat.core;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.euptron.billingcompat.core.builders.PurchaseBuilder;
import com.euptron.billingcompat.core.handlers.ProductHandler;
import com.euptron.billingcompat.core.handlers.SubscriptionHandler;
import com.euptron.billingcompat.core.listeners.BillingConnectionListener;
import com.euptron.billingcompat.core.listeners.PurchaseEventListener;
import com.euptron.billingcompat.core.model.PurchaseType;
import com.euptron.billingcompat.core.model.SubscriptionPlan;
import com.euptron.billingcompat.core.products.Purchasable;
import com.euptron.billingcompat.core.providers.GooglePlayProvider;
import com.euptron.billingcompat.core.providers.PaymentProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BillingManager {
  private static final String TAG = "BillingManager";

  private final PaymentProvider provider;
  private PurchaseEventListener listener;
  private final Map<String, Purchasable> productMap = new HashMap<>();
  private boolean initialized = false;
  private Context context;

  public static final String PREFS_HANDLER_SUBSCRIPTIONS = "subscriptions";
  public static final String PREFS_HANDLER_CONSUMABLE = "consumable_balances";
  public static final String PREFS_HANDLER_NON_CONSUMABLE = "non_consumable_purchases";
  public static final String PREFS_HANDLER_PENDING = "pending_purchases";

  public BillingManager(PaymentProvider provider, Context context) {
    this.provider = provider;
    this.context = context.getApplicationContext();
  }

  public void connect() {
    provider.connect();
  }

  public void disconnect() {
    provider.disconnect();
  }

  public boolean isReady() {
    return provider.isReady();
  }

  public void setListener(PurchaseEventListener listener) {
    this.listener = listener;
    if (provider instanceof GooglePlayProvider) {
      // Forward listener to provider
    }
  }

  public void setConnectionListener(BillingConnectionListener listener) {
    provider.setConnectionListener(listener);
  }

  public void registerProduct(Purchasable product) {
    productMap.put(product.getId(), product);
  }

  public Purchasable getProduct(String id) {
    return productMap.get(id);
  }

  public <T extends Purchasable> ProductHandler<T> getHandler(PurchaseType type) {
    if (provider instanceof GooglePlayProvider) {
      return ((GooglePlayProvider) provider).getHandler(type);
    }
    return null;
  }

  public Context getContext() {
    return context;
  }

  public void purchase(Activity activity, Purchasable product) {
    purchase(activity, product, null);
  }

  public void purchase(Activity activity, Purchasable product, String oldSku) {
    if (!provider.isReady()) {
      Log.e(TAG, "Provider not ready");
      if (listener != null) {
        listener.onPurchaseError("Billing service not ready", product);
      }
      return;
    }
    provider.launchPurchase(activity, product, oldSku);
  }

  public PurchaseBuilder purchase(Activity activity) {
    return new PurchaseBuilder(activity, this);
  }

  public void syncPurchases() {
    provider.queryPurchases();
  }

  /**
   * True if the user currently owns/has access to the given registered product, regardless of
   * whether it's a subscription, non-consumable, or consumable with a positive balance.
   *
   * <p>For subscriptions this reflects cancellations correctly: a user who cancels auto-renew
   * (from within the app's Play Store deep link or directly in the Play Store app) keeps access
   * until the paid period ends, exactly like Google Play does, while an immediate revocation or
   * refund is reflected right away the next time {@link #syncPurchases()} runs (also called
   * automatically on {@link #connect()}).
   */
  public boolean isUnlocked(String productId) {
    Purchasable product = productMap.get(productId);
    if (product == null) return false;

    ProductHandler<Purchasable> handler = getHandler(product.getType());
    if (handler == null) return false;

    return handler.isOwned(product);
  }

  /**
   * True if the user has an active subscription on ANY configured plan (weekly, monthly,
   * quarterly, or yearly). Use this for a single "is the user premium" check when you don't care
   * which specific plan they're on.
   */
  public boolean isSubscribed() {
    SubscriptionHandler handler = getSubscriptionHandler();
    return handler != null && handler.isAnyActive();
  }

  /** True if the user has an active subscription on the given plan specifically. */
  public boolean isSubscribed(SubscriptionPlan plan) {
    if (plan == null) return false;
    SubscriptionHandler handler = getSubscriptionHandler();
    return handler != null && handler.isActive(plan);
  }

  /** Returns the user's currently active {@link SubscriptionPlan}, or null if none is active. */
  public SubscriptionPlan getActiveSubscriptionPlan() {
    SubscriptionHandler handler = getSubscriptionHandler();
    return handler != null ? handler.getActivePlan() : null;
  }

  /**
   * True if the user has either bought the given one-time/consumable product or is subscribed to
   * it (if it's a subscription product). Equivalent to {@link #isUnlocked(String)}, provided as
   * an explicit name for "has this product been purchased or subscribed to".
   */
  public boolean hasPurchased(String productId) {
    return isUnlocked(productId);
  }

  private SubscriptionHandler getSubscriptionHandler() {
    ProductHandler<?> handler = this.<Purchasable>getHandler(PurchaseType.SUBSCRIPTION);
    if (handler instanceof SubscriptionHandler) {
      return (SubscriptionHandler) handler;
    }
    return null;
  }

  public boolean isFeatureUnlocked(String productId) {
    Purchasable product = productMap.get(productId);
    if (product == null) {
      return false;
    }
    ProductHandler<Purchasable> handler = getHandler(product.getType());
    if (handler == null) {
      return false;
    }
    return handler.isOwned(product);
  }

  public Set<String> getOwnedProducts() {
    Set<String> owned = new HashSet<>();
    for (Purchasable product : productMap.values()) {
      ProductHandler<Purchasable> handler = getHandler(product.getType());
      if (handler != null && handler.isOwned(product)) {
        owned.add(product.getId());
      }
    }
    return owned;
  }

  public void persistPurchaseState(String productId, boolean isUnlocked) {
    Purchasable product = productMap.get(productId);
    if (product == null) return;

    ProductHandler<Purchasable> handler = getHandler(product.getType());
    if (handler == null) return;

    handler.setOwned(productId, isUnlocked);
  }

  public void destroy() {
    disconnect();
  }
}
