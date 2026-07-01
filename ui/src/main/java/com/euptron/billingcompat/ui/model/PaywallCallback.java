package com.euptron.billingcompat.ui.model;

/**
 * Callback interface fired by the paywall dialog.
 *
 * <p>The dialog has zero billing logic. The caller wires these callbacks to
 * the core {@code BillingManager} themselves. Example:
 *
 * <pre>{@code
 * PaywallCallback callback = new PaywallCallback() {
 *
 *     \@Override
 *     public void onSubscribeClicked(SubscriptionPlanUiModel selectedPlan) {
 *         // Map the UI model's planId back to the core SubscriptionPlan enum,
 *         // then launch the purchase flow via BillingManager:
 *         SubscriptionPlan corePlan = SubscriptionPlan.fromProductId(selectedPlan.getPlanId());
 *         new PurchaseBuilder(activity, billingManager)
 *             .subscribe(corePlan)
 *             .execute();
 *     }
 *
 *     \@Override
 *     public void onRestoreClicked() {
 *         billingManager.syncPurchases(); // triggers Google Play purchase query
 *     }
 *
 *     \@Override
 *     public void onDismissed() {
 *         // Optional: log analytics, etc.
 *     }
 * };
 *
 * PaywallDialog.newInstance(config)
 *     .setCallback(callback)
 *     .show(getSupportFragmentManager(), "paywall");
 * }</pre>
 */
public interface PaywallCallback {

    /**
     * Called when the user taps the subscribe button.
     *
     * @param selectedPlan The plan that was selected at the time of the tap.
     */
    void onSubscribeClicked(SubscriptionPlanUiModel selectedPlan);

    /**
     * Called when the user taps "Restore Purchase".
     * The caller should delegate to {@code BillingManager.syncPurchases()}.
     */
    void onRestoreClicked();

    /**
     * Called when the dialog is dismissed — via the Cancel button, back-press,
     * or programmatic dismissal.
     */
    void onDismissed();
}
