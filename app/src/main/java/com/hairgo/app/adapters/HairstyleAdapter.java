package com.hairgo.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.activities.PhotoViewerActivity;

public class HairstyleAdapter extends RecyclerView.Adapter<HairstyleAdapter.ViewHolder> {


    private static final long FADE_IN_MS = 5000;

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

        // A tap ripple, so the photos visibly react to being touched.
        TypedValue ripple = new TypedValue();
        if (parent.getContext().getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            view.setForeground(ContextCompat.getDrawable(parent.getContext(), ripple.resourceId));
        }
        view.setClickable(true);
        view.setFocusable(true);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.imageView.setImageResource(imageResIds[position]);
        holder.imageView.setContentDescription("Hairstyle photo " + (position + 1));

        // Fade each photo in the first time it scrolls into view.
        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.animate()
                    .alpha(1f)
                    .setDuration(FADE_IN_MS)
                    .start();
            lastAnimatedPosition = position;
        } else {
            holder.itemView.setAlpha(1f);
        }

        holder.itemView.setOnClickListener(v ->
                openViewer(v, holder.getBindingAdapterPosition()));
    }

    /** Opens the full-screen viewer on the tapped photo, growing out of the thumbnail. */
    private void openViewer(View source, int position) {
        if (position == RecyclerView.NO_POSITION) return;

        Context context = source.getContext();
        Intent intent = new Intent(context, PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_IMAGES, imageResIds);
        intent.putExtra(PhotoViewerActivity.EXTRA_POSITION, position);

        ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(
                source, 0, 0, source.getWidth(), source.getHeight());
        context.startActivity(intent, options.toBundle());
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