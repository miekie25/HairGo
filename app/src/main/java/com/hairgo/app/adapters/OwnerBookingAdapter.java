package com.hairgo.app.adapters;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.OwnerBooking;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class OwnerBookingAdapter extends RecyclerView.Adapter<OwnerBookingAdapter.ViewHolder> {

    public interface OnBookingActionListener {
        void onConfirm(OwnerBooking booking);
        void onDecline(OwnerBooking booking);
        void onComplete(OwnerBooking booking);
    }

    private final List<OwnerBooking> bookings;
    private final OnBookingActionListener listener;

    public OwnerBookingAdapter(List<OwnerBooking> bookings, OnBookingActionListener listener) {
        this.bookings = bookings;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.owner_item_booking, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OwnerBooking booking = bookings.get(position);

        String clientName = booking.getClientName();
        holder.tvClientName.setText(clientName.isEmpty() ? "Client" : clientName);
        holder.tvService.setText(booking.getServiceName());

        if (booking.getDateTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy • HH:mm", Locale.getDefault());
            holder.tvDateTime.setText(format.format(booking.getDateTime()));
        } else {
            holder.tvDateTime.setText("");
        }

        String status = booking.getStatus();
        holder.tvStatusBadge.setText(capitalize(status));

        int colorRes;
        switch (status) {
            case "confirmed":
                colorRes = R.color.teal;
                break;
            case "completed":
                colorRes = R.color.success;
                break;
            case "cancelled":
                colorRes = R.color.error;
                break;
            case "pending":
            default:
                colorRes = R.color.warning;
                break;
        }
        Drawable background = holder.tvStatusBadge.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate())
                    .setColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));
        }

        // Pending: Decline / Confirm.  Confirmed and already past: Mark completed.  Otherwise no actions.
        boolean isPending = "pending".equals(status);
        boolean canComplete = "confirmed".equals(status) && booking.isPast();

        holder.actionsRow.setVisibility(isPending || canComplete ? View.VISIBLE : View.GONE);
        holder.btnDecline.setVisibility(isPending ? View.VISIBLE : View.GONE);
        holder.btnConfirm.setText(canComplete ? "Mark completed" : "Confirm");

        holder.btnDecline.setOnClickListener(v -> {
            if (listener != null) listener.onDecline(booking);
        });
        holder.btnConfirm.setOnClickListener(v -> {
            if (listener == null) return;
            if (canComplete) listener.onComplete(booking);
            else listener.onConfirm(booking);
        });
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvClientName, tvService, tvDateTime, tvStatusBadge;
        LinearLayout actionsRow;
        Button btnConfirm, btnDecline;

        ViewHolder(View itemView) {
            super(itemView);
            tvClientName = itemView.findViewById(R.id.tvClientName);
            tvService = itemView.findViewById(R.id.tvService);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            actionsRow = itemView.findViewById(R.id.layoutBookingActions);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnDecline = itemView.findViewById(R.id.btnDecline);
        }
    }
}