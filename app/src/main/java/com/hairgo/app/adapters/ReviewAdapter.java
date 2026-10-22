package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.utils.ReviewLoader;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    private final List<ReviewLoader.Item> reviews = new ArrayList<>();
    private final boolean preview;

    /** Preview cards cut long comments short; the full list shows everything. */
    public ReviewAdapter(boolean preview) {
        this.preview = preview;
    }

    public void setReviews(List<ReviewLoader.Item> items) {
        reviews.clear();
        if (items != null) {
            reviews.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        ReviewLoader.Item review = reviews.get(position);
        holder.ratingBar.setVisibility(review.hasRating() ? View.VISIBLE : View.GONE);
        holder.ratingBar.setRating(review.getRating());

        if (review.getReviewerName().isEmpty()) {
            holder.tvName.setText(R.string.reviewer_client);
        } else {
            holder.tvName.setText(review.getReviewerName());
        }

        if (review.getComment().isEmpty()) {
            holder.tvComment.setText(R.string.review_no_comment);
        } else {
            holder.tvComment.setText(review.getComment());
        }
        holder.tvComment.setMaxLines(preview ? 3 : Integer.MAX_VALUE);

        if (review.getCreatedAt() == null) {
            holder.tvDate.setText("");
            holder.tvDate.setVisibility(View.GONE);
        } else {
            holder.tvDate.setText(DATE_FORMAT.format(
                    review.getCreatedAt().toInstant().atZone(ZoneId.systemDefault())));
            holder.tvDate.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvDate, tvComment;
        final RatingBar ratingBar;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvComment = itemView.findViewById(R.id.tvComment);
            ratingBar = itemView.findViewById(R.id.ratingBar);
        }
    }
}
