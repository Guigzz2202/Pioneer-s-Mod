package fr.pioneers.kinetics.kitchen;

import fr.pioneers.farm.kitchen.Station;
import java.util.List;
import java.util.Locale;

/**
 * 1.6 : postes mecaniques de la cuisine, entraines par le reseau de rotation de l'age mecanique. Chacun execute DIRECTEMENT les recettes des
 * postes manuels de ses {@link #stations} (types {@code pioneers:<poste>}, voir {@code KitchenRecipe}) : aucune donnee recopiee. Un geste
 * manuel (coup de couteau, de fouet, tour de vis, de manivelle) vaut {@link #turnsPerStroke} tours de l'arbre.
 * <p>Couple de travail, vitesses minimale et nominale (cadence x1, plafonnee a 1,5 x), inertie : memes ordres de grandeur que les autres
 * machines de l'age mecanique (meule 150 N.m a 30 tr/min, scie 60 N.m a 120 tr/min). Les machines horizontales prennent l'arbre par les deux
 * flancs (axe perpendiculaire a la facade, a y 8 et z 8 comme les arbres) ; les verticales (petrin, extracteur) par dessous, comme la meule.
 * <p>Nombre de cases d'entree et de sortie, reservoirs d'entree et de sortie ; API Machine (lot 8b) : leur place dans l'interface v2 vient de
 * {@code StandardLayout} ({@link KitchenProcess}).
 */
public enum KitchenMachineType {
    /** Presse a deux vis : le plateau descend le long des vis tournantes sur la maie ; jus (pressoir) ou meule de fromage (presse a fromage). */
    PRESS("mechanical_press", List.of(Station.PRESSING, Station.CHEESE_PRESSING), 320, 3, 16, 20, 1.0F, false, 1, 1, false, true),
    /** Baratte a palettes : tonneau couche sur l'arbre, batteur a quatre palettes vu par la trappe ouverte. */
    CHURN("mechanical_churn", List.of(Station.CHURNING), 60, 10, 60, 6, 1.0F, false, 1, 1, true, false),
    /** Hachoir a levier : deux cames levent le couteau qui retombe sur la planche (deux coups par tour). */
    CHOPPER("mechanical_chopper", List.of(Station.CUTTING), 90, 12, 60, 8, 0.5F, false, 1, 2, false, false),
    /** Petrin a spirale (arbre par dessous) : la cuve tourne sur l'arbre, la spirale tourne en sens contraire, deux fois plus vite. */
    KNEADER("mechanical_kneader", List.of(Station.MIXING), 140, 6, 36, 14, 1.0F, true, 6, 1, true, false),
    /**
     * Extracteur de miel centrifuge (arbre par dessous) : la cage a cadres tourne vite dans la cuve, le miel coule au fond. 1.0.5-beta (lot 32,
     * retour 41) : 3 sorties DEDIEES (cire, rayon de miel, cadre vide : ordre des produits de la recette) et la paire de cases « Remplir »
     * (fiole ou seau vide -> miel en bouteille ou en seau, {@link #fills}).
     */
    HONEY_EXTRACTOR("mechanical_honey_extractor", List.of(Station.EXTRACTING), 50, 20, 120, 8, 2.0F, true, 1, 3, false, true);

    /** Contenance des reservoirs (mB). */
    public static final int TANK = 4000;

    private final String id;
    public final List<Station> stations;
    /** Couple de travail (N.m), vitesses (tr/min), inertie. */
    public final double torque, minSpeed, nominalSpeed, inertia;
    /** Tours de l'arbre pour un geste manuel. */
    public final float turnsPerStroke;
    /** Arbre par dessous (sinon par les flancs). */
    public final boolean vertical;
    /** Cases d'entree et de sortie, reservoirs d'entree et de sortie. */
    private final int in, out;
    private final boolean tankIn, tankOut;

    KitchenMachineType(String id, List<Station> stations, double torque, double minSpeed, double nominalSpeed, double inertia, float turnsPerStroke,
          boolean vertical, int in, int out, boolean tankIn, boolean tankOut) {
        this.id = id;
        this.stations = stations;
        this.torque = torque;
        this.minSpeed = minSpeed;
        this.nominalSpeed = nominalSpeed;
        this.inertia = inertia;
        this.turnsPerStroke = turnsPerStroke;
        this.vertical = vertical;
        this.in = in;
        this.out = out;
        this.tankIn = tankIn;
        this.tankOut = tankOut;
    }

    public String id() {
        return id;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int inputs() {
        return in;
    }

    public int outputs() {
        return out;
    }

    public boolean hasTankIn() {
        return tankIn;
    }

    public boolean hasTankOut() {
        return tankOut;
    }

    /**
     * 1.0.5-beta (lot 32) : la machine a la paire de cases « Remplir » (contenant vide -> plein, depuis le reservoir de sortie) et range chaque
     * produit dans la sortie de son rang ({@code KitchenProcess}) : l'extracteur de miel.
     */
    public boolean fills() {
        return this == HONEY_EXTRACTOR;
    }

    public boolean handles(Station station) {
        return stations.contains(station);
    }

    /** Puissance nominale (W). */
    public double nominalPower() {
        return torque * nominalSpeed * 2 * Math.PI / 60;
    }
}
