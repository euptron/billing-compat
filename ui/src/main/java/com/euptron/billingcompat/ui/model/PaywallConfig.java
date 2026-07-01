package com.euptron.billingcompat.ui.model;

import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable configuration object for the paywall dialog.
 *
 * <p>All copy and content is supplied by the caller — nothing is hardcoded in the dialog itself.
 * Build one with {@link Builder} and pass it to
 * {@link com.euptron.billingcompat.ui.dialog.PaywallDialog#newInstance(PaywallConfig)}.
 *
 * <pre>{@code
 * PaywallConfig config = new PaywallConfig.Builder()
 *     .appName("MyApp")
 *     .heroImage(ContextCompat.getDrawable(this, R.drawable.ic_crown))
 *     .ctaTitle("Go Pro Today")
 *     .ctaSubtitle("Unlock everything with a single subscription")
 *     .featuresCardHeader("What you get")
 *     .addFeature(new FeatureItemUiModel("Ad-free experience"))
 *     .addFeature(new FeatureItemUiModel("Unlimited downloads"))
 *     .addPlan(weeklyPlan)
 *     .addPlan(monthlyPlan)
 *     .addPlan(yearlyPlan)
 *     .planOrientation(PaywallConfig.Orientation.VERTICAL)
 *     .defaultSelectedIndex(2)          // pre-select Yearly
 *     .subscribeCta("Subscribe Now")
 *     .restoreLabel("Restore Purchase")
 *     .cancelNote("Cancel anytime. No commitments.")
 *     .build();
 * }</pre>
 */
public final class PaywallConfig {

    /** Controls whether plan cards stack vertically or scroll horizontally. */
    public enum Orientation { VERTICAL, HORIZONTAL }

    private final String appName;
    @Nullable private final Drawable heroImage;
    private final String ctaTitle;
    private final String ctaSubtitle;
    private final String featuresCardHeader;
    private final List<FeatureItemUiModel> features;
    private final List<SubscriptionPlanUiModel> plans;
    private final Orientation planOrientation;
    private final int defaultSelectedIndex;
    private final String subscribeCta;
    private final String restoreLabel;
    private final String cancelNote;

    private PaywallConfig(Builder b) {
        this.appName = b.appName;
        this.heroImage = b.heroImage;
        this.ctaTitle = b.ctaTitle;
        this.ctaSubtitle = b.ctaSubtitle;
        this.featuresCardHeader = b.featuresCardHeader;
        this.features = Collections.unmodifiableList(new ArrayList<>(b.features));
        this.plans = Collections.unmodifiableList(new ArrayList<>(b.plans));
        this.planOrientation = b.planOrientation;
        this.defaultSelectedIndex = b.defaultSelectedIndex;
        this.subscribeCta = b.subscribeCta;
        this.restoreLabel = b.restoreLabel;
        this.cancelNote = b.cancelNote;
    }

    public String getAppName() { return appName; }
    @Nullable public Drawable getHeroImage() { return heroImage; }
    public String getCtaTitle() { return ctaTitle; }
    public String getCtaSubtitle() { return ctaSubtitle; }
    public String getFeaturesCardHeader() { return featuresCardHeader; }
    public List<FeatureItemUiModel> getFeatures() { return features; }
    public List<SubscriptionPlanUiModel> getPlans() { return plans; }
    public Orientation getPlanOrientation() { return planOrientation; }
    /** Index into {@link #getPlans()} that is selected on open. Defaults to 0. */
    public int getDefaultSelectedIndex() { return defaultSelectedIndex; }
    public String getSubscribeCta() { return subscribeCta; }
    public String getRestoreLabel() { return restoreLabel; }
    public String getCancelNote() { return cancelNote; }



    public static final class Builder {
        private String appName = "";
        @Nullable private Drawable heroImage;
        private String ctaTitle = "";
        private String ctaSubtitle = "";
        private String featuresCardHeader = "What you get";
        private final List<FeatureItemUiModel> features = new ArrayList<>();
        private final List<SubscriptionPlanUiModel> plans = new ArrayList<>();
        private Orientation planOrientation = Orientation.VERTICAL;
        private int defaultSelectedIndex = 0;
        private String subscribeCta = "Subscribe Now";
        private String restoreLabel = "Restore Purchase";
        private String cancelNote = "Cancel anytime. No commitments.";

        public Builder appName(String appName) { this.appName = appName; return this; }
        public Builder heroImage(@Nullable Drawable d) { this.heroImage = d; return this; }
        public Builder ctaTitle(String title) { this.ctaTitle = title; return this; }
        public Builder ctaSubtitle(String subtitle) { this.ctaSubtitle = subtitle; return this; }
        public Builder featuresCardHeader(String header) { this.featuresCardHeader = header; return this; }

        public Builder addFeature(FeatureItemUiModel item) {
            this.features.add(item);
            return this;
        }

        public Builder features(List<FeatureItemUiModel> items) {
            this.features.clear();
            this.features.addAll(items);
            return this;
        }

        public Builder addPlan(SubscriptionPlanUiModel plan) {
            this.plans.add(plan);
            return this;
        }

        public Builder plans(List<SubscriptionPlanUiModel> plans) {
            this.plans.clear();
            this.plans.addAll(plans);
            return this;
        }

        /** @see Orientation */
        public Builder planOrientation(Orientation orientation) {
            this.planOrientation = orientation;
            return this;
        }

        /**
         * Zero-based index of the plan that should be selected when the dialog opens.
         * If the index is out of range, index 0 is used as a safe fallback.
         */
        public Builder defaultSelectedIndex(int index) {
            this.defaultSelectedIndex = index;
            return this;
        }

        public Builder subscribeCta(String cta) { this.subscribeCta = cta; return this; }
        public Builder restoreLabel(String label) { this.restoreLabel = label; return this; }
        public Builder cancelNote(String note) { this.cancelNote = note; return this; }

        public PaywallConfig build() {
            if (plans.isEmpty()) throw new IllegalStateException("At least one plan is required");
            // Clamp defaultSelectedIndex
            if (defaultSelectedIndex < 0 || defaultSelectedIndex >= plans.size()) {
                defaultSelectedIndex = 0;
            }
            return new PaywallConfig(this);
        }
    }
}
