package fr.pioneers.client.rail;

import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.rail.block.Works;
import net.minecraft.core.Direction;

/**
 * 0.20.6 : maillages des ouvrages (pilier de viaduc, garde-corps, portail de tunnel, dalle, abri et lampadaire de quai, barriere de passage a
 * niveau). Repere canonique des blocs orientes : tourne vers le nord ({@link InfraModels#orient}).
 */
public final class WorksModels {

    private WorksModels() {
    }

    /** Pilier de viaduc : fut, chapiteau sous la voie (rien au-dessus), semelle (rien dessous). */
    public static RailMesh pillar(boolean up, boolean down) {
        RailMesh m = new RailMesh();
        m.box(3, 0, 3, 13, 16, 13, BigMat.R_CONCRETE);
        //Joints de coffrage
        for (float y : new float[]{4, 8, 12}) {
            if (!up && y >= 12) {
                continue;
            }
            m.box(2.85F, y - 0.15F, 2.85F, 13.15F, y + 0.15F, 13.15F, BigMat.R_SLAB);
        }
        if (!up) {
            m.box(2, 11, 2, 14, 12.5F, 14, BigMat.R_CONCRETE);
            m.box(1, 12.5F, 1, 15, 16, 15, BigMat.R_CONCRETE);
            m.box(0.85F, 15.2F, 0.85F, 15.15F, 15.5F, 15.15F, BigMat.R_SLAB);
            //Appareils d'appui sous la voie
            for (float x : new float[]{3.5F, 10.5F}) {
                m.box(x, 15.6F, 6.5F, x + 2, 16, 9.5F, BigMat.R_DARK);
            }
        }
        if (!down) {
            m.box(1, 0, 1, 15, 2.5F, 15, BigMat.R_CONCRETE);
            m.box(2, 2.5F, 2, 14, 3.2F, 14, BigMat.R_SLAB);
        }
        return m;
    }

    /** Garde-corps : poteau, main courante, lisse et plinthe vers chaque voisin raccorde. */
    public static RailMesh railing(boolean n, boolean e, boolean s, boolean w) {
        RailMesh m = new RailMesh();
        m.box(7, 0, 7, 9, 15, 9, BigMat.R_STEEL);
        m.box(6.6F, 15, 6.6F, 9.4F, 15.6F, 9.4F, BigMat.R_DARK);
        boolean[] c = {n, e, s, w};
        for (int i = 0; i < 4; i++) {
            if (!c[i]) {
                continue;
            }
            m.rotate('y', 8, 0, 8, -90 * i);
            m.box(7.2F, 13.4F, 0, 8.8F, 14.8F, 7, BigMat.R_STEEL_L);
            m.box(7.5F, 7.4F, 0, 8.5F, 8.4F, 7, BigMat.R_STEEL);
            m.box(7.6F, 0, 0, 8.4F, 2.2F, 7, BigMat.R_HAZARD);
            //Barreaudage
            for (float z = 1.5F; z < 7; z += 2.4F) {
                m.box(7.75F, 2.2F, z, 8.25F, 13.4F, z + 0.5F, BigMat.R_STEEL);
            }
            m.pop();
        }
        return m;
    }

    /** Portail de tunnel (face avant vers le nord dans le repere canonique). */
    public static RailMesh portal(Direction facing, Works.PortalPart part) {
        RailMesh m = new RailMesh();
        m.rotate('y', 8, 0, 8, -90 * idx(facing));
        m.box(0, 0, 0.8F, 16, 16, 16, BigMat.R_CONCRETE);
        //Parement en moellons (assises decalees)
        for (int row = 0; row < 4; row++) {
            float y = row * 4;
            float off = row % 2 == 0 ? 0 : 4;
            for (float x = -off; x < 16; x += 8) {
                float x0 = Math.max(0, x + 0.25F), x1 = Math.min(16, x + 7.75F);
                if (x1 > x0) {
                    m.box(x0, y + 0.25F, 0.2F, x1, y + 3.75F, 0.8F, BigMat.R_SLAB);
                }
            }
        }
        switch (part) {
            case JAMB_L, JAMB_R -> {
                //Pierres d'angle du piedroit, cote ouverture
                float x0 = part == Works.PortalPart.JAMB_L ? 12.5F : 0, x1 = part == Works.PortalPart.JAMB_L ? 16 : 3.5F;
                for (int k = 0; k < 4; k++) {
                    float y = k * 4;
                    float w = k % 2 == 0 ? 0 : 1.5F;
                    float a = part == Works.PortalPart.JAMB_L ? x0 - w : x0, b = part == Works.PortalPart.JAMB_L ? x1 : x1 + w;
                    m.box(a, y + 0.2F, -0.4F, b, y + 3.8F, 0.6F, BigMat.R_CONCRETE);
                }
            }
            case LINTEL_C -> {
                //Voussoirs et cle de voute
                for (int k = 0; k < 5; k++) {
                    float x = 0.4F + k * 3.1F;
                    m.box(x, 0, -0.4F, x + 2.8F, 5.5F, 0.6F, BigMat.R_CONCRETE);
                }
                m.box(6, 0, -1.2F, 10, 7, 0.6F, BigMat.R_CONCRETE);
                m.box(0, 13, -1.2F, 16, 14.5F, 0.6F, BigMat.R_CONCRETE);
            }
            case LINTEL_L, LINTEL_R -> {
                float x0 = part == Works.PortalPart.LINTEL_L ? 11 : 0, x1 = part == Works.PortalPart.LINTEL_L ? 16 : 5;
                m.box(x0, 0, -0.4F, x1, 5.5F, 0.6F, BigMat.R_CONCRETE);
                m.box(0, 13, -1.2F, 16, 14.5F, 0.6F, BigMat.R_CONCRETE);
            }
            default -> {
            }
        }
        m.pop();
        return m;
    }

    /** Dalle de quai : beton, bordure blanche et bande jaune du cote de la voie (nord dans le repere canonique). */
    public static RailMesh platformSlab(Direction toTrack) {
        RailMesh m = new RailMesh();
        m.rotate('y', 8, 0, 8, -90 * idx(toTrack));
        m.box(0, 0, 0, 16, 7.6F, 16, BigMat.R_CONCRETE);
        m.box(0, 7.6F, 2.6F, 16, 8, 16, BigMat.R_SLAB);
        m.box(0, 7.6F, 0, 16, 8, 1.2F, BigMat.R_WHITE);
        m.box(0, 7.6F, 1.2F, 16, 8.05F, 2.6F, BigMat.R_HAZARD);
        m.box(0, 5.6F, -0.6F, 16, 7.6F, 0, BigMat.R_CONCRETE);
        m.pop();
        return m;
    }

    /** Lampadaire de quai (deux blocs de haut). */
    public static RailMesh lamp() {
        RailMesh m = new RailMesh();
        m.box(5.5F, 0, 5.5F, 10.5F, 1.2F, 10.5F, BigMat.R_DARK);
        m.box(6.3F, 1.2F, 6.3F, 9.7F, 4, 9.7F, BigMat.R_STEEL);
        m.cyl('y', 8, 8, 0.9F, 4, 25, BigMat.R_STEEL, 8);
        m.box(7.2F, 24.2F, 7.2F, 8.8F, 25, 13, BigMat.R_STEEL);
        m.box(5.6F, 23.4F, 9.6F, 10.4F, 24.4F, 15.2F, BigMat.R_DARK);
        m.box(6.2F, 22.8F, 10.2F, 9.8F, 23.4F, 14.6F, BigMat.R_LAMP);
        return m;
    }

    /** Barriere de passage a niveau (voie au nord) : poteau, feux, lisse fermee a l'horizontale ou levee. */
    public static RailMesh barrier(Direction toTrack, boolean closed, boolean blink) {
        RailMesh m = new RailMesh();
        m.rotate('y', 8, 0, 8, -90 * idx(toTrack));
        m.box(0.6F, 0, 0.6F, 3.9F, 1, 3.9F, BigMat.R_DARK);
        m.box(1.4F, 1, 1.4F, 3.1F, 13, 3.1F, BigMat.R_WHITE);
        m.box(1.2F, 4, 1.2F, 3.3F, 4.6F, 3.3F, BigMat.R_RED);
        m.box(1.2F, 8, 1.2F, 3.3F, 8.6F, 3.3F, BigMat.R_RED);
        //Feux tournes vers la route (au sud), allumes en alternance quand c'est ferme
        m.box(0.2F, 9.6F, 3.3F, 4.5F, 11.6F, 3.8F, BigMat.R_BLACK);
        m.box(0.6F, 10, 3.8F, 1.8F, 11.2F, 4.2F, closed && blink ? BigMat.R_LAMP_R : BigMat.R_LAMP_OFF);
        m.box(2.9F, 10, 3.8F, 4.1F, 11.2F, 4.2F, closed && !blink ? BigMat.R_LAMP_R : BigMat.R_LAMP_OFF);
        //Contrepoids et lisse (pivot en haut du poteau)
        m.translate(2.25F, 12.2F, 2.25F);
        m.rotate('z', 0, 0, 0, closed ? 0 : 82);
        m.box(-3.6F, -0.9F, -0.7F, -1.0F, 0.9F, 0.7F, BigMat.R_DARK);
        for (int k = 0; k < 7; k++) {
            float x = 1.0F + k * 2.0F;
            m.box(x, -0.6F, -0.45F, x + 2.0F, 0.6F, 0.45F, k % 2 == 0 ? BigMat.R_RED : BigMat.R_WHITE);
        }
        m.pop();
        m.pop();
        m.pop();
        return m;
    }

    private static int idx(Direction d) {
        return switch (d) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }
}
