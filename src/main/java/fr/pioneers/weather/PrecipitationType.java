package fr.pioneers.weather;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.0.5-beta (lot 2) : SORTE de precipitation de l'API commune ({@link Precipitations}). Trois sortes de base ({@link #NONE}, {@link #RAIN},
 * {@link #SNOW}) ; d'autres pourront s'ajouter plus tard par {@link #register} (pluie acide, poussiere, cendres...) sans toucher a l'API.
 * <p>
 * Chaque sorte dit a quelle precipitation VANILLA elle se ramene ({@link #vanilla()}) : c'est ce que voient le jeu vanilla et les mixins
 * (rendu, joueur mouille, feux eteints, chaudrons, eclairs). Une pluie acide se comportera donc comme une pluie (et ajoutera ses propres
 * effets), une poussiere comme « rien ».
 */
public final class PrecipitationType {

    private static final Map<ResourceLocation, PrecipitationType> ALL = new LinkedHashMap<>();

    /** Rien ne tombe. */
    public static final PrecipitationType NONE = register(new ResourceLocation("minecraft", "none"), Biome.Precipitation.NONE);
    /** Pluie (vanilla). */
    public static final PrecipitationType RAIN = register(new ResourceLocation("minecraft", "rain"), Biome.Precipitation.RAIN);
    /** Neige (vanilla). */
    public static final PrecipitationType SNOW = register(new ResourceLocation("minecraft", "snow"), Biome.Precipitation.SNOW);

    private final ResourceLocation id;
    private final Biome.Precipitation vanilla;

    private PrecipitationType(ResourceLocation id, Biome.Precipitation vanilla) {
        this.id = id;
        this.vanilla = vanilla;
    }

    /**
     * Declare une nouvelle sorte (a faire au chargement du mod, une fois).
     *
     * @param vanilla comportement vanilla de repli (rendu, pluie sur les joueurs et les feux, chaudrons).
     */
    public static synchronized PrecipitationType register(ResourceLocation id, Biome.Precipitation vanilla) {
        if (ALL.containsKey(id)) {
            throw new IllegalStateException("Sorte de precipitation deja declaree : " + id);
        }
        PrecipitationType t = new PrecipitationType(id, vanilla);
        ALL.put(id, t);
        return t;
    }

    @Nullable
    public static PrecipitationType byId(ResourceLocation id) {
        return ALL.get(id);
    }

    public static Collection<PrecipitationType> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }

    public ResourceLocation id() {
        return id;
    }

    /** Precipitation vanilla equivalente. */
    public Biome.Precipitation vanilla() {
        return vanilla;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
