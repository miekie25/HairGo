package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.Salon;

import java.util.List;
import java.util.Locale;

public class SalonAdapter extends RecyclerView.Adapter<SalonAdapter.SalonViewHolder> {

    public interface OnSalonClickListener {
        void onSalonClick(Salon salon);
    }

    private final List<Salon> salonList;
    private final OnSalonClickListener listener;

    public SalonAdapter(List<Salon> salonList, OnSalonClickListener listener) {
        this.salonList = salonList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SalonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_salon, parent, false);
        return new SalonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SalonViewHolder holder, int position) {
        Salon salon = salonList.get(position);
        holder.tvSalonName.setText(salon.getName());
        holder.tvSalonLocation.setText(salon.getLocation());
        holder.tvSalonRating.setText(String.format(Locale.getDefault(), "%.1f", salon.getAvgRating()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSalonClick(salon);
        });
    }

    @Override
    public int getItemCount() {
        return salonList.size();
    }

    static class SalonViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSalonIcon;
        TextView tvSalonName, tvSalonLocation, tvSalonRating;

        SalonViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSalonIcon = itemView.findViewById(R.id.ivSalonIcon);
            tvSalonName = itemView.findViewById(R.id.tvSalonName);
            tvSalonLocation = itemView.findViewById(R.id.tvSalonLocation);
            tvSalonRating = itemView.findViewById(R.id.tvSalonRating);
        }
    }
}