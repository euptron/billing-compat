package com.euptron.billingcompat.core.builders;

import android.content.Context;
import com.euptron.billingcompat.core.BillingManager;
import com.euptron.billingcompat.core.handlers.ConsumableHandler;
import com.euptron.billingcompat.core.handlers.NonConsumableHandler;
import com.euptron.billingcompat.core.handlers.PendingHandler;
import com.euptron.billingcompat.core.handlers.ProductHandler;
import com.euptron.billingcompat.core.handlers.SubscriptionHandler;
import com.euptron.billingcompat.core.listeners.PurchaseEventListener;
import com.euptron.billingcompat.core.model.PurchaseType;
import com.euptron.billingcompat.core.products.Purchasable;
import com.euptron.billingcompat.core.providers.GooglePlayProvider;
import com.euptron.billingcompat.core.providers.OfferSelector;

import java.util.ArrayList;
import java.util.List;

public class BillingConfigBuilder {
  private final Context context;
  private final List<Purchasable> products = new ArrayList<>();
  private PurchaseEventListener listener;
  private boolean autoConnect = true;

  // Handler configurations
  private boolean enableNonConsumable = true;
  private boolean enableConsumable = true;
  private boolean enableSubscription = true;
  private boolean enablePending = true;
  private OfferSelector offerSelector;

  public BillingConfigBuilder(Context context) {
    this.context = context.getApplicationContext();
  }

  public BillingConfigBuilder addProduct(Purchasable product) {
    products.add(product);
    return this;
  }

  public BillingConfigBuilder addProducts(List<Purchasable> products) {
    this.products.addAll(products);
    return this;
  }

  public BillingConfigBuilder setListener(PurchaseEventListener listener) {
    this.listener = listener;
    return this;
  }

  public BillingConfigBuilder autoConnect(boolean autoConnect) {
    this.autoConnect = autoConnect;
    return this;
  }

  public BillingConfigBuilder disableNonConsumable() {
    this.enableNonConsumable = false;
    return this;
  }

  public BillingConfigBuilder disableConsumable() {
    this.enableConsumable = false;
    return this;
  }

  public BillingConfigBuilder disableSubscription() {
    this.enableSubscription = false;
    return this;
  }

  public BillingConfigBuilder disablePending() {
    this.enablePending = false;
    return this;
  }

  public BillingConfigBuilder setOfferSelector(OfferSelector offerSelector) {
    this.offerSelector = offerSelector;
    return this;
  }

  public BillingManager build() {
    // Create provider
    GooglePlayProvider provider = new GooglePlayProvider(context);

    if (offerSelector != null) {
      provider.setOfferSelector(offerSelector);
    }

    // Register handlers
    if (enableNonConsumable) {
      NonConsumableHandler ncHandler =
          new NonConsumableHandler(provider.getBillingClient(), context);
      ncHandler.setListener(listener);
      provider.registerHandler(PurchaseType.NON_CONSUMABLE, ncHandler);
    }

    if (enableConsumable) {
      ConsumableHandler cHandler = new ConsumableHandler(provider.getBillingClient(), context);
      cHandler.setListener(listener);
      provider.registerHandler(PurchaseType.CONSUMABLE, cHandler);
    }

    if (enableSubscription) {
      SubscriptionHandler sHandler = new SubscriptionHandler(provider.getBillingClient(), context);
      sHandler.setListener(listener);
      provider.registerHandler(PurchaseType.SUBSCRIPTION, sHandler);
    }

    if (enablePending) {
      PendingHandler pHandler = new PendingHandler(provider.getBillingClient(), context);
      pHandler.setListener(listener);
      provider.registerHandler(PurchaseType.PENDING, pHandler);
    }

    // Register products with appropriate handlers
    for (Purchasable product : products) {
      ProductHandler<Purchasable> handler = provider.getHandler(product.getType());
      if (handler != null) {
        handler.registerProduct(product);
      }
    }

    // Query product details
    provider.setPendingProducts(products);

    // Create manager
    BillingManager manager = new BillingManager(provider, context);
    manager.setListener(listener);

    // Auto-connect if requested
    if (autoConnect) {
      manager.connect();
    }

    return manager;
  }
}
