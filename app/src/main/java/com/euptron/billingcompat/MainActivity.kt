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
                .planId("plan_weekly")
                .label("Weekly Plan")
                .price("$ X.XX")
                .period("/ wk")
                .recommended(false)
                .build()

        val monthlyPlan =
            SubscriptionPlanUiModel.Builder()
                .planId("plan_monthly")
                .label("Monthly Plan")
                .price("$ X.XX")
                .period("/ mo")
                .discountBadge("Best Value")
                .recommended(false)
                .build()

        val yearlyPlan =
            SubscriptionPlanUiModel.Builder()
                .planId("plan_yearly")
                .label("Yearly Plan")
                .price("$ X.XX")
                .period("/ yr")
                .discountBadge("Best Value")
                .recommended(true)
                .build()

        val config =
            PaywallConfig.Builder()
                .appName("My App")
                .ctaTitle("Upgrade Now")
                .heroImage(ContextCompat.getDrawable(this, R.drawable.ic_gift_premium))
                .ctaSubtitle("Get access to all premium features")
                .addFeature(
                    FeatureItemUiModel("Feature One", "Description of the first feature goes here")
                )
                .addFeature(
                    FeatureItemUiModel("Feature Two", "Description of the second feature goes here")
                )
                .addFeature(
                    FeatureItemUiModel(
                        "Feature Three", "Description of the third feature goes here"
                    )
                )
                .addFeature(
                    FeatureItemUiModel(
                        "Feature Four",
                        "Description of the fourth feature goes here"
                    )
                )
                .addFeature(
                    FeatureItemUiModel("Feature Five", "Description of the fifth feature goes here")
                )
                .addPlan(weeklyPlan)
                .addPlan(monthlyPlan)
                .addPlan(yearlyPlan)
                .featuresCardHeader("Included Features")
                .subscribeCta("Start Subscription")
                .restoreLabel("Restore Purchases")
                .cancelNote("You can cancel anytime. No long-term commitment.")
                .planOrientation(PaywallConfig.Orientation.VERTICAL)
                .defaultSelectedIndex(1)
                .build()

        PaywallDialog.newInstance(config)
            .setCallback(
                object : PaywallCallback {
                    override fun onSubscribeClicked(plan: SubscriptionPlanUiModel) {
                        toast("Selected: " + plan.label)
                        // Initiate purchase flow here
                    }

                    override fun onRestoreClicked() {
                        toast("Checking for previous purchases...")
                        // Query Google Play for existing purchases
                    }

                    override fun onDismissed() {
                        // Optional: handle dialog close
                    }
                })
            .show(supportFragmentManager, "paywall")
    }

    private fun toast(msg: String?) {
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
    }
}