package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.adapters.ServiceAdapter;
import com.hairgo.app.models.Salon;
import com.hairgo.app.utils.DummyData;

import java.util.Locale;

public class SalonProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salon_profile);

        String salonId = getIntent().getStringExtra("salonId");

        // TEMP: dummy lookup — replaced with SalonManager.getSalonById() in Phase 2
        Salon salon = DummyData.getDummySalonById(salonId);

        if (salon == null) {
            Toast.makeText(this, "Salon not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(salon.getName());

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> onBackPressed());

        TextView tvSalonName = findViewById(R.id.tvSalonName);
        TextView tvSalonLocation = findViewById(R.id.tvSalonLocation);
        TextView tvSalonRating = findViewById(R.id.tvSalonRating);

        tvSalonName.setText(salon.getName());
        tvSalonLocation.setText(salon.getLocation());
        tvSalonRating.setText(String.format(Locale.getDefault(), "%.1f", salon.getAvgRating()));

        RecyclerView rvServices = findViewById(R.id.rvServices);
        rvServices.setLayoutManager(new LinearLayoutManager(this));
        rvServices.setAdapter(new ServiceAdapter(salon.getServices()));

        findViewById(R.id.btnBook).setOnClickListener(v -> {
            Intent intent = new Intent(this, BookAppointmentActivity.class);
            intent.putExtra("salonId", salon.getSalonId());
            startActivity(intent);
        });
    }
}