package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.activities.SalonProfileActivity;
import com.hairgo.app.adapters.SalonAdapter;
import com.hairgo.app.utils.DummyData;

public class HomeFragment extends Fragment {

    private RecyclerView rvSalons;
    private SalonAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvSalons = view.findViewById(R.id.rvSalons);
        rvSalons.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SalonAdapter(DummyData.getDummySalons(), salon -> {
            Intent intent = new Intent(getContext(), SalonProfileActivity.class);
            intent.putExtra("salonId", salon.getSalonId());
            startActivity(intent);
        });
        rvSalons.setAdapter(adapter);
    }
}