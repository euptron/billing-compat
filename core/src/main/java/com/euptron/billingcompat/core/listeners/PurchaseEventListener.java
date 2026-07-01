package com.euptron.billingcompat.core.listeners;

import com.euptron.billingcompat.core.products.Purchasable;

public interface PurchaseEventListener {
  void onProductPurchased(Purchasable product, String purchaseToken);

  void onProductRestored(Purchasable product);

  void onPurchasePending(Purchasable product);

  void onPurchaseError(String error, Purchasable product);

  void onPurchasesSynced();

  // Default empty implementations
  class SimpleListener implements PurchaseEventListener {
    @Override
    public void onProductPurchased(Purchasable product, String purchaseToken) {
      // NO-OP
    }

    @Override
    public void onProductRestored(Purchasable product) {
      // NO-OP
    }

    @Override
    public void onPurchasePending(Purchasable product) {
      // NO-OP
    }

    @Override
    public void onPurchaseError(String error, Purchasable product) {
      // NO-OP
    }

    @Override
    public void onPurchasesSynced() {
      // NO-OP
    }
  }
}
