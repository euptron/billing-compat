package com.euptron.billingcompat.core.handlers;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.QueryPurchasesParams;
import com.euptron.billingcompat.core.products.Purchasable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingHandler extends BaseProductHandler<Purchasable> {
  private final Map<String, Purchase> pendingPurchases = new HashMap<>();
  private final Map<String, Long> pendingSince = new HashMap<>();

  public PendingHandler(BillingClient billingClient, Context context) {
    super(billingClient, context);
  }

  @Override
  protected String getPrefsName() {
    return "pending_purchases";
  }

  @Override
  public void onPurchaseSuccess(Purchasable product, Purchase purchase) {
    // This should not be called for pending - it should be handled by the appropriate handler
    Log.w(TAG, "Pending purchase completed: " + product.getId());
    pendingPurchases.remove(purchase.getOrderId());
    pendingSince.remove(purchase.getOrderId());

    if (listener != null) {
      listener.onProductPurchased(product, purchase.getPurchaseToken());
    }
  }

  @Override
  public void onPurchaseFailure(Purchasable product, String error) {
    Log.e(TAG, "Pending purchase failed: " + error);
    if (listener != null) {
      listener.onPurchaseError(error, product);
    }
  }

  @Override
  public void onPurchasePending(Purchasable product, Purchase purchase) {
    Log.d(TAG, "Purchase is pending: " + product.getId());
    pendingPurchases.put(purchase.getOrderId(), purchase);
    pendingSince.put(purchase.getOrderId(), System.currentTimeMillis());

    if (listener != null) {
      listener.onPurchasePending(product);
    }
  }

  @Override
  public boolean isOwned(Purchasable product) {
    // Pending purchases are not yet owned
    return false;
  }

  @Override
  public void sync() {
    if (!billingClient.isReady()) {
      Log.w(TAG, "Billing client not ready for pending sync");
      return;
    }

    QueryPurchasesParams params =
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
    
    billingClient.queryPurchasesAsync(params, new PurchasesResponseListener() {
      @Override
      public void onQueryPurchasesResponse(@NonNull BillingResult result, @NonNull List<Purchase> purchases) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;

        for (Purchase purchase : purchases) {
          if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Was pending, now completed — remove from pending map and notify
            pendingPurchases.remove(purchase.getOrderId());
            pendingSince.remove(purchase.getOrderId());

            for (String productId : purchase.getProducts()) {
              Purchasable product = getProduct(productId);
              if (product != null && listener != null) {
                listener.onProductPurchased(product, purchase.getPurchaseToken());
              }
            }
          }
        }
      }
    });
  }

  public boolean hasPendingPurchases() {
    return !pendingPurchases.isEmpty();
  }

  public int getPendingCount() {
    return pendingPurchases.size();
  }
}
