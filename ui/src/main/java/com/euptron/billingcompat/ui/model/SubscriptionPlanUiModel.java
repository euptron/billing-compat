package com.euptron.billingcompat.ui.model;

import androidx.annotation.Nullable;

/**
 * UI model representing a single subscription plan shown in the paywall.
 *
 * <p>The caller maps their domain/billing model (e.g. {@code SubscriptionPlan} from the core
 * module) to this class before passing it to {@link
 * com.euptron.billingcompat.ui.dialog.PaywallDialog}.
 *
 * <p><b>Integration example:</b>
 *
 * <pre>{@code
 * SubscriptionPlanUiModel weeklyPlan = new SubscriptionPlanUiModel.Builder()
 *     .planId("subs_weekly")
 *     .label("Weekly")
 *     .price("$2.99")
 *     .period("/ week")
 *     .recommended(false)
 *     .build();
 *
 * SubscriptionPlanUiModel yearlyPlan = new SubscriptionPlanUiModel.Builder()
 *     .planId("subs_yearly")
 *     .label("Yearly")
 *     .price("$49.99")
 *     .period("/ year")
 *     .discountBadge("Save 40%")
 *     .recommended(true)
 *     .build();
 * }</pre>
 *
 * To connect {@code onSubscribeClicked} to the core billing module:
 *
 * <pre>{@code
 * // In your Activity / Fragment (wired by the caller — NOT by this library):
 * paywallCallback = new PaywallCallback() {
 *     \@Override public void onSubscribeClicked(SubscriptionPlanUiModel plan) {
 *         // Map planId back to your SubscriptionPlan enum:
 *         SubscriptionPlan corePlan = SubscriptionPlan.fromProductId(plan.getPlanId());
 *         new PurchaseBuilder(activity, billingManager)
 *             .subscribe(corePlan)
 *             .execute();
 *     }
 *     \@Override public void onRestoreClicked() { billingManager.syncPurchases(); }
 *     \@Override public void onDismissed() { }
 * };
 * }</pre>
 */
public final class SubscriptionPlanUiModel {

  private final String planId;
  private final String label;
  private final String price;
  private final String period;
  @Nullable private final String discountBadge;
  private final boolean recommended;

  private SubscriptionPlanUiModel(Builder builder) {
    this.planId = builder.planId;
    this.label = builder.label;
    this.price = builder.price;
    this.period = builder.period;
    this.discountBadge = builder.discountBadge;
    this.recommended = builder.recommended;
  }

  /** Matches the product ID used in the core {@code SubscriptionPlan} enum. */
  public String getPlanId() {
    return planId;
  }

  /** Display label e.g. "Weekly", "Monthly", "Yearly". */
  public String getLabel() {
    return label;
  }

  /** Formatted price string e.g. "$9.99". */
  public String getPrice() {
    return price;
  }

  /** Billing period string e.g. "/ month". */
  public String getPeriod() {
    return period;
  }

  /** Optional discount badge text e.g. "Save 40%". {@code null} means no badge is shown. */
  @Nullable
  public String getDiscountBadge() {
    return discountBadge;
  }

  /**
   * When {@code true}, the plan card shows a "Most Popular" badge. You can also use this flag in
   * your own logic (e.g. pre-selecting this plan).
   */
  public boolean isRecommended() {
    return recommended;
  }

  // -----------------------------------------------------------------------
  // Builder
  // -----------------------------------------------------------------------

  public static final class Builder {
    private String planId;
    private String label;
    private String price;
    private String period;
    @Nullable private String discountBadge;
    private boolean recommended = false;

    public Builder planId(String planId) {
      this.planId = planId;
      return this;
    }

    public Builder label(String label) {
      this.label = label;
      return this;
    }

    public Builder price(String price) {
      this.price = price;
      return this;
    }

    public Builder period(String period) {
      this.period = period;
      return this;
    }

    public Builder discountBadge(@Nullable String badge) {
      this.discountBadge = badge;
      return this;
    }

    public Builder recommended(boolean recommended) {
      this.recommended = recommended;
      return this;
    }

    public SubscriptionPlanUiModel build() {
      if (planId == null || label == null || price == null || period == null) {
        throw new IllegalStateException("planId, label, price and period are required");
      }
      return new SubscriptionPlanUiModel(this);
    }
  }
}
