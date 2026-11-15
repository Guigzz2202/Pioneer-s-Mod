package fr.pioneers.client.naval;

import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.client.rail.RailMesh;
import fr.pioneers.naval.HarborEquipment;

/**
 * 0.20.14 : machines des equipements de quai (repere du bloc en px : x 0..16, y vers le haut, cote de l'eau vers +z ; la dalle 0..16 est le
 * modele du bloc, la machine part de y = 16). Maillage refait a chaque image avec l'etat d'animation :
 * <ul>
 * <li>{@code deploy} 0..1 : range (0) ou tourne vers le navire amarre (1) ;</li>
 * <li>{@code phase} : ticks de travail (cycles de la grue, flux lumineux) ;</li>
 * <li>{@code reach} : distance horizontale du centre du bloc a l'axe du navire (blocs) ; {@code drop} : hauteur du pont du navire au-dessus
 * du dessus du bloc (blocs).</li>
 * </ul>
 * Grue de quai a fleche horizontale et chariot (va-et-vient navire / quai avec un conteneur), bras de chargement articules (fluide, gaz, air,
 * soutage), borne de recharge a cable, mat de donnees a parabole et faisceau, passerelle couverte qui s'abaisse sur le pont.
 */
public final class HarborModels {

    private HarborModels() {
    }

    static final float TOP = 16;

    public static RailMesh build(HarborEquipment kind, int tier, float deploy, float phase, float reach, float drop, boolean lit) {
        RailMesh m = new RailMesh();
        float d = smooth(Math.max(0, Math.min(1, deploy)));
        //Socle commun : platine sombre boulonnee, liseret du tier
        m.box(2, TOP, 2, 14, TOP + 1.0F, 14, BigMat.R_DARK);
        ShipModels.trim(m, tier, 2, 14, TOP + 0.6F, TOP + 1.1F, 2, 14);
        for (float x : new float[]{3, 12.4F}) {
            for (float z : new float[]{3, 12.4F}) {
                m.box(x, TOP + 1.0F, z, x + 0.6F, TOP + 1.4F, z + 0.6F, BigMat.R_STEEL);
            }
        }
        switch (kind) {
            case CRANE -> crane(m, tier, d, phase, reach, drop);
            case FLUID_ARM -> arm(m, tier, d, phase, reach, drop, BigMat.R_FAM_FLUID, 1.3F, BigMat.R_STEEL, false);
            case GAS_ARM -> arm(m, tier, d, phase, reach, drop, BigMat.R_FAM_GAS, 1.7F, BigMat.R_WHITE, false);
            case AIR_ARM -> arm(m, tier, d, phase, reach, drop, BigMat.R_FAM_PRESSURE, 1.0F, BigMat.R_STEEL_L, false);
            case BUNKER -> arm(m, tier, d, phase, reach, drop, BigMat.R_FAM_HEAT, 1.2F, BigMat.R_DARK, true);
            case CHARGER -> charger(m, tier, d, phase, reach, drop, lit);
            case DATA_MAST -> dataMast(m, tier, d, phase, reach, drop);
            case GANGWAY -> gangway(m, tier, d, reach, drop);
        }
        return m;
    }

    /** Avec la dalle de quai dessous (objet, apercus) : beton, bande jaune et noire cote eau. */
    public static RailMesh withSlab(HarborEquipment kind, int tier, float deploy, float phase, float reach, float drop) {
        RailMesh m = build(kind, tier, deploy, phase, reach, drop, false);
        slab(m);
        return m;
    }

    public static void slab(RailMesh m) {
        m.box(0, 0, 0, 16, 15.6F, 16, BigMat.R_CONCRETE);
        m.box(0, 15.6F, 0, 16, 16, 13, BigMat.R_CONCRETE);
        m.box(0, 15.6F, 13, 16, 16.05F, 16, BigMat.R_HAZARD);
    }

    static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Point d'arrivee sur le pont du navire (z local, y local), d'apres la distance et le denivele. */
    static float deckZ(float reach) {
        //Bord du navire a (reach - largeur/2) ; on vise un peu en dedans : reach * 16 - 14 px depuis le centre
        return 8 + Math.max(16, reach * 16 - 14);
    }

    static float deckY(float drop) {
        return TOP + drop * 16 + 1.5F;
    }

    // ------------------------------------------------------------------ grue de quai

    /**
     * Grue a fleche horizontale : fut rond, cabine tournante, fleche de 46 px et contre-fleche lestee, chariot et crochet. Rangee le long du
     * quai ; deployee : va-et-vient entre le navire et le quai (cycle de 160 ticks : descente, montee, rotation, descente, montee, retour),
     * un conteneur accroche pendant l'aller.
     */
    static void crane(RailMesh m, int tier, float d, float phase, float reach, float drop) {
        float cx = 8, cz = 8;
        //Fut et couronne
        m.cyl('y', cx, cz, 3.2F, TOP + 1, TOP + 20, BigMat.R_STRIPE, 12);
        m.cyl('y', cx, cz, 3.5F, TOP + 8, TOP + 9.5F, ShipModels.tierMat(tier), 12);
        m.cyl('y', cx, cz, 4.0F, TOP + 20, TOP + 21.2F, BigMat.R_DARK, 14);
        //Cycle de travail
        float slew, trolley, hook, load;
        float target = deckZ(reach) - cz;
        float cyc = (phase % 160) / 160F;
        boolean working = phase > 0;
        if (!working || d < 0.99F) {
            slew = 90 * (1 - d);
            trolley = 10 + (target - 10) * d;
            hook = 0;
            load = 0;
        } else {
            //0-0.2 descente au navire, 0.2-0.35 montee, 0.35-0.55 rotation vers le quai, 0.55-0.7 descente, 0.7-0.8 montee, 0.8-1 retour
            float back = 150;
            if (cyc < 0.2F) {
                slew = 0;
                hook = cyc / 0.2F;
                load = 1;
            } else if (cyc < 0.35F) {
                slew = 0;
                hook = 1 - (cyc - 0.2F) / 0.15F;
                load = 0;
            } else if (cyc < 0.55F) {
                slew = back * smooth((cyc - 0.35F) / 0.2F);
                hook = 0;
                load = 0;
            } else if (cyc < 0.7F) {
                slew = back;
                hook = (cyc - 0.55F) / 0.15F;
                load = 0;
            } else if (cyc < 0.8F) {
                slew = back;
                hook = 1 - (cyc - 0.7F) / 0.1F;
                load = 1;
            } else {
                slew = back * (1 - smooth((cyc - 0.8F) / 0.2F));
                hook = 0;
                load = 1;
            }
            trolley = target;
        }
        m.rotate('y', cx, 0, cz, slew);
        //Cabine (vitree vers la fleche), machinerie, contre-fleche et lest
        float y0 = TOP + 21.2F;
        m.box(cx - 3.6F, y0, cz - 3.6F, cx + 3.6F, y0 + 6, cz + 3.0F, BigMat.R_STRIPE);
        m.box(cx - 2.6F, y0 + 1.6F, cz + 2.95F, cx + 2.6F, y0 + 5.2F, cz + 3.1F, BigMat.R_GLASS_D);
        m.box(cx - 3.8F, y0 + 6, cz - 3.8F, cx + 3.8F, y0 + 6.6F, cz + 3.2F, BigMat.R_WHITE);
        m.box(cx - 2.2F, y0 + 6.6F, cz - 12, cx + 2.2F, y0 + 8.4F, cz - 3, BigMat.R_DARK);
        m.box(cx - 3.0F, y0 + 3.0F, cz - 15, cx + 3.0F, y0 + 9.0F, cz - 11.5F, BigMat.R_CONCRETE);
        //Fleche en treillis : deux membrures, entretoises, sommet de tirants
        float jy = y0 + 7, len = 46;
        for (float s : new float[]{-1, 1}) {
            m.box(cx + s * 1.6F - 0.35F, jy, cz, cx + s * 1.6F + 0.35F, jy + 0.7F, cz + len, BigMat.R_STRIPE);
            m.box(cx + s * 1.6F - 0.3F, jy + 2.6F, cz - 2, cx + s * 1.6F + 0.3F, jy + 3.2F, cz + len * 0.6F, BigMat.R_STRIPE);
        }
        for (float z = 2; z < len; z += 4) {
            m.box(cx - 1.6F, jy, cz + z, cx + 1.6F, jy + 0.5F, cz + z + 0.4F, BigMat.R_STEEL);
            m.box(cx - 0.2F, jy + 0.5F, cz + z, cx + 0.2F, jy + 2.8F, cz + z + 0.4F, BigMat.R_STEEL);
        }
        m.box(cx - 0.5F, y0 + 6.6F, cz - 0.5F, cx + 0.5F, jy + 7, cz + 0.5F, BigMat.R_STEEL);
        m.rodYZ(cx - 0.15F, cx + 0.15F, jy + 7, cz, jy + 0.7F, cz + len * 0.75F, 0.3F, BigMat.R_DARK);
        m.rodYZ(cx - 0.15F, cx + 0.15F, jy + 7, cz, y0 + 8, cz - 13, 0.3F, BigMat.R_DARK);
        //Chariot, cable et crochet (+ conteneur)
        float tz = cz + Math.max(6, Math.min(len - 2, trolley));
        m.box(cx - 2.0F, jy - 1.2F, tz - 1.4F, cx + 2.0F, jy, tz + 1.4F, BigMat.R_DARK);
        float low = deckY(drop) + (load > 0 ? 3.2F : 0.6F);
        float hy = jy - 2 - (jy - 2 - low) * smooth(hook);
        if (hy > jy - 3) {
            hy = jy - 3;
        }
        m.box(cx - 0.12F, hy + 1.0F, tz - 0.12F, cx + 0.12F, jy - 1.2F, tz + 0.12F, BigMat.R_DARK);
        m.box(cx - 1.2F, hy, tz - 0.8F, cx + 1.2F, hy + 1.0F, tz + 0.8F, BigMat.R_HAZARD);
        if (load > 0 && d > 0.99F) {
            m.tinted(cx - 1.6F, hy - 3.0F, tz - 4.2F, cx + 1.6F, hy - 0.1F, tz + 4.2F, BigMat.R_WHITE, 0xFF3A6EA8);
            m.box(cx - 1.65F, hy - 0.6F, tz - 4.25F, cx + 1.65F, hy - 0.1F, tz + 4.25F, BigMat.R_DARK);
        }
        m.pop();
    }

    // ------------------------------------------------------------------ bras de chargement

    /**
     * Bras de chargement marin : colonne montante avec vanne, rotule, bras interieur et bras exterieur (contrepoids au coude), raccord qui se
     * pose sur le pont. Range : bras dresse le long de la colonne ; deploye : le raccord rejoint le pont du navire (deux segments, coude
     * calcule).
     */
    static void arm(RailMesh m, int tier, float d, float phase, float reach, float drop, BigMat fam, float r, BigMat pipe, boolean tank) {
        float cx = 8, cz = 6;
        if (tank) {
            //Soute de ravitaillement : cuve couchee sur berceaux, cote quai
            m.cyl('x', TOP + 4.6F, 3.4F, 3.2F, 2.5F, 13.5F, BigMat.R_STEEL_L, 12);
            m.cyl('x', TOP + 4.6F, 3.4F, 3.35F, 7.4F, 8.6F, fam, 12);
            for (float x : new float[]{4, 12}) {
                m.box(x - 0.5F, TOP + 1, 1.0F, x + 0.5F, TOP + 2.4F, 5.8F, BigMat.R_DARK);
            }
            cz = 9;
        }
        //Colonne montante, vanne, rotule
        float top = TOP + 22;
        m.cyl('y', cx, cz, r, TOP + 1, top, pipe, 10);
        m.cyl('y', cx, cz, r + 0.8F, TOP + 1, TOP + 2.2F, BigMat.R_DARK, 10);
        m.cyl('y', cx, cz, r + 0.5F, TOP + 7, TOP + 9, fam, 10);
        m.box(cx - 2.6F, TOP + 7.4F, cz - 0.5F, cx - r - 0.2F, TOP + 8.6F, cz + 0.5F, BigMat.R_RED);
        m.cyl('y', cx, cz, r + 0.6F, top - 1, top + 1, BigMat.R_DARK, 10);
        //Bras : longueurs et angles (dans le plan y-z)
        float l1 = 20, l2 = 20;
        float bx = cz, by = top;
        float tzz = deckZ(reach) - 2, tyy = deckY(drop) + 1.0F;
        double ia, oa;
        //Range : interieur dresse (90 deg), exterieur pendant le long (-90 deg)
        double stowIn = Math.toRadians(84), stowOut = Math.toRadians(-90);
        double dzz = tzz - bx, dyy = tyy - by, dist = Math.min(l1 + l2 - 0.5, Math.hypot(dzz, dyy));
        double base = Math.atan2(dyy, dzz);
        double cosA = (l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist);
        double ikIn = base + Math.acos(Math.max(-1, Math.min(1, cosA)));
        double ex = bx + l1 * Math.cos(ikIn), ey = by + l1 * Math.sin(ikIn);
        double ikOut = Math.atan2(tyy - ey, tzz - ex);
        ia = stowIn + (ikIn - stowIn) * d;
        oa = stowOut + (ikOut - stowOut) * d;
        float elZ = (float) (bx + l1 * Math.cos(ia)), elY = (float) (by + l1 * Math.sin(ia));
        float enZ = (float) (elZ + l2 * Math.cos(oa)), enY = (float) (elY + l2 * Math.sin(oa));
        m.rodYZ(cx - r, cx + r, by, bx, elY, elZ, r * 1.6F, pipe);
        m.rodYZ(cx - r * 0.9F, cx + r * 0.9F, elY, elZ, enY, enZ, r * 1.4F, pipe);
        //Coude : rotule et contrepoids
        m.box(cx - r - 0.6F, elY - 1.2F, elZ - 1.2F, cx + r + 0.6F, elY + 1.2F, elZ + 1.2F, BigMat.R_DARK);
        float cwZ = (float) (elZ - 6 * Math.cos(ia)), cwY = (float) (elY - 6 * Math.sin(ia));
        m.rodYZ(cx - 0.4F, cx + 0.4F, elY, elZ, cwY, cwZ, 0.8F, BigMat.R_DARK);
        m.box(cx - 2.2F, cwY - 1.8F, cwZ - 1.8F, cx + 2.2F, cwY + 1.8F, cwZ + 1.8F, BigMat.R_CONCRETE);
        //Bague de la ressource au milieu des bras, raccord au bout (lumineux pendant le transfert)
        float mz = (elZ + enZ) / 2, my = (elY + enY) / 2;
        m.box(cx - r - 0.3F, my - 0.8F, mz - 0.8F, cx + r + 0.3F, my + 0.8F, mz + 0.8F, fam);
        boolean flow = phase > 0 && d > 0.99F;
        m.box(cx - r - 0.4F, enY - 1.6F, enZ - 1.2F, cx + r + 0.4F, enY + 0.2F, enZ + 1.2F, flow ? BigMat.R_LAMP_G : BigMat.R_DARK);
        if (flow) {
            //Impulsions le long du bras exterieur
            float k = (phase % 20) / 20F;
            float pz = elZ + (enZ - elZ) * k, py = elY + (enY - elY) * k;
            m.box(cx - r - 0.25F, py - 0.5F, pz - 0.5F, cx + r + 0.25F, py + 0.5F, pz + 0.5F, fam);
        }
    }

    // ------------------------------------------------------------------ borne de recharge

    /** Borne de recharge : armoire a fentes lumineuses, enrouleur ; deployee : potence et cable dont la fiche descend sur le pont. */
    static void charger(RailMesh m, int tier, float d, float phase, float reach, float drop, boolean lit) {
        m.box(3.5F, TOP + 1, 3, 12.5F, TOP + 17, 9, BigMat.R_DARK);
        m.box(3.3F, TOP + 17, 2.8F, 12.7F, TOP + 17.8F, 9.2F, ShipModels.tierMat(tier));
        boolean on = phase > 0 && d > 0.99F;
        for (float y = TOP + 4; y < TOP + 15; y += 2) {
            m.box(4.5F, y, 9.0F, 11.5F, y + 0.8F, 9.1F, on || lit ? BigMat.R_GLOW_CYAN : BigMat.R_LAMP_OFF);
        }
        m.box(5, TOP + 13, 9.1F, 11, TOP + 15.6F, 9.2F, BigMat.R_SCREEN);
        //Enrouleur
        m.cyl('x', TOP + 19.8F, 6, 2.0F, 5, 11, BigMat.R_BLACK, 12);
        m.cyl('x', TOP + 19.8F, 6, 2.3F, 4.6F, 5, BigMat.R_STEEL, 12);
        m.cyl('x', TOP + 19.8F, 6, 2.3F, 11, 11.4F, BigMat.R_STEEL, 12);
        //Potence : mat et fleche qui pivote du quai vers le navire
        m.box(7.4F, TOP + 17.8F, 10.2F, 8.6F, TOP + 30, 11.4F, BigMat.R_STEEL);
        float ang = 90 * (1 - d);
        m.rotate('y', 8, 0, 10.8F, ang);
        float jz = deckZ(reach) + 2;
        m.box(7.5F, TOP + 29, 10.8F, 8.5F, TOP + 30, jz, BigMat.R_STEEL);
        m.rodYZ(7.85F, 8.15F, TOP + 30, 10.8F, TOP + 29.5F, jz, 0.25F, BigMat.R_DARK);
        float plugY = TOP + 28 - (TOP + 28 - deckY(drop) - 1.5F) * d;
        m.box(7.75F, plugY + 1.4F, jz - 0.25F, 8.25F, TOP + 29, jz + 0.25F, BigMat.R_BLACK);
        m.box(7.0F, plugY, jz - 0.8F, 9.0F, plugY + 1.6F, jz + 0.8F, on ? BigMat.R_GLOW_CYAN : BigMat.R_FAM_ENERGY);
        m.pop();
    }

    // ------------------------------------------------------------------ mat de donnees

    /** Mat en treillis, parabole qui se tourne vers le navire et faisceau lumineux pendant le transfert. */
    static void dataMast(RailMesh m, int tier, float d, float phase, float reach, float drop) {
        float h = TOP + 30;
        for (float x : new float[]{6, 10}) {
            for (float z : new float[]{6, 10}) {
                m.box(x - 0.35F, TOP + 1, z - 0.35F, x + 0.35F, h, z + 0.35F, BigMat.R_STEEL);
            }
        }
        for (float y = TOP + 3; y < h; y += 4) {
            m.box(5.8F, y, 5.8F, 10.2F, y + 0.35F, 6.2F, BigMat.R_STEEL_L);
            m.box(5.8F, y, 9.8F, 10.2F, y + 0.35F, 10.2F, BigMat.R_STEEL_L);
            m.box(5.8F, y + 2, 5.8F, 6.2F, y + 2.35F, 10.2F, BigMat.R_STEEL_L);
            m.box(9.8F, y + 2, 5.8F, 10.2F, y + 2.35F, 10.2F, BigMat.R_STEEL_L);
        }
        m.box(5.4F, h, 5.4F, 10.6F, h + 1, 10.6F, ShipModels.tierMat(tier));
        m.box(7.5F, h + 1, 7.5F, 8.5F, h + 3, 8.5F, BigMat.R_DARK);
        //Parabole : regarde le ciel au repos, se penche vers le pont du navire
        float tz = deckZ(reach), ty = deckY(drop);
        //Angle de la normale (+y) vers +z : 10 deg au repos, vers le pont deploye (90 + angle de plongee)
        double aim = Math.toDegrees(Math.atan2(h + 3 - ty, tz - 8));
        float tilt = (float) (10 + (90 + aim - 10) * d);
        m.rotate('x', 8, h + 3.5F, 8, tilt);
        m.cyl('y', 8, 8, 4.6F, h + 3.0F, h + 3.6F, BigMat.R_WHITE, 16);
        m.cyl('y', 8, 8, 3.6F, h + 3.6F, h + 4.0F, BigMat.R_WHITE, 16);
        m.cyl('y', 8, 8, 0.3F, h + 4.0F, h + 7.0F, BigMat.R_STEEL, 6);
        m.box(7.4F, h + 7.0F, 7.4F, 8.6F, h + 8.0F, 8.6F, BigMat.R_FAM_DATA);
        m.pop();
        if (phase > 0 && d > 0.99F) {
            //Faisceau de la parabole au pont, impulsion qui le parcourt
            m.rodYZ(7.7F, 8.3F, h + 5, 8, ty, tz, 0.6F, BigMat.R_FAM_DATA);
            float k = (phase % 16) / 16F;
            float py = h + 5 + (ty - h - 5) * k, pz = 8 + (tz - 8) * k;
            m.box(7.3F, py - 0.6F, pz - 0.6F, 8.7F, py + 0.6F, pz + 0.6F, BigMat.R_LAMP);
        }
    }

    // ------------------------------------------------------------------ passerelle

    /** Tour d'escalier et passerelle couverte, dressee au repos, abaissee sur le pont du navire. */
    static void gangway(RailMesh m, int tier, float d, float reach, float drop) {
        //Tour : poteaux, paliers, toit
        float th = TOP + 12;
        for (float x : new float[]{3, 13}) {
            for (float z : new float[]{3, 11}) {
                m.box(x - 0.5F, TOP + 1, z - 0.5F, x + 0.5F, th + 4, z + 0.5F, BigMat.R_WHITE);
            }
        }
        for (float y = TOP + 4; y <= th; y += 4) {
            m.box(2.6F, y, 2.6F, 13.4F, y + 0.6F, 11.4F, BigMat.R_GRILL);
        }
        m.box(2.2F, th + 4, 2.2F, 13.8F, th + 4.8F, 11.8F, ShipModels.tierMat(tier));
        //Passerelle couverte : charniere au palier haut, cote eau
        float hz = 11.4F, hy = th + 0.6F, len = 26;
        float tz = deckZ(reach), ty = deckY(drop);
        double down = Math.toDegrees(Math.atan2(ty - hy, tz - hz));
        float ang = (float) (80 + (down - 80) * d);
        m.translate(0, hy, hz);
        m.rotate('x', 0, 0, 0, -ang);
        m.box(4.5F, 0, 0, 11.5F, 0.6F, len, BigMat.R_GRILL);
        for (float s : new float[]{4.5F, 11.0F}) {
            m.box(s, 0.6F, 0, s + 0.5F, 4.4F, len, BigMat.R_GLASS_D);
        }
        m.box(4.3F, 4.4F, 0, 11.7F, 5.2F, len, BigMat.R_WHITE);
        m.box(4.3F, 3.6F, 0, 11.7F, 4.4F, len, ShipModels.tierMat(tier));
        for (float z = 0; z < len; z += 6.4F) {
            m.box(4.3F, 0, z, 11.7F, 5.2F, z + 0.5F, BigMat.R_WHITE);
        }
        m.pop();
        m.pop();
    }
}
