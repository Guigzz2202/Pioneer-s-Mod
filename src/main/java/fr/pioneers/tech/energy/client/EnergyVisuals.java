package fr.pioneers.tech.energy.client;

import fr.pioneers.tech.kinetics.client.KDraw;
import net.minecraft.core.Direction;

/**
 * 0.20.45 : pieces animees des generateurs renouvelables, dessinees par {@link KDraw} (repere local : facade +Z, pixels de bloc). Utilise
 * par le rendu en jeu et par le banc d'essai hors jeu ({@code tools/kinetics}).
 * <p>
 * 1.9.2 (retours de Guillaume) : le rotor est un bloc a part, pose devant la nacelle ; il se dessine dans SA case (arriere z 0 contre la
 * nacelle, avant +Z vers le vent).
 */
public final class EnergyVisuals {

    private EnergyVisuals() {
    }

    /** Centre du moyeu de l'eolienne (repere local de la case du rotor, facade +Z) et plan des pales. */
    public static final float HUB_Y = 8.0F, HUB_Z = 3.4F;
    private static final String[] TIER = {"wind_tier_basic", "wind_tier_advanced", "wind_tier_elite", "wind_tier_ultimate"};

    /**
     * Rotor d'eolienne : palier d'acier contre la nacelle, moyeu cercle a la couleur du tier, nez profile, trois pales vrillees (manchon de
     * pied, corde qui s'affine, bout rouge de balisage) de {@code length} pixels, tournees de {@code angle} degres autour de l'axe.
     */
    public static void windRotor(KDraw d, float angle, float length, int tier) {
        String band = TIER[Math.max(0, Math.min(3, tier))];
        //1.10.1 (rotors agrandis) : corde et epaisseur des pales a l'echelle de leur longueur (52 px = 1), moyeu un peu plus gros aussi
        float c = Math.max(0.8F, length / 52F), th = (float) Math.sqrt(c), hub = 1 + 0.12F * (c - 1);
        //Palier fixe (ne tourne pas) : collerette contre la nacelle
        d.push().rotate(Direction.Axis.X, 90, 8, HUB_Y, 0);
        d.octa(8, 0, 3.4F, HUB_Y, HUB_Y + 1.0F, "wind_bearing", "wind_bearing");
        d.pop();
        d.push().rotate(Direction.Axis.Z, angle, 8, HUB_Y, HUB_Z);
        //Moyeu (axe Z) : cylindre octogonal, bague du tier, nez en trois etages
        d.push().rotate(Direction.Axis.X, 90, 8, HUB_Y, HUB_Z);
        float o = HUB_Y - HUB_Z;
        d.octa(8, HUB_Z, 2.9F * hub, HUB_Z - 2.4F + o, HUB_Z + 1.8F * hub + o, "wind_hub", "wind_hub");
        d.octa(8, HUB_Z, 3.05F * hub, HUB_Z - 1.6F + o, HUB_Z - 0.9F + o, band, band);
        d.octa(8, HUB_Z, 2.3F * hub, HUB_Z + 1.8F * hub + o, HUB_Z + 3.0F * hub + o, "wind_hub", "wind_hub");
        d.octa(8, HUB_Z, 1.5F * hub, HUB_Z + 3.0F * hub + o, HUB_Z + 4.0F * hub + o, "wind_hub", "wind_hub");
        d.octa(8, HUB_Z, 0.7F * hub, HUB_Z + 4.0F * hub + o, HUB_Z + 4.7F * hub + o, "wind_blade_tip", "wind_blade_tip");
        d.pop();
        for (int k = 0; k < 3; k++) {
            d.push().rotate(Direction.Axis.Z, 120 * k, 8, HUB_Y, HUB_Z);
            //Pas de la pale : vrillee de 14 degres autour de son axe
            d.push().rotate(Direction.Axis.Y, 14, 8, HUB_Y, HUB_Z);
            float cuff = HUB_Y + 4.4F * hub, wide = HUB_Y + length * 0.30F, mid = HUB_Y + length * 0.62F, tip = HUB_Y + length;
            float mark = 3.2F * th;
            d.octa(8, HUB_Z, 1.1F * hub, HUB_Y + 2.0F, cuff, "wind_hub", "wind_hub");                              //manchon de pied
            d.box(8 - 1.1F * c, cuff, HUB_Z - 0.45F * th, 8 + 1.1F * c, wide, HUB_Z + 0.45F * th, "wind_blade", "wind_blade");  //corde large
            d.box(8 - 0.85F * c, wide, HUB_Z - 0.36F * th, 8 + 0.95F * c, mid, HUB_Z + 0.36F * th, "wind_blade", "wind_blade");
            d.box(8 - 0.6F * c, mid, HUB_Z - 0.28F * th, 8 + 0.7F * c, tip - mark, HUB_Z + 0.28F * th, "wind_blade", "wind_blade");
            d.box(8 - 0.45F * c, tip - mark, HUB_Z - 0.22F * th, 8 + 0.5F * c, tip, HUB_Z + 0.22F * th, "wind_blade_tip",
                  "wind_blade_tip");                                                                                   //balisage
            d.pop();
            d.pop();
        }
        d.pop();
    }
}
