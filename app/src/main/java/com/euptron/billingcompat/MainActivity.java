package com.euptron.billingcompat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.euptron.billingcompat.ui.dialog.PaywallDialog;
import com.euptron.billingcompat.ui.model.FeatureItemUiModel;
import com.euptron.billingcompat.ui.model.PaywallCallback;
import com.euptron.billingcompat.ui.model.PaywallConfig;
import com.euptron.billingcompat.ui.model.SubscriptionPlanUiModel;

public class MainActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    findViewById(R.id.show_paywall_button)
        .setOnClickListener(
            v -> {
              showPaywall();
            });

    findViewById(R.id.goto_test_button)
        .setOnClickListener(
            v -> {
              var intent = new Intent(MainActivity.this, BillingTestActivity.class);
              startActivity(intent);
            });
  }

  private void showPaywall() {
    SubscriptionPlanUiModel weeklyPlan =
        new SubscriptionPlanUiModel.Builder()
            .planId("subs_weekly")
            .label("Weekly")
            .price("$2.99")
            .period("/ week")
            .recommended(false)
            .build();

    SubscriptionPlanUiModel monthlyPlan =
        new SubscriptionPlanUiModel.Builder()
            .planId("subs_monthly")
            .label("Monthly")
            .price("$9.99")
            .period("/ month")
            .discountBadge("Save 16%")
            .recommended(false)
            .build();

    SubscriptionPlanUiModel yearlyPlan =
        new SubscriptionPlanUiModel.Builder()
            .planId("subs_yearly")
            .label("Yearly")
            .price("$24.99")
            .period("/ year")
            .discountBadge("Save 16%")
            .recommended(true)
            .build();

    PaywallConfig config =
        new PaywallConfig.Builder()
            .appName("Billing Compat")
            .ctaTitle("Go Pro Today")
            .heroImage(ContextCompat.getDrawable(this, R.drawable.ic_gift_premium))
            .ctaSubtitle("Unlock everything and grow faster")
            .addFeature(
                new FeatureItemUiModel("Ad 25+ Tools unlocked", "Full access to every feature"))
            .addFeature(new FeatureItemUiModel("Unlimited AI Assistant", "No daily message limit"))
            .addFeature(
                new FeatureItemUiModel(
                    "Advanced Competitor Analysis", "Spy on top channels in your niche"))
            .addFeature(new FeatureItemUiModel("Ad-free experience", "Clean ad-free experience"))
            .addFeature(new FeatureItemUiModel("Priority support", "Get help within 24 hours"))
            .addPlan(weeklyPlan)
            .addPlan(monthlyPlan)
            .addPlan(yearlyPlan)
            .featuresCardHeader("What you get")
            .subscribeCta("Subscribe Now")
            .restoreLabel("Restore Purchase")
            .cancelNote("Cancel anytime. No commitments.")
            .planOrientation(PaywallConfig.Orientation.VERTICAL)
            .defaultSelectedIndex(1)
            .build();

    PaywallDialog.newInstance(config)
        .setCallback(
            new PaywallCallback() {
              @Override
              public void onSubscribeClicked(SubscriptionPlanUiModel plan) {
                toast("Subscribing to: " + plan.getLabel());
                // load billing here
              }

              @Override
              public void onRestoreClicked() {
                toast("Restoring purchases...");
                // Trigger Google Play purchase query to restore past purchases:
              }

              @Override
              public void onDismissed() {
                // Optional: log analytics, clear loading states, etc.
              }
            })
        .show(getSupportFragmentManager(), "paywall");
  }

  private void toast(String msg) {
    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
  }
}
