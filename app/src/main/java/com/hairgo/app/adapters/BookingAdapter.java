package com.hairgo.app.adapters;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.Booking;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    public interface OnBookingClickListener {
        void onBookingClick(Booking booking);
    }

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
        Booking booking = bookingList.get(position);

        holder.tvSalonName.setText(booking.getSalonName());
        holder.tvServiceName.setText(booking.getServiceName());

        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault());
        holder.tvDateTime.setText(format.format(booking.getDateTime()));

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

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookingClick(booking);
        });
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        TextView tvSalonName, tvServiceName, tvDateTime, tvStatusBadge;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSalonName = itemView.findViewById(R.id.tvSalonName);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
        }
    }
}