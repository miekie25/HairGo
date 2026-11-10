package com.hairgo.app.models;

public class OwnerSalon {

    private final String id;
    private final String name;
    private final String location;
    // Optional map coordinates. Null for salons saved without the map picker.
    private final Double latitude;
    private final Double longitude;

    public OwnerSalon(String id, String name, String location) {
        this(id, name, location, null, null);
    }

    public OwnerSalon(String id, String name, String location, Double latitude, Double longitude) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}