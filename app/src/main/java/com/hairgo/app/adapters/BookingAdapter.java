package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.hairgo.app.R;
import com.hairgo.app.models.Booking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public interface OnBookingClickListener {
        void onBookingClick(Booking booking);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    private final List<Booking> bookingList;
    private final OnBookingClickListener listener;

    public BookingAdapter(List<Booking> bookingList, OnBookingClickListener listener) {
        this.bookingList = bookingList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        bindBooking(holder, bookingList.get(position), listener);
    }

    /**
     * Binds a single booking row. Exposed so BookingSectionAdapter can reuse the
     * same rendering for its booking view type instead of duplicating it.
     */
    public static void bindBooking(@NonNull BookingViewHolder holder,
                                   @NonNull Booking booking,
                                   OnBookingClickListener listener) {
        holder.tvSalonName.setText(booking.getSalonName());
        holder.tvServiceName.setText(booking.getServiceName());
        holder.tvDateTime.setText(formatRelativeDate(booking.getDateTime()));
        bindSalonPhoto(holder, booking);

        String status = booking.getStatus();
        holder.tvStatusBadge.setText(status == null ? "" : status);

        int pillRes;
        int textColorRes;
        switch (status == null ? "" : status) {
            case "confirmed":
                pillRes = R.drawable.bg_pill_confirmed;
                textColorRes = R.color.teal_dark;
                break;
            case "completed":
                pillRes = R.drawable.bg_pill_completed;
                textColorRes = R.color.success_dark;
                break;
            case "cancelled":
                pillRes = R.drawable.bg_pill_cancelled;
                textColorRes = R.color.error_dark;
                break;
            case "pending":
            default:
                pillRes = R.drawable.bg_pill_pending;
                textColorRes = R.color.warning_dark;
                break;
        }

        holder.tvStatusBadge.setBackgroundResource(pillRes);
        holder.tvStatusBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), textColorRes));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookingClick(booking);
        });
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    private static void bindSalonPhoto(BookingAdapter.BookingViewHolder holder, Booking booking) {
        int imageRes = booking.getSalonImageRes();
        holder.ivSalonPhoto.setImageResource(
                imageRes != 0 ? imageRes : R.drawable.ic_salon_placeholder_teal);
    }

    private static String formatRelativeDate(Date dateTime) {
        if (dateTime == null) return "";

        LocalDate bookingDate = dateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        long daysAway = java.time.temporal.ChronoUnit.DAYS.between(today, bookingDate);

        String time = TIME_FORMAT.format(dateTime.toInstant().atZone(ZoneId.systemDefault()));
        String dayLabel;
        if (daysAway == 0) {
            dayLabel = "Today";
        } else if (daysAway == 1) {
            dayLabel = "Tomorrow";
        } else if (daysAway == -1) {
            dayLabel = "Yesterday";
        } else {
            dayLabel = DATE_FORMAT.format(dateTime.toInstant().atZone(ZoneId.systemDefault()));
        }
        return dayLabel + " • " + time;
    }

    public static class BookingViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivSalonPhoto;
        TextView tvSalonName, tvServiceName, tvDateTime, tvStatusBadge;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSalonPhoto = itemView.findViewById(R.id.ivSalonPhoto);
            tvSalonName = itemView.findViewById(R.id.tvSalonName);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
        }
    }
}