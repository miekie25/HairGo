package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.adapters.NotificationAdapter;
import com.hairgo.app.models.AppNotification;
import com.hairgo.app.utils.NotificationStore;
import com.hairgo.app.utils.Snackbars;

import java.util.List;

/**
 * The in-app list of notifications, opened from the bell icon on the client
 * dashboard. Reads the on-device history and marks it as seen when opened.
 */
public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvEmptyState;
    private TextView tvClearAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(R.string.title_notifications);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvClearAll = findViewById(R.id.tvClearAll);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));

        tvClearAll.setOnClickListener(v -> {
            NotificationStore.clear(this);
            loadNotifications();
            // Clearing is instant (on-device preferences), so only the
            // confirmation is needed: tell the user the list is now empty.
            Snackbars.show(findViewById(android.R.id.content),
                    R.string.notifications_cleared);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
        // Opening the list counts as reading everything in it.
        NotificationStore.markAllSeen(this);
    }

    private void loadNotifications() {
        List<AppNotification> notifications = NotificationStore.getAll(this);

        if (notifications.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            tvClearAll.setVisibility(View.GONE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        tvClearAll.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.VISIBLE);
        rvNotifications.setAdapter(
                new NotificationAdapter(notifications, this::openNotification));
    }

    /**
     * A notification that came from a booking opens that booking's details.
     * Others have nowhere useful to go, so they simply do nothing.
     */
    private void openNotification(AppNotification notification) {
        String bookingId = notification.getBookingId();
        if (bookingId == null || bookingId.isEmpty()) return;

        Intent intent = new Intent(this, BookingDetailsActivity.class);
        intent.putExtra("bookingId", bookingId);
        startActivity(intent);
    }
}
