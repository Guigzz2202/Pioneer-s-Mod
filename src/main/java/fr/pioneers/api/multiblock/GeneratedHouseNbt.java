// FICHIER GENERE par tools/gc/industry/nbt_shapes.py (1.0.4-beta, lot 24) depuis les NBT de Guillaume : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.api.multiblock;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 24) : STRUCTURES DES MULTIBLOCS DE L'AGE MECANIQUE ET DU BRONZE d'apres les NBT de Guillaume ({@code
 * tools/gc/industry/nbt/dev_v1}, table fichier -> cle : {@code HOUSE} de {@code T:industry/nbt_shapes.py}) : boite dans le repere du controleur (x a droite en regardant la facade, y en haut, z vers l'interieur),
 * case du controleur et une lettre par case (index ((y * sz) + z) * sx + x) : K controleur, X foyer en briques refractaires, B briques
 * refractaires, T tuyere, V cuve de fonderie, b socle de foreuse (ou prise), f derrick, c couronne, D train de forage en bronze, o corps de
 * chaudiere, d dome de vapeur, k cheminee, w indicateur de niveau (facultatif), r briques, / escalier en briques, _ dalle de briques,
 * I air interieur, . case libre. {@code states} : orientation gardee du NBT (escaliers, dalle, tuyere, indicateur), « index:prop=val,... »
 * separes par « ; », facing RELATIF a la facade (front, back, left, right du joueur qui la regarde). Lue par {@link HouseNbt}.
 */
public final class GeneratedHouseNbt {

    /** Une structure : cle, taille, controleur, lettres, orientations. */
    public record Shape(String key, int sx, int sy, int sz, int cx, int cy, int cz, String layout, String states) {

        public char at(int x, int y, int z) {
            return layout.charAt((y * sz + z) * sx + x);
        }
    }

    private static final Map<String, Shape> BY_KEY = new HashMap<>();

    static {
        put("auto_foundry", 3, 5, 3, 1, 0, 0,
              "XKXXXXXTXVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV",
              "7:facing=back");
        put("blast_furnace", 5, 7, 5, 2, 0, 0,
              ".XKX.XXXXXXXXXXXXXXX.XTX..BBB.BBIBBBIIIBBBIBB.BBB..BBB.BBIBBBIIIBBBIBB.BBB...B...BBB.BBIBB.BBB...B........BBB."
              + ".BIB..BBB.............B...BIB...B..............B...BIB...B.......",
              "22:facing=back");
        put("bronze_boiler", 3, 6, 3, 1, 0, 0,
              ".K.XXX.X.wo.ooo.o..o.ooo.o..d..o........k........k....",
              "9:facing=left");
        put("coke_oven", 3, 4, 3, 1, 0, 0,
              "XKXXXXXTXBBBBIBBBBBBBBIBBBB.B.BBB.B.",
              "7:facing=back");
        put("pottery_kiln", 3, 4, 3, 1, 1, 0,
              "rrrrrrrrr/K//I//r/._../..r........r.",
              "9:facing=right,half=bottom;11:facing=left,half=bottom;12:facing=right,half=bottom;14:facing=left,half=bottom;15:facing=right,half=bottom;17:facing=left,half=bottom;19:type=bottom;22:facing=back,half=bottom");
        put("steam_drill", 3, 7, 3, 1, 0, 0,
              "bKbbDbbbb.f.fDf.f..f.fDf.f..f.fDf.f.....f........f........c....",
              "");
        put("steel_boiler", 3, 6, 3, 1, 0, 0,
              ".K.XXX.X..o.ooo.o..o.ooo.o..d..o........k........k....",
              "");
    }

    private GeneratedHouseNbt() {
    }

    private static void put(String key, int sx, int sy, int sz, int cx, int cy, int cz, String layout, String states) {
        BY_KEY.put(key, new Shape(key, sx, sy, sz, cx, cy, cz, layout, states));
    }

    /** Structure maison de la cle (bronze_boiler, steel_boiler, pottery_kiln, blast_furnace, coke_oven, auto_foundry, steam_drill), ou null. */
    @Nullable
    public static Shape of(String key) {
        return BY_KEY.get(key);
    }
}
