package com.example.hairgo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class HairstyleAdapter extends RecyclerView.Adapter<HairstyleAdapter.ViewHolder> {

    private final int[] imageResIds;
    private int lastAnimatedPosition = -1;

    public HairstyleAdapter(int[] imageResIds) {
        this.imageResIds = imageResIds;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hairstyle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.imageView.setImageResource(imageResIds[position]);

        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.animate()
                    .alpha(1f)
                    .setDuration(5000)
                    .start();
            lastAnimatedPosition = position;
        } else {
            holder.itemView.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() {
        return imageResIds.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivHairstyle);
        }
    }
}
