package com.euptron.billingcompat.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.euptron.billingcompat.ui.R;
import com.euptron.billingcompat.ui.model.FeatureItemUiModel;
import java.util.List;

/**
 * Adapter for the feature rows inside the "What you get" card.
 * Each row renders a checkmark icon + title + optional subtitle
 * (defined in {@code item_paywall_feature_row.xml}).
 */
public class FeatureRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<FeatureItemUiModel> items;

    public FeatureRowAdapter(List<FeatureItemUiModel> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paywall_feature_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        ViewHolder holder = (ViewHolder) viewHolder;
        FeatureItemUiModel item = items.get(position);

        holder.featureText.setText(item.getText());

        if (item.hasSubtitle()) {
            holder.featureSubtitle.setVisibility(View.VISIBLE);
            holder.featureSubtitle.setText(item.getSubtitle());
        } else {
            holder.featureSubtitle.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView featureText;
        TextView featureSubtitle;

        ViewHolder(@NonNull View v) {
            super(v);
            featureText = v.findViewById(R.id.feature_text);
            featureSubtitle = v.findViewById(R.id.feature_subtitle);
        }
    }
}
