package fr.pioneers.tech.kinetics.client;

import fr.pioneers.tech.kinetics.machines.MachineType;
import net.minecraft.core.Direction;

/**
 * Parties mobiles des machines de l'age mecanique, du treuil, des poulies et de la machine a balancier. Repere local : la facade regarde
 * +Z (voir {@link KDraw#face}), l'arbre des machines horizontales suit X.
 */
public final class MachineVisuals {

    private MachineVisuals() {
    }

    /** Partie mobile d'une machine ; {@code angle} = angle de l'arbre (degres), {@code progress} = avancement de la recette. */
    public static void machine(KDraw d, MachineType type, float angle, float progress, boolean working) {
        switch (type) {
            case MILLSTONE -> millstone(d, angle);
            case SIFTER -> sifter(d, angle);
            case JAW_CRUSHER -> crusher(d, angle);
            case SAW -> saw(d, angle);
            case LATHE -> lathe(d, angle);
            case BELLOWS -> bellows(d, angle);
            case NORIA -> noria(d, angle);
            case DRILL -> drill(d, angle);
        }
    }

    /**
     * Arbre traversant le long de X : 0.20.42, toujours a (y 8, z 8) et octogonal au rayon commun {@link KineticVisuals#AXLE}, comme les arbres
     * voisins avec lesquels il se raccorde (meme angle, tenu par le reseau).
     */
    static void crossShaft(KDraw d, float angle, float x0, float x1) {
        d.push().align(Direction.Axis.X).rotate(Direction.Axis.Y, angle, 8, 8, 8);
        d.rigid(new Axle(x0, x1), r -> r.octa(8, 8, KineticVisuals.AXLE, x0, x1, "iron_bright", "iron"));
        d.pop();
    }

    // 1.0.4-beta (lot 27, audit de fluidite) : cles des parties rigides en cache (KDraw.rigid)
    private record Axle(float x0, float x1) {
    }

    private record Flywheel(float x0) {
    }

    private record Wheel(float radius, float axleFrom, float axleTo, boolean line) {
    }

    private static final Object SAW_BLADE = "rigid:saw_blade", SIFTER_FRAME = "rigid:shaker_frame";

    static void millstone(KDraw d, float angle) {
        d.push().rotate(Direction.Axis.Y, angle, 8, 8, 8);
        d.octa(8, 8, KineticVisuals.AXLE, 0, 16, "iron_bright", "iron");                   //fer de meule
        d.octa(8, 8, 6.6F, 7.0F, 11.0F, "stone", "millstone");                              //meule courante
        d.octa(8, 8, 2.4F, 11.0F, 12.0F, "iron", "iron");                                   //anille
        d.box(7.2F, 11.0F, 2.0F, 8.8F, 12.4F, 14.0F, "oak", "oak");                         //barre d'entrainement
        d.pop();
    }

    /**
     * 1.0.2-beta (lot 2, Guillaume : « le tamis a secousses ne demande plus de filtre ; le design doit etre revu pour l'inclure ») : le caisson
     * qui secoue (avance / recule de 1,6 px a chaque tour) est un CADRE ouvert de chene avec sa TOILE DE FIBRES tendue dedans (brins croises :
     * on voit le sediment dessus et le bac dessous a travers les mailles), une levre basse a l'avant par ou sort le refus ; bielle et
     * excentrique sous le caisson. Le sediment est dessine par {@code MachineItems} sur la toile (y {@link #SIFTER_MESH_Y}).
     */
    public static final float SIFTER_MESH_Y = 13.3F;

    /** Caisson du tamis a secousses (cadre, toile, equerres), dans son repere (le va-et-vient est dans la pose). */
    private static void sifterFrame(KDraw d) {
        d.box(2.0F, 12.6F, 1.5F, 3.2F, 15.2F, 14.5F, "oak", "oak_end");                    //longerons du cadre
        d.box(12.8F, 12.6F, 1.5F, 14.0F, 15.2F, 14.5F, "oak", "oak_end");
        d.box(3.2F, 12.6F, 1.5F, 12.8F, 15.2F, 2.7F, "oak_dark", "oak_dark");               //traverse arriere (haute)
        d.box(3.2F, 12.6F, 13.3F, 12.8F, 13.8F, 14.5F, "oak_dark", "oak_dark");             //levre avant (basse : le refus sort)
        for (int k = 0; k < 6; k++) {                                                        //toile : brins tendus d'avant en arriere
            float x = 4.0F + k * 1.6F;
            d.box(x - 0.28F, 12.95F, 2.7F, x + 0.28F, 13.25F, 13.3F, "rope", "rope");
        }
        for (int k = 0; k < 7; k++) {                                                        //et de gauche a droite (trame)
            float z = 3.6F + k * 1.55F;
            d.box(3.2F, 13.0F, z - 0.28F, 12.8F, 13.3F, z + 0.28F, "rope", "rope");
        }
        for (float z : new float[]{1.3F, 14.5F}) {                                           //equerres de fer aux coins
            d.box(1.8F, 12.4F, z, 3.4F, 13.0F, z + 0.2F, "iron", "iron");
            d.box(12.6F, 12.4F, z, 14.2F, 13.0F, z + 0.2F, "iron", "iron");
        }
    }

    static void sifter(KDraw d, float angle) {
        float shake = (float) Math.sin(Math.toRadians(angle)) * 1.6F;
        d.push().translate(0, 0, shake);
        d.rigid(SIFTER_FRAME, MachineVisuals::sifterFrame);                                  //1.0.4-beta (lot 27) : cadre rigide en cache
        d.pop();
        crossShaft(d, angle, 0, 16);
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        d.box(11.0F, 6.0F, 6.0F, 12.5F, 10.0F, 11.0F, "iron", "iron");                      //excentrique
        d.pop();
        double a = Math.toRadians(angle);
        d.beam(11.75F, 8.0F + (float) Math.cos(a) * 2.2F, 8.0F + (float) Math.sin(a) * 2.2F, 11.75F, 12.6F, 13.0F + shake, 1.0F, "oak");   //bielle
    }

    /**
     * 1.0.2-beta (lot 2, Guillaume : « ameliorer le design du concasseur a machoires ») : concasseur a SIMPLE GENOUILLERE, le mecanisme a
     * vue : la machoire mobile (plaque dentee) est pendue par sa tete a l'excentrique de l'arbre (y 8, z 8) : sa tete decrit un petit cercle,
     * son pied (talon) s'appuie sur la genouillere calee contre la traverse avant du bati et retenue par la tige de rappel a ressort ; en face,
     * la machoire fixe dentee, penchee vers l'arriere : le V s'ouvre en haut (la tremie) et se ferme en bas a chaque tour. Deux volants a
     * rayons sur les bouts de l'arbre, a l'exterieur des flasques ajourees (chassis fixe : {@code T:mechanics/blocks2.py}).
     */
    /** Volant a 6 rayons du concasseur (repere de l'arbre tourne). */
    private static void crusherFlywheel(KDraw d, float x0) {
        for (int k = 0; k < 6; k++) {
            d.push().rotate(Direction.Axis.X, 60 * k, 8, 8, 8);
            d.box(x0 + 0.1F, 7.4F, 9.6F, x0 + 0.8F, 8.6F, 12.8F, "iron", "iron");
            d.pop();
        }
        for (int k = 0; k < 12; k++) {
            d.push().rotate(Direction.Axis.X, 30 * k, 8, 8, 8);
            d.box(x0, 6.6F, 12.6F, x0 + 0.9F, 9.4F, 13.8F, "iron_bright", "iron");
            d.pop();
        }
    }

    static void crusher(KDraw d, float angle) {
        crossShaft(d, angle, 0, 16);
        double a = Math.toRadians(angle);
        for (float x0 : new float[]{15.05F, 0.05F}) {                                        //deux volants a 6 rayons
            d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
            d.rigid(new Flywheel(x0), r -> crusherFlywheel(r, x0));                          //1.0.4-beta (lot 27) : volant rigide en cache
            d.pop();
        }
        //Machoire fixe : plaque dentee penchee de 8 degres vers l'arriere (pied en z 4,2, y 2,5)
        d.push().rotate(Direction.Axis.X, -8, 8, 2.5F, 4.2F);
        d.box(3.2F, 2.5F, 3.0F, 12.8F, 14.5F, 4.2F, "iron_plate", "iron");
        for (float y = 3.0F; y < 14.0F; y += 1.6F) {
            d.box(3.2F, y, 4.2F, 12.8F, y + 0.8F, 4.8F, "iron_bright", "iron_bright");
        }
        d.pop();
        //Tete de la machoire mobile sur l'excentrique (rayon 0,9 px) ; pied : avance et recule de 0,35 px (genouillere)
        float hy = 8 + (float) Math.cos(a) * 0.9F, hz = 8 + (float) Math.sin(a) * 0.9F;
        float by = 3.2F, bz = 7.6F + (float) Math.sin(a + 0.6) * 0.35F;
        double phi = -Math.atan2(bz - hz, hy - by);                                          //rotation (autour de X) qui porte le pied en (by, bz)
        float tilt = (float) Math.toDegrees(phi);
        d.push().rotate(Direction.Axis.X, tilt, 8, hy, hz);
        d.push().align(Direction.Axis.X);
        d.octa(16 - hy, hz, 3.0F, 4.0F, 12.0F, "iron", "iron_bright");                      //tete : coussinet autour de l'excentrique
        d.pop();
        float foot = hy - (float) Math.hypot(hy - by, bz - hz);                             //pied dans le repere de la machoire
        d.box(3.5F, foot, hz - 1.4F, 12.5F, hy - 1.5F, hz + 1.2F, "iron_plate", "iron");    //corps de la machoire
        d.box(3.5F, hy - 1.5F, hz - 1.2F, 12.5F, hy + 2.4F, hz + 1.0F, "iron_plate", "iron");
        for (float y = foot + 0.4F; y < hy - 2.2F; y += 1.6F) {                             //dents (face arriere, vers la machoire fixe)
            d.box(3.5F, y, hz - 2.0F, 12.5F, y + 0.8F, hz - 1.4F, "iron_bright", "iron_bright");
        }
        d.box(4.5F, foot - 0.4F, hz + 1.2F, 11.5F, foot + 1.6F, hz + 2.2F, "iron", "iron"); //talon (siege de la genouillere)
        d.pop();
        //Genouillere : du talon (repere monde) a l'appui de la traverse avant ; tige de rappel et ressort sous elle
        double ly = foot + 0.6 - hy, lz = 2.2;
        float ty = hy + (float) (ly * Math.cos(phi) - lz * Math.sin(phi)), tz = hz + (float) (ly * Math.sin(phi) + lz * Math.cos(phi));
        float fy = 3.4F, fz = 13.0F;
        d.beam(5.5F, ty, tz, 5.5F, fy, fz, 1.0F, "iron_bright");
        d.beam(10.5F, ty, tz, 10.5F, fy, fz, 1.0F, "iron_bright");
        d.beam(8.0F, ty - 0.8F, tz - 0.4F, 8.0F, 2.6F, 14.6F, 0.5F, "iron");
        for (int k = 0; k < 4; k++) {                                                        //ressort de rappel
            float t = 0.5F + k * 0.12F;
            float z = tz - 0.4F + (14.6F - tz + 0.4F) * t, y = ty - 0.8F + (2.6F - ty + 0.8F) * t;
            d.box(7.3F, y - 0.35F, z - 0.15F, 8.7F, y + 0.35F, z + 0.15F, "copper", "copper");
        }
    }

    /** Lame de la scie (16 secteurs et leurs dents, flasque), dans le repere de la lame tournee. */
    private static void sawBlade(KDraw d) {
        for (int k = 0; k < 16; k++) {
            d.push().rotate(Direction.Axis.X, 22.5F * k, 8, 8, 8);
            d.box(7.7F, 8.0F, 13.0F, 8.3F, 10.6F, 15.6F, "iron_bright", "iron_bright");
            d.box(7.75F, 9.6F, 15.6F, 8.25F, 10.8F, 16.6F, "iron", "iron");                //dents
            d.pop();
        }
        d.box(7.6F, 4.8F, 4.8F, 8.4F, 11.2F, 11.2F, "iron_bright", "iron_bright");
    }

    static void saw(KDraw d, float angle) {
        crossShaft(d, angle, 0, 16);
        d.push().rotate(Direction.Axis.X, angle * 3, 8, 8, 8);                              //lame (demultipliee)
        d.rigid(SAW_BLADE, MachineVisuals::sawBlade);                                        //1.0.4-beta (lot 27) : lame rigide en cache
        d.pop();
    }

    static void lathe(KDraw d, float angle) {
        crossShaft(d, angle, 0, 2.5F);
        crossShaft(d, angle, 12.5F, 16);
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        d.box(2.5F, 5.8F, 5.8F, 4.5F, 10.2F, 10.2F, "iron", "iron");                       //mandrin
        d.box(4.5F, 6.8F, 6.8F, 12.5F, 9.2F, 9.2F, "oak", "oak_end");                      //piece en cours
        d.pop();
    }

    static void bellows(KDraw d, float angle) {
        double a = Math.toRadians(angle);
        float open = (float) (0.5 + 0.5 * Math.sin(a)) * 8;
        d.push().rotate(Direction.Axis.X, open, 8, 13.4F, 13);              //1.2.3 : meme sens que le cuir et la tige (ils etaient opposes)
        d.box(2.0F, 14.4F, 1.0F, 14.0F, 15.2F, 13.0F, "oak_planks", "oak_planks");        //planche haute
        d.box(7.0F, 15.2F, 1.0F, 9.0F, 15.8F, 4.0F, "iron", "iron");
        d.pop();
        float tan = (float) Math.tan(Math.toRadians(open));
        for (int k = 0; k < 4; k++) {                                                      //soufflet de cuir : en coin sous la planche haute
            float z0 = 1.4F + k * 2.8F, z1 = z0 + 2.8F;
            d.box(2.4F, 13.0F, z0, 13.6F, 14.3F + tan * (13 - z1), z1, "leather", "leather");
        }
        d.box(6.5F, 13.2F, 13.0F, 9.5F, 15.0F, 16.0F, "iron", "iron");                    //tuyere
        crossShaft(d, angle, 0, 16);
        //Manivelle et tige qui souleve la planche haute
        float py = 8 + (float) Math.sin(a) * 2.6F, pz = 8 - (float) Math.cos(a) * 2.6F;
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        d.box(7.0F, 7.0F, 8.0F - 3.4F, 9.0F, 9.0F, 8.0F, "iron", "iron");
        d.pop();
        float topZ = 5.0F, topY = 14.4F + (float) Math.tan(Math.toRadians(open)) * (13 - topZ);
        d.beam(8, py, pz, 8, topY, topZ, 0.8F, "oak");
    }

    static void noria(KDraw d, float angle) {
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        for (int k = 0; k < 8; k++) {
            d.push().rotate(Direction.Axis.X, 45 * k, 8, 8, 8);
            d.box(3.0F, 7.0F, 8.0F, 13.0F, 9.0F, 14.0F, "oak", "oak");                     //rayons
            d.box(4.0F, 6.0F, 14.0F, 12.0F, 10.0F, 17.0F, "oak_planks", "oak_dark");       //godets
            d.pop();
        }
        d.pop();
        crossShaft(d, angle, 0, 16);
    }

    /**
     * 0.20.42 : perceuse : l'arbre s'arrete de part et d'autre de la broche ; deux couronnes (une par demi-arbre) menent le pignon de la broche
     * verticale, qui fait tourner la meche (vis d'Archimede) dans la roche sous le bloc.
     */
    static void drill(KDraw d, float angle) {
        crossShaft(d, angle, 0, 5.0F);
        crossShaft(d, angle, 11.0F, 16);
        for (int side = 0; side < 2; side++) {
            float x0 = side == 0 ? 4.4F : 10.6F, x1 = x0 + 1.0F, tx0 = side == 0 ? 5.4F : 9.8F;
            d.push().align(Direction.Axis.X).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            d.octa(8, 8, 4.0F, x0, x1, "oak", "cog_face");                                   //couronne
            for (int k = 0; k < 8; k++) {
                d.push().rotate(Direction.Axis.Y, 45 * k + 22.5F, 8, 8, 8);
                d.box(7.4F, tx0, 10.6F, 8.6F, tx0 + 0.8F, 12.2F, "oak_dark", "oak_end");          //dents tournees vers la broche
                d.pop();
            }
            d.pop();
        }
        float spin = -angle * 4.0F / 3.0F;
        d.push().rotate(Direction.Axis.Y, spin, 8, 8, 8);
        d.octa(8, 8, 1.3F, -1.0F, 14.0F, "iron_bright", "iron");                              //broche
        d.octa(8, 8, 2.9F, 12.2F, 13.2F, "brass", "brass");                                  //pignon
        for (int k = 0; k < 8; k++) {
            d.push().rotate(Direction.Axis.Y, 45 * k, 8, 8, 8);
            d.box(7.5F, 11.6F, 9.6F, 8.5F, 12.2F, 11.0F, "brass", "brass");
            d.pop();
        }
        for (int k = 0; k < 14; k++) {                                                        //meche en vis
            float y = -1.0F + k * 0.55F;
            d.push().rotate(Direction.Axis.Y, k * 40, 8, 8, 8);
            d.box(7.6F, y, 8.0F, 8.4F, y + 0.5F, 10.5F, "iron_bright", "iron");
            d.pop();
        }
        d.octa(8, 8, 0.6F, -2.0F, -1.0F, "iron_bright", "iron_bright");
        d.pop();
    }

    // ------------------------------------------------------------------
    // Poulies, cordes, treuil
    // ------------------------------------------------------------------

    /** 1.0.4-beta (lot 2) : dessous de la flasque basse d'une poulie de ligne d'entrepot (px). */
    public static final float LINE_CHEEK = 6.0F;

    /** Poulie a gorge (axe local Y), rayon en pixels. */
    static void pulley(KDraw d, float radius) {
        pulley(d, radius, 0);
    }

    static void pulley(KDraw d, float radius, float axleFrom) {
        pulley(d, radius, axleFrom, false);
    }

    /**
     * 1.0.4-beta (lot 2) : poulie dont l'arbre part de {@code axleFrom} px (5,6 : pas d'arbre sous la roue, les colis passent dessous) ;
     * {@code line} : poulie d'une ligne d'entrepot (axe vertical, posee) : la joue basse est une mince flasque de fer (0,5 px) : l'etrier
     * des colis passe dessous, meme sous la plus haute des deux cordes.
     */
    static void pulley(KDraw d, float radius, float axleFrom, boolean line) {
        pulley(d, radius, axleFrom, 16, line);
    }

    /** 1.0.4-beta (lot 27) : meme poulie, arbre de {@code axleFrom} a {@code axleTo} px (roue montee dans son bloc : l'arbre s'arrete au bord). */
    static void pulley(KDraw d, float radius, float axleFrom, float axleTo, boolean line) {
        //1.0.4-beta (lot 27, audit de fluidite) : la roue est rigide (l'angle est dans la pose) : en cache
        d.rigid(new Wheel(radius, axleFrom, axleTo, line), r -> pulleyWheel(r, radius, axleFrom, axleTo, line));
    }

    /**
     * 1.0.5-beta (lot 15, Guillaume : « textures connectees des poulies largement ameliorees ») : roue d'une poulie, MEMES cotes qu'avant
     * (joues y 5..6,5 et 9,5..11, gorge y 6,5..9,5 au rayon - 2,6 .. - 0,2, joues jusqu'au rayon + 0,8 : cordes, colis et etrier inchanges),
     * dessin refait : joues et gorge en anneaux a 16 pans ({@link #ring} : un segment sur deux 0,03 px plus mince, plus de dessus confondus
     * entre segments voisins), joues bordees d'un cercle de fer, rayons (4, ou 6 des 8 px de rayon) qui vont du moyeu a la gorge, moyeu de fer.
     */
    private static void pulleyWheel(KDraw d, float radius, float axleFrom, float axleTo, boolean line) {
        d.octa(8, 8, KineticVisuals.AXLE, axleFrom, axleTo, "oak", "oak_end");
        float in = Math.max(KineticVisuals.AXLE + 0.6F, radius - 2.2F), rim = radius + 0.5F;
        if (line) {
            ring(d, in, radius + 0.8F, LINE_CHEEK, 6.5F, "iron", "iron");                               //flasque
        } else {
            ring(d, in, rim, 5.0F, 6.5F, "oak_dark", "oak_dark");                                        //joue
            ring(d, rim, radius + 0.8F, 5.0F, 6.5F, "iron", "iron");
        }
        ring(d, in, rim, 9.5F, 11.0F, "oak_dark", "oak_dark");
        ring(d, rim, radius + 0.8F, 9.5F, 11.0F, "iron", "iron");
        //gorge 0,1 px plus etroite que les joues (1.0.2-beta : flancs jamais dans le meme plan)
        ring(d, Math.max(KineticVisuals.AXLE + 0.6F, radius - 2.6F), radius - 0.2F, 6.5F, 9.5F, "oak", "oak");
        int spokes = radius >= 8 ? 6 : 4;
        for (int k = 0; k < spokes; k++) {
            d.push().rotate(Direction.Axis.Y, 360F / spokes * k + 45, 8, 8, 8);
            d.box(7.2F, 6.8F, 9.6F, 8.8F, 9.2F, 8 + Math.max(2.2F, radius - 2.4F), "oak", "oak");
            d.pop();
        }
        //1.0.2-beta (Guillaume : « la petite poulie a un bug de superposition des pixels ») : moyeu 0,3 px en dedans des joues (y 5,3..10,7)
        hub(d, 2.8F, 5.3F, 10.7F, "iron");
    }

    /**
     * 1.0.5-beta (lot 15) : anneau a 16 pans d'axe Y, du rayon {@code r0} au rayon {@code r1} (apothemes), de y0 a y1 : 16 segments dont les
     * bouts se recouvrent ; un segment sur deux est 0,03 px plus mince a chaque face (dessus et dessous jamais confondus entre voisins).
     */
    static void ring(KDraw d, float r0, float r1, float y0, float y1, String side, String ends) {
        float h = r1 * (float) Math.tan(Math.PI / 16) + 0.03F;
        for (int k = 0; k < 16; k++) {
            float t = (k & 1) == 0 ? 0 : 0.03F;
            d.push().rotate(Direction.Axis.Y, 22.5F * k, 8, 8, 8);
            d.box(8 - h, y0 + t, 8 + r0, 8 + h, y1 - t, 8 + r1, side, ends);
            d.pop();
        }
    }

    /** 1.0.2-beta : moyeu octogonal d'axe Y sans couvercles confondus (le prisme tourne a 45 degres est 0,12 px plus court a chaque bout). */
    static void hub(KDraw d, float r, float y0, float y1, String tex) {
        d.box(8 - r, y0, 8 - r, 8 + r, y1, 8 + r, tex, tex);
        d.push().rotate(Direction.Axis.Y, 45, 8, 8, 8);
        d.box(8 - r, y0 + 0.12F, 8 - r, 8 + r, y1 - 0.12F, 8 + r, tex, tex);
        d.pop();
    }

    /**
     * 1.0.3-beta (lot 11) : defilement (px) de la corde d'une poulie de rayon de gorge {@code ropeRadius} (px) tournee de {@code degrees} :
     * la longueur d'arc de la gorge (meme vitesse lineaire que la jante, rayon compris). Avant : un nombre entier de motifs de 16 px par
     * tour (jusqu'a 50 % d'ecart de vitesse sur les petites poulies) ; le jeu cumule maintenant les ecarts d'angle d'une image a l'autre
     * ({@code PulleyTile.ropeTravel}) : plus de saut quand l'angle repasse par 0.
     */
    public static double ropeTravel(double degrees, double ropeRadius) {
        return fr.pioneers.tech.kinetics.rope.RopeMotion.travel(degrees, ropeRadius);
    }

    /**
     * Corde entre deux poulies d'axe {@code axis} : la poulie A au centre du bloc, B decalee de (ox, oy, oz) blocs ; rayons en pixels ; brins
     * exterieurs (ou croises). Les brins pendent un peu vers le bas (corde), presque pas (courroie). {@code scroll} : defilement (px) de la
     * gorge de A dans le sens ou l'angle de A croit ({@link #ropeTravel}).
     * <p>
     * 1.0.3-beta (lot 11, Guillaume : « la corde semble tourner a contresens des poulies ») : sens de chaque brin = sens de la jante au point
     * de tangence. Une poulie tourne autour de +axe (regle de la main droite : {@code align} puis rotation en y) : dans le plan (u, v) de la
     * corde, la vitesse de la jante en (u, v) est (v, -u) pour les axes X et Y, (-v, u) pour l'axe Z (le plan (x, y) est vu depuis +z) ; au
     * point de tangence du brin {@code side}, elle va vers B si {@code side x signe de l'axe} > 0. Avant, les deux brins allaient a l'envers
     * sur les axes X et Y (seul l'axe Z etait juste). Verifie au banc (GIF du lot 11) et par le GameTest du lot.
     */
    static void rope(KDraw d, Direction.Axis axis, float ox, float oy, float oz, float ra, float rb, boolean crossed, String tex, float scroll) {
        rope(d, axis, ox, oy, oz, ra, rb, crossed, tex, scroll, 0, 0);
    }

    /**
     * 1.0.4-beta (lot 27, plusieurs cordes par poulie) : meme corde, decalee le long de l'axe de {@code alongA} px au depart et {@code alongB}
     * px a l'arrivee (gorge de la corde a chaque bout : basse ou haute).
     */
    static void rope(KDraw d, Direction.Axis axis, float ox, float oy, float oz, float ra, float rb, boolean crossed, String tex, float scroll,
          float alongA, float alongB) {
        //Plan perpendiculaire a l'axe : (u, v)
        float du = (axis == Direction.Axis.X ? oz : ox) * 16, dv = (axis == Direction.Axis.Y ? oz : oy) * 16;
        double dist = Math.hypot(du, dv);
        if (dist < 1) {
            return;
        }
        double base = Math.atan2(dv, du);
        float old = d.scroll;
        d.scroll = scroll;
        float w = tex.equals("leather") ? 1.6F : 1.2F;
        for (int side = -1; side <= 1; side += 2) {
            double phi = Math.asin(Math.max(-1, Math.min(1, (crossed ? ra + rb : ra - rb) / dist)));
            double na = base + side * (Math.PI / 2 - phi);
            double nb = crossed ? na + Math.PI : na;
            float au = (float) (Math.cos(na) * ra), av = (float) (Math.sin(na) * ra);
            float bu = du + (float) (Math.cos(nb) * rb), bv = dv + (float) (Math.sin(nb) * rb);
            float sag = tex.equals("leather") ? 0.4F : (float) (dist * 0.03);
            int n = 8;
            float[] prev = null;
            //1.2.0 : le motif continue d'un troncon au suivant, et les deux brins defilent en sens contraires (boucle) ; lot 11 : dans le
            //sens de la jante (strandSign : +1 = de A vers B quand l'angle de A croit)
            float acc = 0;
            float run = strandSign(axis, side) * scroll;
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n;
                float u = au + (bu - au) * t, v = av + (bv - av) * t, al = 8 + alongA + (alongB - alongA) * t;
                float[] p = switch (axis) {
                    case X -> new float[]{al, v + 8, u + 8};
                    case Y -> new float[]{u + 8, al, v + 8};
                    case Z -> new float[]{u + 8, v + 8, al};
                };
                p[1] -= sag * 4 * t * (1 - t);
                if (prev != null) {
                    d.scroll = acc - run;
                    d.beam(prev[0], prev[1], prev[2], p[0], p[1], p[2], w, tex);
                    acc += (float) Math.sqrt((p[0] - prev[0]) * (p[0] - prev[0]) + (p[1] - prev[1]) * (p[1] - prev[1]) + (p[2] - prev[2]) * (p[2] - prev[2]));
                }
                prev = p;
            }
        }
        d.scroll = old;
    }

    /**
     * 1.0.3-beta (lot 11) : sens du brin {@code side} (-1 / +1) quand l'angle de la poulie A croit : +1 = il avance de A vers B (le motif de
     * la corde est decale de {@code run = signe x defilement} le long du brin, de A vers B).
     */
    public static int strandSign(Direction.Axis axis, int side) {
        return fr.pioneers.tech.kinetics.rope.RopeMotion.strandSign(axis, side);
    }

    /** Treuil : tambour (axe local Y) et corde qui descend de {@code depth} blocs (repere monde vertical, voir appel). */
    static void winchDrum(KDraw d, float angle) {
        d.push().rotate(Direction.Axis.Y, angle, 8, 8, 8);
        d.octa(8, 8, KineticVisuals.AXLE, 0, 16, "iron_bright", "iron");
        d.octa(8, 8, 4.5F, 3, 13, "rope", "oak_end");
        d.octa(8, 8, 6.0F, 2, 3, "oak_dark", "oak_dark");
        d.octa(8, 8, 6.0F, 13, 14, "oak_dark", "oak_dark");
        d.pop();
    }

    static void winchLoad(KDraw d, float depthPx, boolean water, boolean full) {
        d.box(7.4F, -depthPx + 4, 7.4F, 8.6F, 4.0F, 8.6F, "rope", "rope");
        if (water) {
            d.box(5.0F, -depthPx - 2, 5.0F, 11.0F, -depthPx + 4, 11.0F, "oak_planks", "oak_dark");
            d.box(5.4F, -depthPx - 1.8F, 5.4F, 10.6F, -depthPx + 3.6F, 10.6F, full ? "water" : "oak_dark", full ? "water" : "oak_dark");
            d.box(4.8F, -depthPx + 1, 4.8F, 11.2F, -depthPx + 2, 11.2F, "iron", "iron");
        } else {
            d.box(3.5F, -depthPx - 4, 3.5F, 12.5F, -depthPx + 4, 12.5F, "oak_planks", "oak_planks");
            d.box(3.3F, -depthPx - 1, 3.3F, 12.7F, -depthPx + 0, 12.7F, "iron", "iron");
            if (full) {
                d.box(4.5F, -depthPx + 4, 4.5F, 11.5F, -depthPx + 5.5F, 11.5F, "hay", "hay");
            }
        }
    }

    // ------------------------------------------------------------------
    // Machine a balancier
    // ------------------------------------------------------------------

    /** Balancier, bielle et volant ; repere : facade +Z, arbre de sortie selon X (centre du bloc), volant sur le flanc gauche. */
    static void beamEngine(KDraw d, float angle) {
        double a = Math.toRadians(angle);
        float rock = (float) (Math.sin(a) * 12);
        //Balancier sur son pivot (y 26), au-dessus de la chaudiere
        d.push().rotate(Direction.Axis.X, rock, 8, 26, 8);
        d.box(6.8F, 25.0F, 0.5F, 9.2F, 27.6F, 15.5F, "oak", "oak_end");
        d.box(6.4F, 24.6F, 7.0F, 9.6F, 28.0F, 9.0F, "iron", "iron");
        d.pop();
        double r = Math.toRadians(rock);
        float backY = 26 + (float) Math.sin(r) * 6.5F, frontY = 26 - (float) Math.sin(r) * 6.5F;
        d.beam(8, backY, 2.0F, 8, 16.0F, 2.0F, 1.2F, "iron_bright");                     //tige du piston
        float crankY = 8 + (float) Math.sin(a) * 4.5F, crankZ = 8 + (float) Math.cos(a) * 4.5F;
        d.beam(8, frontY, 14.0F, -0.8F, crankY, crankZ, 1.1F, "iron");                   //bielle vers le volant
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        for (int k = 0; k < 6; k++) {                                                    //volant (flanc gauche)
            d.push().rotate(Direction.Axis.X, 60 * k, 8, 8, 8);
            d.box(-1.8F, 7.2F, 8.0F, -0.4F, 8.8F, 14.0F, "iron", "iron");
            d.box(-2.0F, 6.4F, 13.5F, -0.2F, 9.6F, 15.6F, "iron_bright", "iron");
            d.pop();
        }
        d.box(-2.2F, 6.6F, 6.6F, 0.0F, 9.4F, 9.4F, "iron_bright", "iron");
        d.pop();
    }

    // ------------------------------------------------------------------
    // Machine a vapeur (pilon inverse)
    // ------------------------------------------------------------------

    /**
     * Cylindre vertical au-dessus de l'arbre : tige de piston, crosse dans ses glissieres, bielle, manivelle a deux flasques et volant sur le
     * flanc gauche. Repere : echappement +Z, arbre selon X a (y 8, z 8). Le cylindre et les glissieres sont dans le modele fixe.
     * 1.0.2-beta (lot 3, Guillaume : « eviter les textures de 2 de haut quand le bloc n'en fait qu'un ») : manivelle de 1,5 px et bielle de 3 px
     * (avant 2 et 4,6) : la crosse reste sous le cylindre, qui tient sous y 16 (T:bronze/compact_design.py m_engine).
     */
    public static final float ENGINE_CRANK = 1.5F, ENGINE_ROD = 3.0F, ENGINE_CYLINDER_Y = 13.2F;

    static void steamEngine(KDraw d, float angle) {
        double a = Math.toRadians(angle);
        float r = ENGINE_CRANK, length = ENGINE_ROD;
        float pinY = 8 + (float) Math.sin(a) * r, pinZ = 8 + (float) Math.cos(a) * r;
        float dz = pinZ - 8;
        float headY = pinY + (float) Math.sqrt(length * length - dz * dz);
        //Arbre, flasques et volant (tournent)
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        d.push().align(Direction.Axis.X);
        //1.0.2-beta (Guillaume : « superpositions de pixels sur la roue ») : l'arbre part de x 0,6, dans le moyeu du volant (avant x 0 : son
        //bout etait dans le plan de la face du moyeu, au meme gonflement : faces EXACTEMENT confondues) ; il ressort par x 16 vers l'arbre voisin
        d.octa(8, 8, KineticVisuals.AXLE, 0.6F, 16, "steel", "iron_bright");           //0.20.42 : arbre au rayon commun
        d.pop();
        //Flasques et moyeu plus grands que l'arbre (rayon 2 : avant, ses aretes les traversaient)
        for (float x : new float[]{6.2F, 9.0F}) {
            d.box(x, 5.6F, 5.6F, x + 0.8F, 10.4F, 10.8F, "iron", "iron_bright");
        }
        d.box(7.0F, 7.4F, 8 + r - 0.4F, 9.0F, 8.6F, 8 + r + 0.4F, "iron_bright", "iron_bright");   //maneton
        for (int k = 0; k < 12; k++) {                                                    //volant : jante continue, six rayons
            d.push().rotate(Direction.Axis.X, 30 * k, 8, 8, 8);
            if (k % 2 == 0) {
                d.box(0.7F, 7.5F, 9.2F, 1.5F, 8.5F, 13.4F, "iron", "iron");
            }
            //1.0.2-beta : un segment sur deux 0,25 px en retrait sur chaque flanc : deux segments voisins ne se recouvrent plus dans le meme plan
            float in = k % 2 == 0 ? 0 : 0.25F;
            d.box(0.2F + in, 6.25F, 13.4F, 2.0F - in, 9.75F, 15.2F, "iron_bright", "iron");
            d.pop();
        }
        d.box(0.0F, 5.2F, 5.2F, 2.2F, 10.8F, 10.8F, "brass", "brass");
        d.pop();
        //Bielle, crosse, tige de piston (glissent)
        d.beam(8, pinY, pinZ, 8, headY, 8, 0.9F, "steel");
        d.box(6.6F, headY - 0.8F, 6.9F, 9.4F, headY + 0.8F, 9.1F, "brass", "brass");
        d.beam(8, headY + 0.8F, 8, 8, ENGINE_CYLINDER_Y + 0.2F, 8, 0.7F, "iron_bright");
    }

}
