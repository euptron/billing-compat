package com.euptron.billingcompat.core.security;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.euptron.billingcompat.core.BillingManager;
import com.euptron.billingcompat.core.handlers.ProductHandler;
import com.euptron.billingcompat.core.handlers.SubscriptionHandler;
import com.euptron.billingcompat.core.model.PurchaseType;
import com.euptron.billingcompat.core.model.SubscriptionPlan;
import com.euptron.billingcompat.core.products.Purchasable;
import java.util.List;
import java.util.Set;

/**
 * Validates the legitimacy of in-app purchases by cross-checking the {@link BillingManager}'s
 * Google-Play-verified purchase state against the locally cached SharedPreferences state.
 *
 * <p>Tools like Lucky Patcher can intercept the billing flow and inject fake purchase responses
 * into SharedPreferences (or via memory editing) to trick the app into thinking a product was
 * purchased. This guard mitigates that by:
 *
 * <ol>
 *   <li>Comparing the in-memory, Google-Play-sourced set of owned products ({@link
 *       BillingManager#isFeatureUnlocked}) against the persisted local cache ({@link
 *       BillingManager#isUnlocked}).
 *   <li>Flagging discrepancies where the local cache claims "unlocked" but Google Play does not
 *       confirm ownership.
 *   <li>Detecting entries marked as owned in SharedPreferences for products never returned by
 *       Google Play.
 * </ol>
 *
 * <p><b>Server-side verification</b> is the most robust protection. After a successful purchase:
 *
 * <ol>
 *   <li>Send {@code purchase.getPurchaseToken()} to your backend.
 *   <li>Your backend calls the Google Play Developer API: {@code GET
 *       https://androidpublisher.googleapis.com/androidpublisher/v3/applications/
 *       {packageName}/purchases/products/{productId}/tokens/{token}}
 *   <li>Only unlock the feature after your backend confirms the purchase is valid.
 * </ol>
 *
 * <p>{@link #verifyWithServer} is provided as a clearly marked placeholder for this integration.
 * All client-side checks here are complementary — not a replacement.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * PurchaseFraudGuard guard = new PurchaseFraudGuard(billingManager);
 * PurchaseFraudGuard.FraudResult result = guard.validate(context, "premium_feature");
 * if (result.isSuspicious()) {
 *     result.remediate(context, billingManager);
 * }
 * }</pre>
 */
public class PurchaseFraudGuard {

  private static final String TAG = "PurchaseFraudGuard";

  private final BillingManager billingManager;

  /**
   * @param billingManager The app's singleton {@link BillingManager}.
   */
  public PurchaseFraudGuard(@NonNull BillingManager billingManager) {
    this.billingManager = billingManager;
  }

  /**
   * Validates a single product's purchase state by comparing the local cache against the Google
   * Play-confirmed state.
   *
   * <table border="1">
   *   <tr><th>Local cache</th><th>Google Play</th><th>Result</th></tr>
   *   <tr><td>Unlocked</td><td>Owned</td><td>{@link FraudResult.Status#LEGITIMATE}</td></tr>
   *   <tr><td>Unlocked</td><td>Not owned</td><td>{@link FraudResult.Status#FAKE_PURCHASE}</td></tr>
   *   <tr><td>Locked</td><td>Not owned</td><td>{@link FraudResult.Status#NOT_PURCHASED}</td></tr>
   *   <tr><td>Locked</td><td>Owned</td><td>{@link FraudResult.Status#SYNC_REQUIRED}</td></tr>
   * </table>
   *
   * @param context Any non-null context.
   * @param productId The product ID to validate (e.g. {@code "premium_feature"}).
   * @return A {@link FraudResult} describing the validation outcome.
   */
  @NonNull
  public FraudResult validate(@NonNull Context context, @NonNull String productId) {
    boolean localSaysUnlocked = billingManager.isUnlocked(productId);
    boolean playConfirmsOwned = billingManager.isFeatureUnlocked(productId);

    Log.d(
        TAG,
        String.format(
            "validate(%s): local=%s, play=%s", productId, localSaysUnlocked, playConfirmsOwned));

    if (localSaysUnlocked && playConfirmsOwned) {
      return new FraudResult(
          productId, FraudResult.Status.LEGITIMATE, "Google Play confirms purchase is valid.");
    } else if (localSaysUnlocked) {
      Log.e(
          TAG,
          "FRAUD DETECTED for "
              + productId
              + ": local cache claims unlocked but Google Play did NOT confirm ownership.");
      return new FraudResult(
          productId,
          FraudResult.Status.FAKE_PURCHASE,
          "Local state says unlocked but Google Play did not confirm — possible injection or patch attack.");
    } else if (playConfirmsOwned) {
      Log.w(
          TAG, "Cache out of sync for " + productId + " — Play confirms owned but cache is stale.");
      return new FraudResult(
          productId,
          FraudResult.Status.SYNC_REQUIRED,
          "Google Play confirms ownership but local cache is stale. Re-sync recommended.");
    } else {
      return new FraudResult(
          productId, FraudResult.Status.NOT_PURCHASED, "Product is not purchased.");
    }
  }

  /**
   * Validates all Play-confirmed owned products against the local cache, and additionally checks
   * for SharedPreferences entries that Google Play never returned (indicative of direct SP
   * manipulation).
   *
   * @param context Application context.
   * @param knownProducts The full list of product IDs the app tracks.
   * @return {@code true} if all states are consistent; {@code false} if any fraud is detected.
   */
  public boolean validateAll(@NonNull Context context, List<String> knownProducts) {
    Set<String> playOwnedProducts = billingManager.getOwnedProducts();
    boolean allClean = true;

    for (String productId : playOwnedProducts) {
      FraudResult result = validate(context, productId);
      if (result.isSuspicious()) {
        Log.e(TAG, "Suspicious result for " + productId + ": " + result.getMessage());
        result.remediate(context, billingManager);
        allClean = false;
      }
    }

    detectOrphanedLocalEntries(context, playOwnedProducts, knownProducts);
    return allClean;
  }

  /**
   * Initiates server-side purchase token verification with a default 10-second timeout.
   *
   * <p>Replace the body of this method to call your own backend, which should call:
   *
   * <pre>
   * GET <a href="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/">...</a>
   *     {packageName}/purchases/products/{productId}/tokens/{purchaseToken}
   * </pre>
   *
   * <p>Only unlock the feature once your server confirms the purchase. On network failure, this
   * method falls back to the Google Play client-side state.
   *
   * @param serverUrl Your backend verification endpoint URL.
   * @param purchasable The product being verified.
   * @param purchaseToken The token from {@code Purchase.getPurchaseToken()}.
   * @param callback Called with the verification result.
   */
  public void verifyWithServer(
      @NonNull String serverUrl,
      @NonNull Purchasable purchasable,
      @NonNull String purchaseToken,
      @NonNull ServerVerificationCallback callback) {
    verifyWithServer(10_000L, serverUrl, purchasable, purchaseToken, callback);
  }

  /**
   * Initiates server-side purchase token verification with a custom timeout.
   *
   * @param timeout Network timeout in milliseconds.
   * @param serverUrl Your backend verification endpoint URL.
   * @param purchasable The product being verified.
   * @param purchaseToken The token from {@code Purchase.getPurchaseToken()}.
   * @param callback Called with the verification result.
   * @see #verifyWithServer(String, Purchasable, String, ServerVerificationCallback)
   */
  public void verifyWithServer(
      long timeout,
      @NonNull String serverUrl,
      @NonNull Purchasable purchasable,
      @NonNull String purchaseToken,
      @NonNull ServerVerificationCallback callback) {
    String packageName = billingManager.getContext().getPackageName();
    SSVClient client = new SSVClient(serverUrl);

    String productId = purchasable.getId();
    boolean isSubscription = purchasable.getType() == PurchaseType.SUBSCRIPTION;

    client.verifyPurchase(
        productId,
        purchaseToken,
        packageName,
        isSubscription,
        new SSVClient.Callback() {
          @Override
          public void onVerified(boolean isValid, long expiryMillis, boolean autoRenewing) {
            Log.d(TAG, "Server verification result: " + isValid);

            if (isValid && expiryMillis > 0) {
              ProductHandler<?> handler = billingManager.getHandler(PurchaseType.SUBSCRIPTION);
              if (handler instanceof SubscriptionHandler) {
                SubscriptionHandler subscriptionHandler = (SubscriptionHandler) handler;
                SubscriptionPlan plan = subscriptionHandler.getPlanFromProductId(productId);
                if (plan != null) {
                  subscriptionHandler.saveServerExpiry(plan, expiryMillis);
                  Log.d(TAG, "Saved server expiry for: " + plan.name());
                }
              }
            }
            callback.onResult(isValid, expiryMillis, autoRenewing);
            client.shutdown();
          }

          @Override
          public void onError(String error) {
            Log.e(TAG, "Server verification error: " + error);
            boolean playConfirms = billingManager.isFeatureUnlocked(productId);
            Log.w(TAG, "Falling back to client-side verification: " + playConfirms);
            callback.onResult(playConfirms, 0, false);
            client.shutdown();
          }
        },
        timeout);
  }

  /**
   * Checks for product IDs present in the local cache but absent from the Google-Play-confirmed
   * owned set, which indicates direct SharedPreferences manipulation. Any such entries are
   * immediately revoked via {@link FraudResult#remediate}.
   *
   * @param context Application context.
   * @param playOwnedProducts Products confirmed as owned by Google Play.
   * @param knownProducts The full list of product IDs the app tracks.
   */
  private void detectOrphanedLocalEntries(
      Context context, Set<String> playOwnedProducts, List<String> knownProducts) {
    for (String productId : knownProducts) {
      if (billingManager.isUnlocked(productId) && !playOwnedProducts.contains(productId)) {
        Log.e(TAG, "Orphaned entry detected in local cache for: " + productId);
        new FraudResult(
                productId,
                FraudResult.Status.FAKE_PURCHASE,
                "Orphaned SharedPreferences entry — not confirmed by Google Play.")
            .remediate(context, billingManager);
      }
    }
  }

  public interface ServerVerificationCallback {

    /**
     * @param isValid {@code true} if the server confirmed the purchase is authentic.
     * @param expiryMillis Subscription expiry time in milliseconds, or {@code 0} for one-time
     *     products.
     * @param autoRenewing {@code true} if this is an active auto-renewing subscription.
     */
    void onResult(boolean isValid, long expiryMillis, boolean autoRenewing);
  }

  public static class FraudResult {

    public enum Status {
      /** Google Play confirms ownership and the local cache agrees. */
      LEGITIMATE,
      /** Local cache claims "unlocked" but Google Play did not confirm ownership. */
      FAKE_PURCHASE,
      /** Product is not owned in either the local cache or Google Play. */
      NOT_PURCHASED,
      /** Google Play confirms ownership but the local cache is stale and needs a sync. */
      SYNC_REQUIRED
    }

    private final String productId;
    private final Status status;
    private final String message;

    FraudResult(@NonNull String productId, @NonNull Status status, @NonNull String message) {
      this.productId = productId;
      this.status = status;
      this.message = message;
    }

    @NonNull
    public String getProductId() {
      return productId;
    }

    @NonNull
    public Status getStatus() {
      return status;
    }

    @NonNull
    public String getMessage() {
      return message;
    }

    public boolean isSuspicious() {
      return status == Status.FAKE_PURCHASE;
    }

    /**
     * Clears the local cache for this product and triggers a re-sync from Google Play. Called
     * automatically by {@link PurchaseFraudGuard#validateAll} on suspicious results. Has no effect
     * if the status is neither {@link Status#FAKE_PURCHASE} nor {@link Status#SYNC_REQUIRED}.
     *
     * @param context Application context.
     * @param billingManager The billing provider used to trigger the re-sync.
     */
    public void remediate(@NonNull Context context, @NonNull BillingManager billingManager) {
      if (!isSuspicious() && status != Status.SYNC_REQUIRED) return;
      Log.w(TAG, "Remediating " + productId + ": clearing local unlock cache and re-syncing.");
      billingManager.syncPurchases();
    }

    @NonNull
    @Override
    public String toString() {
      return "FraudResult{productId='"
          + productId
          + "', status="
          + status
          + ", message='"
          + message
          + "'}";
    }
  }
}
