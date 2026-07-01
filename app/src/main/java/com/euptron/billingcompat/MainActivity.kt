package com.euptron.billingcompat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.euptron.billingcompat.ui.dialog.PaywallDialog
import com.euptron.billingcompat.ui.model.FeatureItemUiModel
import com.euptron.billingcompat.ui.model.PaywallCallback
import com.euptron.billingcompat.ui.model.PaywallConfig
import com.euptron.billingcompat.ui.model.SubscriptionPlanUiModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.show_paywall_button)
            .setOnClickListener { _: View? ->
                showPaywall()
            }

        findViewById<View>(R.id.goto_test_button)
            .setOnClickListener { _: View? ->
                val intent = Intent(this@MainActivity, BillingTestActivity::class.java)
                startActivity(intent)
            }
    }

    private fun showPaywall() {
        val weeklyPlan =
            SubscriptionPlanUiModel.Builder()
                .planId("subs_weekly")
                .label("Weekly")
                .price("$2.99")
                .period("/ week")
                .recommended(false)
                .build()

        val monthlyPlan =
            SubscriptionPlanUiModel.Builder()
                .planId("subs_monthly")
                .label("Monthly")
                .price("$9.99")
                .period("/ month")
                .discountBadge("Save 16%")
                .recommended(false)
                .build()

        val yearlyPlan =
            SubscriptionPlanUiModel.Builder()
                .planId("subs_yearly")
                .label("Yearly")
                .price("$24.99")
                .period("/ year")
                .discountBadge("Save 16%")
                .recommended(true)
                .build()

        val config =
            PaywallConfig.Builder()
                .appName("Billing Compat")
                .ctaTitle("Go Pro Today")
                .heroImage(ContextCompat.getDrawable(this, R.drawable.ic_gift_premium))
                .ctaSubtitle("Unlock everything and grow faster")
                .addFeature(
                    FeatureItemUiModel("Ad 25+ Tools unlocked", "Full access to every feature")
                )
                .addFeature(FeatureItemUiModel("Unlimited AI Assistant", "No daily message limit"))
                .addFeature(
                    FeatureItemUiModel(
                        "Advanced Competitor Analysis", "Spy on top channels in your niche"
                    )
                )
                .addFeature(FeatureItemUiModel("Ad-free experience", "Clean ad-free experience"))
                .addFeature(FeatureItemUiModel("Priority support", "Get help within 24 hours"))
                .addPlan(weeklyPlan)
                .addPlan(monthlyPlan)
                .addPlan(yearlyPlan)
                .featuresCardHeader("What you get")
                .subscribeCta("Subscribe Now")
                .restoreLabel("Restore Purchase")
                .cancelNote("Cancel anytime. No commitments.")
                .planOrientation(PaywallConfig.Orientation.VERTICAL)
                .defaultSelectedIndex(1)
                .build()

        PaywallDialog.newInstance(config)
            .setCallback(
                object : PaywallCallback {
                    override fun onSubscribeClicked(plan: SubscriptionPlanUiModel) {
                        toast("Subscribing to: " + plan.label)
                        // load billing here
                    }

                    override fun onRestoreClicked() {
                        toast("Restoring purchases...")
                        // Trigger Google Play purchase query to restore past purchases:
                    }

                    override fun onDismissed() {
                        // Optional: log analytics, clear loading states, etc.
                    }
                })
            .show(supportFragmentManager, "paywall")
    }

    private fun toast(msg: String?) {
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
    }
}
