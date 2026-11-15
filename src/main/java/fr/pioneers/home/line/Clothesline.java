package fr.pioneers.home.line;

import fr.pioneers.Pioneers;
import fr.pioneers.cosmetic.CosmeticItem;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.fish.FishItem;
import fr.pioneers.farm.kitchen.Heat;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Patch 1.0.3-beta, lot 7 (Guillaume : « des crochets pour relier 2 cordes [...] cette corde sert a accrocher des choses : des vetements, des
 * poissons et autres idees » ; choix : « deco + sechage ») : regles pures du fil a linge, communes au serveur, au client (visee, rendu) et aux
 * GameTests.
 * <ul>
 *   <li>Geometrie : chainette entre les deux yeux des crochets, fleche {@link #sag} (comme une laisse : un peu plus avec la longueur et le
 *   poids) ; places espacees de {@link #SPACING} bloc ({@link #places}).</li>
 *   <li>Ce qui s'accroche ({@link #hangable}) : vetements (armures, cosmetiques, bannieres) = deco ; poissons, viandes, herbes, plantes : tout
 *   ce qui a une recette de SECHOIR ({@code pioneers:drying}) ou de FUMOIR ({@code pioneers:smoking}), et ce qui en sort ; tag
 *   {@code pioneers:clothesline_hangable}.</li>
 *   <li>Sechage ({@link #rate}) : la duree du sechoir x {@link #LINE_TIME} (a l'air) ; plus vite au soleil (ciel ouvert, jour) x 1,5 = le
 *   sechoir, au sec (biome aride) x 1,25 ; moins vite au biome humide x 0,75 ; ARRET sous la pluie. Feu de camp allume 1 a 3 blocs sous la
 *   piece : FUMAGE si la piece a une recette de fumoir, sinon sechage x 2 (comme le sechoir pose sur un feu) ; une recette de sechoir qui
 *   exige le feu ({@code smoke}) attend un feu.</li>
 * </ul>
 */
public final class Clothesline {

    /** Portee maximale d'une corde entre deux crochets (blocs). */
    public static final int MAX_LENGTH = 16;
    /** Ecart entre deux places sur la corde (blocs) ; au plus {@link #MAX_PLACES} places. */
    public static final double SPACING = 0.5;
    public static final int MAX_PLACES = 32;
    /** Le fil a linge seche moins vite que le sechoir a l'ombre : duree de la recette x 1,5 (au soleil : x 1,5 aussi, donc comme le sechoir). */
    public static final double LINE_TIME = 1.5;
    /** Saut de calcul du sechage (ticks). */
    public static final int STEP = 20;
    public static final TagKey<Item> HANGABLE = ItemTags.create(Pioneers.rl("clothesline_hangable"));

    private Clothesline() {
    }

    // ------------------------------------------------------------------ geometrie
    /** Fleche au milieu (blocs) : 3 % de la portee + 0,02 par piece accrochee, au plus 1,2. */
    public static double sag(double length, int hung) {
        return Math.min(1.2, 0.03 * length + 0.02 * hung + 0.05);
    }

    /** Point de la corde a {@code t} (0..1) entre les yeux {@code a} et {@code b}. */
    public static Vec3 point(Vec3 a, Vec3 b, double sag, double t) {
        Vec3 p = a.add(b.subtract(a).scale(t));
        return p.add(0, -sag * 4 * t * (1 - t), 0);
    }

    /** 1.0.3-beta (lot 11) : point de la corde chargee (corde de {@link #point} + creux des pieces, {@link LineShape#dip}). */
    public static Vec3 loadedPoint(Vec3 a, Vec3 b, double sag, double t, double[] loads) {
        return point(a, b, sag, t).add(0, -LineShape.dip(t, loads), 0);
    }

    /** Nombre de places sur une corde de cette portee. */
    public static int places(double length) {
        return Math.max(1, Math.min(MAX_PLACES, (int) Math.floor(length / SPACING) - 1));
    }

    /** Position (0..1) de la place {@code i} sur {@code n}. */
    public static double t(int i, int n) {
        return (i + 1) / (double) (n + 1);
    }

    /** Place la plus proche de {@code t}. */
    public static int nearestPlace(double t, int n) {
        return Math.max(0, Math.min(n - 1, (int) Math.round(t * (n + 1)) - 1));
    }

    /**
     * Visee d'une corde (polyligne de 16 troncons) par un rayon : renvoie {t, distance le long du rayon} si le rayon passe a moins de
     * {@code radius} de la corde, sinon null.
     */
    @Nullable
    public static double[] pick(Vec3 a, Vec3 b, double sag, Vec3 from, Vec3 dir, double reach, double radius) {
        double best = Double.MAX_VALUE, bestT = -1, bestS = 0;
        int n = 16;
        Vec3 prev = point(a, b, sag, 0);
        for (int i = 1; i <= n; i++) {
            Vec3 cur = point(a, b, sag, i / (double) n);
            double[] c = closest(from, dir, prev, cur);
            if (c != null && c[0] >= 0 && c[0] <= reach && c[2] < best) {
                best = c[2];
                bestT = (i - 1 + c[1]) / n;
                bestS = c[0];
            }
            prev = cur;
        }
        return best <= radius * radius ? new double[]{bestT, bestS} : null;
    }

    /** Plus courte distance entre le rayon (o, d unitaire) et le segment [p, q] : {s le long du rayon, u sur le segment 0..1, distance au carre}. */
    @Nullable
    static double[] closest(Vec3 o, Vec3 d, Vec3 p, Vec3 q) {
        Vec3 e = q.subtract(p);
        Vec3 w = o.subtract(p);
        double a = d.dot(d), b = d.dot(e), c = e.dot(e), dd = d.dot(w), ee = e.dot(w);
        double den = a * c - b * b;
        double s, u;
        if (den < 1e-9) {
            u = 0;
            s = -dd / a;
        } else {
            s = (b * ee - c * dd) / den;
            u = (a * ee - b * dd) / den;
        }
        u = Math.max(0, Math.min(1, u));
        //Point du segment fixe : recalcul de s
        Vec3 ps = p.add(e.scale(u));
        s = ps.subtract(o).dot(d) / a;
        Vec3 pr = o.add(d.scale(s));
        return new double[]{s, u, pr.distanceToSqr(ps)};
    }

    // ------------------------------------------------------------------ ce qui s'accroche
    /** Recettes du sechoir / du fumoir a un seul ingredient qui prennent cet objet (la premiere par identifiant), ou null. */
    @Nullable
    public static KitchenRecipe recipe(Level level, Station station, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        List<KitchenRecipe> found = new ArrayList<>();
        for (KitchenRecipe r : level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(station).get())) {
            if (r.matchesSingle(stack)) {
                found.add(r);
            }
        }
        found.sort(Comparator.comparing(r -> r.getId().toString()));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Sortie d'une recette du sechoir ou du fumoir (piece sechee ou fumee qui reste accrochee). */
    static boolean produced(Level level, ItemStack stack) {
        for (Station s : new Station[]{Station.DRYING, Station.SMOKING}) {
            for (KitchenRecipe r : level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(s).get())) {
                for (KitchenRecipe.Result res : r.results()) {
                    if (ItemStack.isSameItem(res.stack(), stack)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Vetement (deco) : armure, cosmetique, banniere. */
    public static boolean garment(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem || stack.getItem() instanceof CosmeticItem || stack.getItem() instanceof BannerItem;
    }

    public static boolean hangable(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return garment(stack) || stack.is(HANGABLE) || stack.getItem() instanceof FishItem || stack.is(ItemTags.FISHES)
               || recipe(level, Station.DRYING, stack) != null || recipe(level, Station.SMOKING, stack) != null || produced(level, stack);
    }

    // ------------------------------------------------------------------ sechage
    /** Conditions de l'air a une place : pluie, soleil (ciel ouvert et jour), climat du biome, feu de camp dessous. */
    public record Air(boolean rain, boolean sun, double climate, boolean fire) {

        public static Air at(Level level, Vec3 p) {
            BlockPos pos = BlockPos.containing(p);
            boolean sky = level.canSeeSky(pos);
            Biome biome = level.getBiome(pos).value();
            float down = biome.getModifiedClimateSettings().downfall(), temp = biome.getBaseTemperature();
            double climate = down > 0.85F ? 0.75 : temp >= 1.5F && down < 0.1F ? 1.25 : 1.0;
            return new Air(level.isRainingAt(pos.above()), sky && level.isDay(), climate, Heat.smoke(level, pos));
        }
    }

    /** Ce que fait l'air a une piece : SMOKE (fumage), DRY (sechage), NONE. */
    public enum Mode { NONE, DRY, SMOKE }

    public static Mode mode(boolean hasDry, boolean dryNeedsFire, boolean hasSmoke, Air air) {
        if (air.fire && hasSmoke) {
            return Mode.SMOKE;
        }
        if (hasDry && (!dryNeedsFire || air.fire)) {
            return Mode.DRY;
        }
        return Mode.NONE;
    }

    /** Avancement par tick (en ticks de recette du fil) : pluie 0 (sauf sur un feu) ; feu x 2 ; soleil x 1,5 ; climat. */
    public static double rate(Mode mode, Air air) {
        if (mode == Mode.NONE) {
            return 0;
        }
        if (mode == Mode.SMOKE) {
            return 1;
        }
        if (air.fire) {
            return 2;
        }
        if (air.rain) {
            return 0;
        }
        return (air.sun ? 1.5 : 1.0) * air.climate;
    }

    /** Duree sur le fil (ticks a vitesse 1) d'une recette du sechoir ou du fumoir. */
    public static int lineTime(KitchenRecipe r) {
        return (int) Math.ceil(r.time() * LINE_TIME);
    }
}
