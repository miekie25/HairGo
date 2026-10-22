package com.hairgo.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.ServiceDetail;

import java.util.List;
import java.util.Locale;

public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {

    private final List<ServiceDetail> services;

    public ServiceAdapter(List<ServiceDetail> services) {
        this.services = services;
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        holder.tvServiceName.setText(serviceLine(services.get(position), context));
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    // Builds one line, for example "Haircut - R150 - 45min".
    private String serviceLine(ServiceDetail service, Context context) {
        String price = service.hasPrice()
                ? context.getString(R.string.service_price_rand, formatPrice(service.getPrice()))
                : context.getString(R.string.service_price_not_set);
        String duration = context.getString(R.string.service_duration_minutes,
                service.getDurationMinutes());
        return context.getString(R.string.service_chip_text, service.getName(), price, duration);
    }

    // Whole numbers show no cents. Cents always show two digits, like 150.50.
    private String formatPrice(Double price) {
        if (price == null) {
            return "";
        }
        if (price == Math.rint(price)) {
            return String.valueOf((long) (double) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    static class ServiceViewHolder extends RecyclerView.ViewHolder {
        TextView tvServiceName;

        ServiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
        }
    }
}
