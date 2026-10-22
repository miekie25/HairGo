package com.hairgo.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.activities.PhotoViewerActivity;

/**
 * The drifting photo strip in the welcome screen's hero. It loops forever: the adapter claims a
 * huge item count and wraps the position back onto the photo list.
 */
public class FeaturedPhotoAdapter extends RecyclerView.Adapter<FeaturedPhotoAdapter.ViewHolder> {

    private final int[] images;

    public FeaturedPhotoAdapter(int[] images) {
        this.images = images;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_welcome_featured, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int index = position % images.length;
        holder.imageView.setImageResource(images[index]);
        holder.imageView.setContentDescription("Hairstyle photo " + (index + 1));

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;
            openViewer(v, adapterPosition % images.length);
        });
    }

    /** Opens the full-screen viewer on this photo, growing out of the thumbnail. */
    private void openViewer(View source, int index) {
        Context context = source.getContext();
        Intent intent = new Intent(context, PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_IMAGES, images);
        intent.putExtra(PhotoViewerActivity.EXTRA_POSITION, index);

        ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(
                source, 0, 0, source.getWidth(), source.getHeight());
        context.startActivity(intent, options.toBundle());
    }

    @Override
    public int getItemCount() {
        return images.length == 0 ? 0 : Integer.MAX_VALUE;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivFeatured);
        }
    }
}