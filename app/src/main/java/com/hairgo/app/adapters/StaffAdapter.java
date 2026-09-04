package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.StaffMember;

import java.util.List;

public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.ViewHolder> {

    public interface OnStaffActionListener {
        void onRemove(StaffMember staffMember, int position);
    }

    private final List<StaffMember> staffList;
    private final OnStaffActionListener listener;

    public StaffAdapter(List<StaffMember> staffList, OnStaffActionListener listener) {
        this.staffList = staffList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_staff, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StaffMember staff = staffList.get(position);
        holder.tvName.setText(staff.getName());
        holder.tvRole.setText(staff.getRole());
        holder.tvPhone.setText(staff.getPhone());

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(staff, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return staffList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvRole, tvPhone;
        Button btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStaffName);
            tvRole = itemView.findViewById(R.id.tvStaffRole);
            tvPhone = itemView.findViewById(R.id.tvStaffPhone);
            btnRemove = itemView.findViewById(R.id.btnRemoveStaff);
        }
    }
}
