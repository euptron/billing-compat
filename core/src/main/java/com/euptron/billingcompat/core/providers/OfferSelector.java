package com.euptron.billingcompat.core.providers;

import com.android.billingclient.api.ProductDetails;
import java.util.List;

public interface OfferSelector {
  /**
   * Called when a subscription purchase is about to be launched. Return the offerToken the user
   * should be charged with.
   *
   * @param offers All eligible offers returned by Google Play for this product.
   * @param productId The product ID being purchased.
   * @return The offerToken to use, or null to use the first available offer.
   */
  String selectOffer(List<ProductDetails.SubscriptionOfferDetails> offers, String productId);
}
