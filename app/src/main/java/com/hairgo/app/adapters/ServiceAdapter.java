package com.hairgo.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.hairgo.app.R;
import com.hairgo.app.activities.BookAppointmentActivity;
import com.hairgo.app.activities.ServicePhotoViewerActivity;
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.ServiceImages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The salon page's services: one big card per service, with its photo on top, the price and
 * duration under it, and a Book button. Tapping the photo opens a full-screen gallery.
 */
public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {

    /**
     * How sharp the card photos are decoded. Every card on the page loads at once, so this is
     * sharp enough for a full-width card without making a long list heavy on memory.
     */
    private static final int CARD_IMAGE_DIMENSION = 800;

    private final List<ServiceDetail> services;
    private final String salonId;

    public ServiceAdapter(String salonId, List<ServiceDetail> services) {
        this.salonId = salonId;
        this.services = services;
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_service_card, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        ServiceDetail service = services.get(position);
        Context context = holder.itemView.getContext();

        holder.tvName.setText(service.getName());

        holder.tvPrice.setText(service.hasPrice()
                ? context.getString(R.string.service_price_rand, formatPrice(service.getPrice()))
                : context.getString(R.string.service_price_not_set));

        holder.tvDuration.setText(context.getString(
                R.string.service_duration_minutes, service.getDurationMinutes()));

        holder.btnBook.setOnClickListener(v -> openBooking(v.getContext(), service.getName()));

        bindImage(holder, service);
    }

    private void bindImage(ServiceViewHolder holder, ServiceDetail service) {
        String imageId = service.getImageId();
        long imageVersion = service.getImageVersion();
        if (salonId == null || imageId == null) {
            holder.hideImage();
            return;
        }

        // A recycled card may still have an older request in flight; the key tells them apart.
        String requestKey = salonId + "/" + imageId + "@" + imageVersion;
        holder.ivImage.setTag(R.id.ivServiceCardImage, requestKey);
        holder.showLoading();

        ServiceImages.load(salonId, imageId, imageVersion, CARD_IMAGE_DIMENSION, bitmap -> {
            if (!requestKey.equals(holder.ivImage.getTag(R.id.ivServiceCardImage))) {
                return;
            }
            if (bitmap == null) {
                holder.hideImage();
            } else {
                holder.showImage(bitmap);
            }
        });

        holder.imageFrame.setOnClickListener(v -> openViewer(v.getContext(), service));
    }

    /** Opens the booking screen with this service already ticked. */
    private void openBooking(Context context, String serviceName) {
        Intent intent = new Intent(context, BookAppointmentActivity.class);
        intent.putExtra("salonId", salonId);
        intent.putExtra("preselectServiceName", serviceName);
        context.startActivity(intent);
    }

    /** Opens the full-screen gallery of this salon's service photos, on the tapped one. */
    private void openViewer(Context context, ServiceDetail tapped) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> imageIds = new ArrayList<>();
        List<Long> versions = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        int startPosition = 0;

        for (ServiceDetail service : services) {
            if (service.getImageId() == null) continue;
            if (service == tapped) startPosition = names.size();
            names.add(service.getName());
            imageIds.add(service.getImageId());
            versions.add(service.getImageVersion());
            prices.add(service.hasPrice() ? service.getPrice() : Double.NaN);
            durations.add(service.getDurationMinutes());
        }
        if (names.isEmpty()) return;

        long[] versionArray = new long[versions.size()];
        double[] priceArray = new double[prices.size()];
        int[] durationArray = new int[durations.size()];
        for (int i = 0; i < names.size(); i++) {
            versionArray[i] = versions.get(i);
            priceArray[i] = prices.get(i);
            durationArray[i] = durations.get(i);
        }

        Intent intent = new Intent(context, ServicePhotoViewerActivity.class);
        intent.putExtra(ServicePhotoViewerActivity.EXTRA_SALON_ID, salonId);
        intent.putStringArrayListExtra(ServicePhotoViewerActivity.EXTRA_NAMES, names);
        intent.putStringArrayListExtra(ServicePhotoViewerActivity.EXTRA_IMAGE_IDS, imageIds);
        intent.putExtra(ServicePhotoViewerActivity.EXTRA_VERSIONS, versionArray);
        intent.putExtra(ServicePhotoViewerActivity.EXTRA_PRICES, priceArray);
        intent.putExtra(ServicePhotoViewerActivity.EXTRA_DURATIONS, durationArray);
        intent.putExtra(ServicePhotoViewerActivity.EXTRA_POSITION, startPosition);
        context.startActivity(intent);
    }

    @Override
    public void onViewRecycled(@NonNull ServiceViewHolder holder) {
        // Let go of the photo so a long list does not hold on to memory it is not showing.
        holder.ivImage.setImageBitmap(null);
        super.onViewRecycled(holder);
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
        final FrameLayout imageFrame;
        final ShapeableImageView ivImage;
        final ProgressBar progress;
        final TextView tvName;
        final TextView tvPrice;
        final TextView tvDuration;
        final MaterialButton btnBook;

        ServiceViewHolder(@NonNull View itemView) {
            super(itemView);
            imageFrame = itemView.findViewById(R.id.flServiceCardImage);
            ivImage = itemView.findViewById(R.id.ivServiceCardImage);
            progress = itemView.findViewById(R.id.pbServiceCardImage);
            tvName = itemView.findViewById(R.id.tvServiceCardName);
            tvPrice = itemView.findViewById(R.id.tvServiceCardPrice);
            tvDuration = itemView.findViewById(R.id.tvServiceCardDuration);
            btnBook = itemView.findViewById(R.id.btnBookService);
        }

        void showLoading() {
            imageFrame.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            ivImage.setImageBitmap(null);
        }

        void showImage(Bitmap bitmap) {
            imageFrame.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
            ivImage.setImageBitmap(bitmap);
        }

        /** No photo (or it failed to load): the card shrinks to just the name, price and Book. */
        void hideImage() {
            ivImage.setTag(R.id.ivServiceCardImage, null);
            ivImage.setImageBitmap(null);
            imageFrame.setOnClickListener(null);
            imageFrame.setVisibility(View.GONE);
        }
    }
}