package com.euptron.billingcompat.core.security;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;
import com.euptron.billingcompat.core.BillingManager;
import java.security.MessageDigest;
import java.util.List;

/**
 * Provides comprehensive security validation for the application environment and billing
 * transactions.
 *
 * <p>This class acts as a central security coordinator that performs:
 *
 * <ul>
 *   <li><b>Environment Validation:</b> Checks for device integrity (rooting, debugging, emulators)
 *       via {@link IntegrityGuard}.
 *   <li><b>Signature Verification:</b> Ensures the application has not been tampered with or
 *       re-signed by comparing the runtime SHA-256 signature against an expected hash.
 *   <li><b>Purchase Integrity:</b> Detects and remediates fraudulent billing activities or
 *       inconsistent purchase states using {@link PurchaseFraudGuard}.
 * </ul>
 *
 * <p>Unauthorized modifications or integrity failures typically result in application termination
 * or the revocation of local premium states.
 */
public class SecurityGuard {

  private static final String TAG = "SecurityGuard";

  private SecurityGuard() {
    // No-Op
  }

  public static void validateEnvironment(Activity activity, String expectedSignatureHash) {
    IntegrityGuard.enforce(activity);

    if (isSignatureTampered(activity, expectedSignatureHash)) {
      showSignatureViolation(activity);
    }
  }

  public static void validateEnvironment(
      Activity activity,
      String expectedSignatureHash,
      IntegrityGuard.ViolationType... ignoredViolations) {
    IntegrityGuard.enforce(activity, ignoredViolations);

    if (isSignatureTampered(activity, expectedSignatureHash)) {
      showSignatureViolation(activity);
    }
  }

  public static void checkPurchaseIntegrity(
      BillingManager billingManager, String productId, List<String> knownProducts) {
    PurchaseFraudGuard guard = new PurchaseFraudGuard(billingManager);

    if (productId != null) {
      PurchaseFraudGuard.FraudResult result =
          guard.validate(billingManager.getContext(), productId);
      Log.d(TAG, "Purchase integrity for " + productId + ": " + result);
      if (result.isSuspicious()) {
        Log.e(TAG, "Suspicious purchase detected for " + productId + " — revoking local state.");
        result.remediate(billingManager.getContext(), billingManager);
      }
    } else {
      boolean clean = guard.validateAll(billingManager.getContext(), knownProducts);
      Log.d(TAG, "Full purchase integrity check: " + (clean ? "CLEAN" : "VIOLATIONS FOUND"));
    }
  }

  @SuppressWarnings("deprecation")
  private static boolean isSignatureTampered(Context context, String expectedSignatureHash) {
    if (expectedSignatureHash == null || expectedSignatureHash.isEmpty()) {
      Log.e(TAG, "Signatuer empty");
      return false;
    }

    try {
      Signature[] signatures;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageInfo packageInfo =
            context
                .getPackageManager()
                .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (packageInfo.signingInfo != null) {
          signatures =
              packageInfo.signingInfo.hasMultipleSigners()
                  ? packageInfo.signingInfo.getApkContentsSigners()
                  : packageInfo.signingInfo.getSigningCertificateHistory();
        } else {
          return true; // Missing signing information
        }
      } else {
        PackageInfo packageInfo =
            context
                .getPackageManager()
                .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        signatures = packageInfo.signatures;
      }

      if (signatures == null) return true;

      for (Signature signature : signatures) {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(signature.toByteArray());

        //        String currentHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP);
        //        Log.d(TAG, "App Signature Hash: " + currentHash);
        //
        //        if (expectedSignatureHash.equals(currentHash)) return false;
        byte[] digest = md.digest();
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
          hexString.append(String.format("%02X", digest[i]));
          if (i < digest.length - 1) hexString.append(":");
        }
        String currentHash = hexString.toString();
        Log.d(TAG, "App Signature Hash: " + currentHash);

        // 3. Use case-insensitive comparison
        if (expectedSignatureHash.equalsIgnoreCase(currentHash)) return false;
      }
      // Checked all signatures, none matched — tampered
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Error validating application signature.", e);
      return true;
    }
  }

  private static void showSignatureViolation(Activity activity) {
    activity.runOnUiThread(
        () ->
            new android.app.AlertDialog.Builder(activity)
                .setTitle("Security Violation")
                .setMessage(
                    "This copy of the app has been modified or re-signed. "
                        + "It cannot run safely.\n\n"
                        + "Please download the official version from the Google Play Store.")
                .setCancelable(false)
                .setPositiveButton(
                    "Go to Play Store",
                    (dialog, which) -> {
                      try {
                        activity.startActivity(
                            new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "market://details?id=" + activity.getPackageName())));
                      } catch (Exception ignored) {
                        // No-Op
                      }
                      activity.finishAffinity();
                      System.exit(0);
                    })
                .show());
  }
}
