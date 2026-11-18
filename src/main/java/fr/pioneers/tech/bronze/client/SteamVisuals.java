package fr.pioneers.tech.bronze.client;

import fr.pioneers.tech.kinetics.client.KDraw;
import net.minecraft.core.Direction;

/** Parties mobiles des organes de vapeur (communes au jeu et au banc d'essai des apercus). */
public final class SteamVisuals {

    private SteamVisuals() {
    }

    /**
     * Aiguille du manometre, repere « vers le haut » (cadran horizontal en y 8, centre 8/8). {@code t} = pression / pleine echelle : 0 en bas a
     * gauche, 1 en bas a droite (270 degres).
     */
    public static void needle(KDraw d, float t) {
        float a = 135 - 270 * Math.max(0, Math.min(1.04F, t));
        d.push().rotate(Direction.Axis.Y, a, 8, 8, 8);
        d.box(7.75F, 8.0F, 4.2F, 8.25F, 8.3F, 8.0F, "steel", "steel");
        d.box(7.85F, 8.0F, 8.0F, 8.15F, 8.3F, 9.6F, "steel", "steel");
        d.pop();
        d.box(7.4F, 8.0F, 7.4F, 8.6F, 8.5F, 8.6F, "brass", "brass");
    }

    /** Colonne d'eau du tube de niveau monte sur un flanc (repere facade +Z) ; {@code level} 0..1. */
    public static void waterSide(KDraw d, float level) {
        if (level > 0.01F) {
            d.box(7.3F, 3.1F, 4.3F, 8.7F, 3.1F + 9.8F * Math.min(1, level), 5.7F, "water", "water");
        }
    }

    /** Colonne d'eau du tube de niveau pose a plat (dessus). */
    public static void waterTop(KDraw d, float level) {
        if (level > 0.01F) {
            d.box(7.3F, 2.1F, 7.3F, 8.7F, 2.1F + 10.8F * Math.min(1, level), 8.7F, "water", "water");
        }
    }

    /**
     * Masse du marteau-pilon et sa tige ; {@code h} = 0 a la frappe, 1 en haut. Repere facade +Z. 1.0.2-beta (lot 3) : course de 4,4 px sous
     * la traverse (y 12), tige jusqu'au cylindre (y 13,6) : tout tient dans le bloc (avant : jusqu'a y 25,5).
     */
    public static void hammerRam(KDraw d, float h) {
        float y = 4.4F + 4.4F * h;
        d.box(4.5F, y, 4.5F, 11.5F, y + 3.2F, 11.5F, "steel", "iron");
        d.box(3.9F, y + 0.8F, 6.5F, 4.5F, y + 2.4F, 9.5F, "brass", "brass");
        d.box(11.5F, y + 0.8F, 6.5F, 12.1F, y + 2.4F, 9.5F, "brass", "brass");
        d.box(5.0F, y - 0.4F, 5.0F, 11.0F, y, 11.0F, "iron_bright", "iron_bright");
        d.box(7.3F, y + 3.2F, 7.3F, 8.7F, 13.6F, 8.7F, "iron_bright", "iron_bright");
    }

    /** Plateau mobile de la presse ; {@code h} = 0 presse en bas, 1 en haut. 1.0.2-beta (lot 3) : course de 4,6 px sous le bras (y 10,6). */
    public static void pressPlaten(KDraw d, float h) {
        float y = 4.1F + 4.6F * h;
        d.box(3.5F, y, 5.0F, 12.5F, y + 1.6F, 13.4F, "steel", "iron");
        d.box(5.0F, y - 0.5F, 6.5F, 11.0F, y, 12.5F, "iron_bright", "iron_bright");
        d.box(7.2F, y + 1.6F, 8.7F, 8.8F, 10.6F, 10.3F, "iron_bright", "iron_bright");
    }

    /** Table de rotation et tige carree de la foreuse (du puits jusqu'a la couronne, 4 blocs plus haut). */
    public static void drillString(KDraw d, float angle) {
        d.push().rotate(Direction.Axis.Y, angle, 8, 8, 8);
        d.box(3.0F, 13.6F, 3.0F, 13.0F, 15.2F, 13.0F, "steel", "iron");
        d.box(2.2F, 13.8F, 5.0F, 13.8F, 15.0F, 11.0F, "steel", "iron");
        d.box(5.0F, 13.8F, 2.2F, 11.0F, 15.0F, 13.8F, "steel", "iron");
        d.box(6.4F, 15.2F, 6.4F, 9.6F, 15.8F, 9.6F, "brass", "brass");
        d.box(6.8F, -2.0F, 6.8F, 9.2F, 70.0F, 9.2F, "iron_bright", "iron");
        for (int k = 0; k < 4; k++) {
            float y = 20 + k * 14;
            d.box(6.4F, y, 6.4F, 9.6F, y + 1.2F, 9.6F, "steel", "steel");
        }
        d.pop();
    }
}
