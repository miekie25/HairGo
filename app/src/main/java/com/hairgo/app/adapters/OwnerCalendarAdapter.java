package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.models.PaymentDetails;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Compact appointment cards used by the salon owner's calendar day view. */
public class OwnerCalendarAdapter extends RecyclerView.Adapter<OwnerCalendarAdapter.ViewHolder> {

    private final List<OwnerBooking> bookings;

    public OwnerCalendarAdapter(List<OwnerBooking> bookings) {
        this.bookings = bookings;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.owner_item_calendar_booking, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OwnerBooking booking = bookings.get(position);

        if (booking.getDateTime() != null) {
            holder.tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(booking.getDateTime()));
        } else {
            holder.tvTime.setText("--:--");
        }

        String name = booking.getClientName();
        holder.tvClient.setText(name == null || name.trim().isEmpty()
                ? "Unknown client" : name);

        String service = booking.getServiceName();
        holder.tvService.setText(service == null || service.trim().isEmpty()
                ? "Service not specified" : service);

        String status = booking.getStatus() == null ? "pending" : booking.getStatus().toLowerCase(Locale.ROOT);
        holder.tvStatus.setText(capitalize(status));
        switch (status) {
            case "completed":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_booking_status_completed);
                holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.booking_status_completed_text));
                break;
            case "cancelled":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_booking_status_cancelled);
                holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.booking_status_cancelled_text));
                break;
            case "expired":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_booking_status_expired);
                holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.booking_status_expired_text));
                break;
            case "pending":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_booking_status_pending);
                holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.booking_status_pending_text));
                break;
            case "confirmed":
            default:
                holder.tvStatus.setBackgroundResource(R.drawable.bg_booking_status_confirmed);
                holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.booking_status_confirmed_text));
                break;
        }
        holder.tvPayment.setText(paymentSummary(booking));
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    private String paymentSummary(OwnerBooking booking) {
        PaymentDetails payment = booking.getPaymentDetails();
        Double total = booking.getTotalPrice();

        if (payment == null) {
            return total == null ? "Payment: Not recorded"
                    : "Total: " + money(total) + " • Payment: Not recorded";
        }

        Double paid = payment.getAmountPaid();
        Double outstanding = payment.getAmountOutstanding();
        String type = payment.getType();
        String status = payment.getStatus();

        if (PaymentDetails.TYPE_CASH.equals(type)) {
            return outstanding != null && outstanding > 0
                    ? "Cash at salon • " + money(outstanding) + " due"
                    : "Cash at salon • Paid";
        }

        if (PaymentDetails.STATUS_PAID.equals(status)
                || (outstanding != null && outstanding == 0)) {
            return "Paid in full" + (paid == null ? "" : " • " + money(paid));
        }

        StringBuilder result = new StringBuilder();
        if (PaymentDetails.TYPE_DEPOSIT.equals(type)) {
            result.append("Deposit");
        } else {
            result.append("Card payment");
        }
        if (paid != null) result.append(" • ").append(money(paid)).append(" paid");
        if (outstanding != null && outstanding > 0) {
            result.append(" • ").append(money(outstanding)).append(" due");
        }
        return result.toString();
    }

    private String money(double amount) {
        if (amount == Math.rint(amount)) {
            return "R" + (long) amount;
        }
        return String.format(Locale.US, "R%.2f", amount);
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "Pending";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvClient, tvService, tvPayment, tvStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvCalendarBookingTime);
            tvClient = itemView.findViewById(R.id.tvCalendarClientName);
            tvService = itemView.findViewById(R.id.tvCalendarService);
            tvPayment = itemView.findViewById(R.id.tvCalendarPayment);
            tvStatus = itemView.findViewById(R.id.tvCalendarStatus);
        }
    }
}