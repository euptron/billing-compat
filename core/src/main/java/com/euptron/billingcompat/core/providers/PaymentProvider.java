package com.euptron.billingcompat.core.providers;

import android.app.Activity;
import com.euptron.billingcompat.core.listeners.BillingConnectionListener;
import com.euptron.billingcompat.core.products.Purchasable;

public interface PaymentProvider {
  void connect();

  void disconnect();

  boolean isReady();

  void launchPurchase(Activity activity, Purchasable product);

  void launchPurchase(Activity activity, Purchasable product, String oldSku);

  void queryPurchases();

  void acknowledge(String purchaseToken);

  void consume(String purchaseToken);

  void setConnectionListener(BillingConnectionListener listener);
}
