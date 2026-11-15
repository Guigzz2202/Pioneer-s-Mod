package fr.pioneers.farm.greenhouse;

import java.util.Locale;

/**
 * 1.0.5-beta (lot 3) : matieres des blocs de serre : bois traite (age du bronze : le bois impregne de creosote) et fer (plus solide, resiste
 * mieux aux explosions). Meme role dans la serre ; ids {@code <matiere>_greenhouse_<glass|frame|vent>}.
 */
public enum GreenhouseMaterial {
    TREATED(0.8F, 3.0F),
    IRON(1.6F, 6.0F);

    public final float hardness, resistance;

    GreenhouseMaterial(float hardness, float resistance) {
        this.hardness = hardness;
        this.resistance = resistance;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
