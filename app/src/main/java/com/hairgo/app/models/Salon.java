package com.hairgo.app.models;

import java.util.ArrayList;
import java.util.List;

public class Salon {
    private String salonId;
    private String name;
    private String location;
    private double avgRating;
    // The old list of service names. Other screens still read this.
    private List<String> services;
    // The services with price and duration. Built from the names if none are saved.
    private List<ServiceDetail> serviceDetails;
    // Optional map coordinates. Null for salons saved without the map picker.
    private Double latitude;
    private Double longitude;
    // Distance from the client in metres. Null when either side has no
    // usable coordinates, or when the salon has no location at all.
    private Double distanceMetres;
    private int imageRes;

    public Salon() {
    }

    public Salon(String salonId, String name, String location, double avgRating, List<String> services) {
        this(salonId, name, location, avgRating, services, null, 0);
    }

    public Salon(String salonId, String name, String location, double avgRating, List<String> services, int imageRes) {
        this(salonId, name, location, avgRating, services, null, imageRes);
    }

    public Salon(String salonId, String name, String location, double avgRating,
                 List<String> services, List<ServiceDetail> serviceDetails, int imageRes) {
        this.salonId = salonId;
        this.name = name;
        this.location = location;
        this.avgRating = avgRating;
        this.services = services != null ? services : new ArrayList<>();
        // If no details were saved, build them from the old names.
        this.serviceDetails = (serviceDetails != null && !serviceDetails.isEmpty())
                ? serviceDetails
                : ServiceDetail.fromNames(this.services);
        this.imageRes = imageRes;
    }

    public String getSalonId() { return salonId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public double getAvgRating() { return avgRating; }
    public List<String> getServices() { return services; }

    // Always returns a list, never null, even for the empty Salon() constructor.
    public List<ServiceDetail> getServiceDetails() {
        return serviceDetails != null ? serviceDetails : ServiceDetail.fromNames(services);
    }

    public int getImageRes() { return imageRes; }
    public void setImageRes(int imageRes) { this.imageRes = imageRes; }

    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public void setCoordinates(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Double getDistanceMetres() { return distanceMetres; }
    public void setDistanceMetres(Double distanceMetres) { this.distanceMetres = distanceMetres; }
}