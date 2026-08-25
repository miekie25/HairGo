package com.hairgo.app.utils;

import com.hairgo.app.models.Salon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DummyData {

    public static List<Salon> getDummySalons() {
        List<Salon> salons = new ArrayList<>();

        salons.add(new Salon("1", "Fresh Fadez Barbershop", "Randburg", 4.8,
                Arrays.asList("Haircut", "Beard Trim", "Line Up", "Hot Towel Shave")));

        salons.add(new Salon("2", "Glow Hair Studio", "Sandton", 4.5,
                Arrays.asList("Braids", "Weave Install", "Silk Press", "Deep Conditioning")));

        salons.add(new Salon("3", "The Cutting Edge", "Randburg", 4.2,
                Arrays.asList("Haircut", "Fade", "Kids Cut", "Beard Sculpt")));

        salons.add(new Salon("4", "Bella Hair Lounge", "Roodepoort", 4.9,
                Arrays.asList("Braids", "Cornrows", "Treatment", "Blow Dry")));

        return salons;
    }

    public static Salon getDummySalonById(String salonId) {
        for (Salon salon : getDummySalons()) {
            if (salon.getSalonId().equals(salonId)) {
                return salon;
            }
        }
        return null;
    }
}