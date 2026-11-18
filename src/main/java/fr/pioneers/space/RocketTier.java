package fr.pioneers.space;

import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Les treize fusees habitees, du saut vers la Lune jusqu'aux etoiles voisines.
 * <pre>
 *  T1  Lune                      T6  Systeme jovien (Io, Europe, Ganymede, Callisto)
 *  T2  Mars, Phobos, Deimos      T7  Systeme saturnien (Titan, Encelade)
 *  T3  Venus                     T8  Systeme uranien (Miranda, Titania)
 *  T4  Mercure                   T9  Systeme neptunien (Triton)
 *  T5  Ceinture d'asteroides     T10 Pluton et Charon
 *  T11 Geantes gazeuses (descente dans les nuages)
 *  T12 Alpha du Centaure          T13 TRAPPIST-1
 * </pre>
 * Les sondes suivent la meme echelle (sonde solaire : tier 5).
 */
public enum RocketTier {
    //       niveau, carburant, hauteur, siege, energie d'assemblage, duree (s), largeur, echec sonde
    TIER_1(1, 10_000, 7, 2.98, 20_000_000L, 20, 1.6F, 0.05F),
    TIER_2(2, 20_000, 9, 5.11, 60_000_000L, 20, 2.4F, 0.12F),
    TIER_3(3, 30_000, 11, 6.73, 120_000_000L, 20, 2.6F, 0.20F),
    TIER_4(4, 40_000, 12, 7.73, 180_000_000L, 30, 2.6F, 0.22F),
    TIER_5(5, 50_000, 13, 8.73, 250_000_000L, 40, 2.8F, 0.25F),
    TIER_6(6, 60_000, 14, 9.73, 350_000_000L, 50, 2.8F, 0.27F),
    TIER_7(7, 70_000, 15, 10.73, 480_000_000L, 60, 2.8F, 0.30F),
    TIER_8(8, 80_000, 16, 11.73, 640_000_000L, 75, 2.8F, 0.32F),
    TIER_9(9, 90_000, 17, 12.48, 820_000_000L, 90, 2.8F, 0.35F),
    TIER_10(10, 100_000, 18, 13.48, 1_000_000_000L, 120, 2.8F, 0.38F),
    //0.10 : geantes gazeuses, puis les etoiles voisines
    TIER_11(11, 120_000, 19, 14.36, 1_500_000_000L, 150, 3.0F, 0.40F),
    TIER_12(12, 160_000, 20, 15.23, 2_500_000_000L, 180, 3.0F, 0.42F),
    TIER_13(13, 200_000, 21, 16.23, 4_000_000_000L, 240, 3.2F, 0.45F);

    public final int level;
    public final int fuelCapacity;
    public final int height;
    /** Hauteur du siege dans la capsule. */
    public final double riderOffset;
    /** Cout d'assemblage (FE). */
    public final long buildEnergyFE;
    /** Duree d'assemblage (secondes). */
    public final int buildSeconds;
    /** Largeur de la boite de collision (blocs). */
    public final float width;
    private final float probeFailure;

    RocketTier(int level, int fuelCapacity, int height, double riderOffset, long buildEnergyFE, int buildSeconds, float width, float probeFailure) {
        this.level = level;
        this.fuelCapacity = fuelCapacity;
        this.height = height;
        this.riderOffset = riderOffset;
        this.buildEnergyFE = buildEnergyFE;
        this.buildSeconds = buildSeconds;
        this.width = width;
        this.probeFailure = probeFailure;
    }

    public String getName() {
        return "rocket_tier_" + level;
    }

    public Item getItem() {
        return PiItems.ROCKETS.get(this).get();
    }

    public EntityType<RocketEntity> getEntityType() {
        return PiEntityTypes.ROCKETS.get(this).get();
    }

    /** Probabilite qu'une sonde lancee par cette fusee rate sa mise en orbite (fusees plus puissantes = plus complexes). */
    public float probeLaunchFailure() {
        return probeFailure;
    }

    public static RocketTier byIndex(int index) {
        RocketTier[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static RocketTier byLevel(int level) {
        return byIndex(level - 1);
    }
}
