package com.euptron.billingcompat.core.security;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;

/**
 * Client for performing Server-Side Verification (SSV) of Google Play purchases.
 *
 * <p>This class manages asynchronous communication with a backend server to validate purchase
 * tokens. It handles network requests on a background thread and provides callbacks for
 * verification results, helping to prevent fraudulent transactions by offloading validation
 * logic to a secure environment.
 */
public class SSVClient {
  private static final String TAG = "SSVClient";
  private final String serverUrl;
  private final ExecutorService executor;

  public SSVClient(String serverUrl) {
    this.serverUrl = serverUrl;
    this.executor = Executors.newSingleThreadExecutor();
  }

  public interface Callback {
    void onVerified(boolean isValid, long expiryMillis, boolean autoRenewing);
    void onError(String error);
  }

  /**
   * Verify purchase asynchronously
   *
   * @param productId Google Play product ID
   * @param purchaseToken Purchase token from Google Play
   * @param packageName Your app's package name
   * @param isSubscription true if this product is a subscription, false for a one-time product
   * @param callback Callback for result
   * @return Future<?> for cancellation if needed
   */
  public Future<?> verifyPurchase(
          String productId, String purchaseToken, String packageName, boolean isSubscription, Callback callback) {
    return executor.submit(
            () -> {
              try {
                VerificationResult result = doVerification(productId, purchaseToken, packageName, isSubscription);
                if (callback != null) {
                  callback.onVerified(result.valid, result.expiryMillis, result.autoRenewing);
                }
              } catch (Exception e) {
                Log.e(TAG, "Verification error", e);
                if (callback != null) {
                  callback.onError(e.getMessage());
                }
              }
            });
  }

  /**
   * Verify purchase with timeout
   *
   * @param timeoutMillis Timeout in milliseconds
   */
  public Future<?> verifyPurchase(
          String productId,
          String purchaseToken,
          String packageName,
          boolean isSubscription,
          Callback callback,
          long timeoutMillis) {
    return executor.submit(
            () -> {
              try {
                final VerificationResult[] result = new VerificationResult[1];
                final Exception[] error = new Exception[1];

                Thread worker =
                        new Thread(
                                () -> {
                                  try {
                                    result[0] = doVerification(productId, purchaseToken, packageName, isSubscription);
                                  } catch (Exception e) {
                                    error[0] = e;
                                  }
                                });

                worker.start();
                worker.join(timeoutMillis);

                if (worker.isAlive()) {
                  worker.interrupt();
                  if (callback != null) {
                    callback.onError("Verification timeout");
                  }
                } else if (error[0] != null) {
                  if (callback != null) {
                    callback.onError(error[0].getMessage());
                  }
                } else {
                  if (callback != null) {
                    callback.onVerified(result[0].valid, result[0].expiryMillis, result[0].autoRenewing);
                  }
                }

              } catch (Exception e) {
                Log.e(TAG, "Verification error", e);
                if (callback != null) {
                  callback.onError(e.getMessage());
                }
              }
            });
  }

  /** Actual verification logic (runs on background thread) */
  private VerificationResult doVerification(
          String productId, String purchaseToken, String packageName, boolean isSubscription)
          throws Exception {
    HttpURLConnection connection = null;
    try {
      JSONObject payload = new JSONObject();
      payload.put("productId", productId);
      payload.put("purchaseToken", purchaseToken);
      payload.put("packageName", packageName);
      payload.put("type", isSubscription ? "subscription" : "product");

      URL url = new URL(serverUrl);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(15000);

      try (OutputStream os = connection.getOutputStream()) {
        os.write(payload.toString().getBytes());
        os.flush();
      }

      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
          StringBuilder response = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }

          JSONObject result = new JSONObject(response.toString());
          boolean valid = result.optBoolean("valid", false);
          long expiryMillis = result.optLong("expiry", 0);
          boolean autoRenewing = result.optBoolean("autoRenewing", false);

          return new VerificationResult(valid, expiryMillis, autoRenewing);
        }
      } else {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
          StringBuilder error = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            error.append(line);
          }
          Log.e(TAG, "Server error: " + error);
        }
        return new VerificationResult(false, 0, false);
      }

    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  public void shutdown() {
    executor.shutdown();
  }

  private static class VerificationResult {
    final boolean valid;
    final long expiryMillis;
    final boolean autoRenewing;

    VerificationResult(boolean valid, long expiryMillis, boolean autoRenewing) {
      this.valid = valid;
      this.expiryMillis = expiryMillis;
      this.autoRenewing = autoRenewing;
    }
  }
}