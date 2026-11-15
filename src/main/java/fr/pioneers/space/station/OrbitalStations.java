package fr.pioneers.space.station;

import fr.pioneers.space.SolarSystem.Body;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Stations orbitales : une au plus par astre, toutes dans la dimension {@code pioneers:orbit}, chacune dans sa propre region
 * (l'astre survole se deduit donc de la position). La liste des stations deployees est sauvegardee avec le monde.
 */
public class OrbitalStations extends SavedData {

    private static final String NAME = "pioneers_orbital_stations";
    /** Espacement des regions (blocs, selon x). */
    public static final int REGION_SPACING = 4096;
    /** Niveau du pont des stations. */
    public static final int DECK_Y = 100;
    /** Altitude d'arrivee de la capsule au-dessus du pont. */
    public static final int ARRIVAL_HEIGHT = 40;

    private final Set<Body> deployed = EnumSet.noneOf(Body.class);

    public static OrbitalStations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OrbitalStations::load, OrbitalStations::new, NAME);
    }

    /**
     * Astres qui peuvent avoir une station : tous, sauf la ceinture elle-meme. Les stations autour des etoiles (absorbeur d'energie
     * solaire) demandent la recherche « Absorption stellaire ».
     */
    public static boolean canHost(Body body) {
        return body != Body.ASTEROID_BELT;
    }

    /** Centre du pont de la station de cet astre. */
    public static BlockPos origin(Body body) {
        return new BlockPos(body.ordinal() * REGION_SPACING, DECK_Y, 0);
    }

    /** Astre survole depuis cette position de la dimension orbitale (null hors des regions). */
    @Nullable
    public static Body bodyAt(double x) {
        int index = (int) Math.round(x / REGION_SPACING);
        if (index < 0 || index >= Body.ALL.size() || Math.abs(x - index * REGION_SPACING) > REGION_SPACING / 4.0) {
            return null;
        }
        Body body = Body.ALL.get(index);
        return canHost(body) ? body : null;
    }

    public boolean has(Body body) {
        return deployed.contains(body);
    }

    public void markDeployed(Body body) {
        if (deployed.add(body)) {
            setDirty();
        }
    }

    private static OrbitalStations load(CompoundTag tag) {
        OrbitalStations stations = new OrbitalStations();
        ListTag list = tag.getList("stations", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                stations.deployed.add(Body.valueOf(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                //Astre disparu d'une version a l'autre
            }
        }
        return stations;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Body body : deployed) {
            list.add(StringTag.valueOf(body.name()));
        }
        tag.put("stations", list);
        return tag;
    }
}
