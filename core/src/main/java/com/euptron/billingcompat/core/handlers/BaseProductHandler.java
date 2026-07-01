package com.euptron.billingcompat.core.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.euptron.billingcompat.core.listeners.PurchaseEventListener;
import com.euptron.billingcompat.core.products.Purchasable;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseProductHandler<T extends Purchasable> implements ProductHandler<T> {
  static final String TAG = BaseProductHandler.class.getSimpleName();
  protected final BillingClient billingClient;
  protected final Context context;
  protected PurchaseEventListener listener;
  protected final Map<String, T> productCache = new HashMap<>();
  protected final SharedPreferences prefs;

  protected BaseProductHandler(BillingClient billingClient, Context context) {
    this.billingClient = billingClient;
    this.context = context;
    this.prefs = context.getSharedPreferences(getPrefsName(), Context.MODE_PRIVATE);
  }

  protected abstract String getPrefsName();

  @Override
  public void registerProduct(T product) {
    productCache.put(product.getId(), product);
    Log.d(TAG, "Registered product: " + product.getId());
  }

  @Override
  public T getProduct(String id) {
    return productCache.get(id);
  }

  @Override
  public void setListener(PurchaseEventListener listener) {
    this.listener = listener;
  }

  protected void acknowledgePurchase(Purchase purchase) {
    if (purchase.isAcknowledged()) return;

    AcknowledgePurchaseParams params =
        AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken())
            .build();

    billingClient.acknowledgePurchase(
        params,
        result -> {
          if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Purchase acknowledged successfully");
          } else {
            Log.e(TAG, "Failed to acknowledge: " + result.getDebugMessage());
          }
        });
  }

  @Override
  public void setOwned(String productId, boolean owned) {
    saveBoolean(productId, owned);
  }

  protected void saveBoolean(String key, boolean value) {
    prefs.edit().putBoolean(key, value).apply();
  }

  protected boolean getBoolean(String key, boolean defValue) {
    return prefs.getBoolean(key, defValue);
  }

  protected void saveInt(String key, int value) {
    prefs.edit().putInt(key, value).apply();
  }

  protected int getInt(String key, int defValue) {
    return prefs.getInt(key, defValue);
  }
}
