package com.hairgo.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.hairgo.app.R;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.models.OwnerBooking;
import com.hairgo.app.models.PaymentDetails;
import com.hairgo.app.utils.OwnerSession;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class OwnerReportsFragment extends Fragment {

    private BookingManager bookingManager;
    private String salonId;
    private int currentPeriod = PERIOD_TODAY;

    public static final int PERIOD_TODAY = 0;
    public static final int PERIOD_THIS_MONTH = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_reports, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bookingManager = new BookingManager();
        setupPeriodSelector(view);
        refreshReports();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshReports();
    }

    private void setupPeriodSelector(View view) {
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupPeriod);
        Chip chipToday = view.findViewById(R.id.chipToday);
        Chip chipThisMonth = view.findViewById(R.id.chipThisMonth);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int selectedId = checkedIds.get(0);
            int newPeriod = selectedId == R.id.chipToday ? PERIOD_TODAY : PERIOD_THIS_MONTH;
            if (newPeriod != currentPeriod) {
                currentPeriod = newPeriod;
                refreshReports();
            }
        });

        // Set initial selection
        chipGroup.check(currentPeriod == PERIOD_TODAY ? R.id.chipToday : R.id.chipThisMonth);
    }

    private void refreshReports() {
        salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) {
            showEmptyState();
            return;
        }

        View view = getView();
        if (view == null) return;

        view.findViewById(R.id.progressReports).setVisibility(View.VISIBLE);
        view.findViewById(R.id.tvEmptyReports).setVisibility(View.GONE);
        hideAllReportSections(view);

        bookingManager.getBookingsForSalon(salonId, new BookingManager.BookingListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> raw) {
                if (!isAdded() || getView() == null) return;

                View view = getView();
                view.findViewById(R.id.progressReports).setVisibility(View.GONE);

                if (raw == null || raw.isEmpty()) {
                    showEmptyState();
                    return;
                }

                ReportData data = calculateReport(raw);
                displayReport(data);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded() || getView() == null) return;
                View view = getView();
                view.findViewById(R.id.progressReports).setVisibility(View.GONE);
                view.findViewById(R.id.tvEmptyReports).setVisibility(View.VISIBLE);
                ((TextView) view.findViewById(R.id.tvEmptyReports)).setText(
                        getString(R.string.reports_load_failed, errorMessage));
            }
        });
    }

    private ReportData calculateReport(List<Map<String, Object>> rawBookings) {
        ReportData data = new ReportData();
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH);
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);

        for (Map<String, Object> map : rawBookings) {
            OwnerBooking booking = OwnerBooking.fromMap(map);
            if (booking == null) continue;

            Date bookingDate = booking.getDateTime();
            if (bookingDate == null) continue;

            boolean inPeriod;
            if (currentPeriod == PERIOD_TODAY) {
                Calendar bc = Calendar.getInstance();
                bc.setTime(bookingDate);
                inPeriod = bc.get(Calendar.YEAR) == currentYear
                        && bc.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
            } else {
                Calendar bc = Calendar.getInstance();
                bc.setTime(bookingDate);
                inPeriod = bc.get(Calendar.YEAR) == currentYear
                        && bc.get(Calendar.MONTH) == currentMonth;
            }

            if (!inPeriod) continue;

            data.totalBookingsInPeriod++;

            // Booking Summary (by appointment date)
            Double bookingTotal = booking.getTotalPrice();
            if (bookingTotal != null) {
                data.totalBookingValue = data.totalBookingValue.add(BigDecimal.valueOf(bookingTotal));
            }

            PaymentDetails payment = booking.getPaymentDetails();
            if (payment != null) {
                Double paid = payment.getAmountPaid();
                if (paid != null && paid > 0) {
                    data.totalAmountPaid = data.totalAmountPaid.add(BigDecimal.valueOf(paid));

                    // Payment Summary (by payment date)
                    Date paymentDate = payment.getPaymentTimestampAsDate();
                    if (paymentDate != null) {
                        Calendar pc = Calendar.getInstance();
                        pc.setTime(paymentDate);
                        boolean paymentInPeriod;
                        if (currentPeriod == PERIOD_TODAY) {
                            paymentInPeriod = pc.get(Calendar.YEAR) == currentYear
                                    && pc.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);
                        } else {
                            paymentInPeriod = pc.get(Calendar.YEAR) == currentYear
                                    && pc.get(Calendar.MONTH) == currentMonth;
                        }

                        if (paymentInPeriod) {
                            if (payment.isCardPayment()) {
                                data.cardPaymentsReceived = data.cardPaymentsReceived.add(BigDecimal.valueOf(paid));
                            } else if (PaymentDetails.TYPE_CASH.equals(payment.getType())) {
                                data.cashPaymentsRecorded = data.cashPaymentsRecorded.add(BigDecimal.valueOf(paid));
                            }
                        }
                    } else {
                        // Payment has no timestamp - count in "unknown date" bucket
                        data.paymentsWithoutTimestamp += paid;
                    }
                }

                Double outstanding = payment != null ? payment.getAmountOutstanding() : null;
                if (outstanding != null && outstanding > 0) {
                    data.totalOutstandingBalance = data.totalOutstandingBalance.add(BigDecimal.valueOf(outstanding));
                    data.bookingsWithOutstanding++;
                }
            }
        }

        return data;
    }

    private void displayReport(ReportData data) {
        View view = getView();
        if (view == null) return;

        // Booking Summary
        setText(view, R.id.tvReportTotalValue, formatPrice(data.totalBookingValue));
        setText(view, R.id.tvReportBookingsOutstanding, String.valueOf(data.bookingsWithOutstanding));
        view.findViewById(R.id.tvBookingSummaryLabel).setVisibility(View.VISIBLE);
        view.findViewById(R.id.layoutBookingSummary).setVisibility(View.VISIBLE);

        // Payment Summary
        setText(view, R.id.tvReportAmountPaid, formatPrice(data.totalAmountPaid));
        setText(view, R.id.tvReportOutstandingBalance, formatPrice(data.totalOutstandingBalance));
        view.findViewById(R.id.tvPaymentSummaryLabel).setVisibility(View.VISIBLE);
        view.findViewById(R.id.layoutPaymentSummary).setVisibility(View.VISIBLE);

        // Payment Breakdown
        setText(view, R.id.tvReportCardPayments, formatPrice(data.cardPaymentsReceived));
        setText(view, R.id.tvReportCashPayments, formatPrice(data.cashPaymentsRecorded));
        view.findViewById(R.id.tvPaymentBreakdownLabel).setVisibility(View.VISIBLE);
        view.findViewById(R.id.layoutPaymentBreakdown).setVisibility(View.VISIBLE);

        // Data completeness note
        if (data.paymentsWithoutTimestamp > 0) {
            String note = getString(R.string.reports_data_completeness_note)
                    + " " + getString(R.string.reports_unknown_payment_dates, formatPrice(BigDecimal.valueOf(data.paymentsWithoutTimestamp)));
            TextView noteView = view.findViewById(R.id.tvDataCompletenessNote);
            noteView.setText(note);
            noteView.setVisibility(View.VISIBLE);
        }

        if (data.totalBookingsInPeriod == 0) {
            showEmptyState();
        }
    }

    private void showEmptyState() {
        View view = getView();
        if (view == null) return;
        view.findViewById(R.id.progressReports).setVisibility(View.GONE);
        view.findViewById(R.id.tvEmptyReports).setVisibility(View.VISIBLE);
        hideAllReportSections(view);
    }

    private void hideAllReportSections(View view) {
        view.findViewById(R.id.tvBookingSummaryLabel).setVisibility(View.GONE);
        view.findViewById(R.id.layoutBookingSummary).setVisibility(View.GONE);
        view.findViewById(R.id.tvPaymentSummaryLabel).setVisibility(View.GONE);
        view.findViewById(R.id.layoutPaymentSummary).setVisibility(View.GONE);
        view.findViewById(R.id.tvPaymentBreakdownLabel).setVisibility(View.GONE);
        view.findViewById(R.id.layoutPaymentBreakdown).setVisibility(View.GONE);
        view.findViewById(R.id.tvDataCompletenessNote).setVisibility(View.GONE);
    }

    private void setText(View root, int viewId, String value) {
        ((TextView) root.findViewById(viewId)).setText(value);
    }

    private String formatPrice(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "R0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return "R" + stripped.toPlainString();
    }

    private static class ReportData {
        int totalBookingsInPeriod = 0;
        BigDecimal totalBookingValue = BigDecimal.ZERO;
        int bookingsWithOutstanding = 0;
        BigDecimal totalAmountPaid = BigDecimal.ZERO;
        BigDecimal totalOutstandingBalance = BigDecimal.ZERO;
        BigDecimal cardPaymentsReceived = BigDecimal.ZERO;
        BigDecimal cashPaymentsRecorded = BigDecimal.ZERO;
        double paymentsWithoutTimestamp = 0;
    }
}