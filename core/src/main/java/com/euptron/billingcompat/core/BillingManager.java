package com.euptron.billingcompat.core;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.euptron.billingcompat.core.builders.PurchaseBuilder;
import com.euptron.billingcompat.core.handlers.ProductHandler;
import com.euptron.billingcompat.core.listeners.BillingConnectionListener;
import com.euptron.billingcompat.core.listeners.PurchaseEventListener;
import com.euptron.billingcompat.core.model.PurchaseType;
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

  private static final String PREFS_NAME = "billing_preferences";

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

  public boolean isUnlocked(String productId) {
    Purchasable product = productMap.get(productId);
    if (product == null) return false;

    ProductHandler<Purchasable> handler = getHandler(product.getType());
    if (handler == null) return false;

    return handler.isOwned(product);
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
