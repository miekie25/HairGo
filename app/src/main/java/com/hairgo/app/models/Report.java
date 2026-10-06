package com.hairgo.app.models;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A problem report raised by a user about the app itself (not a salon review).
 *
 * Field names are the de-facto Firestore schema for the {@code reports}
 * collection and must stay in sync with what ReportManager writes.
 */
public class Report {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_REVIEW = "in_review";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_CLOSED = "closed";

    public static final String CATEGORY_BOOKING = "booking";
    public static final String CATEGORY_LOGIN = "login_account";
    public static final String CATEGORY_APP_ERROR = "app_error";
    public static final String CATEGORY_PAYMENT = "payment";
    public static final String CATEGORY_OTHER = "other";

    public static final String SEVERITY_LOW = "low";
    public static final String SEVERITY_MEDIUM = "medium";
    public static final String SEVERITY_HIGH = "high";

    private String reportID = "";
    private String userID = "";
    private String userName = "";
    private String userEmail = "";
    private String userRole = "";
    private String category = "";
    private String severity = "";
    private String description = "";
    private String status = STATUS_PENDING;
    private List<String> screenshots = new ArrayList<>();
    private String appVersion = "";
    private String deviceModel = "";
    private String osVersion = "";
    private boolean isDeleted = false;

    /**
     * Populated by Firestore with the server time on write, so a wrong
     * device clock can't skew report ordering.
     */
    @ServerTimestamp
    private Date createdAt;

    public Report() {}

    public String getReportID() { return reportID; }
    public void setReportID(String reportID) { this.reportID = reportID; }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getScreenshots() { return screenshots; }
    public void setScreenshots(List<String> screenshots) {
        this.screenshots = screenshots == null ? new ArrayList<>() : screenshots;
    }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

    /**
     * Accessors are getIsDeleted/setIsDeleted (not isDeleted/setDeleted) so the
     * JavaBean property name stays "isDeleted", matching every other collection.
     */
    public boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
