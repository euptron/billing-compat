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
import com.euptron.billingcompat.core.products.NonConsumableProduct;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NonConsumableHandler extends BaseProductHandler<NonConsumableProduct> {
  private final Set<String> ownedProducts = new HashSet<>();

  public NonConsumableHandler(BillingClient billingClient, Context context) {
    super(billingClient, context);
    loadOwnedProducts();
  }

  @Override
  protected String getPrefsName() {
    return BillingManager.PREFS_HANDLER_NON_CONSUMABLE;
  }

  @Override
  public void onPurchaseSuccess(NonConsumableProduct product, Purchase purchase) {
    Log.d(TAG, "Non-consumable purchased: " + product.getId());

    // Acknowledge the purchase
    acknowledgePurchase(purchase);

    // Store ownership
    ownedProducts.add(product.getId());
    saveBoolean(product.getId(), true);

    // Notify listener
    if (listener != null) {
      listener.onProductPurchased(product, purchase.getPurchaseToken());
    }
  }

  @Override
  public void onPurchaseFailure(NonConsumableProduct product, String error) {
    Log.e(TAG, "Purchase failed: " + error);
    if (listener != null) {
      listener.onPurchaseError(error, product);
    }
  }

  @Override
  public void onPurchasePending(NonConsumableProduct product, Purchase purchase) {
    Log.d(TAG, "Purchase pending: " + product.getId());
    if (listener != null) {
      listener.onPurchasePending(product);
    }
  }

  @Override
  public boolean isOwned(NonConsumableProduct product) {
    return ownedProducts.contains(product.getId());
  }

  @Override
  public void sync() {
    if (!billingClient.isReady()) {
      Log.w(TAG, "Billing client not ready for sync");
      return;
    }

    QueryPurchasesParams params =
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
    
    billingClient.queryPurchasesAsync(params, new PurchasesResponseListener() {
      @Override
      public void onQueryPurchasesResponse(@NonNull BillingResult result, @NonNull List<Purchase> purchases) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
          Log.w(TAG, "Sync failed: " + result.getDebugMessage());
          return;
        }

        ownedProducts.clear();
        for (Purchase purchase : purchases) {
          if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            for (String productId : purchase.getProducts()) {
              ownedProducts.add(productId);
              saveBoolean(productId, true);
            }
          }
        }

        Log.d(TAG, "Synced " + ownedProducts.size() + " products");

        if (listener != null) {
          listener.onPurchasesSynced();
        }
      }
    });
  }

  public boolean isUnlocked(String productId) {
    return getBoolean(productId, false);
  }

  public Set<String> getOwnedProductIds() {
    return new HashSet<>(ownedProducts);
  }

  private void loadOwnedProducts() {
    Map<String, ?> all = prefs.getAll();
    for (Map.Entry<String, ?> entry : all.entrySet()) {
      if (Boolean.TRUE.equals(entry.getValue())) {
        ownedProducts.add(entry.getKey());
      }
    }
  }
}
