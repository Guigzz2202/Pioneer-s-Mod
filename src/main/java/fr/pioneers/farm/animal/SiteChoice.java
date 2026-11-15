package fr.pioneers.farm.animal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41, retour 67 de Guillaume : « Quand un animal d'elevage doit manger ou boire, il doit checker si le path finding lui
 * permet sinon il regarde ailleur, dans mes enclos mes animaux focus les autres abreuvoirs et se laisse deperir au lieu de boire dans le
 * leur ») : CHOIX D'UN LIEU JOIGNABLE (mangeoire, abreuvoir, eau du monde, pondoir) pour un but d'IA d'un animal d'elevage.
 * <ul>
 *     <li>les candidats arrivent tries du plus proche au plus loin ({@link #choose}) ;</li>
 *     <li>chacun est essaye par le VRAI calcul de chemin du jeu ({@code PathNavigation.createPath(cible, 1)}, fourni par {@link Paths}) :
 *     il n'est retenu que si le BOUT du chemin est a portee de la cible ({@link Reach} : la meme regle que celle qui fait manger, boire ou
 *     pondre), un chemin partiel qui s'arrete contre une cloture ne compte pas ;</li>
 *     <li>une cible injoignable est ECARTEE {@link #FORGET_TICKS} ticks (30 s ; l'eau du monde : toute la mare autour, rayon
 *     {@link #WATER_RADIUS}) et le candidat suivant est essaye ; au plus {@link #PATHS_PER_DECISION} calculs de chemin par decision (regle de
 *     performance 5 de CLAUDE.md : calculs espaces et bornes ; la cadence des decisions est celle du but, a son compte a rebours, jamais
 *     {@code tickCount % n}) ;</li>
 *     <li>au plus {@link #MAX_FORGOTTEN} cibles ecartees a la fois par bete (la plus ancienne cede sa place).</li>
 * </ul>
 * Aucune lecture du monde ici : le banc hors jeu {@code tools/farmtest/PenBench.java} fait tourner cette classe telle quelle avec le vrai
 * {@code PathFinder} du jeu sur deux enclos voisins (« ENCLOS : fautes 0 »).
 */
public final class SiteChoice {

    /** Duree pendant laquelle une cible injoignable est ecartee (ticks). */
    public static final int FORGET_TICKS = 600;
    /** Calculs de chemin au plus par decision. */
    public static final int PATHS_PER_DECISION = 2;
    /** Cibles ecartees retenues au plus par bete. */
    public static final int MAX_FORGOTTEN = 8;
    /** Eau du monde injoignable : toute l'eau a ce rayon est ecartee avec elle (une mare derriere une cloture). */
    public static final int WATER_RADIUS = 3;
    /** Nouveaux calculs de chemin au plus en route vers une cible (bete arretee avant d'y etre) avant de l'ecarter. */
    public static final int MAX_REPATHS = 2;

    /** Le calcul de chemin du jeu (en jeu : {@code mob.getNavigation().createPath(cible, 1)}). */
    @FunctionalInterface
    public interface Paths {

        @Nullable
        Path find(BlockPos target);
    }

    /** Une bete debout en (x, y, z) est-elle a portee de la cible (la regle du but : manger, boire, pondre) ? */
    @FunctionalInterface
    public interface Reach {

        boolean test(BlockPos target, double x, double y, double z);
    }

    /** Cible retenue et son chemin (deja calcule : la bete le suit sans nouveau calcul). */
    public record Choice(BlockPos target, Path path) {
    }

    private final long[] forgotPos = new long[MAX_FORGOTTEN];
    private final long[] forgotUntil = new long[MAX_FORGOTTEN];
    private final byte[] forgotRadius = new byte[MAX_FORGOTTEN];
    private int budget;
    private int computed;

    /**
     * Candidats : les lieux a {@code radius} blocs de {@code from}, du plus proche au plus loin, qui passent le test (teste dans cet ordre,
     * seulement jusqu'a en avoir {@code max}) : {@code FarmSites.sorted} et le banc.
     */
    public static List<BlockPos> nearestFirst(Collection<BlockPos> sites, BlockPos from, int radius, Predicate<BlockPos> ok, int max) {
        double r2 = (double) radius * radius;
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos p : sites) {
            if (p.distSqr(from) <= r2) {
                near.add(p);
            }
        }
        near.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
        List<BlockPos> out = new ArrayList<>(Math.min(max, near.size()));
        for (BlockPos p : near) {
            if (out.size() >= max) {
                break;
            }
            if (ok.test(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Debut d'une decision : budget de calculs de chemin remis. */
    public void begin() {
        budget = PATHS_PER_DECISION;
    }

    /** Calculs de chemin encore permis dans cette decision. */
    public int budget() {
        return budget;
    }

    /** Calculs de chemin faits depuis la creation (banc, GameTests). */
    public int computed() {
        return computed;
    }

    /** La cible est-elle ecartee a l'instant {@code now} ? */
    public boolean forgotten(BlockPos pos, long now) {
        for (int i = 0; i < MAX_FORGOTTEN; i++) {
            if (forgotUntil[i] > now) {
                int r = forgotRadius[i];
                if (r == 0 ? forgotPos[i] == pos.asLong() : BlockPos.of(forgotPos[i]).closerThan(pos, r + 0.5)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ecarte la cible {@link #FORGET_TICKS} ticks (avec les cases a {@code radius} blocs ; 0 : elle seule). */
    public void forget(BlockPos pos, long now, int radius) {
        int slot = 0;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < MAX_FORGOTTEN; i++) {
            if (forgotUntil[i] <= now) {
                slot = i;
                break;
            }
            if (forgotUntil[i] < oldest) {
                oldest = forgotUntil[i];
                slot = i;
            }
        }
        forgotPos[slot] = pos.asLong();
        forgotUntil[slot] = now + FORGET_TICKS;
        forgotRadius[slot] = (byte) radius;
    }

    /** Nombre de cibles ecartees a l'instant {@code now}. */
    public int forgottenCount(long now) {
        int n = 0;
        for (long until : forgotUntil) {
            if (until > now) {
                n++;
            }
        }
        return n;
    }

    /**
     * Premier candidat joignable (candidats tries du plus proche au plus loin) dans le budget de la decision ; les injoignables essayes sont
     * ecartes (rayon {@code radius}). Null : aucun (budget epuise, tous ecartes ou injoignables).
     */
    @Nullable
    public Choice choose(List<BlockPos> sorted, long now, int radius, Paths paths, Reach reach) {
        for (BlockPos p : sorted) {
            if (budget <= 0) {
                return null;
            }
            if (forgotten(p, now)) {
                continue;
            }
            Path path = attempt(p, paths, reach);
            if (path != null) {
                return new Choice(p, path);
            }
            forget(p, now, radius);
        }
        return null;
    }

    /** Un calcul de chemin vers la cible, compte dans le budget : le chemin s'il arrive a portee, sinon null. */
    @Nullable
    public Path attempt(BlockPos target, Paths paths, Reach reach) {
        budget--;
        computed++;
        Path path = paths.find(target);
        return path != null && reaches(path, target, reach) ? path : null;
    }

    /** Le bout du chemin est-il a portee de la cible ? (Un chemin partiel arrete contre une cloture : non.) */
    public static boolean reaches(Path path, BlockPos target, Reach reach) {
        Node end = path.getEndNode();
        return end != null && reach.test(target, end.x + 0.5, end.y, end.z + 0.5);
    }
}
