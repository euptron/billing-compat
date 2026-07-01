package com.euptron.billingcompat

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.euptron.billingcompat.core.BillingManager
import com.euptron.billingcompat.core.builders.BillingConfigBuilder
import com.euptron.billingcompat.core.listeners.PurchaseEventListener.SimpleListener
import com.euptron.billingcompat.core.products.ConsumableProduct
import com.euptron.billingcompat.core.products.NonConsumableProduct
import com.euptron.billingcompat.core.products.ProductFactory
import com.euptron.billingcompat.core.products.Purchasable
import com.euptron.billingcompat.core.security.IntegrityGuard
import com.euptron.billingcompat.core.security.PurchaseFraudGuard
import com.euptron.billingcompat.core.security.SecurityGuard

class BillingTestActivity : Activity() {
    private var billingManager: BillingManager? = null
    private var logText: TextView? = null
    private var log: StringBuilder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecurityGuard.validateEnvironment(
            this,
            EXPECTED_HASH,
            IntegrityGuard.ViolationType.APP_DEBUGGABLE,
            IntegrityGuard.ViolationType.PATCHER_INSTALLED,
            IntegrityGuard.ViolationType.DEBUGGER_ATTACHED,
            IntegrityGuard.ViolationType.EMULATOR_DETECTED
        )

        // Simple UI: just a text log and two buttons
        log = StringBuilder()
        logText = TextView(this)
        val buyNonConsumable = Button(this)
        buyNonConsumable.text = "Buy Non-Consumable"
        val buyConsumable = Button(this)
        buyConsumable.text = "Buy Consumable"
        val syncBtn = Button(this)
        syncBtn.text = "Sync Purchases"
        val checkBtn = Button(this)
        checkBtn.text = "Check Ownership"
        val fraudBtn = Button(this)
        fraudBtn.text = "Check Fraud"
        val ssvBtn = Button(this)
        ssvBtn.text = "SSV Verify"

        // Layout (vertical stack)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(logText)
        layout.addView(buyNonConsumable)
        layout.addView(buyConsumable)
        layout.addView(syncBtn)
        layout.addView(checkBtn)
        layout.addView(fraudBtn)
        layout.addView(ssvBtn)
        setContentView(layout)

        // 2. Build BillingManager
        val removeAds: Purchasable? =
            NonConsumableProduct.Builder()
                .id(NON_CONSUMABLE_ID)
                .name("Remove Ads")
                .price(2.99)
                .build()
        val coins: Purchasable? =
            ConsumableProduct.Builder()
                .id(CONSUMABLE_ID)
                .name("100 Coins")
                .price(0.99)
                .quantity(100)
                .build()

        billingManager =
            BillingConfigBuilder(this)
                .addProduct(removeAds)
                .addProduct(coins)
                .setListener(
                    object : SimpleListener() {
                        override fun onProductPurchased(p: Purchasable, purchaseToken: String?) {
                            addLog("PURCHASED: " + p.getId() + ", token: " + purchaseToken)
                        }

                        override fun onPurchaseError(error: String?, p: Purchasable?) {
                            addLog("ERROR: $error")
                        }

                        override fun onPurchasesSynced() {
                            addLog("SYNCED")
                        }
                    })
                .build()

        // 3. Button actions
        buyNonConsumable.setOnClickListener { _: View? ->
            addLog("Buying non-consumable...")
            billingManager!!.purchase(this).nonConsumable(NON_CONSUMABLE_ID).execute()
        }

        buyConsumable.setOnClickListener { _: View? ->
            addLog("Buying consumable...")
            billingManager!!.purchase(this).consumable(CONSUMABLE_ID, 1).execute()
        }

        syncBtn.setOnClickListener { _: View? ->
            addLog("Syncing...")
            billingManager!!.syncPurchases()
        }

        checkBtn.setOnClickListener { _: View? ->
            val unlocked = billingManager!!.isFeatureUnlocked(NON_CONSUMABLE_ID)
            val cached = billingManager!!.isUnlocked(NON_CONSUMABLE_ID)
            addLog("Feature unlocked: $unlocked | Cached: $cached")
        }

        fraudBtn.setOnClickListener { _: View? ->
            val guard = PurchaseFraudGuard(billingManager!!)
            val result = guard.validate(this, NON_CONSUMABLE_ID)
            addLog("Fraud check: " + result.status + " - " + result.message)
        }

        ssvBtn.setOnClickListener { _: View? ->
            addLog("SSV: Starting server verification...")
            val guard = PurchaseFraudGuard(billingManager!!)
            val product =
                ProductFactory.createNonConsumable(NON_CONSUMABLE_ID, "test_name", 1.00)
            guard.verifyWithServer(
                "https://ssv.jonipedinc.workers.dev/",
                product,
                "PLACEHOLDER_TOKEN"
            )  // Replace with real token from a purchase
            { isValid: Boolean, expiry: Long, _: Boolean ->
                runOnUiThread {
                    addLog("SSV result: valid=$isValid expiry=$expiry")
                }
            }
        }
    }

    private fun addLog(msg: String) {
        Log.d("BillingTest", msg)
        log!!.append(msg).append("\n")
        // Run UI updates on the main thread since
        // onPurchasesSynced callback runs on a background thread
        runOnUiThread { logText!!.text = log.toString() }
    }

    override fun onResume() {
        super.onResume()
        if (billingManager != null) billingManager!!.syncPurchases()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (billingManager != null) billingManager!!.destroy()
    }

    companion object {
        // Replace with your real product IDs
        private const val NON_CONSUMABLE_ID = "remove_ads"
        private const val CONSUMABLE_ID = "coins_100"
        private const val EXPECTED_HASH =
            "B3:BF:74:A8:43:90:6B:80:F9:26:A6:9F:C7:3E:01:0F:F8:22:4F:3E:B3:56:F2:2E:55:A2:D8:85:ED:87:97:72"
    }
}
