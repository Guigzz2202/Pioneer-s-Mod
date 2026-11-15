package fr.pioneers.transport.logistic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.11 (spec §6.2) : geometrie du contenu des cables transparents, en tres petit, SANS classe client (le banc hors jeu
 * {@code tools/cables/FlowBench} l'execute et la mesure ; le rendu Java qui la dessinait dans les cables transparents est retire avec eux, MAJ simplification).
 * <p>
 * Un couloir par type actif dans la section 6..10 du cable : 1 type = couloir de 2 px au centre ; 2 = deux de 1,4 px ; 3-4 = grille 2x2 de
 * 1,2 px ; 5-7 = grille 3x3 de 0,9 px. Ordre fixe : objets, fluides, gaz, energie, chaleur, pression, donnees. Memes animations que l'ancien
 * rendu, reduites : objets = vraies piles a l'echelle 0,12 qui avancent ; fluides = colonne + paquets plus denses ; gaz = brume + bouffees ;
 * energie = etincelles cyan ; chaleur = incandescence ; pression = brume blanche ; donnees = bits arc-en-ciel. Sens = cotes d'arrivee
 * ({@code in}) et de depart ({@code out}) de chaque type.
 * <p>
 * Niveaux de detail : au-dela de {@link #FAR} blocs, plus de piles ni de paquets (couloir plein a la couleur du type). Aucune allocation par
 * image (etat decode une fois : {@link Decoded}).
 */
public final class FlowLanes {

    /** Distance (blocs, au carre) au-dela de laquelle le detail est retire. */
    public static final double FAR_SQR = 12 * 12;
    /** Materiaux : texture du gaz teintee, texture du fluide, lumineux (plein feu), pile d'objet. */
    public static final int MAT_HAZE = 0, MAT_FLUID = 1, MAT_GLOW = 2;
    /** Couleurs par defaut des types (couloir plein, objets au loin). */
    public static final int[] COLORS = {0xB07A3E, 0x3F76E4, 0xE8EEF5, 0x9FF4FF, 0xFF7A2A, 0xF0F6FA, 0xC48AF0};
    private static final int[] RAINBOW = {0xFF5A5A, 0xFFB84A, 0xF4F06A, 0x6AF08A, 0x5AD8FF, 0x8A7AFF, 0xE07AF0};

    private FlowLanes() {
    }

    /** Destination de la geometrie (Minecraft : sommets ; banc : boites enregistrees). Coordonnees en pixels du bloc (0..16). */
    public interface Sink {

        /**
         * Une boite ; {@code faces} = faces a dessiner (bits = Direction.ordinal : bas, haut, nord, sud, ouest, est) : les faces cachees
         * dans la continuite d'un couloir (bouts des bras, faces du noyau tournees vers un bras) ne sont pas emises.
         */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float alpha, int material, int kind, int faces);

        /** Une pile d'objet (centre en pixels, echelle d'un bloc, rotation en degres autour de y). */
        void item(float x, float y, float z, float scale, float yaw);
    }

    /** Flux recu, decode une fois (tableaux fixes, au plus 7 types). */
    public static final class Decoded {

        public int count;
        public final int[] kind = new int[7], in = new int[7], out = new int[7], strength = new int[7], tint = new int[7];
        public final float[] value = new float[7], max = new float[7];
        /** Ressource montree par type (NBT recu ; le rendu en tire la pile, le sprite et la teinte). */
        public final CompoundTag[] resource = new CompoundTag[7];

        public static Decoded of(CompoundTag flow) {
            Decoded d = new Decoded();
            ListTag list = flow.getList("l", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && d.count < 7; i++) {
                CompoundTag t = list.getCompound(i);
                int n = d.count++;
                d.kind[n] = Math.max(0, Math.min(6, t.getByte("k")));
                d.strength[n] = Math.max(1, t.getByte("s"));
                d.in[n] = t.getByte("i") & 63;
                d.out[n] = t.getByte("o") & 63;
                d.value[n] = t.getFloat("v");
                d.max[n] = t.getFloat("m");
                d.resource[n] = t.getCompound("r");
                d.tint[n] = COLORS[d.kind[n]];
            }
            return d;
        }
    }

    /** Centre (u, v) du couloir {@code lane} parmi {@code n}, et sa demi-largeur, dans la section du cable (pixels). */
    static float laneU(int lane, int n) {
        if (n == 1) {
            return 8;
        }
        if (n == 2) {
            return lane == 0 ? 7.1F : 8.9F;
        }
        if (n <= 4) {
            return lane % 2 == 0 ? 7.2F : 8.8F;
        }
        return 6.8F + 1.2F * (lane % 3);
    }

    static float laneV(int lane, int n) {
        if (n <= 2) {
            return 8;
        }
        if (n <= 4) {
            return lane < 2 ? 7.2F : 8.8F;
        }
        return 6.8F + 1.2F * (lane / 3);
    }

    static float laneHalf(int n) {
        return n == 1 ? 1.0F : n == 2 ? 0.7F : n <= 4 ? 0.6F : 0.45F;
    }

    /**
     * Emet la geometrie d'un cable transparent.
     *
     * @param links masque des bras relies (cables et fiches, bits = Direction.ordinal)
     * @param time  temps en secondes (heure du monde + fraction)
     * @param far   au-dela de 12 blocs : couloirs pleins, sans piles ni paquets
     * @param seed  graine du cable (position) pour decaler les animations
     */
    public static void emit(Decoded d, int links, float time, boolean far, int seed, Sink sink) {
        int n = d.count;
        float h = laneHalf(n);
        //Beaucoup de couloirs (5 a 7) : fluides et gaz en colonne seule, un seul paquet par bras ailleurs (spec §6.3 : 0,5 ms pour 200 transparents)
        boolean dense = n >= 5;
        for (int lane = 0; lane < n; lane++) {
            int kind = d.kind[lane];
            float u = laneU(lane, n), v = laneV(lane, n);
            int in = d.in[lane], out = d.out[lane], strength = d.strength[lane];
            int moving = in | out;
            float speed = 0.8F + 0.35F * strength;
            switch (kind) {
                case 0 -> {
                    //Objets : piles a l'echelle 0,12 qui avancent (au plus 2 par bras) ; au loin, couloir plein
                    if (far) {
                        column(d.tint[lane], 0.85F, MAT_HAZE, kind, links, u, v, h * 0.8F, sink);
                    } else {
                        int perArm = strength >= 3 && !dense ? 2 : 1;
                        for (int side = 0; side < 6; side++) {
                            boolean incoming = (in >> side & 1) != 0, outgoing = (out >> side & 1) != 0;
                            if (!incoming && !outgoing) {
                                continue;
                            }
                            for (int k = 0; k < perArm; k++) {
                                float f = frac(time * speed * 0.6F + (float) k / perArm + (seed & 7) * 0.13F);
                                float along = incoming ? 1 - f : f;
                                float dist = 8 * along;
                                sink.item(px(side, 0, u, v, dist), px(side, 1, u, v, dist), px(side, 2, u, v, dist), 0.12F,
                                      (time * 40 + side * 60) % 360);
                            }
                        }
                    }
                }
                case 1 -> {
                    column(d.tint[lane], 0.55F, MAT_FLUID, kind, links, u, v, h, sink);
                    if (!far && !dense && moving != 0) {
                        packets(d.tint[lane], 0.95F, MAT_FLUID, kind, in, out, time * speed, 1 + strength / 2, 1.3F, u, v, h * 1.1F, sink);
                    }
                }
                case 2 -> {
                    column(d.tint[lane], 0.25F + 0.08F * strength, MAT_HAZE, kind, links, u, v, h, sink);
                    //Couloirs nombreux : la brume seule (les bouffees se perdent a cette taille)
                    if (!far && !dense && moving != 0) {
                        packets(d.tint[lane], 0.5F, MAT_HAZE, kind, in, out, time * speed * 0.8F, 2, 2.0F, u, v, h * 1.15F, sink);
                    }
                }
                case 3 -> {
                    column(0x3FB6C4, 0.18F + 0.08F * strength, MAT_GLOW, kind, links, u, v, h * 0.7F, sink);
                    if (!far && moving != 0) {
                        packets(0x9FF4FF, 0.95F, MAT_GLOW, kind, in, out, time * speed * 2.2F, dense ? 1 : 2 + strength / 2, 0.8F, u, v, h * 0.9F, sink);
                    }
                }
                case 4 -> {
                    float t = Math.min(1F, Math.max(0, (d.value[lane] - 320) / 1400F));
                    int color = t < 0.33F ? lerp(0x5A2A20, 0xB5281A, t / 0.33F) : t < 0.66F ? lerp(0xB5281A, 0xFF7A1F, (t - 0.33F) / 0.33F)
                          : lerp(0xFF7A1F, 0xFFF2C0, (t - 0.66F) / 0.34F);
                    column(color, 0.35F + 0.6F * t, d.value[lane] > 700 ? MAT_GLOW : MAT_HAZE, kind, links, u, v, h, sink);
                }
                case 5 -> {
                    float ratio = d.max[lane] <= 0 ? 0.5F : Math.min(1, d.value[lane] / d.max[lane]);
                    float pulse = 0.85F + 0.15F * (float) Math.sin(time * 3 + seed);
                    column(0xE6EEF4, (0.15F + 0.5F * ratio) * pulse, MAT_HAZE, kind, links, u, v, h, sink);
                }
                default -> {
                    //Donnees : bits arc-en-ciel qui filent
                    column(0x5A3A78, 0.35F, MAT_GLOW, kind, links, u, v, h * 0.6F, sink);
                    if (!far && moving != 0) {
                        bits(in, out, time * speed * 1.6F, u, v, h, sink, kind, dense ? 2 : 4);
                    }
                }
            }
        }
    }

    /** Coordonnee (axe 0 x, 1 y, 2 z) d'un point du couloir (u, v) a {@code dist} pixels du centre, le long du bras {@code side}. */
    private static float px(int side, int axis, float u, float v, float dist) {
        //Directions : 0 bas, 1 haut, 2 nord, 3 sud, 4 ouest, 5 est
        int along = side <= 1 ? 1 : side <= 3 ? 2 : 0;
        float sign = side == 0 || side == 2 || side == 4 ? -1 : 1;
        if (axis == along) {
            return 8 + sign * dist;
        }
        //Section : les deux autres axes, dans l'ordre x, y, z
        int first = along == 0 ? 1 : 0;
        return axis == first ? u : v;
    }

    /** Colonne continue : noyau (enveloppe des departs des bras relies) + bras relies. */
    private static void column(int rgb, float alpha, int material, int kind, int links, float u, float v, float h, Sink sink) {
        float x0 = 99, y0 = 99, z0 = 99, x1 = -99, y1 = -99, z1 = -99;
        for (int side = 0; side < 6; side++) {
            if ((links >> side & 1) == 0 && !(links == 0 && side == 4)) {
                continue;
            }
            float x = px(side, 0, u, v, 0), y = px(side, 1, u, v, 0), z = px(side, 2, u, v, 0);
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            y0 = Math.min(y0, y);
            y1 = Math.max(y1, y);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        sink.box(x0 - h, y0 - h, z0 - h, x1 + h, y1 + h, z1 + h, rgb, alpha, material, kind, 63 & ~links);
        for (int side = 0; side < 6; side++) {
            if ((links >> side & 1) != 0) {
                arm(side, u, v, h, h, 8, rgb, alpha, material, kind, sink, false);
            }
        }
    }

    /** Boite le long du bras {@code side} de la distance {@code from} a {@code to} (pixels depuis le centre). */
    private static void arm(int side, float u, float v, float h, float from, float to, int rgb, float alpha, int material, int kind, Sink sink,
          boolean caps) {
        float a0 = px(side, 0, u, v, from), a1 = px(side, 0, u, v, to);
        float b0 = px(side, 1, u, v, from), b1 = px(side, 1, u, v, to);
        float c0 = px(side, 2, u, v, from), c1 = px(side, 2, u, v, to);
        int along = side <= 1 ? 1 : side <= 3 ? 2 : 0;
        float x0 = along == 0 ? Math.min(a0, a1) : a0 - h, x1 = along == 0 ? Math.max(a0, a1) : a0 + h;
        float y0 = along == 1 ? Math.min(b0, b1) : b0 - h, y1 = along == 1 ? Math.max(b0, b1) : b0 + h;
        float z0 = along == 2 ? Math.min(c0, c1) : c0 - h, z1 = along == 2 ? Math.max(c0, c1) : c0 + h;
        //Bouts d'un troncon de colonne : caches par le noyau et par le bras du cable voisin
        int axisFaces = along == 0 ? 0b110000 : along == 1 ? 0b000011 : 0b001100;
        sink.box(x0, y0, z0, x1, y1, z1, rgb, alpha, material, kind, caps ? 63 : 63 & ~axisFaces);
    }

    private static void packets(int rgb, float alpha, int material, int kind, int in, int out, float phase, int count, float length, float u, float v,
          float h, Sink sink) {
        for (int side = 0; side < 6; side++) {
            boolean incoming = (in >> side & 1) != 0, outgoing = (out >> side & 1) != 0;
            if (!incoming && !outgoing) {
                continue;
            }
            for (int k = 0; k < count; k++) {
                float f = frac(phase + (float) k / count);
                float along = incoming ? 1 - f : f;
                float center = 8 * along;
                float a = Math.max(0, center - length / 2), b = Math.min(8, center + length / 2);
                if (b > a) {
                    arm(side, u, v, h, a, b, rgb, alpha, material, kind, sink, true);
                }
            }
        }
    }

    private static void bits(int in, int out, float phase, float u, float v, float h, Sink sink, int kind, int count) {
        for (int side = 0; side < 6; side++) {
            boolean incoming = (in >> side & 1) != 0, outgoing = (out >> side & 1) != 0;
            if (!incoming && !outgoing) {
                continue;
            }
            for (int k = 0; k < count; k++) {
                float f = frac(phase + k / (float) count);
                float along = incoming ? 1 - f : f;
                float center = 8 * along;
                arm(side, u, v, h * 0.9F, Math.max(0, center - 0.35F), Math.min(8, center + 0.35F), RAINBOW[(k + (int) (phase * 3)) % RAINBOW.length],
                      1F, MAT_GLOW, kind, sink, true);
            }
        }
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    static int lerp(int c0, int c1, float t) {
        int r = (int) ((c0 >> 16 & 255) + ((c1 >> 16 & 255) - (c0 >> 16 & 255)) * t);
        int g = (int) ((c0 >> 8 & 255) + ((c1 >> 8 & 255) - (c0 >> 8 & 255)) * t);
        int b = (int) ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return r << 16 | g << 8 | b;
    }
}
