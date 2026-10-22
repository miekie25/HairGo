package com.hairgo.app.adapters;

import android.graphics.Bitmap;
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
import com.hairgo.app.utils.BookingSections;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public interface OnBookingClickListener {
        void onBookingClick(Booking booking);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    private final List<Booking> bookingList;
    private final OnBookingClickListener listener;
    private Map<String, Bitmap> photos;

    public BookingAdapter(List<Booking> bookingList, OnBookingClickListener listener) {
        this.bookingList = bookingList;
        this.listener = listener;
    }

    public void setPhotos(Map<String, Bitmap> photos) {
        this.photos = photos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        bindBooking(holder, bookingList.get(position), listener, photos);
    }

    /**
     * Binds a single booking row. Exposed so BookingSectionAdapter can reuse the
     * same rendering for its booking view type instead of duplicating it.
     */
    public static void bindBooking(@NonNull BookingViewHolder holder,
                                   @NonNull Booking booking,
                                   OnBookingClickListener listener,
                                   Map<String, Bitmap> photos) {
        holder.tvSalonName.setText(booking.getSalonName());
        holder.tvServiceName.setText(booking.getServiceLabel());
        holder.tvDateTime.setText(formatRelativeDate(booking.getDateTime()));
        bindTotal(holder, booking);
        bindSalonPhoto(holder, booking, photos);

        String status = BookingSections.effectiveStatus(booking);
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
            case "expired":
                pillRes = R.drawable.bg_pill_expired;
                textColorRes = R.color.grey_medium;
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

    /**
     * One short total line for new bookings. Old bookings have no saved
     * totals, so the line stays hidden and the card looks the same as before.
     */
    private static void bindTotal(BookingAdapter.BookingViewHolder holder, Booking booking) {
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
    private static String formatPriceValue(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    private static void bindSalonPhoto(BookingAdapter.BookingViewHolder holder, Booking booking, Map<String, Bitmap> photos) {
        if (photos != null) {
            Bitmap photo = photos.get(booking.getSalonId());
            if (photo != null) {
                holder.ivSalonPhoto.setImageBitmap(photo);
                return;
            }
        }
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
        TextView tvSalonName, tvServiceName, tvBookingTotal, tvDateTime, tvStatusBadge;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSalonPhoto = itemView.findViewById(R.id.ivSalonPhoto);
            tvSalonName = itemView.findViewById(R.id.tvSalonName);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvBookingTotal = itemView.findViewById(R.id.tvBookingTotal);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
        }
    }
}