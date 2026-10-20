package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import com.hairgo.app.R;
import com.hairgo.app.models.Salon;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SalonAdapter extends RecyclerView.Adapter<SalonAdapter.SalonViewHolder> {

    public interface OnSalonClickListener {
        void onSalonClick(Salon salon);
    }

    private final List<Salon> salonList;
    private final OnSalonClickListener listener;
    private Map<String, Bitmap> photos = new HashMap<>();

    public SalonAdapter(List<Salon> salonList, OnSalonClickListener listener) {
        this.salonList = salonList;
        this.listener = listener;
    }

    /**
     * Uploaded salon photos, keyed by salon id. They live outside the Salon
     * model so the list query stays light; the fragment supplies them in one
     * batch. Always rebinds, so recycled rows can never keep a stale photo.
     */
    public void setPhotos(Map<String, Bitmap> photos) {
        this.photos = photos == null ? Collections.emptyMap() : photos;
        notifyDataSetChanged();
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

        // Uploaded photo first, then any local drawables, then the placeholder.
        // Every branch sets the image explicitly so recycled rows cannot show
        // a photo that belongs to a different salon.
        Bitmap photo = photos.get(salon.getSalonId());
        if (photo != null) {
            holder.ivSalonPhoto.setImageBitmap(photo);
        } else if (salon.getImageRes() != 0) {
            holder.ivSalonPhoto.setImageResource(salon.getImageRes());
        } else {
            holder.ivSalonPhoto.setImageResource(R.drawable.ic_salon_placeholder_teal);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSalonClick(salon);
        });
    }

    @Override
    public int getItemCount() {
        return salonList.size();
    }

    static class SalonViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivSalonPhoto;
        TextView tvSalonName, tvSalonLocation, tvSalonRating;

        SalonViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSalonPhoto = itemView.findViewById(R.id.ivSalonPhoto);
            tvSalonName = itemView.findViewById(R.id.tvSalonName);
            tvSalonLocation = itemView.findViewById(R.id.tvSalonLocation);
            tvSalonRating = itemView.findViewById(R.id.tvSalonRating);
        }
    }
}