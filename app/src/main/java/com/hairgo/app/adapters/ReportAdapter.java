package com.hairgo.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.models.Report;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy • h:mm a", Locale.getDefault());

    private final List<Report> reports;

    public ReportAdapter(List<Report> reports) {
        this.reports = reports;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        bind(holder, reports.get(position));
    }

    private void bind(@NonNull ReportViewHolder holder, Report report) {
        holder.tvCategory.setText(categoryLabel(holder, report.getCategory()));
        holder.tvDescription.setText(report.getDescription());

        if (report.getCreatedAt() != null) {
            holder.tvDate.setText(DATE_FORMAT.format(
                    report.getCreatedAt().toInstant().atZone(ZoneId.systemDefault())));
        } else {
            holder.tvDate.setText("");
        }

        holder.tvMeta.setText(metaLabel(holder, report));
        bindStatusBadge(holder, report.getStatus());
    }

    private void bindStatusBadge(ReportViewHolder holder, String status) {
        String normalized = status == null ? "" : status;
        holder.tvStatusBadge.setText(normalized);

        int pillRes;
        int textColorRes;
        switch (normalized) {
            case Report.STATUS_IN_REVIEW:
                pillRes = R.drawable.bg_pill_confirmed;
                textColorRes = R.color.teal_dark;
                break;
            case Report.STATUS_RESOLVED:
                pillRes = R.drawable.bg_pill_completed;
                textColorRes = R.color.success_dark;
                break;
            case Report.STATUS_CLOSED:
                pillRes = R.drawable.bg_pill_closed;
                textColorRes = R.color.grey_dark;
                break;
            case Report.STATUS_PENDING:
            default:
                pillRes = R.drawable.bg_pill_pending;
                textColorRes = R.color.warning_dark;
                break;
        }

        holder.tvStatusBadge.setBackgroundResource(pillRes);
        holder.tvStatusBadge.setTextColor(
                ContextCompat.getColor(holder.itemView.getContext(), textColorRes));
    }

    private String categoryLabel(ReportViewHolder holder, String category) {
        int resId;
        if (Report.CATEGORY_BOOKING.equals(category)) {
            resId = R.string.report_category_booking;
        } else if (Report.CATEGORY_LOGIN.equals(category)) {
            resId = R.string.report_category_login;
        } else if (Report.CATEGORY_APP_ERROR.equals(category)) {
            resId = R.string.report_category_app_error;
        } else if (Report.CATEGORY_PAYMENT.equals(category)) {
            resId = R.string.report_category_payment;
        } else if (Report.CATEGORY_OTHER.equals(category)) {
            resId = R.string.report_category_other;
        } else {
            return category == null ? "" : category;
        }
        return holder.itemView.getContext().getString(resId);
    }

    private String metaLabel(ReportViewHolder holder, Report report) {
        String severity = report.getSeverity();
        String severityLabel = severity == null || severity.isEmpty()
                ? ""
                : severity.substring(0, 1).toUpperCase(Locale.getDefault()) + severity.substring(1);

        int screenshotCount = report.getScreenshots() == null ? 0 : report.getScreenshots().size();
        String screenshotLabel = screenshotCount > 0
                ? holder.itemView.getContext().getString(R.string.report_screenshots_count, screenshotCount)
                : holder.itemView.getContext().getString(R.string.report_no_screenshots);

        if (severityLabel.isEmpty()) return screenshotLabel;
        return severityLabel + " • " + screenshotLabel;
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategory, tvStatusBadge, tvDescription, tvDate, tvMeta;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvMeta = itemView.findViewById(R.id.tvMeta);
        }
    }
}
