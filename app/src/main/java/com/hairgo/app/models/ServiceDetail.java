package com.hairgo.app.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One service a salon offers, with its price and how long it takes.
 * The price is optional. Old services have no price saved, so price can be null.
 */
public class ServiceDetail {

    // Used when a service has no duration saved yet.
    public static final int DEFAULT_DURATION_MINUTES = 60;

    private final String name;
    private final Double price;      // null means "price not set"
    private final int durationMinutes;

    public ServiceDetail(String name, Double price, int durationMinutes) {
        this.name = name != null ? name.trim() : "";
        this.price = price;
        // Use the default if the duration is missing or not a positive number.
        this.durationMinutes = durationMinutes > 0 ? durationMinutes : DEFAULT_DURATION_MINUTES;
    }

    public String getName() { return name; }
    public Double getPrice() { return price; }
    public int getDurationMinutes() { return durationMinutes; }

    // True when the owner has saved a price for this service.
    public boolean hasPrice() { return price != null; }

    // Turns this service into a map that can be saved in Firestore.
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        // Only save the price if there is one, so "not set" stays empty.
        if (price != null) {
            map.put("price", price);
        }
        map.put("duration", durationMinutes);
        return map;
    }

    // Reads one service from a Firestore map. Missing fields are handled safely.
    public static ServiceDetail fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Object nameValue = map.get("name");
        if (nameValue == null) {
            return null;
        }

        Double price = null;
        Object priceValue = map.get("price");
        if (priceValue instanceof Number) {
            price = ((Number) priceValue).doubleValue();
        }

        int duration = DEFAULT_DURATION_MINUTES;
        Object durationValue = map.get("duration");
        if (durationValue instanceof Number) {
            duration = ((Number) durationValue).intValue();
        }

        return new ServiceDetail(String.valueOf(nameValue), price, duration);
    }

    // Reads a list of services from a raw Firestore value. Never returns null.
    @SuppressWarnings("unchecked")
    public static List<ServiceDetail> fromRawList(Object raw) {
        List<ServiceDetail> result = new ArrayList<>();
        if (!(raw instanceof List)) {
            return result;
        }
        for (Object item : (List<Object>) raw) {
            if (item instanceof Map) {
                ServiceDetail detail = fromMap((Map<String, Object>) item);
                if (detail != null && !detail.getName().isEmpty()) {
                    result.add(detail);
                }
            } else if (item instanceof String) {
                // Old style: just a name, so no price and the default duration.
                String text = ((String) item).trim();
                if (!text.isEmpty()) {
                    result.add(new ServiceDetail(text, null, DEFAULT_DURATION_MINUTES));
                }
            }
        }
        return result;
    }

    // Builds services from the old list of names (no price, default duration).
    public static List<ServiceDetail> fromNames(List<String> names) {
        List<ServiceDetail> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
                result.add(new ServiceDetail(name, null, DEFAULT_DURATION_MINUTES));
            }
        }
        return result;
    }

    // Gets just the names, for the old "services" field that other screens read.
    public static List<String> toNames(List<ServiceDetail> details) {
        List<String> names = new ArrayList<>();
        if (details != null) {
            for (ServiceDetail detail : details) {
                names.add(detail.getName());
            }
        }
        return names;
    }

    // Turns a list of services into maps that can be saved in Firestore.
    public static List<Map<String, Object>> toMapList(List<ServiceDetail> details) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (details != null) {
            for (ServiceDetail detail : details) {
                maps.add(detail.toMap());
            }
        }
        return maps;
    }
}