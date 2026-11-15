package fr.pioneers.weather;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 1.0.5-beta (lot 2) : API COMMUNE DES PRECIPITATIONS, par dimension et par position (demande de Guillaume : « une vraie neige qui ne genere
 * pas de couche [...] ça pourra être réutilisé pour les planètes plus tard »).
 * <p>
 * Une dimension declare un ou plusieurs {@link PrecipitationProvider} ({@link #register}, le plus prioritaire d'abord ; le premier qui
 * repond gagne). Le jeu entier suit leur reponse, cote SERVEUR comme cote CLIENT, par quatre mixins minimalistes ({@code fr.pioneers.mixin}) :
 * <ul>
 *     <li>{@code Level#isRainingAt} : il ne « pleut » que sous la pluie (sous la neige : feux pas eteints, joueur pas mouille, golem de neige
 *     intact, pas d'eclair, terre labouree pas arrosee - comme dans un biome enneige vanilla) ;</li>
 *     <li>{@code ServerLevel#tickChunk} : le chaudron recoit la precipitation (neige poudreuse sous la neige) ;</li>
 *     <li>{@code Biome#shouldSnow} / {@code shouldFreeze} : couche de neige et glace seulement si le fournisseur le dit
 *     ({@link Precipitation#leavesLayer()}, {@link Precipitation#freezesWater()}), aux memes conditions de lieu que le vanilla ;</li>
 *     <li>client {@code LevelRenderer} : pluie et neige DESSINEES par le vanilla (rendu, eclaboussures, bruit) a partir de la vraie precipitation.</li>
 * </ul>
 * Sans fournisseur, ou quand le fournisseur rend null, tout est exactement vanilla. La generation du monde (lacs geles, neige de surface,
 * {@code WorldGenRegion}) n'est jamais touchee.
 * <p>
 * <b>Declarer les precipitations d'un astre</b> (rien a ajouter aux mixins) :
 * <pre>{@code
 * Precipitations.register(MES_DIMENSIONS.MARS, 0, (level, pos, biome) ->
 *       biome.is(MES_TAGS.POLAR) ? Precipitation.SNOW_NO_LAYER          // neige carbonique qui tombe sans couche
 *     : new Precipitation(MES_TYPES.DUST, false, false, 0.6F));        // sorte ajoutee par PrecipitationType.register(id, NONE)
 * }</pre>
 * Le temps de la dimension (pluie ou non) reste celui du vanilla ({@code isRaining}) ; le biome de l'astre doit declarer
 * {@code has_precipitation: true} pour que le vanilla dessine et applique quelque chose (sinon rien ne tombe, comme au desert).
 */
public final class Precipitations {

    /** Fournisseur inscrit avec sa priorite (le plus grand d'abord). */
    public record Entry(ResourceKey<Level> dimension, int priority, PrecipitationProvider provider) {
    }

    private static final Map<ResourceKey<Level>, List<Entry>> PROVIDERS = new ConcurrentHashMap<>();

    private Precipitations() {
    }

    /** Inscrit un fournisseur pour une dimension ; rend l'inscription (pour {@link #unregister}, GameTests). */
    public static Entry register(ResourceKey<Level> dimension, int priority, PrecipitationProvider provider) {
        Entry e = new Entry(dimension, priority, provider);
        List<Entry> list = PROVIDERS.computeIfAbsent(dimension, k -> new CopyOnWriteArrayList<>());
        list.add(e);
        list.sort(Comparator.comparingInt(Entry::priority).reversed());
        return e;
    }

    public static void unregister(Entry e) {
        List<Entry> list = PROVIDERS.get(e.dimension());
        if (list != null) {
            list.remove(e);
        }
    }

    /** Vrai si au moins un fournisseur est inscrit pour cette dimension. */
    public static boolean has(ResourceKey<Level> dimension) {
        List<Entry> list = PROVIDERS.get(dimension);
        return list != null && !list.isEmpty();
    }

    // ------------------------------------------------------------------ lecture

    /** Reponse des fournisseurs en ce lieu, ou null (vanilla). */
    @Nullable
    public static Precipitation at(@Nullable Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        List<Entry> list = PROVIDERS.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return null;
        }
        var biome = level.getBiome(pos);
        for (Entry e : list) {
            Precipitation p = e.provider().at(level, pos, biome);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    /** Sorte de precipitation en ce lieu, reponse vanilla comprise (jamais null). */
    public static PrecipitationType typeAt(Level level, BlockPos pos) {
        Precipitation p = at(level, pos);
        if (p != null) {
            return p.type();
        }
        Biome.Precipitation v = level.getBiome(pos).value().getPrecipitationAt(pos);
        return v == Biome.Precipitation.SNOW ? PrecipitationType.SNOW : v == Biome.Precipitation.RAIN ? PrecipitationType.RAIN : PrecipitationType.NONE;
    }

    // ------------------------------------------------------------------ crochets appeles par les mixins (fr.pioneers.mixin)

    /** {@code Level#isRainingAt}, juste avant la lecture de la precipitation du biome : vrai / faux, ou null = vanilla. */
    @Nullable
    public static Boolean rainingAt(Level level, BlockPos pos) {
        Precipitation p = at(level, pos);
        return p == null ? null : p.type().vanilla() == Biome.Precipitation.RAIN;
    }

    /** Precipitation vanilla equivalente en ce lieu (chaudrons, rendu) ; {@code vanilla} si aucun fournisseur ne repond. */
    public static Biome.Precipitation vanillaAt(@Nullable Level level, BlockPos pos, Biome.Precipitation vanilla) {
        Precipitation p = at(level, pos);
        return p == null ? vanilla : p.type().vanilla();
    }

    /** {@code Biome#shouldSnow} : couche de neige ici ? null = vanilla (aussi pendant la generation du monde, qui n'est pas un Level). */
    @Nullable
    public static Boolean shouldSnow(LevelReader reader, BlockPos pos) {
        if (!(reader instanceof Level level)) {
            return null;
        }
        Precipitation p = at(level, pos);
        if (p == null) {
            return null;
        }
        return p.type().vanilla() == Biome.Precipitation.SNOW && p.leavesLayer() && snowCanSettle(reader, pos);
    }

    /** {@code Biome#shouldFreeze} : l'eau gele ici ? null = vanilla. */
    @Nullable
    public static Boolean shouldFreeze(LevelReader reader, BlockPos pos, boolean mustBeAtEdge) {
        if (!(reader instanceof Level level)) {
            return null;
        }
        Precipitation p = at(level, pos);
        if (p == null) {
            return null;
        }
        return p.freezesWater() && waterCanFreeze(reader, pos, mustBeAtEdge);
    }

    /** Conditions de LIEU du vanilla pour une couche de neige ({@code Biome#shouldSnow} 1.20.1, temperature mise a part). */
    public static boolean snowCanSettle(LevelReader level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.SNOW)) && Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }

    /** Conditions de LIEU du vanilla pour la glace ({@code Biome#shouldFreeze} 1.20.1, temperature mise a part). */
    public static boolean waterCanFreeze(LevelReader level, BlockPos pos, boolean mustBeAtEdge) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        if (fluid.getType() != Fluids.WATER || !(state.getBlock() instanceof LiquidBlock)) {
            return false;
        }
        if (!mustBeAtEdge) {
            return true;
        }
        boolean surrounded = level.isWaterAt(pos.west()) && level.isWaterAt(pos.east()) && level.isWaterAt(pos.north()) && level.isWaterAt(pos.south());
        return !surrounded;
    }
}
