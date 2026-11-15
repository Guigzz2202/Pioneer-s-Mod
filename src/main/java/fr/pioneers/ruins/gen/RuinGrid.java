package fr.pioneers.ruins.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.9 (MAJ RP, bloc 1) : grille deterministe des sites de ruines (Java pur : le banc hors jeu rejoue la meme grille). Chaque tier decoupe le
 * monde en regions carrees de {@code spacing} chunks ; une region propose {@code candidates} chunks candidats (tires de la graine du monde,
 * dans [0, spacing - separation) depuis son coin, comme les structures vanilla) ; le site de la region est le premier candidat valable :
 * assez loin du point de reference (spawn), pas trop pres d'un site d'un tier superieur, terrain qui convient ({@link Terrain}). Aucune
 * donnee sauvee : n'importe quel chunk peut recalculer les sites voisins (annexes).
 */
public final class RuinGrid {

    public static final int TIERS = 3;

    /** Reglage d'un tier : espacement et separation en chunks, distance mini au point de reference en blocs. */
    public record TierGrid(int spacing, int separation, int spawnDistance) {

        /** Reglage a une densite donnee (1 = normale, 3 = triple) : l'espacement suit 1 / racine(densite). */
        public TierGrid scaled(int density) {
            if (density <= 1) {
                return this;
            }
            double k = Math.sqrt(density);
            int s = Math.max(4, (int) Math.round(spacing / k));
            int sep = (int) Math.round(separation / k);
            sep = Math.max(Math.min(separation, 7), sep);
            sep = Math.min(sep, s - 1);
            return new TierGrid(s, sep, spawnDistance);
        }
    }

    /** Grille complete : tiers MKI..MKIII, candidats par region, exclusion entre tiers (chunks), point de reference (blocs). */
    public record Layout(TierGrid[] tiers, int candidates, int exclusion, int refX, int refZ) {

        public TierGrid tier(int tier) {
            return tiers[Math.max(1, Math.min(TIERS, tier)) - 1];
        }
    }

    /** Un site : tier, region, index du candidat retenu, chunk du centre, graine propre au site. */
    public record Site(int tier, int regionX, int regionZ, int index, int chunkX, int chunkZ, long seed) {

        public int centerX() {
            return (chunkX << 4) + 8;
        }

        public int centerZ() {
            return (chunkZ << 4) + 8;
        }
    }

    /** Le terrain convient-il a ce candidat ? (jeu : bruit du generateur ; banc : modele de rejet). */
    public interface Terrain {

        boolean suits(Site candidate);
    }

    private RuinGrid() {
    }

    private static final long SALT_REGION = 0x52_55_49_4E_47_52_49_44L;

    /** Candidat {@code k} de la region (rx, rz) du tier. */
    public static Site candidate(long world, Layout layout, int tier, int rx, int rz, int k) {
        TierGrid g = layout.tier(tier);
        long h = Rng.hash(world, SALT_REGION, tier, rx, rz, k);
        int span = Math.max(1, g.spacing() - g.separation());
        int cx = rx * g.spacing() + (int) Math.floorMod(h, (long) span);
        int cz = rz * g.spacing() + (int) Math.floorMod(h >>> 32, (long) span);
        return new Site(tier, rx, rz, k, cx, cz, Rng.mix(h ^ 0x5DEECE66DL));
    }

    /** Site de la region (premier candidat valable), ou null. */
    public static Site resolve(long world, Layout layout, int tier, int rx, int rz, Terrain terrain) {
        TierGrid g = layout.tier(tier);
        for (int k = 0; k < layout.candidates(); k++) {
            Site c = candidate(world, layout, tier, rx, rz, k);
            if (g.spawnDistance() > 0 && dist2(c.centerX() - layout.refX(), c.centerZ() - layout.refZ())
                                         < (long) g.spawnDistance() * g.spawnDistance()) {
                continue;
            }
            if (suppressed(world, layout, c, terrain) || !terrain.suits(c)) {
                continue;
            }
            return c;
        }
        return null;
    }

    /** Un site d'un tier superieur occupe deja les environs (exclusion en chunks). */
    private static boolean suppressed(long world, Layout layout, Site c, Terrain terrain) {
        int ex = layout.exclusion();
        for (int t = c.tier() + 1; t <= TIERS; t++) {
            TierGrid g = layout.tier(t);
            int r0x = Math.floorDiv(c.chunkX() - ex, g.spacing()), r1x = Math.floorDiv(c.chunkX() + ex, g.spacing());
            int r0z = Math.floorDiv(c.chunkZ() - ex, g.spacing()), r1z = Math.floorDiv(c.chunkZ() + ex, g.spacing());
            for (int rx = r0x; rx <= r1x; rx++) {
                for (int rz = r0z; rz <= r1z; rz++) {
                    if (!anyCandidateNear(world, layout, t, rx, rz, c.chunkX(), c.chunkZ(), ex)) {
                        continue;
                    }
                    Site s = resolve(world, layout, t, rx, rz, terrain);
                    if (s != null && Math.abs(s.chunkX() - c.chunkX()) <= ex && Math.abs(s.chunkZ() - c.chunkZ()) <= ex) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean anyCandidateNear(long world, Layout layout, int tier, int rx, int rz, int cx, int cz, int r) {
        for (int k = 0; k < layout.candidates(); k++) {
            Site s = candidate(world, layout, tier, rx, rz, k);
            if (Math.abs(s.chunkX() - cx) <= r && Math.abs(s.chunkZ() - cz) <= r) {
                return true;
            }
        }
        return false;
    }

    /** Site du tier dont le centre est dans ce chunk, ou null (cout : candidats de la region, terrain seulement si l'un tombe ici). */
    public static Site siteInChunk(long world, Layout layout, int tier, int chunkX, int chunkZ, Terrain terrain) {
        TierGrid g = layout.tier(tier);
        int rx = Math.floorDiv(chunkX, g.spacing()), rz = Math.floorDiv(chunkZ, g.spacing());
        if (!anyCandidateNear(world, layout, tier, rx, rz, chunkX, chunkZ, 0)) {
            return null;
        }
        Site s = resolve(world, layout, tier, rx, rz, terrain);
        return s != null && s.chunkX() == chunkX && s.chunkZ() == chunkZ ? s : null;
    }

    /**
     * Candidats (non resolus) dont le centre est a moins de {@code radius} blocs de (x, z), tous tiers : pour les annexes, qui ne resolvent
     * un site (terrain) que si l'une d'elles tombe dans leur chunk.
     */
    public static List<Site> candidatesNear(long world, Layout layout, int x, int z, int radius) {
        List<Site> out = new ArrayList<>();
        for (int t = 1; t <= TIERS; t++) {
            TierGrid g = layout.tier(t);
            int span = g.spacing() * 16;
            int r0x = Math.floorDiv(x - radius, span), r1x = Math.floorDiv(x + radius, span);
            int r0z = Math.floorDiv(z - radius, span), r1z = Math.floorDiv(z + radius, span);
            for (int rx = r0x; rx <= r1x; rx++) {
                for (int rz = r0z; rz <= r1z; rz++) {
                    for (int k = 0; k < layout.candidates(); k++) {
                        Site s = candidate(world, layout, t, rx, rz, k);
                        if (dist2(s.centerX() - x, s.centerZ() - z) <= (long) radius * radius) {
                            out.add(s);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Le candidat est-il le site retenu de sa region ? */
    public static boolean isSite(long world, Layout layout, Site candidate, Terrain terrain) {
        Site s = resolve(world, layout, candidate.tier(), candidate.regionX(), candidate.regionZ(), terrain);
        return s != null && s.index() == candidate.index();
    }

    /** Sites resolus d'un tier dans un carre de blocs (banc de densite). */
    public static List<Site> sitesIn(long world, Layout layout, int tier, int minX, int minZ, int maxX, int maxZ, Terrain terrain) {
        List<Site> out = new ArrayList<>();
        TierGrid g = layout.tier(tier);
        int span = g.spacing() * 16;
        for (int rx = Math.floorDiv(minX, span); rx <= Math.floorDiv(maxX, span); rx++) {
            for (int rz = Math.floorDiv(minZ, span); rz <= Math.floorDiv(maxZ, span); rz++) {
                Site s = resolve(world, layout, tier, rx, rz, terrain);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public static long dist2(long dx, long dz) {
        return dx * dx + dz * dz;
    }
}
