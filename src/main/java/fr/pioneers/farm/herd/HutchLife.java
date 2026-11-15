package fr.pioneers.farm.herd;

/**
 * 1.0.4-beta (lot 33, Guillaume : « L'animation du clapier doit etre mieux ») : VIE DES LAPINS DU CLAPIER, calculee (PURE : aucune classe
 * client ni monde ; GameTest et banc {@code tools/farmtest/HutchBench.java}) d'apres l'etat REEL du clapier synchronise ({@code HutchTile} :
 * lapins, adultes / lapereaux, reserve de nourriture) et l'heure du monde : rien cote serveur, aucun paquet.
 * <p>
 * Le temps est coupe en fenetres de {@link #WINDOW} ticks. Chaque fenetre, deux ou trois lapins (les lapereaux plus souvent) se deplacent vers
 * une PLACE libre ({@link #spots}) : demi-tour vers le but, petits sauts de {@link #HOP_PX} px au plus (pattes arriere detendues comme le
 * lapin vanilla), puis ils se tournent (vers le ratelier sur une place « mangeoire »). Les autres vivent sur place : mangent au ratelier
 * (tete basse, machoire, seulement s'il reste de la nourriture), se couchent (corps bas, oreilles rabattues), font leur toilette, se
 * retournent, ou attendent (nez, oreilles qui frissonnent) ; les lapereaux jouent (petits bonds sur place). L'etat (place et cap de chacun)
 * est rejoue depuis le debut d'une periode de {@link #PERIOD} fenetres : continu d'une fenetre a l'autre ; au changement de periode,
 * chacun saute de sa place d'avant a sa nouvelle place (pas de teleportation).
 * <p>
 * Repere : celui du clapier, en px, case principale, facade (grillage) au nord (z petit), ratelier contre le dos (z grand) ; cap en
 * degres pour {@code Axis.YP} (0 = le lapin regarde le grillage, 180 = le ratelier).
 */
public final class HutchLife {

    private HutchLife() {
    }

    /** Duree d'une fenetre (ticks), fenetres par periode, ticks d'un saut, longueur max d'un saut (px), hauteur d'un saut (px). */
    public static final int WINDOW = 60, PERIOD = 48, HOP_TICKS = 9, TURN_TICKS = 6;
    public static final float HOP_PX = 3.6F, HOP_H = 1.5F;

    /** Ce que fait un lapin. */
    public enum Act {
        IDLE, HOP, EAT, LIE, GROOM, TURN, PLAY
    }

    /**
     * Pose d'un lapin : position (px), cap (degres), hauteur du saut (px), extension des pattes du saut (0..1), activite, tete baissee
     * (rad, + = vers le sol), tete tournee (rad), oreilles (frisson, rad), oreilles rabattues (0..1), couche (0..1), nez (rad).
     */
    public record Pose(float x, float z, float yaw, float hop, float jump, Act act, float headPitch, float headYaw, float ear, float earBack,
                       float lie, float nose) {
    }

    /**
     * Places (x, z en px) : clapier double (case principale x 0..16, case laterale x -16..0) et simple. Les deux premieres sont devant le
     * ratelier (« mangeoire »). Plus de places que de lapins (8 au plus) : il en reste toujours de libres.
     */
    public static final float[][] DOUBLE = {{-2.3F, 9.4F}, {2.3F, 9.4F}, {-12.0F, 5.4F}, {12.0F, 5.4F}, {-5.8F, 5.4F}, {5.8F, 5.4F},
                                            {-9.3F, 10.4F}, {9.3F, 10.4F}, {0F, 5.0F}};
    public static final float[][] SINGLE = {{6.2F, 9.4F}, {9.8F, 9.4F}, {4.2F, 5.4F}, {11.8F, 5.4F}, {8.0F, 5.0F}, {5.0F, 10.6F}, {11.0F, 10.6F},
                                            {8.0F, 12.4F}};
    /** Places « mangeoire » (devant le ratelier) : les deux premieres. */
    public static final int EAT_SPOTS = 2;

    public static float[][] spots(boolean dbl) {
        return dbl ? DOUBLE : SINGLE;
    }

    // ------------------------------------------------------------------ hasard deterministe

    static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static float unit(long h) {
        return (h >>> 40) / (float) (1L << 24);
    }

    // ------------------------------------------------------------------ etat d'une fenetre

    /** Etat au DEBUT d'une fenetre : place et cap de chaque lapin ; mouvements de la fenetre (place de depart, -1 : immobile). */
    static final class State {
        final int[] slot, from;
        final float[] yaw, yawFrom;

        State(int n) {
            slot = new int[n];
            from = new int[n];
            yaw = new float[n];
            yawFrom = new float[n];
        }
    }

    /** Place du rang de devant (contre le grillage) ? */
    static boolean front(float[] spot) {
        return spot[1] < 7.5F;
    }

    /**
     * Cap au repos sur une place : mangeoire = vers le ratelier ; rang de devant = vers le grillage (nez vers le joueur, la queue ne touche
     * jamais le grillage) ; rang du fond = en travers (vers l'est ou l'ouest), le long du dos.
     */
    static float restYaw(long seed, int i, int k, int spot, float[][] sp) {
        float r = unit(mix(seed + i * 3L, k * 17L + 3));
        if (spot < EAT_SPOTS) {
            return 180F + (r - 0.5F) * 24F;
        }
        if (front(sp[spot])) {
            return (r - 0.5F) * 70F;
        }
        return (r < 0.5F ? 90F : -90F) + (unit(mix(seed + i, k * 31L + 7)) - 0.5F) * 40F;
    }

    /** Places au debut de la periode {@code p} (melange deterministe). */
    static int[] base(long seed, int p, int n, int spots) {
        int[] perm = new int[spots];
        for (int i = 0; i < spots; i++) {
            perm[i] = i;
        }
        for (int i = spots - 1; i > 0; i--) {
            int j = (int) Math.floorMod(mix(seed, p * 977L + i), (long) (i + 1));
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        int[] out = new int[n];
        System.arraycopy(perm, 0, out, 0, n);
        return out;
    }

    /** Etat au debut de la fenetre {@code k} (et mouvements de cette fenetre). */
    static State state(long seed, boolean[] adult, int k, float[][] sp) {
        int spots = sp.length;
        int n = adult.length;
        int p = Math.floorDiv(k, PERIOD), k0 = p * PERIOD;
        State s = new State(n);
        int[] start = base(seed, p, n, spots);
        for (int i = 0; i < n; i++) {
            s.slot[i] = start[i];
            s.yaw[i] = restYaw(seed, i, k0, start[i], sp);
        }
        if (k == k0) {
            //changement de periode : chacun saute de sa place de fin de la periode d'avant a sa nouvelle place
            State prev = p == Integer.MIN_VALUE ? null : endOf(seed, adult, p - 1, sp);
            for (int i = 0; i < n; i++) {
                s.from[i] = prev == null || prev.slot[i] == s.slot[i] ? -1 : prev.slot[i];
                s.yawFrom[i] = prev == null ? s.yaw[i] : prev.yaw[i];
            }
            return s;
        }
        java.util.Arrays.fill(s.from, -1);
        for (int w = k0 + 1; w <= k; w++) {
            step(seed, adult, w, sp, s, w == k);
        }
        return s;
    }

    /** Etat a la fin de la periode {@code p} (debut de sa derniere fenetre, mouvements faits). */
    static State endOf(long seed, boolean[] adult, int p, float[][] sp) {
        int spots = sp.length;
        int n = adult.length, k0 = p * PERIOD;
        State s = new State(n);
        int[] start = base(seed, p, n, spots);
        for (int i = 0; i < n; i++) {
            s.slot[i] = start[i];
            s.yaw[i] = restYaw(seed, i, k0, start[i], sp);
        }
        for (int w = k0 + 1; w < k0 + PERIOD; w++) {
            step(seed, adult, w, sp, s, false);
        }
        return s;
    }

    /** Une fenetre : 2 ou 3 lapins (lapereaux deux fois plus souvent) vont a une place libre ; les autres peuvent se retourner. */
    static void step(long seed, boolean[] adult, int w, float[][] sp, State s, boolean keep) {
        int spots = sp.length;
        int n = adult.length;
        if (keep) {
            java.util.Arrays.fill(s.from, -1);
            System.arraycopy(s.yaw, 0, s.yawFrom, 0, n);
        }
        if (n == 0) {
            return;
        }
        int movers = 2 + (int) Math.floorMod(mix(seed, w * 13L), 2L);
        boolean[] used = new boolean[spots];
        for (int i = 0; i < n; i++) {
            used[s.slot[i]] = true;
        }
        for (int m = 0; m < movers; m++) {
            //tirage pondere : lapereau 2, adulte 1
            int total = 0;
            for (int i = 0; i < n; i++) {
                total += adult[i] ? 1 : 2;
            }
            int r = (int) Math.floorMod(mix(seed + m, w * 7L + 1), (long) total), who = 0;
            for (int i = 0; i < n; i++) {
                r -= adult[i] ? 1 : 2;
                if (r < 0) {
                    who = i;
                    break;
                }
            }
            if (keep && s.from[who] >= 0) {
                continue;
            }
            int free = 0;
            for (boolean u : used) {
                free += u ? 0 : 1;
            }
            if (free == 0) {
                return;
            }
            int pick = (int) Math.floorMod(mix(seed + who, w * 5L + m), (long) free), to = -1;
            for (int j = 0; j < spots; j++) {
                if (!used[j] && pick-- == 0) {
                    to = j;
                    break;
                }
            }
            used[s.slot[who]] = false;
            used[to] = true;
            if (keep) {
                s.from[who] = s.slot[who];
                s.yawFrom[who] = s.yaw[who];
            }
            s.slot[who] = to;
            s.yaw[who] = restYaw(seed, who, w, to, sp);
        }
        //demi-tours sur place : au fond seulement (en travers ; ni sur une mangeoire, ni la queue dans le grillage)
        for (int i = 0; i < n; i++) {
            if ((!keep || s.from[i] < 0) && s.slot[i] >= EAT_SPOTS && !front(sp[s.slot[i]]) && unit(mix(seed + i * 11L, w * 3L + 2)) < 0.25F) {
                if (keep) {
                    s.yawFrom[i] = s.yaw[i];
                }
                s.yaw[i] += 180F;
            }
        }
    }

    // ------------------------------------------------------------------ poses

    static float lerpYaw(float a, float b, float t) {
        float d = ((b - a) % 360F + 540F) % 360F - 180F;
        return a + d * Math.max(0F, Math.min(1F, t));
    }

    static float smooth(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return t * t * (3F - 2F * t);
    }

    /**
     * Poses de tous les lapins a l'instant {@code time} (ticks du monde, fraction comprise) : graine du clapier (sa position), adultes /
     * lapereaux (ordre des lapins du clapier), reserve de nourriture, clapier double ?
     */
    public static Pose[] poses(long seed, boolean[] adult, int food, boolean dbl, float time) {
        float[][] sp = spots(dbl);
        int n = Math.min(adult.length, sp.length);
        boolean[] a = java.util.Arrays.copyOf(adult, n);
        Pose[] out = new Pose[n];
        if (n == 0) {
            return out;
        }
        int k = (int) Math.floor(time / WINDOW);
        float tau = time - k * WINDOW;
        State s = state(seed, a, k, sp);
        for (int i = 0; i < n; i++) {
            out[i] = pose(seed, i, a[i], s, sp, k, tau, food > 0, time);
        }
        return out;
    }

    static Pose pose(long seed, int i, boolean adult, State s, float[][] sp, int k, float tau, boolean food, float time) {
        float[] to = sp[s.slot[i]];
        float yaw = s.yaw[i], left = WINDOW - tau;
        float earTw = ear(seed, i, time);
        float noseW = 0.06F * (float) Math.sin(time * 1.9F + i);
        if (s.from[i] >= 0) {
            float[] from = sp[s.from[i]];
            float dx = to[0] - from[0], dz = to[1] - from[1], d = (float) Math.sqrt(dx * dx + dz * dz);
            int hops = Math.max(1, (int) Math.ceil(d / HOP_PX));
            float delay = Math.floorMod(mix(seed + i, k * 19L), 18L);
            float t = tau - delay;
            float travel = (float) Math.toDegrees(Math.atan2(-dx, -dz));
            if (t < TURN_TICKS) {
                float yy = lerpYaw(s.yawFrom[i], travel, smooth(t / TURN_TICKS));
                return new Pose(from[0], from[1], t < 0 ? s.yawFrom[i] : yy, 0, 0, Act.IDLE, 0, 0, earTw, 0, 0, noseW);
            }
            t -= TURN_TICKS;
            if (t < hops * HOP_TICKS) {
                int h = (int) (t / HOP_TICKS);
                float u = (t - h * HOP_TICKS) / HOP_TICKS;
                float f = (h + smooth(u)) / hops;
                float j = (float) Math.sin(u * Math.PI);
                float hh = j * HOP_H * (adult ? 1F : 0.7F);
                return new Pose(from[0] + dx * f, from[1] + dz * f, travel, hh, j, Act.HOP, -0.15F * j, 0, earTw - 0.25F * j, 0, 0, 0);
            }
            t -= hops * HOP_TICKS;
            if (t < TURN_TICKS) {
                return new Pose(to[0], to[1], lerpYaw(travel, yaw, smooth(t / TURN_TICKS)), 0, 0, Act.IDLE, 0, 0, earTw, 0, 0, noseW);
            }
            tau = t;                                     //le reste de la fenetre : activite sur place
        } else if (s.yawFrom[i] != yaw) {
            //demi-tour sur place au milieu de la fenetre
            float t = (tau - 20F) / 12F;
            float yy = lerpYaw(s.yawFrom[i], yaw, smooth(t));
            if (t < 1F) {
                float j = t > 0F && t < 1F ? (float) Math.sin(t * Math.PI) * 0.5F : 0F;
                return new Pose(to[0], to[1], yy, j * 0.6F, j, t > 0F ? Act.TURN : Act.IDLE, 0, 0, earTw, 0, 0, noseW);
            }
        }
        //activite sur place
        float r = unit(mix(seed + i * 5L, k * 23L + 11));
        Act act;
        if (s.slot[i] < EAT_SPOTS && food) {
            act = Act.EAT;
        } else if (!adult) {
            act = r < 0.45F ? Act.PLAY : r < 0.75F ? Act.IDLE : Act.LIE;
        } else {
            act = r < 0.3F ? Act.LIE : r < 0.5F ? Act.GROOM : Act.IDLE;
        }
        //hors d'une fenetre de saut, on garde la meme activite tant qu'on ne bouge pas : la decision suit la fenetre
        switch (act) {
            case EAT: {
                float chew = (float) Math.sin(time * 1.6F + i * 2F), in = smooth(tau / 8F) * smooth(left / 8F);
                return new Pose(to[0], to[1], yaw, 0, 0, Act.EAT, (0.42F + 0.05F * chew) * in, 0.12F * (float) Math.sin(time * 0.07F + i), earTw * 0.6F,
                      0.15F, 0, 0.1F * chew);
            }
            case LIE: {
                float in = smooth(tau / 14F) * smooth(left / 10F);
                return new Pose(to[0], to[1], yaw, 0, 0, Act.LIE, 0.1F * in, 0.25F * (float) Math.sin(time * 0.03F + i), earTw * 0.3F, 0.85F * in, in,
                      noseW * 0.5F);
            }
            case GROOM: {
                float g = (float) Math.sin(time * 0.9F + i), in = smooth(tau / 8F) * smooth(left / 8F);
                return new Pose(to[0], to[1], yaw, 0, 0, Act.GROOM, (0.55F + 0.15F * g) * in, 0.35F * g * in, earTw, 0.2F, 0, noseW);
            }
            case PLAY: {
                float c = (time * 0.11F + i * 1.7F) % 3F;
                float j = c < 1F ? (float) Math.sin(c * Math.PI) : 0F;
                return new Pose(to[0], to[1], yaw + 25F * (float) Math.sin(time * 0.05F + i), j * HOP_H * 0.6F, j, Act.PLAY, 0, 0, earTw, 0, 0, noseW);
            }
            default: {
                float look = 0.45F * (float) Math.sin(time * 0.021F + i * 1.3F);
                return new Pose(to[0], to[1], yaw, 0, 0, Act.IDLE, 0, look, earTw, 0, 0, noseW);
            }
        }
    }

    /** Hauteur du foin dans le ratelier (px) pour une reserve donnee (0 : vide ; au moins 0,5 px tant qu'il en reste). */
    public static float hayHeight(int food) {
        if (food <= 0) {
            return 0F;
        }
        return Math.max(0.5F, GeneratedHerd.HUTCH_HAY_H * Math.min(1F, food / (float) GeneratedHerd.HUTCH_FOOD_MAX));
    }

    /** Frisson des oreilles : de temps en temps (toutes les 2 a 5 s), un coup bref. */
    static float ear(long seed, int i, float time) {
        int c = (int) Math.floor(time / 40F);
        float ph = time - c * 40F, at = unit(mix(seed + i * 7L, c)) * 30F;
        return ph > at && ph < at + 5F ? -0.35F * (float) Math.sin((ph - at) / 5F * Math.PI) : 0F;
    }
}
