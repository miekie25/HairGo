package com.hairgo.app.models;

import java.util.ArrayList;
import java.util.List;

public class Salon {
    private String salonId;
    private String name;
    private String location;
    private double avgRating;
    private List<String> services;
    private int imageRes;

    public Salon() {
    }

    public Salon(String salonId, String name, String location, double avgRating, List<String> services) {
        this(salonId, name, location, avgRating, services, 0);
    }

    public Salon(String salonId, String name, String location, double avgRating, List<String> services, int imageRes) {
        this.salonId = salonId;
        this.name = name;
        this.location = location;
        this.avgRating = avgRating;
        this.services = services != null ? services : new ArrayList<>();
        this.imageRes = imageRes;
    }

    public String getSalonId() { return salonId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public double getAvgRating() { return avgRating; }
    public List<String> getServices() { return services; }
    public int getImageRes() { return imageRes; }
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }
}