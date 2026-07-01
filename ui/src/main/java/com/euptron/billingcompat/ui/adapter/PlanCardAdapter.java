package com.euptron.billingcompat.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.euptron.billingcompat.ui.R;
import com.euptron.billingcompat.ui.model.SubscriptionPlanUiModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import java.util.List;

/**
 * Adapter for the plan selection cards.
 *
 * <p><b>Selected card:</b> filled {@code paywall_primary} background, all text in {@code
 * paywall_on_primary}.
 *
 * <p><b>Unselected card:</b> {@code paywall_surface} background, text in {@code
 * paywall_on_surface}.
 *
 * <p>The "Most Popular" badge is shown when {@link SubscriptionPlanUiModel#isRecommended()} is
 * {@code true}. The discount badge chip is shown when {@link
 * SubscriptionPlanUiModel#getDiscountBadge()} is non-null.
 */
public class PlanCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  public interface OnPlanSelectedListener {
    void onPlanSelected(int index);
  }

  private final List<SubscriptionPlanUiModel> plans;
  private int selectedIndex;
  private final OnPlanSelectedListener listener;

  public PlanCardAdapter(
      List<SubscriptionPlanUiModel> plans, int selectedIndex, OnPlanSelectedListener listener) {
    this.plans = plans;
    this.selectedIndex = selectedIndex;
    this.listener = listener;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_paywall_plan_card, parent, false);
    return new ViewHolder(v);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
    PlanCardAdapter.ViewHolder h = (PlanCardAdapter.ViewHolder) viewHolder;

    SubscriptionPlanUiModel plan = plans.get(position);
    boolean isSelected = position == selectedIndex;

    h.planLabel.setText(plan.getLabel());
    h.planPrice.setText(plan.getPrice());
    h.planPeriod.setText(plan.getPeriod());

    int bgColor =
        isSelected
            ? ContextCompat.getColor(h.itemView.getContext(), R.color.paywall_primary)
            : ContextCompat.getColor(h.itemView.getContext(), R.color.paywall_surface);
    int textColor =
        isSelected
            ? ContextCompat.getColor(h.itemView.getContext(), R.color.paywall_on_primary)
            : ContextCompat.getColor(h.itemView.getContext(), R.color.paywall_on_surface);

    h.card.setCardBackgroundColor(bgColor);
    h.planLabel.setTextColor(textColor);
    h.planPrice.setTextColor(textColor);
    h.planPeriod.setTextColor(textColor);

    // Stroke: only on selected card
    h.card.setStrokeWidth(
        isSelected
            ? h.itemView.getResources().getDimensionPixelSize(R.dimen.paywall_card_stroke_width)
            : 0);
    if (isSelected) {
      h.card.setStrokeColor(
          ContextCompat.getColor(h.itemView.getContext(), R.color.paywall_primary));
    }

    if (plan.getDiscountBadge() != null) {
      h.discountChip.setVisibility(View.VISIBLE);
      h.discountChip.setText(plan.getDiscountBadge());
    } else {
      h.discountChip.setVisibility(View.GONE);
    }

    if (plan.isRecommended()) {
      h.recommendedChip.setVisibility(View.VISIBLE);
    } else {
      h.recommendedChip.setVisibility(View.GONE);
    }

    h.card.setOnClickListener(
        v -> {
          int prev = selectedIndex;
          selectedIndex = h.getAbsoluteAdapterPosition();
          notifyItemChanged(prev);
          notifyItemChanged(selectedIndex);
          if (listener != null) {
            listener.onPlanSelected(selectedIndex);
          }
        });
  }

  @Override
  public int getItemCount() {
    return plans.size();
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    MaterialCardView card;
    TextView planLabel;
    TextView planPrice;
    TextView planPeriod;
    Chip discountChip;
    Chip recommendedChip;

    ViewHolder(@NonNull View v) {
      super(v);
      card = v.findViewById(R.id.plan_card);
      planLabel = v.findViewById(R.id.plan_label);
      planPrice = v.findViewById(R.id.plan_price);
      planPeriod = v.findViewById(R.id.plan_period);
      discountChip = v.findViewById(R.id.plan_discount_chip);
      recommendedChip = v.findViewById(R.id.plan_recommended_chip);
    }
  }
}
