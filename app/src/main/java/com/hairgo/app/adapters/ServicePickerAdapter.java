package com.hairgo.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.hairgo.app.R;
import com.hairgo.app.models.ServiceDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServicePickerAdapter extends RecyclerView.Adapter<ServicePickerAdapter.ServiceViewHolder> {

    private final List<ServiceDetail> services;
    private final List<Boolean> checkedStates;
    private final OnServiceCheckChangeListener listener;

    public interface OnServiceCheckChangeListener {
        void onServiceCheckChanged(int position, boolean isChecked);
    }

    public ServicePickerAdapter(List<ServiceDetail> services, List<Boolean> checkedStates, OnServiceCheckChangeListener listener) {
        this.services = services;
        this.checkedStates = checkedStates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service_picker, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        ServiceDetail service = services.get(position);
        boolean isChecked = checkedStates.get(position);

        holder.tvServiceName.setText(service.getName());
        holder.cbServiceSelected.setChecked(isChecked);

        // Price chip
        if (service.hasPrice()) {
            Context context = holder.itemView.getContext();
            String priceText = context.getString(R.string.service_price_rand, formatPrice(service.getPrice()));
            holder.tvServicePrice.setText(priceText);
            holder.tvServicePrice.setVisibility(View.VISIBLE);
        } else {
            holder.tvServicePrice.setVisibility(View.GONE);
        }

        // Duration chip
        Context context = holder.itemView.getContext();
        String durationText = context.getString(R.string.service_duration_minutes, service.getDurationMinutes());
        holder.tvServiceDuration.setText(durationText);

        // Show/hide details row
        holder.llServiceDetails.setVisibility(service.hasPrice() ? View.VISIBLE : View.GONE);

        // Handle card click for checkbox toggle
        holder.itemView.setOnClickListener(v -> {
            boolean newChecked = !checkedStates.get(position);
            checkedStates.set(position, newChecked);
            holder.cbServiceSelected.setChecked(newChecked);
            if (listener != null) {
                listener.onServiceCheckChanged(position, newChecked);
            }
        });

        // Checkbox click also toggles
        holder.cbServiceSelected.setOnClickListener(v -> {
            boolean newChecked = !checkedStates.get(position);
            checkedStates.set(position, newChecked);
            holder.cbServiceSelected.setChecked(newChecked);
            if (listener != null) {
                listener.onServiceCheckChanged(position, newChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    private String formatPrice(Double price) {
        if (price == null) return "";
        if (price == Math.rint(price)) {
            return String.valueOf((long) (double) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    static class ServiceViewHolder extends RecyclerView.ViewHolder {
        MaterialCheckBox cbServiceSelected;
        TextView tvServiceName;
        TextView tvServicePrice;
        TextView tvServiceDuration;
        LinearLayout llServiceDetails;

        ServiceViewHolder(@NonNull View itemView) {
            super(itemView);
            cbServiceSelected = itemView.findViewById(R.id.cbServiceSelected);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvServicePrice = itemView.findViewById(R.id.tvServicePrice);
            tvServiceDuration = itemView.findViewById(R.id.tvServiceDuration);
            llServiceDetails = itemView.findViewById(R.id.llServiceDetails);
        }
    }
}