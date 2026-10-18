package com.hairgo.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.hairgo.app.models.OwnerSalon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds who the logged-in owner is and which of their salons is currently selected.
 * Every owner tab reads the selected salon from here.
 */
public class OwnerSession {

    private static final String PREFS = "owner_session";
    private static final String KEY_SALON = "selected_salon_id";

    private static List<OwnerSalon> salons = new ArrayList<>();
    private static String selectedSalonId;
    private static String ownerName = "";
    // Client names already looked up this session. "" means looked up, no name found.
    private static final Map<String, String> clientNames = new HashMap<>();

    private OwnerSession() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Stores the owner's salons and restores their last choice, else picks the first. */
    public static void setSalons(Context context, List<OwnerSalon> list) {
        salons = list == null ? new ArrayList<>() : list;
        String saved = prefs(context).getString(KEY_SALON, null);

        selectedSalonId = null;
        for (OwnerSalon salon : salons) {
            if (salon.getId().equals(saved)) selectedSalonId = saved;
        }
        if (selectedSalonId == null && !salons.isEmpty()) {
            selectedSalonId = salons.get(0).getId();
        }
    }

    public static void select(Context context, String salonId) {
        selectedSalonId = salonId;
        prefs(context).edit().putString(KEY_SALON, salonId).apply();
    }

    public static List<OwnerSalon> getSalons() { return salons; }

    public static String getSelectedSalonId() { return selectedSalonId; }

    public static OwnerSalon getSelectedSalon() {
        for (OwnerSalon salon : salons) {
            if (salon.getId().equals(selectedSalonId)) return salon;
        }
        return null;
    }

    public static String getOwnerName() { return ownerName; }

    public static void setOwnerName(String name) { ownerName = name == null ? "" : name; }

    /** Returns null if this client has not been looked up yet. */
    public static String getCachedClientName(String clientId) {
        return clientNames.get(clientId);
    }

    public static void cacheClientName(String clientId, String name) {
        clientNames.put(clientId, name == null ? "" : name);
    }

    /** Call on logout so the next account never sees this owner's data. */
    public static void clear() {
        salons = new ArrayList<>();
        selectedSalonId = null;
        ownerName = "";
        clientNames.clear();
    }
}