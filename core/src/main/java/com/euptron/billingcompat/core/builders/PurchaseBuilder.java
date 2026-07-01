package com.euptron.billingcompat.core.builders;

import android.app.Activity;
import com.euptron.billingcompat.core.BillingManager;
import com.euptron.billingcompat.core.model.PurchaseType;
import com.euptron.billingcompat.core.model.SubscriptionPlan;
import com.euptron.billingcompat.core.products.ConsumableProduct;
import com.euptron.billingcompat.core.products.NonConsumableProduct;
import com.euptron.billingcompat.core.products.Purchasable;
import com.euptron.billingcompat.core.products.SubscriptionProduct;

public class PurchaseBuilder {
  private final Activity activity;
  private final BillingManager manager;
  private Purchasable product;
  private String oldSku;
  private String prorationMode;

  public PurchaseBuilder(Activity activity, BillingManager manager) {
    this.activity = activity;
    this.manager = manager;
  }

  public PurchaseBuilder product(Purchasable product) {
    this.product = product;
    return this;
  }

  public PurchaseBuilder productId(String productId) {
    this.product = manager.getProduct(productId);
    if (this.product == null) {
      throw new IllegalArgumentException("Product not found: " + productId);
    }
    return this;
  }

  public PurchaseBuilder nonConsumable(String id) {
    Purchasable p = manager.getProduct(id);
    if (p != null && p.getType() == PurchaseType.NON_CONSUMABLE) {
      this.product = p;
    } else {
      // Create temporary product
      this.product = new NonConsumableProduct.Builder().id(id).name(id).price(0).build();
    }
    return this;
  }

  public PurchaseBuilder consumable(String id, int quantity) {
    this.product =
        new ConsumableProduct.Builder().id(id).name(id).price(0).quantity(quantity).build();
    return this;
  }

  public PurchaseBuilder subscribe(SubscriptionPlan plan) {
    Purchasable p = manager.getProduct(plan.getProductId());
    if (p != null && p.getType() == PurchaseType.SUBSCRIPTION) {
      this.product = p;
    } else {
      this.product =
          new SubscriptionProduct.Builder()
              .id(plan.getProductId())
              .name(plan.name())
              .price(0)
              .plan(plan)
              .build();
    }
    return this;
  }

  public PurchaseBuilder upgradeFrom(String oldSku) {
    this.oldSku = oldSku;
    return this;
  }

  public PurchaseBuilder withProration(String mode) {
    this.prorationMode = mode;
    return this;
  }

  public void execute() {
    if (product == null) {
      throw new IllegalStateException("Product must be set");
    }
    manager.purchase(activity, product, oldSku);
  }
}
