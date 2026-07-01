package com.euptron.billingcompat;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.euptron.billingcompat.core.BillingManager;
import com.euptron.billingcompat.core.builders.BillingConfigBuilder;
import com.euptron.billingcompat.core.listeners.PurchaseEventListener;
import com.euptron.billingcompat.core.products.ConsumableProduct;
import com.euptron.billingcompat.core.products.NonConsumableProduct;
import com.euptron.billingcompat.core.products.ProductFactory;
import com.euptron.billingcompat.core.products.Purchasable;
import com.euptron.billingcompat.core.security.IntegrityGuard;
import com.euptron.billingcompat.core.security.PurchaseFraudGuard;
import com.euptron.billingcompat.core.security.SecurityGuard;

public class BillingTestActivity extends Activity {

  private BillingManager billingManager;
  private TextView logText;
  private StringBuilder log;

  // Replace with your real product IDs
  private static final String NON_CONSUMABLE_ID = "remove_ads";
  private static final String CONSUMABLE_ID = "coins_100";
  private static final String EXPECTED_HASH =
      "B3:BF:74:A8:43:90:6B:80:F9:26:A6:9F:C7:3E:01:0F:F8:22:4F:3E:B3:56:F2:2E:55:A2:D8:85:ED:87:97:72";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    SecurityGuard.validateEnvironment(
        this,
        EXPECTED_HASH,
        IntegrityGuard.ViolationType.APP_DEBUGGABLE,
        IntegrityGuard.ViolationType.PATCHER_INSTALLED,
        IntegrityGuard.ViolationType.DEBUGGER_ATTACHED,
        IntegrityGuard.ViolationType.EMULATOR_DETECTED);

    // Simple UI: just a text log and two buttons
    log = new StringBuilder();
    logText = new TextView(this);
    Button buyNonConsumable = new Button(this);
    buyNonConsumable.setText("Buy Non-Consumable");
    Button buyConsumable = new Button(this);
    buyConsumable.setText("Buy Consumable");
    Button syncBtn = new Button(this);
    syncBtn.setText("Sync Purchases");
    Button checkBtn = new Button(this);
    checkBtn.setText("Check Ownership");
    Button fraudBtn = new Button(this);
    fraudBtn.setText("Check Fraud");
    Button ssvBtn = new Button(this);
    ssvBtn.setText("SSV Verify");

    // Layout (vertical stack)
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.addView(logText);
    layout.addView(buyNonConsumable);
    layout.addView(buyConsumable);
    layout.addView(syncBtn);
    layout.addView(checkBtn);
    layout.addView(fraudBtn);
    layout.addView(ssvBtn);
    setContentView(layout);

    // 2. Build BillingManager
    Purchasable removeAds =
        new NonConsumableProduct.Builder()
            .id(NON_CONSUMABLE_ID)
            .name("Remove Ads")
            .price(2.99)
            .build();
    Purchasable coins =
        new ConsumableProduct.Builder()
            .id(CONSUMABLE_ID)
            .name("100 Coins")
            .price(0.99)
            .quantity(100)
            .build();

    billingManager =
        new BillingConfigBuilder(this)
            .addProduct(removeAds)
            .addProduct(coins)
            .setListener(
                new PurchaseEventListener.SimpleListener() {
                  @Override
                  public void onProductPurchased(Purchasable p, String purchaseToken) {
                    addLog("✓ PURCHASED: " + p.getId() + ", token: " + purchaseToken);
                  }

                  @Override
                  public void onPurchaseError(String error, Purchasable p) {
                    addLog("✗ ERROR: " + error);
                  }

                  @Override
                  public void onPurchasesSynced() {
                    addLog("✓ SYNCED");
                  }
                })
            .build();

    // 3. Button actions
    buyNonConsumable.setOnClickListener(
        v -> {
          addLog("Buying non-consumable...");
          billingManager.purchase(this).nonConsumable(NON_CONSUMABLE_ID).execute();
        });

    buyConsumable.setOnClickListener(
        v -> {
          addLog("Buying consumable...");
          billingManager.purchase(this).consumable(CONSUMABLE_ID, 1).execute();
        });

    syncBtn.setOnClickListener(
        v -> {
          addLog("Syncing...");
          billingManager.syncPurchases();
        });

    checkBtn.setOnClickListener(
        v -> {
          boolean unlocked = billingManager.isFeatureUnlocked(NON_CONSUMABLE_ID);
          boolean cached = billingManager.isUnlocked(NON_CONSUMABLE_ID);
          addLog("Feature unlocked: " + unlocked + " | Cached: " + cached);
        });

    fraudBtn.setOnClickListener(
        v -> {
          PurchaseFraudGuard guard = new PurchaseFraudGuard(billingManager);
          PurchaseFraudGuard.FraudResult result = guard.validate(this, NON_CONSUMABLE_ID);
          addLog("Fraud check: " + result.getStatus() + " - " + result.getMessage());
        });

    ssvBtn.setOnClickListener(
        v -> {
          addLog("SSV: Starting server verification...");
          PurchaseFraudGuard guard = new PurchaseFraudGuard(billingManager);
          NonConsumableProduct product =
              ProductFactory.createNonConsumable(NON_CONSUMABLE_ID, "test_name", 1.00);
          guard.verifyWithServer(
              "https://ssv.jonipedinc.workers.dev/",
              product,
              "PLACEHOLDER_TOKEN", // Replace with real token from a purchase
              (isValid, expiry, renewing) -> {
                runOnUiThread(
                    () -> {
                      addLog("SSV result: valid=" + isValid + " expiry=" + expiry);
                    });
              });
        });
  }

  private void addLog(String msg) {
    Log.d("BillingTest", msg);
    log.append(msg).append("\n");
    // Run UI updates on the main thread since
    // onPurchasesSynced callback runs on a background thread
    runOnUiThread(() -> logText.setText(log.toString()));
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (billingManager != null) billingManager.syncPurchases();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (billingManager != null) billingManager.destroy();
  }
}
