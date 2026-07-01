package com.euptron.billingcompat.core.providers;

import com.android.billingclient.api.ProductDetails;
import java.util.List;

public class DefaultOfferSelector implements OfferSelector {

    private final String preferredTag;

    /**
     * Selects an offer by matching a tag you define.
     * Falls back to index 0 if no match is found.
     *
     * @param preferredTag The offer tag to look for e.g. "trial", "promo", "intro".
     *                     Pass null to always use the first available offer.
     */
    public DefaultOfferSelector(String preferredTag) {
        this.preferredTag = preferredTag;
    }

    /** Always picks the first available offer. No tag matching. */
    public DefaultOfferSelector() {
        this.preferredTag = null;
    }

    @Override
    public String selectOffer(
            List<ProductDetails.SubscriptionOfferDetails> offers,
            String productId) {

        if (offers == null || offers.isEmpty()) return null;

        if (preferredTag != null) {
            for (ProductDetails.SubscriptionOfferDetails offer : offers) {
                if (offer.getOfferTags().contains(preferredTag)) {
                    return offer.getOfferToken();
                }
            }
        }

        // Fallback — base plan is always index 0
        return offers.get(0).getOfferToken();
    }
}