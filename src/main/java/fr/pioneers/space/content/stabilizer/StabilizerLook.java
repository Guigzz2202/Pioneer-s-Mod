package fr.pioneers.space.content.stabilizer;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Teinte de la paroi du Stabilisateur spatial (propriete d'etat {@code regime}), posee par le noyau a la formation : gris neutre sans noyau,
 * violet pour la singularite, jaune-orange pour l'etoile. Purement visuel (les modeles choisissent les textures de chaque teinte).
 */
public enum StabilizerLook implements StringRepresentable {
    NONE("none"),
    SINGULARITY("singularity"),
    STAR("star");

    public static final EnumProperty<StabilizerLook> PROPERTY = EnumProperty.create("regime", StabilizerLook.class);
    private final String id;

    StabilizerLook(String id) {
        this.id = id;
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return id;
    }

    /** Teinte d'un regime. */
    public static StabilizerLook of(Regime regime) {
        return regime == Regime.STAR ? STAR : SINGULARITY;
    }
}
