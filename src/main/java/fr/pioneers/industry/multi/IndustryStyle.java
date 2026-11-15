package fr.pioneers.industry.multi;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Habillage d'un multibloc (0.16.4) : une seule famille visuelle (acier brosse, joints sombres, textures connectees), declinee par metier.
 * A la formation, carcasses, verre, hublots, ports et controleur prennent le metier de la machine ({@link #STYLE}) et son tier ({@link #TIER}).
 * Thermique (chaleur et froid), electrique, fluides et chimie, mecanique, vivant. Neutre : structure libre.
 * Les anciens styles (0.16.1 a 0.16.3) inconnus au chargement reprennent la valeur par defaut, puis le controleur repeint a la formation.
 */
public enum IndustryStyle implements StringRepresentable {
    NEUTRAL, THERMAL, ELECTRIC, FLUID, MECHANICAL, LIVING,
    /** 0.17.0 : nucleaire (beton et plomb) et numerique (aluminium anodise). */
    NUCLEAR, DIGITAL;

    public static final EnumProperty<IndustryStyle> STYLE = EnumProperty.create("style", IndustryStyle.class);
    /** Tier peint sur les blocs a texture unique (verre, hublot, ports, controleur) : 0 = libre, 1..4 = basique..ultime. */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 4);

    @NotNull
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
