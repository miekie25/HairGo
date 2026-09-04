package com.hairgo.app.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hairgo.app.R;
import com.hairgo.app.adapters.StaffAdapter;
import com.hairgo.app.models.StaffMember;
import com.hairgo.app.utils.DashboardData;
import java.util.List;

public class OwnerStaffFragment extends Fragment {

    private List<StaffMember> staffList;
    private StaffAdapter adapter;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_owner_staff, container, false);

        RecyclerView rv = view.findViewById(R.id.rvOwnerStaff);
        tvEmpty = view.findViewById(R.id.tvStaffEmpty);
        Button btnAddStaff = view.findViewById(R.id.btnAddStaff);

        staffList = DashboardData.getStaff();

        adapter = new StaffAdapter(staffList, (staffMember, position) -> {
            staffList.remove(position);
            adapter.notifyItemRemoved(position);
            updateEmptyState();
        });

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        btnAddStaff.setOnClickListener(v -> showAddStaffDialog());

        updateEmptyState();
        return view;
    }

    private void showAddStaffDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_staff, null);
        EditText etName = dialogView.findViewById(R.id.etStaffName);
        EditText etRole = dialogView.findViewById(R.id.etStaffRole);
        EditText etPhone = dialogView.findViewById(R.id.etStaffPhone);

        new AlertDialog.Builder(getContext())
                .setTitle("Add staff member")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String role = etRole.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(getContext(), "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(role)) role = "Not specified";
                    if (TextUtils.isEmpty(phone)) phone = "Not provided";

                    StaffMember newStaff = new StaffMember(
                            String.valueOf(System.currentTimeMillis()), name, role, phone);
                    staffList.add(0, newStaff);
                    adapter.notifyItemInserted(0);
                    updateEmptyState();

                    Toast.makeText(getContext(), name + " has been added successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateEmptyState() {
        tvEmpty.setVisibility(staffList.isEmpty() ? View.VISIBLE : View.GONE);
    }
}