package com.euptron.billingcompat.core.handlers;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.QueryPurchasesParams;
import com.euptron.billingcompat.core.products.ConsumableProduct;
import com.euptron.billingcompat.core.utils.Compatibility;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsumableHandler extends BaseProductHandler<ConsumableProduct> {
  private final Map<String, Integer> balances = new HashMap<>();
  private final Map<String, Integer> pendingConsumptions = new HashMap<>();

  public ConsumableHandler(BillingClient billingClient, Context context) {
    super(billingClient, context);
    loadBalances();
  }

  @Override
  protected String getPrefsName() {
    return "consumable_balances";
  }

  @Override
  public void onPurchaseSuccess(ConsumableProduct product, Purchase purchase) {
    Log.d(TAG, "Consumable purchased: " + product.getId() + " x" + product.getQuantity());
    // Consume the purchase to make it available again
    consumePurchase(product, purchase);
  }

  @Override
  public void onPurchaseFailure(ConsumableProduct product, String error) {
    Log.e(TAG, "Purchase failed: " + error);
    if (listener != null) {
      listener.onPurchaseError(error, product);
    }
  }

  @Override
  public void onPurchasePending(ConsumableProduct product, Purchase purchase) {
    Log.d(TAG, "Purchase pending: " + product.getId());
    pendingConsumptions.put(product.getId(), product.getQuantity());
    if (listener != null) {
      listener.onPurchasePending(product);
    }
  }

  @Override
  public boolean isOwned(ConsumableProduct product) {
    // Consumables aren't "owned" - they have a balance
    return getBalance(product) > 0;
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
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
          for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
              // Consumables should be consumed, but if they weren't, consume them
              for (String productId : purchase.getProducts()) {
                ConsumableProduct product = getProduct(productId);
                if (product != null) {
                  consumePurchase(product, purchase);
                }
              }
            }
          }
          Log.d(TAG, "Synced consumables");
        }
      }
    });
  }

  private void consumePurchase(ConsumableProduct product, Purchase purchase) {
    ConsumeParams params =
        ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();

    billingClient.consumeAsync(params, new ConsumeResponseListener() {
      @Override
      public void onConsumeResponse(@NonNull BillingResult result, @NonNull String purchaseToken) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
          // Add to balance
          int currentBalance = Compatibility.getOrDefault(balances, product.getId(), 0);
          int newBalance = currentBalance + product.getQuantity();
          balances.put(product.getId(), newBalance);
          saveBalance(product.getId(), newBalance);

          Log.d(TAG, "Consumed successfully. New balance: " + newBalance);

          if (listener != null) {
            listener.onProductPurchased(product, purchaseToken);
          }
        } else {
          Log.e(TAG, "Consumption failed: " + result.getDebugMessage());
          if (listener != null) {
            listener.onPurchaseError("Consumption failed: " + result.getDebugMessage(), product);
          }
        }
      }
    });
  }


  public int getBalance(ConsumableProduct product) {
    return Compatibility.getOrDefault(balances, product.getId(), 0);
  }

  public boolean spend(ConsumableProduct product, int amount) {
    String id = product.getId();
    int current = Compatibility.getOrDefault(balances, id, 0);

    if (current >= amount) {
      int newBalance = current - amount;
      balances.put(id, newBalance);
      saveBalance(id, newBalance);
      return true;
    }
    return false;
  }

  private void saveBalance(String productId, int balance) {
    saveInt(productId, balance);
  }

  private void loadBalances() {
    Map<String, ?> all = prefs.getAll();
    for (Map.Entry<String, ?> entry : all.entrySet()) {
      if (entry.getValue() instanceof Integer) {
        balances.put(entry.getKey(), (Integer) entry.getValue());
      }
    }
  }
}
