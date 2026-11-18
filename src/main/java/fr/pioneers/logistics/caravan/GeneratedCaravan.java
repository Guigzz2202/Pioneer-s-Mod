package fr.pioneers.logistics.caravan;

/**
 * GENERE par tools/gc/caravan/caravan.py (1.0.4-beta, lot 10 : caravanes) : regles des caravanes et rations. Java pur, sans Minecraft.
 * Ne pas modifier a la main : changer caravan.py et le relancer.
 */
public final class GeneratedCaravan {

    private GeneratedCaravan() {
    }

    /** Ration : id d'objet, unites (une unite = une ration pour une bete), reservee aux chameaux et dromadaires. */
    public record Food(String id, int units, boolean camelOnly) {
    }

    public static final Food[] FOOD = {
        new Food("minecraft:wheat", 1, false),
        new Food("minecraft:hay_block", 9, false),
        new Food("minecraft:carrot", 1, false),
        new Food("minecraft:apple", 1, false),
        new Food("minecraft:golden_carrot", 3, false),
        new Food("pioneers:oats", 1, false),
        new Food("pioneers:barley", 1, false),
        new Food("pioneers:animal_feed", 4, false),
        new Food("minecraft:sugar", 1, false),
        new Food("minecraft:cactus", 2, true)
    };

    public static final int MAX_LEG = 96, MAX_STOPS = 16, MAX_FOLLOWERS = 5, KG_PER_SLOT = 9, MIN_SLOTS = 4, MAX_SLOTS = 27;
    public static final float PACE = 3.2F, LOAD_SLOW = 0.25F, HUNGRY_SLOW = 0.3F, DESERT_TEMP = 1F;
    public static final int FED_BLOCKS = 256, FED_BLOCKS_CAMEL = 512;
    public static final int WAIT_DEFAULT = 10, WAIT_MAX = 600, WAIT_STEP = 5, MIN_STOP = 3, TRANSFER_TICKS = 10, TRANSFER_ITEMS = 16;
    public static final float ARRIVE = 2.5F, HOP = 12F, WAIT_GAP = 7F, RESCUE_GAP = 9.5F, CATCH_UP = 1.35F;
    public static final int STUCK_SECONDS = 45, TICKET_RADIUS = 4, FILTER = 9;
}
