package com.euptron.billingcompat.core.products;

import com.android.billingclient.api.Purchase;
import com.euptron.billingcompat.core.model.PurchaseType;

public interface Purchasable {
  String getId();

  String getName();

  double getPrice();

  String getCurrency();

  PurchaseType getType();

  String getDescription();

  String getStockKeepingUnit();
}
