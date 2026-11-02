package com.hairgo.app.adapters;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hairgo.app.utils.ImageCompressor;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.hairgo.app.R;
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.ServiceImages;

import java.util.List;
import java.util.Locale;

public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {

    private final List<ServiceDetail> services;
    private final String salonId;

    public ServiceAdapter(String salonId, List<ServiceDetail> services) {
        this.salonId = salonId;
        this.services = services;
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        ServiceDetail service = services.get(position);
        Context context = holder.itemView.getContext();

        holder.tvServiceName.setText(service.getName());

        // Price chip
        String priceText;
        if (service.hasPrice()) {
            priceText = context.getString(R.string.service_price_rand, formatPrice(service.getPrice()));
        } else {
            priceText = context.getString(R.string.service_price_not_set);
        }
        holder.tvServicePrice.setText(priceText);

        // Duration chip
        String durationText = context.getString(R.string.service_duration_minutes, service.getDurationMinutes());
        holder.tvServiceDuration.setText(durationText);

        bindServiceImage(holder, service);
    }

    private void bindServiceImage(ServiceViewHolder holder, ServiceDetail service) {
        String imageId = service.getImageId();
        long imageVersion = service.getImageVersion();
        if (salonId == null || imageId == null) {
            holder.clearServiceImage();
            holder.ivServiceImage.setOnClickListener(null);
            return;
        }

        String requestKey = salonId + "/" + imageId + "@" + imageVersion;
        holder.ivServiceImage.setTag(R.id.ivServiceImage, requestKey);
        holder.showServiceImagePlaceholder();

        ServiceImages.load(salonId, imageId, imageVersion, bitmap -> {
            if (!requestKey.equals(holder.ivServiceImage.getTag(R.id.ivServiceImage))) {
                return;
            }
            if (bitmap == null) {
                holder.clearServiceImage();
            } else {
                holder.showServiceImage(bitmap);
            }
        });

        holder.ivServiceImage.setOnClickListener(v -> {
            if (!requestKey.equals(holder.ivServiceImage.getTag(R.id.ivServiceImage))) {
                return;
            }
            openImagePreview(holder.ivServiceImage.getContext(), salonId, imageId, imageVersion);
        });
    }

    private void openImagePreview(Context context, String salonId, String imageId,
                                  long imageVersion) {
        Dialog dialog = new Dialog(context);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.parseColor("#80000000"));

        int pad = dp(context, 24);
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int maxSize = Math.max(120, Math.min(metrics.widthPixels, metrics.heightPixels) - dp(context, 48));

        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageParams.gravity = Gravity.CENTER;
        imageParams.setMargins(pad, pad, pad, pad);

        ImageView imageView = new ImageView(context);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setMaxWidth(maxSize);
        imageView.setMaxHeight(maxSize);
        imageView.setVisibility(View.GONE);
        imageView.setOnClickListener(v -> { });
        root.addView(imageView, imageParams);

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        ProgressBar progress = new ProgressBar(context);
        root.addView(progress, progressParams);

        ImageButton closeButton = new ImageButton(context);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, pad / 2, pad / 2, 0);
        root.addView(closeButton, closeParams);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.25f);
        }
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        final boolean[] dismissed = {false};
        dialog.setOnDismissListener(d -> dismissed[0] = true);
        closeButton.setOnClickListener(v -> dialog.dismiss());
        root.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        ServiceImages.load(salonId, imageId, imageVersion,
                ImageCompressor.MAX_DIMENSION, bitmap -> {
                    if (dismissed[0] || !dialog.isShowing()) {
                        return;
                    }
                    progress.setVisibility(View.GONE);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        imageView.setVisibility(View.VISIBLE);
                    } else {
                        dialog.dismiss();
                    }
                });
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    // Whole numbers show no cents. Cents always show two digits, like 150.50.
    private String formatPrice(Double price) {
        if (price == null) {
            return "";
        }
        if (price == Math.rint(price)) {
            return String.valueOf((long) (double) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    static class ServiceViewHolder extends RecyclerView.ViewHolder {
        TextView tvServiceName;
        TextView tvServicePrice;
        TextView tvServiceDuration;
        ImageView ivServiceIcon;
        ShapeableImageView ivServiceImage;

        ServiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvServicePrice = itemView.findViewById(R.id.tvServicePrice);
            tvServiceDuration = itemView.findViewById(R.id.tvServiceDuration);
            ivServiceIcon = itemView.findViewById(R.id.ivServiceIcon);
            ivServiceImage = itemView.findViewById(R.id.ivServiceImage);
        }

        void showServiceImagePlaceholder() {
            ivServiceImage.setVisibility(View.VISIBLE);
            ivServiceImage.setBackgroundResource(R.drawable.bg_thumb_placeholder);
            int padding = dp(14);
            ivServiceImage.setPadding(padding, padding, padding, padding);
            ivServiceImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            ivServiceImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        void showServiceImage(Bitmap bitmap) {
            ivServiceImage.setVisibility(View.VISIBLE);
            ivServiceImage.setPadding(0, 0, 0, 0);
            ivServiceImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ivServiceImage.setImageBitmap(bitmap);
        }

        void clearServiceImage() {
            ivServiceImage.setTag(R.id.ivServiceImage, null);
            ivServiceImage.setOnClickListener(null);
            ivServiceImage.setImageBitmap(null);
            ivServiceImage.setVisibility(View.GONE);
        }

        private int dp(int value) {
            return Math.round(value * itemView.getResources().getDisplayMetrics().density);
        }
    }
}