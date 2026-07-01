package com.euptron.billingcompat.core.products;

import com.euptron.billingcompat.core.model.SubscriptionPlan;

public class ProductFactory {

  private ProductFactory() {
    // NO-OP Factory
  }

  public static NonConsumableProduct createNonConsumable(String id, String name, double price) {
    return new NonConsumableProduct.Builder().id(id).name(name).price(price).build();
  }

  public static ConsumableProduct createConsumable(
      String id, String name, double price, int quantity) {
    return new ConsumableProduct.Builder()
        .id(id)
        .name(name)
        .price(price)
        .quantity(quantity)
        .build();
  }

  public static SubscriptionProduct createSubscription(
      String id, String name, double price, SubscriptionPlan plan) {
    return new SubscriptionProduct.Builder().id(id).name(name).price(price).plan(plan).build();
  }

  public static SubscriptionProduct createSubscriptionWithTrial(
      String id, String name, double price, SubscriptionPlan plan, int trialDays) {
    return new SubscriptionProduct.Builder()
        .id(id)
        .name(name)
        .price(price)
        .plan(plan)
        .trialDays(trialDays)
        .build();
  }
}
