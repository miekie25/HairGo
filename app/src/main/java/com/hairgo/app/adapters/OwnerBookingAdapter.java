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
        void onMessage(OwnerBooking booking);
        /** The card was tapped somewhere other than a button: open the detail view. */
        void onOpen(OwnerBooking booking);
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
        holder.tvClientName.setText(clientName.isEmpty() ? "Unknown client" : clientName);
        holder.tvService.setText(booking.getServiceName());

        if (booking.getDateTime() != null) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy • HH:mm", Locale.getDefault());
            holder.tvDateTime.setText(format.format(booking.getDateTime()));
        } else {
            holder.tvDateTime.setText("");
        }

        bindTotal(holder, booking);

        String status = booking.getStatus();
        // Tell the two kinds of cancellation apart for the owner.
        String label;
        if ("cancelled".equals(status)) {
            label = booking.isCancelledByClient() ? "Client cancelled" : "Declined";
        } else {
            label = capitalize(status);
        }
        holder.tvStatusBadge.setText(label);

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
            case "expired":
                colorRes = R.color.grey_medium;
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

        // Pending: Decline / Confirm.  Confirmed: Mark completed, whenever the
        // owner decides the work is done - the clock never completes it for them.
        // Otherwise no actions.
        boolean isPending = "pending".equals(status);
        boolean canComplete = "confirmed".equals(status);

        // The message action is always available: both sides may still need to
        // talk about a booking after it is confirmed, completed or cancelled.
        holder.btnMessageStandalone.setVisibility(isPending || canComplete ? View.GONE : View.VISIBLE);

        holder.actionsRow.setVisibility(isPending || canComplete ? View.VISIBLE : View.GONE);
        holder.btnDecline.setVisibility(isPending ? View.VISIBLE : View.GONE);
        holder.btnConfirm.setText(canComplete ? "Mark completed" : "Confirm");

        holder.btnMessage.setOnClickListener(v -> {
            if (listener != null) listener.onMessage(booking);
        });
        holder.btnMessageStandalone.setOnClickListener(v -> {
            if (listener != null) listener.onMessage(booking);
        });
        holder.btnDecline.setOnClickListener(v -> {
            if (listener != null) listener.onDecline(booking);
        });
        holder.btnConfirm.setOnClickListener(v -> {
            if (listener == null) return;
            if (canComplete) listener.onComplete(booking);
            else listener.onConfirm(booking);
        });

        // Tapping anywhere else on the card opens the detail view. The buttons
        // above are clickable, so they consume their own touches and never
        // reach this listener.
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(booking);
        });
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    /**
     * One short total line for new bookings. Old bookings have no saved
     * totals, so the line stays hidden and the card looks the same as before.
     */
    private void bindTotal(ViewHolder holder, OwnerBooking booking) {
        Double totalPrice = booking.getTotalPrice();
        int totalDuration = booking.getTotalDurationMinutes();

        if (totalPrice == null && totalDuration <= 0) {
            holder.tvBookingTotal.setVisibility(View.GONE);
            return;
        }

        if (totalPrice == null) {
            holder.tvBookingTotal.setText(holder.itemView.getContext()
                    .getString(R.string.service_total_duration, totalDuration));
        } else {
            String priceText = holder.itemView.getContext()
                    .getString(R.string.service_price_rand, formatPriceValue(totalPrice));
            holder.tvBookingTotal.setText(holder.itemView.getContext()
                    .getString(R.string.service_total, priceText, totalDuration));
        }
        holder.tvBookingTotal.setVisibility(View.VISIBLE);
    }

    /** Whole rands show no decimals; cents always show two. */
    private String formatPriceValue(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvClientName, tvService, tvBookingTotal, tvDateTime, tvStatusBadge;
        LinearLayout actionsRow;
        Button btnConfirm, btnDecline, btnMessage, btnMessageStandalone;

        ViewHolder(View itemView) {
            super(itemView);
            tvClientName = itemView.findViewById(R.id.tvClientName);
            tvService = itemView.findViewById(R.id.tvService);
            tvBookingTotal = itemView.findViewById(R.id.tvBookingTotal);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            actionsRow = itemView.findViewById(R.id.layoutBookingActions);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnDecline = itemView.findViewById(R.id.btnDecline);
            btnMessage = itemView.findViewById(R.id.btnMessage);
            btnMessageStandalone = itemView.findViewById(R.id.btnMessageStandalone);
        }
    }
}