package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.Booking;
import com.hairgo.app.utils.BookingSections;

import java.util.ArrayList;
import java.util.List;

public class BookingSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_BOOKING = 1;

    public interface OnBookingClickListener {
        void onBookingClick(Booking booking);
    }

    private final OnBookingClickListener listener;
    private final List<BookingSections.BookingSection> sections;
    private final List<AdapterItem> items = new ArrayList<>();
    private final int[] sectionCounts = new int[2];

    private static class AdapterItem {
        final int viewType;
        final Booking booking;
        final int sectionType;

        AdapterItem(int viewType, Booking booking, int sectionType) {
            this.viewType = viewType;
            this.booking = booking;
            this.sectionType = sectionType;
        }
    }

    public BookingSectionAdapter(List<BookingSections.BookingSection> sections,
                                 OnBookingClickListener listener) {
        this.sections = sections;
        this.listener = listener;
        buildItems();
    }

    private void buildItems() {
        items.clear();
        sectionCounts[BookingSections.TYPE_UPCOMING] = 0;
        sectionCounts[BookingSections.TYPE_PAST] = 0;
        if (sections == null) return;

        for (BookingSections.BookingSection section : sections) {
            if (section == null || section.isEmpty()) continue;
            sectionCounts[section.getType()] = section.getCount();
            items.add(new AdapterItem(VIEW_TYPE_HEADER, null, section.getType()));
            for (Booking booking : section.getBookings()) {
                if (booking != null) {
                    items.add(new AdapterItem(VIEW_TYPE_BOOKING, booking, section.getType()));
                }
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_section_header, parent, false);
            return new SectionHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_booking, parent, false);
            return new BookingAdapter.BookingViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AdapterItem item = items.get(position);
        if (item.viewType == VIEW_TYPE_HEADER) {
            SectionHeaderViewHolder headerHolder = (SectionHeaderViewHolder) holder;
            if (item.sectionType == BookingSections.TYPE_UPCOMING) {
                headerHolder.tvTitle.setText(R.string.section_upcoming);
            } else {
                headerHolder.tvTitle.setText(R.string.section_past);
            }
            headerHolder.tvCount.setText("(" + sectionCounts[item.sectionType] + ")");
        } else if (item.booking != null && holder instanceof BookingAdapter.BookingViewHolder) {
            BookingAdapter.bindBooking((BookingAdapter.BookingViewHolder) holder,
                    item.booking,
                    booking -> {
                        if (listener != null) listener.onBookingClick(booking);
                    });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateSections(List<BookingSections.BookingSection> newSections) {
        sections.clear();
        if (newSections != null) {
            sections.addAll(newSections);
        }
        buildItems();
        notifyDataSetChanged();
    }

    static class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCount;

        SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSectionTitle);
            tvCount = itemView.findViewById(R.id.tvSectionCount);
        }
    }
}