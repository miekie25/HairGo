package com.hairgo.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.hairgo.app.R;

public class OwnerProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_owner_profile, container, false);

        // TODO: replace with real owner data from Firebase once auth/user profile is confirmed
        Button btnLogout = view.findViewById(R.id.btnOwnerLogout);
        btnLogout.setOnClickListener(v -> {
            // TODO: wire to actual sign-out logic once Firebase Authentication is confirmed
        });

        return view;
    }
}