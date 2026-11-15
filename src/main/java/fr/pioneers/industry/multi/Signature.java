package fr.pioneers.industry.multi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Piece signature exigee par le gabarit d'un multibloc : une piece ({@link PartBlock.Kind}), la zone ou elle doit se trouver, le nombre
 * minimal. Exemple : refrigerateur a dilution au sommet (1.0.4-beta, lot 15 : seule piece restante).
 */
public record Signature(PartBlock.Kind part, Zone zone, int count) {

    /** Zones de l'enveloppe ({@link #bit} : masque dans les cellules d'un gabarit ; SIDE = toute l'enveloppe). */
    public enum Zone {
        TOP(1), LOW_RING(2), RIDGE(4), RIM(8), BACK(16), SIDE(0),
        /** 0.17.1 : dalle (toit plat sans son bord), equateur de la sphere. */
        ROOF(32), EQUATOR(64),
        /** 0.17.2 : anneaux exterieur et interieur du tore (a mi-hauteur). */
        OUTER(128), INNER(256);

        public final int bit;

        Zone(int bit) {
            this.bit = bit;
        }

        public boolean in(int zones) {
            return bit == 0 || (zones & bit) != 0;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Lecture du catalogue : {@code "dilution_refrigerator:top:1"}. 1.0.4-beta (lot 15) : {@code "casing:top:1"} n'est pas une piece (carcasse
     * rapportee au sommet, {@code IndustryMachine#summitCasing}) : sautee ici.
     */
    public static List<Signature> parse(String[] specs) {
        List<Signature> out = new ArrayList<>();
        for (String spec : specs) {
            String[] p = spec.split(":");
            if (p[0].equals("casing")) {
                continue;
            }
            out.add(new Signature(PartBlock.Kind.valueOf(p[0].toUpperCase(Locale.ROOT)), Zone.valueOf(p[1].toUpperCase(Locale.ROOT)), Integer.parseInt(p[2])));
        }
        return List.copyOf(out);
    }

    /** Detail d'erreur (valeur d'interface) : 1 + piece * 16 + zone ; 0 = rien. */
    public int detail() {
        return 1 + part.ordinal() * 16 + zone.ordinal();
    }
}
