package com.hairgo.app.models;

public class OwnerBooking {

    private String id;
    private String clientName;
    private String service;
    private String dateTime;
    private String status; // lowercase to match the shared Firebase convention: "pending", "confirmed", "cancelled", "completed"
    private int price;

    public OwnerBooking(String id, String clientName, String service, String dateTime, String status, int price) {
        this.id = id;
        this.clientName = clientName;
        this.service = service;
        this.dateTime = dateTime;
        this.status = status;
        this.price = price;
    }

    public String getId() { return id; }
    public String getClientName() { return clientName; }
    public String getService() { return service; }
    public String getDateTime() { return dateTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPrice() { return price; }
}