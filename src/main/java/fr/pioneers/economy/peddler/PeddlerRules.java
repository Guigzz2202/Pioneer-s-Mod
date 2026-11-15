package fr.pioneers.economy.peddler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 1.9 (MAJ RP, bloc 3) : regles des Colporteurs sans Minecraft (testees hors jeu par {@code tools/rptest/PeddlerBench.java}) : tirage du
 * stock, taille des lots, bannissement, choix d'un emplacement sur un terrain abstrait ({@link Terrain}), plans des tentes de caravane et de
 * Halte. {@link PeddlerService} branche ces regles sur le monde.
 */
public final class PeddlerRules {

    /** Valeurs par defaut (remplacables par les cles de {@code data/pioneers/economy/partners.json} lues par {@link PeddlerService}). */
    public static final double ROUTE_CHANCE = 0.20;
    public static final int STAY_DAYS = 3, BAN_DAYS = 20, BAN_RADIUS = 500, HALT_SPACING = 1500, HALT_RESPAWN_DAYS = 20;
    /** Anneau d'apparition autour du joueur, distance preferee a une Voie, rayon de recherche des Voies autour d'un joueur (Halte). */
    public static final int ROUTE_MIN = 24, ROUTE_MAX = 40, WAY_NEAR = 48, WAY_SCAN = 32;
    /** Scraps au plus dans l'emplacement de scraps ; total d'une caravane pour l'XP ; total d'une vente pour un acte de reputation. */
    public static final int MAX_SCRAPS = 4, CARAVAN_TOTAL = 200, ACT_TOTAL = 100;
    /** Hauteur des pieds inconnue (troncon non charge). */
    public static final int NONE = Integer.MIN_VALUE;

    private PeddlerRules() {
    }

    // ------------------------------------------------------------------ tirage
    /** Une ligne d'une table de tirage : valeur, poids, quantite min et max. */
    public record Weighted<T>(T value, int weight, int min, int max) {
    }

    /** Un emplacement tire : valeur et quantite. */
    public record Pick<T>(T value, int count) {
    }

    /**
     * Tire {@code slots} lignes differentes (sans remise, au poids), chacune avec une quantite uniforme entre min et max, bornee par
     * {@code maxCount} (0 : sans borne). Moins de lignes que d'emplacements : toutes, dans l'ordre du tirage.
     */
    public static <T> List<Pick<T>> draw(List<Weighted<T>> pool, int slots, int maxCount, Random rnd) {
        List<Weighted<T>> left = new ArrayList<>();
        for (Weighted<T> w : pool) {
            if (w.weight() > 0) {
                left.add(w);
            }
        }
        List<Pick<T>> out = new ArrayList<>();
        while (out.size() < slots && !left.isEmpty()) {
            int total = 0;
            for (Weighted<T> w : left) {
                total += w.weight();
            }
            int r = rnd.nextInt(total);
            int i = 0;
            while (r >= left.get(i).weight()) {
                r -= left.get(i).weight();
                i++;
            }
            Weighted<T> w = left.remove(i);
            int lo = Math.max(1, Math.min(w.min(), w.max())), hi = Math.max(lo, w.max());
            int n = lo + rnd.nextInt(hi - lo + 1);
            if (maxCount > 0) {
                n = Math.min(n, maxCount);
            }
            out.add(new Pick<>(w.value(), n));
        }
        return out;
    }

    /**
     * Taille d'un lot : la plus petite puissance de 2 dont le prix au plancher (85 % de la valeur de base) atteint un jeton, bornee par la
     * pile. Un lot coute donc toujours au moins un jeton (le total d'un achat est arrondi au jeton inferieur).
     */
    public static int lotSize(long valueCenti, int maxStack) {
        int cap = Math.max(1, Math.min(64, maxStack));
        int lot = 1;
        while (lot < cap && lot * valueCenti * 0.85 < 100) {
            lot *= 2;
        }
        return Math.min(lot, cap);
    }

    /** Nombre de lots pour {@code units} unites (au moins un). */
    public static int lots(int units, int lot) {
        return Math.max(1, Math.round(units / (float) Math.max(1, lot)));
    }

    // ------------------------------------------------------------------ bannissement
    /** Le bannissement court-il encore ({@code untilDay} exclu) ? */
    public static boolean banActive(long untilDay, long today) {
        return today < untilDay;
    }

    /** Distance horizontale au plus {@code r}. */
    public static boolean within(double dx, double dz, double r) {
        return dx * dx + dz * dz <= r * r;
    }

    // ------------------------------------------------------------------ terrain
    /** Vue du monde pour les choix d'emplacement : rien n'est charge, une colonne inconnue rend {@link #NONE}. */
    public interface Terrain {

        /** Hauteur des pieds sur la colonne : premier bloc libre au-dessus du sol (herbes ignorees), ou {@link #NONE}. */
        int feet(int x, int z);

        /** Case libre : air ou bloc remplacable, sans fluide ni tuile. */
        boolean free(int x, int y, int z);

        /** Le sol de la colonne est un fluide (eau, lave) ou n'a pas de surface solide. */
        boolean wet(int x, int z);

        /** La colonne est dans une ruine (ou tout pres). */
        boolean ruin(int x, int z);

        /** La colonne porte un bloc d'ancienne Voie (rail, chemin de terre, quai). */
        boolean way(int x, int z);
    }

    /**
     * Points de Voie autour de (cx, cz) dans un rayon r, sur les colonnes ou (x + 2z) mod 5 = 0 : une ligne droite ou diagonale y est vue
     * au moins tous les cinq blocs, pour un cinquieme des lectures.
     */
    public static List<int[]> scanWays(Terrain t, int cx, int cz, int r) {
        List<int[]> out = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (Math.floorMod(x + 2 * z, 5) == 0 && within(x - cx, z - cz, r) && t.way(x, z)) {
                    out.add(new int[]{x, z});
                }
            }
        }
        return out;
    }

    /** Distance horizontale au carre au point de Voie le plus proche (Long.MAX_VALUE s'il n'y en a pas). */
    public static long nearestWay(List<int[]> ways, int x, int z) {
        long best = Long.MAX_VALUE;
        for (int[] w : ways) {
            long dx = w[0] - x, dz = w[1] - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    /** Une colonne ou un Colporteur peut se tenir : sol connu, sec, hors ruine, deux cases libres. Renvoie les pieds ou {@link #NONE}. */
    public static int standable(Terrain t, int x, int z) {
        int y = t.feet(x, z);
        if (y == NONE || t.wet(x, z) || t.ruin(x, z) || !t.free(x, y, z) || !t.free(x, y + 1, z)) {
            return NONE;
        }
        return y;
    }

    /**
     * Emplacement d'un Colporteur de route : {@code tries} essais dans l'anneau {@link #ROUTE_MIN}..{@link #ROUTE_MAX} autour du joueur,
     * au sol et sur ; le premier a moins de {@link #WAY_NEAR} blocs d'une Voie est pris, sinon le premier valide. {x, y, z} ou null.
     */
    public static int[] routeSpot(Terrain t, int px, int pz, List<int[]> ways, Random rnd, int tries) {
        int[] fallback = null;
        for (int i = 0; i < tries; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = ROUTE_MIN + rnd.nextDouble() * (ROUTE_MAX - ROUTE_MIN);
            int x = px + (int) Math.round(Math.cos(a) * d), z = pz + (int) Math.round(Math.sin(a) * d);
            int y = standable(t, x, z);
            if (y == NONE) {
                continue;
            }
            int[] spot = {x, y, z};
            if (nearestWay(ways, x, z) <= (long) WAY_NEAR * WAY_NEAR) {
                return spot;
            }
            if (fallback == null) {
                fallback = spot;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ plans
    /** Pieces d'un plan : toile et faitage des tentes, caisse, feu, Tableau des besoins, place d'un Colporteur, case a garder libre. */
    public enum Part {
        CANVAS, RIDGE, CRATE, FIRE, BOARD, PEDDLER, SPACE;

        /** Piece posee dans le monde (les autres ne sont que des cases a garder libres). */
        public boolean placed() {
            return this != PEDDLER && this != SPACE;
        }
    }

    /**
     * Une piece en repere local : decalage, sorte, devant (0 nord -z, 1 est +x, 2 sud +z, 3 ouest -x). Le plan se tourne d'un quart de tour
     * dans le sens horaire vu du dessus a chaque pas de {@code q}.
     */
    public record Piece(int dx, int dy, int dz, Part part, int facing) {
    }

    /** (dx, dz) tourne de q quarts de tour (sens horaire vu du dessus : le nord devient l'est). */
    public static int[] rotate(int dx, int dz, int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> new int[]{-dz, dx};
            case 2 -> new int[]{-dx, -dz};
            case 3 -> new int[]{dz, -dx};
            default -> new int[]{dx, dz};
        };
    }

    /** Quart de tour qui fait regarder le devant d'un plan (nord en local) vers (dx, dz). */
    public static int quarterToward(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? 1 : 3;
        }
        return dz > 0 ? 2 : 0;
    }

    /**
     * Tente en A de 3 x 3, ouverte vers le nord en local (rangee z = 0 devant, fond z = 2) : toile sur les flancs et au fond, faitage au
     * dessus de l'allee, allee libre. Placee en (ox, oz) et tournee de q.
     */
    public static List<Piece> tent(int ox, int oz, int q) {
        List<Piece> raw = new ArrayList<>();
        for (int z = 0; z <= 2; z++) {
            raw.add(new Piece(-1, 0, z, Part.CANVAS, 0));
            raw.add(new Piece(1, 0, z, Part.CANVAS, 0));
            raw.add(new Piece(0, 1, z, Part.RIDGE, 0));
            if (z < 2) {
                raw.add(new Piece(0, 0, z, Part.SPACE, 0));
            }
        }
        raw.add(new Piece(0, 0, 2, Part.CANVAS, 0));
        List<Piece> out = new ArrayList<>();
        for (Piece p : raw) {
            int[] r = rotate(p.dx(), p.dz(), q);
            out.add(new Piece(ox + r[0], p.dy(), oz + r[1], p.part(), Math.floorMod(p.facing() + q, 4)));
        }
        return out;
    }

    /**
     * Halte (11 x 10) : feu au centre, trois tentes ouvertes vers le feu (ouest, est, sud), trois caisses et le Tableau des besoins au nord
     * tournes vers le feu, deux places de Colporteur.
     */
    public static List<Piece> haltPlan() {
        List<Piece> out = new ArrayList<>();
        out.add(new Piece(0, 0, 0, Part.FIRE, 0));
        out.addAll(tent(-3, 0, 1));
        out.addAll(tent(3, 0, 3));
        out.addAll(tent(0, 3, 0));
        out.add(new Piece(0, 0, -4, Part.BOARD, 2));
        out.add(new Piece(-3, 0, -3, Part.CRATE, 2));
        out.add(new Piece(2, 0, -3, Part.CRATE, 2));
        out.add(new Piece(3, 0, -3, Part.CRATE, 2));
        out.add(new Piece(-1, 0, -2, Part.PEDDLER, 2));
        out.add(new Piece(1, 0, 1, Part.PEDDLER, 0));
        for (int x = -2; x <= 2; x++) {
            for (int z = -3; z <= 2; z++) {
                if (x != 0 || z != 0) {
                    out.add(new Piece(x, 0, z, Part.SPACE, 0));
                }
            }
        }
        return clean(out);
    }

    /** Petite place de caravane (5 x 5) : une tente ouverte vers le nord (vers le Tableau du village), trois caisses, deux places. */
    public static List<Piece> caravanPlan() {
        List<Piece> out = new ArrayList<>(tent(0, 0, 0));
        out.add(new Piece(-2, 0, -1, Part.CRATE, 0));
        out.add(new Piece(2, 0, -1, Part.CRATE, 0));
        out.add(new Piece(2, 0, 1, Part.CRATE, 3));
        out.add(new Piece(-1, 0, -2, Part.PEDDLER, 0));
        out.add(new Piece(1, 0, -2, Part.PEDDLER, 0));
        for (int x = -1; x <= 1; x++) {
            out.add(new Piece(x, 0, -1, Part.SPACE, 0));
            out.add(new Piece(x, 0, -2, Part.SPACE, 0));
        }
        return clean(out);
    }

    /** Une case ne garde qu'une piece : une piece posee l'emporte sur une case libre ; les places de Colporteur gardent la tete libre. */
    private static List<Piece> clean(List<Piece> in) {
        Set<Long> solid = new HashSet<>();
        for (Piece p : in) {
            if (p.part().placed()) {
                solid.add(key(p.dx(), p.dy(), p.dz()));
            }
        }
        List<Piece> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Piece p : in) {
            long k = key(p.dx(), p.dy(), p.dz());
            if (!p.part().placed() && solid.contains(k)) {
                continue;
            }
            if (seen.add(k * 8 + p.part().ordinal())) {
                out.add(p);
            }
            if (p.part() == Part.PEDDLER && !solid.contains(key(p.dx(), p.dy() + 1, p.dz())) && seen.add(key(p.dx(), p.dy() + 1, p.dz()) * 8 + 7)) {
                out.add(new Piece(p.dx(), p.dy() + 1, p.dz(), Part.SPACE, 0));
            }
        }
        return out;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 22) | ((long) (y + 512) << 11) | (z + 512);
    }

    /** Piece placee dans le monde : position et devant absolus. */
    public record Placed(int x, int y, int z, Part part, int facing) {
    }

    /** Le plan tourne de q et pose en (x, y, z). */
    public static List<Placed> place(List<Piece> plan, int x, int y, int z, int q) {
        List<Placed> out = new ArrayList<>();
        for (Piece p : plan) {
            int[] r = rotate(p.dx(), p.dz(), q);
            out.add(new Placed(x + r[0], y + p.dy(), z + r[1], p.part(), Math.floorMod(p.facing() + q, 4)));
        }
        return out;
    }

    /**
     * Le plan tient-il, tourne de q, avec son origine aux pieds (x, y, z) ? Toutes les colonnes de l'emprise ont leurs pieds a y (terrain
     * plat), sont seches, hors ruine et hors Voie ; toutes les cases du plan sont libres (jamais un bloc de joueur ecrase).
     */
    public static boolean fits(Terrain t, List<Piece> plan, int x, int y, int z, int q) {
        Set<Long> cols = new HashSet<>();
        for (Placed p : place(plan, x, y, z, q)) {
            if (cols.add(key(p.x() - x, 0, p.z() - z))) {
                if (t.feet(p.x(), p.z()) != y || t.wet(p.x(), p.z()) || t.ruin(p.x(), p.z()) || t.way(p.x(), p.z())) {
                    return false;
                }
            }
            if (!t.free(p.x(), p.y(), p.z())) {
                return false;
            }
        }
        return true;
    }

    /** Un site choisi : origine (pieds) et quart de tour. */
    public record Site(int x, int y, int z, int q) {
    }

    /**
     * Cherche un site pour le plan a une distance rMin..rMax de (cx, cz), {@code tries} essais. Le plan est tourne vers (fx, fz) (le Tableau
     * d'un village, une Voie). {@code eye} non nul = {px, pz, vx, vz} : un site hors du regard du joueur (plus de 70 degres de sa visee, ou
     * plus loin que 24 blocs) est prefere.
     */
    public static Site findSite(Terrain t, List<Piece> plan, int cx, int cz, int rMin, int rMax, int fx, int fz, Random rnd, int tries,
          double[] eye) {
        Site fallback = null;
        for (int i = 0; i < tries; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = rMin + rnd.nextDouble() * Math.max(0, rMax - rMin);
            int x = cx + (int) Math.round(Math.cos(a) * d), z = cz + (int) Math.round(Math.sin(a) * d);
            int y = t.feet(x, z);
            if (y == NONE) {
                continue;
            }
            int q = quarterToward(fx - x, fz - z);
            if (!fits(t, plan, x, y, z, q)) {
                continue;
            }
            Site s = new Site(x, y, z, q);
            if (eye == null || hidden(eye, x, z)) {
                return s;
            }
            if (fallback == null) {
                fallback = s;
            }
        }
        return fallback;
    }

    /** Le point (x, z) est-il hors de la vue du joueur ({px, pz, vx, vz}) : plus de 24 blocs, ou plus de 70 degres de la visee ? */
    public static boolean hidden(double[] eye, int x, int z) {
        double dx = x - eye[0], dz = z - eye[1];
        double d = Math.sqrt(dx * dx + dz * dz), v = Math.sqrt(eye[2] * eye[2] + eye[3] * eye[3]);
        if (d > 24 || d < 1e-6 || v < 1e-6) {
            return d > 24;
        }
        double cos = (dx * eye[2] + dz * eye[3]) / (d * v);
        return cos < Math.cos(Math.toRadians(70));
    }

    // ------------------------------------------------------------------ Haltes
    /** Faut-il une Halte pres de (x, z) : aucune autre a moins de {@code spacing} blocs ? Halte = {x, z}. */
    public static boolean haltNeeded(List<long[]> halts, long x, long z, int spacing) {
        for (long[] h : halts) {
            if (within(h[0] - x, h[1] - z, spacing)) {
                return false;
            }
        }
        return true;
    }

    /** Marque de profil d'un groupe de Colporteurs (XP "trade.caravan" une fois par groupe) : empreinte FNV-1a 64 bits en base 36. */
    public static String caravanMark(String group) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < group.length(); i++) {
            h ^= group.charAt(i);
            h *= 0x100000001b3L;
        }
        return "cv:" + Long.toUnsignedString(h, 36);
    }
}
