package com.euptron.billingcompat.core.handlers;

import com.android.billingclient.api.Purchase;
import com.euptron.billingcompat.core.listeners.PurchaseEventListener;
import com.euptron.billingcompat.core.products.Purchasable;

public interface ProductHandler<T extends Purchasable> {
  void onPurchaseSuccess(T product, Purchase purchase);

  void onPurchaseFailure(T product, String error);

  void onPurchasePending(T product, Purchase purchase);
  void setOwned(String productId, boolean owned);

  boolean isOwned(T product);

  void sync();

  void registerProduct(T product);

  T getProduct(String id);

  void setListener(PurchaseEventListener listener);
}
