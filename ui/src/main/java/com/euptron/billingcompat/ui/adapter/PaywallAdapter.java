package com.euptron.billingcompat.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.euptron.billingcompat.ui.R;
import com.euptron.billingcompat.ui.model.PaywallConfig;
import com.euptron.billingcompat.ui.model.SubscriptionPlanUiModel;
import java.util.List;

/**
 * Single-RecyclerView adapter for the paywall dialog.
 *
 * <p>View type layout (top → bottom):
 * <ol>
 *   <li>{@link #TYPE_HEADER}   — hero image, CTA title, CTA subtitle</li>
 *   <li>{@link #TYPE_FEATURES} — "What you get" card with nested feature rows</li>
 *   <li>{@link #TYPE_PLAN_LABEL} — "Choose your plan" label</li>
 *   <li>{@link #TYPE_PLAN_CARDS} — horizontal or vertical list of plan cards</li>
 *   <li>{@link #TYPE_FOOTER}   — Subscribe button, Restore button, cancel note</li>
 * </ol>
 */
public class PaywallAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_FEATURES = 1;
    public static final int TYPE_PLAN_LABEL = 2;
    public static final int TYPE_PLAN_CARDS = 3;
    public static final int TYPE_FOOTER = 4;

    private final PaywallConfig config;
    private int selectedPlanIndex;
    private OnSubscribeClickListener subscribeListener;
    private OnRestoreClickListener restoreListener;

    public interface OnSubscribeClickListener {
        void onSubscribeClicked(SubscriptionPlanUiModel plan);
    }

    public interface OnRestoreClickListener {
        void onRestoreClicked();
    }

    public PaywallAdapter(PaywallConfig config) {
        this.config = config;
        this.selectedPlanIndex = config.getDefaultSelectedIndex();
    }

    public void setOnSubscribeClickListener(OnSubscribeClickListener l) {
        this.subscribeListener = l;
    }

    public void setOnRestoreClickListener(OnRestoreClickListener l) {
        this.restoreListener = l;
    }

    public SubscriptionPlanUiModel getSelectedPlan() {
        List<SubscriptionPlanUiModel> plans = config.getPlans();
        if (selectedPlanIndex >= 0 && selectedPlanIndex < plans.size()) {
            return plans.get(selectedPlanIndex);
        }
        return plans.get(0);
    }


    @Override
    public int getItemCount() {
        return 5;
    }

    @Override
    public int getItemViewType(int position) {
        switch (position) {
            case 0: return TYPE_HEADER;
            case 1: return TYPE_FEATURES;
            case 2: return TYPE_PLAN_LABEL;
            case 3: return TYPE_PLAN_CARDS;
            case 4: return TYPE_FOOTER;
            default: throw new IllegalArgumentException("Unknown position: " + position);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderViewHolder(inflater.inflate(R.layout.item_paywall_header, parent, false));
            case TYPE_FEATURES:
                return new FeaturesViewHolder(inflater.inflate(R.layout.item_paywall_features, parent, false));
            case TYPE_PLAN_LABEL:
                return new PlanLabelViewHolder(inflater.inflate(R.layout.item_paywall_plan_label, parent, false));
            case TYPE_PLAN_CARDS:
                return new PlanCardsViewHolder(inflater.inflate(R.layout.item_paywall_plan_cards, parent, false));
            case TYPE_FOOTER:
                return new FooterViewHolder(inflater.inflate(R.layout.item_paywall_footer, parent, false));
            default:
                throw new IllegalArgumentException("Unknown viewType: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case TYPE_HEADER:
                bindHeader((HeaderViewHolder) holder);
                break;
            case TYPE_FEATURES:
                bindFeatures((FeaturesViewHolder) holder);
                break;
            case TYPE_PLAN_LABEL:
                // Static label, no binding needed beyond inflation
                break;
            case TYPE_PLAN_CARDS:
                bindPlanCards((PlanCardsViewHolder) holder);
                break;
            case TYPE_FOOTER:
                bindFooter((FooterViewHolder) holder);
                break;
        }
    }



    private void bindHeader(HeaderViewHolder h) {
        if (config.getHeroImage() != null) {
            h.heroImage.setImageDrawable(config.getHeroImage());
            h.heroImage.setVisibility(View.VISIBLE);
        } else {
            h.heroImage.setVisibility(View.GONE);
        }
        h.ctaTitle.setText(config.getCtaTitle());
        h.ctaSubtitle.setText(config.getCtaSubtitle());
    }

    private void bindFeatures(FeaturesViewHolder h) {
        h.featuresHeader.setText(config.getFeaturesCardHeader());
        FeatureRowAdapter featureAdapter = new FeatureRowAdapter(config.getFeatures());
        h.featuresList.setLayoutManager(new LinearLayoutManager(h.featuresList.getContext()));
        h.featuresList.setAdapter(featureAdapter);
        // Prevent nested scroll conflict — features list is not independently scrollable
        h.featuresList.setNestedScrollingEnabled(false);
    }

    private void bindPlanCards(PlanCardsViewHolder h) {
        boolean isHorizontal = config.getPlanOrientation() == PaywallConfig.Orientation.HORIZONTAL;
        LinearLayoutManager llm = new LinearLayoutManager(
                h.planRecycler.getContext(),
                isHorizontal ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL,
                false);
        h.planRecycler.setLayoutManager(llm);
        h.planRecycler.setNestedScrollingEnabled(false);

        PlanCardAdapter planAdapter = new PlanCardAdapter(
                config.getPlans(),
                selectedPlanIndex,
                index -> {
                    selectedPlanIndex = index;
                    // Refresh plan cards and footer (subscribe button)
                    notifyItemChanged(TYPE_PLAN_CARDS);
                    notifyItemChanged(TYPE_FOOTER);
                });
        h.planRecycler.setAdapter(planAdapter);
    }

    private void bindFooter(FooterViewHolder h) {
        h.subscribeButton.setText(config.getSubscribeCta());
        h.subscribeButton.setOnClickListener(v -> {
            if (subscribeListener != null) {
                subscribeListener.onSubscribeClicked(getSelectedPlan());
            }
        });

        h.restoreButton.setText(config.getRestoreLabel());
        h.restoreButton.setOnClickListener(v -> {
            if (restoreListener != null) {
                restoreListener.onRestoreClicked();
            }
        });

        h.cancelNote.setText(config.getCancelNote());
    }



    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        ImageView heroImage;
        TextView ctaTitle;
        TextView ctaSubtitle;

        HeaderViewHolder(@NonNull View v) {
            super(v);
            heroImage = v.findViewById(R.id.paywall_hero_image);
            ctaTitle = v.findViewById(R.id.paywall_cta_title);
            ctaSubtitle = v.findViewById(R.id.paywall_cta_subtitle);
        }
    }

    static class FeaturesViewHolder extends RecyclerView.ViewHolder {
        TextView featuresHeader;
        RecyclerView featuresList;

        FeaturesViewHolder(@NonNull View v) {
            super(v);
            featuresHeader = v.findViewById(R.id.paywall_features_header);
            featuresList = v.findViewById(R.id.paywall_features_list);
        }
    }

    static class PlanLabelViewHolder extends RecyclerView.ViewHolder {
        PlanLabelViewHolder(@NonNull View v) { super(v); }
    }

    static class PlanCardsViewHolder extends RecyclerView.ViewHolder {
        RecyclerView planRecycler;

        PlanCardsViewHolder(@NonNull View v) {
            super(v);
            planRecycler = v.findViewById(R.id.paywall_plan_recycler);
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.button.MaterialButton subscribeButton;
        com.google.android.material.button.MaterialButton restoreButton;
        TextView cancelNote;

        FooterViewHolder(@NonNull View v) {
            super(v);
            subscribeButton = v.findViewById(R.id.paywall_subscribe_button);
            restoreButton = v.findViewById(R.id.paywall_restore_button);
            cancelNote = v.findViewById(R.id.paywall_cancel_note);
        }
    }
}
