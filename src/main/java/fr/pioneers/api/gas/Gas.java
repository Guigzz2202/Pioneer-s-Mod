package fr.pioneers.api.gas;

import fr.pioneers.api.PioneersAPI;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gaz du socle Pioneer's (independant de Mekanism) : une couleur, et des proprietes facultatives (combustible, caloporteur). Les gaz
 * sont enregistres dans le registre {@link PioneersAPI#GAS_REGISTRY_NAME}. Un gaz « etranger » (d'un autre mod, vu par une
 * passerelle de compatibilite) n'est pas enregistre mais porte son identifiant : deux gaz sont egaux s'ils ont le meme identifiant.
 */
public class Gas {

    /** Gaz nul, celui des piles vides (enregistre sous {@code pioneers:empty}). */
    public static final Gas EMPTY = new Gas(new Properties(0x000000).hidden());
    /** Texture (atlas des blocs) par defaut, en niveaux de gris, teintee par la couleur du gaz. */
    public static final ResourceLocation DEFAULT_ICON = ResourceLocation.fromNamespaceAndPath(PioneersAPI.MODID, "block/gas");

    private final Properties properties;
    @Nullable
    private final ResourceLocation foreignId;
    @Nullable
    private String translationKey;

    public Gas(Properties properties) {
        this(properties, null);
    }

    private Gas(Properties properties, @Nullable ResourceLocation foreignId) {
        this.properties = properties;
        this.foreignId = foreignId;
    }

    /** Gaz d'un autre mod (non enregistre ici), identifie par son nom. */
    public static Gas foreign(ResourceLocation id, int tint, @Nullable String translationKey) {
        return foreign(id, tint, translationKey, null, false);
    }

    public static Gas foreign(ResourceLocation id, int tint, @Nullable String translationKey, @Nullable ResourceLocation icon, boolean hidden) {
        Properties properties = new Properties(tint);
        if (icon != null) {
            properties.icon(icon);
        }
        if (hidden) {
            properties.hidden();
        }
        Gas gas = new Gas(properties, id);
        gas.translationKey = translationKey;
        return gas;
    }

    public int getTint() {
        return properties.tint;
    }

    /** Texture (atlas des blocs) du gaz, a teinter par {@link #getTint()}. */
    public ResourceLocation getIcon() {
        return properties.icon;
    }

    /** Cache des listes de choix (gaz techniques). */
    public boolean isHidden() {
        return properties.hidden;
    }

    public boolean isEmptyType() {
        return this == EMPTY;
    }

    public boolean isForeign() {
        return foreignId != null;
    }

    /** Energie (FE) rendue par mB brule, 0 : pas un combustible. */
    public long getFuelEnergy() {
        return properties.fuelEnergy;
    }

    /** Caloporteur : le gaz une fois chauffe (ou refroidi), ou null. */
    @Nullable
    public Gas getHeated() {
        return properties.heated == null ? null : properties.heated.get();
    }

    @Nullable
    public Gas getCooled() {
        return properties.cooled == null ? null : properties.cooled.get();
    }

    @Nullable
    public ResourceLocation getRegistryName() {
        return foreignId != null ? foreignId : PioneersAPI.gasRegistry().getKey(this);
    }

    /** Cle de traduction : {@code gas.<namespace>.<chemin>} (la meme que Mekanism, les traductions existantes servent). */
    @NotNull
    public String getTranslationKey() {
        if (translationKey == null) {
            translationKey = Util.makeDescriptionId("gas", getRegistryName());
        }
        return translationKey;
    }

    public Component getName() {
        return Component.translatable(getTranslationKey());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        //Deux gaz etrangers sont egaux s'ils ont le meme identifiant ; un gaz enregistre n'est egal qu'a lui-meme
        return other instanceof Gas gas && foreignId != null && foreignId.equals(gas.foreignId);
    }

    /** Gaz enregistre : identite (le registre lui-meme s'en sert, pas de recherche ici) ; gaz etranger : son identifiant. */
    @Override
    public int hashCode() {
        return foreignId != null ? foreignId.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        ResourceLocation name = getRegistryName();
        return "Gas[" + (name == null ? "unregistered" : name) + "]";
    }

    /** Proprietes d'un gaz. */
    public static final class Properties {

        private final int tint;
        private long fuelEnergy;
        private ResourceLocation icon = DEFAULT_ICON;
        private boolean hidden;
        @Nullable
        private Supplier<Gas> heated, cooled;

        public Properties(int tint) {
            this.tint = tint & 0xFFFFFF;
        }

        /** Texture propre (atlas des blocs). */
        public Properties icon(ResourceLocation icon) {
            this.icon = icon;
            return this;
        }

        /** Gaz technique, absent des listes de choix. */
        public Properties hidden() {
            this.hidden = true;
            return this;
        }

        /** Combustible : FE rendus par mB. */
        public Properties fuel(long energyPerMb) {
            this.fuelEnergy = Math.max(0, energyPerMb);
            return this;
        }

        /** Caloporteur froid : ce qu'il devient une fois chauffe. */
        public Properties heatsInto(Supplier<Gas> heated) {
            this.heated = heated;
            return this;
        }

        /** Caloporteur chaud : ce qu'il redevient une fois refroidi. */
        public Properties coolsInto(Supplier<Gas> cooled) {
            this.cooled = cooled;
            return this;
        }
    }
}
