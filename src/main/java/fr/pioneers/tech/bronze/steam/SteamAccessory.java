package fr.pioneers.tech.bronze.steam;

import java.util.Locale;

/** Organes montes sur un tuyau ou une chaudiere : soupape de surete, manometre, tube de niveau, sifflet. */
public enum SteamAccessory {
    SAFETY_VALVE, PRESSURE_GAUGE, WATER_GAUGE, STEAM_WHISTLE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
