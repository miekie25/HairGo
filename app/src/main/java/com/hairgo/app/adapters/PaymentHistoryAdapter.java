package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.PaymentRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PaymentHistoryAdapter extends RecyclerView.Adapter<PaymentHistoryAdapter.ViewHolder> {

    private final List<PaymentRecord> items = new ArrayList<>();

    public void setPayments(List<PaymentRecord> payments) {
        items.clear();
        if (payments != null) {
            items.addAll(payments);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaymentRecord record = items.get(position);
        holder.tvAmount.setText(formatAmount(record.getPaymentAmount()));
        holder.tvMethod.setText(methodLabel(record));
        if (record.getPaymentTimestampAsDate() == null) {
            holder.tvDate.setText("");
            holder.tvDate.setVisibility(View.GONE);
        } else {
            holder.tvDate.setText(new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                    .format(record.getPaymentTimestampAsDate()));
            holder.tvDate.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatAmount(double amount) {
        return "R" + String.format(Locale.getDefault(), "%.2f", amount);
    }

    private String methodLabel(PaymentRecord record) {
        String method = record.getPaymentMethod();
        String type = record.getPaymentType();
        if ("card".equals(method) || "deposit".equals(type) || "full".equals(type)) {
            if ("deposit".equals(type)) return "Card deposit";
            if ("full".equals(type)) return "Full card payment";
            return "Card payment";
        }
        if ("cash".equals(method)) {
            return "Cash payment";
        }
        return method.isEmpty() ? "Payment" : method;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAmount, tvMethod, tvDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAmount = itemView.findViewById(R.id.tvPaymentAmount);
            tvMethod = itemView.findViewById(R.id.tvPaymentMethod);
            tvDate = itemView.findViewById(R.id.tvPaymentDate);
        }
    }
}