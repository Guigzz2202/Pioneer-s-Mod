package fr.pioneers.world.fauna;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 4) : une espece de la faune persistante, declaree en UNE LIGNE dans {@link FaunaRegistry#bootstrap} (ou par un lot
 * suivant via {@link FaunaRegistry#add}) :
 * <pre>
 * FaunaRegistry.add(FaunaSpecies.of("pioneers:deer", FaunaGroup.GAME).biomes("#minecraft:is_forest", "minecraft:meadow")
 *       .seasons(1, 1, 1, 0.7).size(3, 6).weight(10).cap(12));
 * FaunaRegistry.add(FaunaSpecies.of("pioneers:rat", FaunaGroup.GAME).biomes("*").size(1, 2).weight(4).nearBuildings());
 * </pre>
 * <ul>
 *     <li>{@link #biomes} : {@code *} (tout l'Overworld), {@code #tag}, id de biome, ou {@code family:<famille>} (familles des saisons :
 *     temperate, cold, dry, tropical, ocean, snowy) ;</li>
 *     <li>{@link #listed} : biomes, poids et tailles de bande LUS dans les listes d'apparition des biomes du jeu (vanilla et modificateurs
 *     de biome du mod) ; lieu d'apres le placement du type ;</li>
 *     <li>{@link #persistOnly} : persistant et compte dans son groupe, jamais remis (poissons vanilla, que le mod ne fait plus naitre) ;</li>
 *     <li>{@link #nearBuildings} : peut reapparaitre dans un claim et pres d'un village (rats, pigeons) ;</li>
 *     <li>{@link #cap} : plafond propre par zone (0 = seulement celui du groupe) ;</li>
 *     <li>{@link #where} : terre, eau, grottes (eau sous la couche 50) ;</li>
 *     <li>{@link #rule} : regle de lieu en plus de celle du type ({@code SpawnPlacements}).</li>
 * </ul>
 */
public final class FaunaSpecies {

    /** Ou la bande apparait (et quel budget de la generation elle partage). */
    public enum Where {
        LAND, WATER, CAVE
    }

    /** Regle de lieu en plus de celle du type. */
    @FunctionalInterface
    public interface Rule {

        boolean test(ServerLevel level, BlockPos pos);
    }

    public final String id;
    public final FaunaGroup group;
    int index = -1;
    String[] biomes = {"*"};
    double[] seasons = {1, 1, 1, 1};
    int min = 1, max = 1, weight = 1, cap;
    /** 1.0.5-beta (lot 28) : rarete (multiplie le poids, saisons comprises : {@link #season}) ; 1 par defaut. */
    double rarity = 1.0;
    boolean listed, persistOnly, nearBuildings;
    @Nullable
    Where where;
    @Nullable
    Rule rule;

    private FaunaSpecies(String id, FaunaGroup group) {
        this.id = id;
        this.group = group;
    }

    public static FaunaSpecies of(String id, FaunaGroup group) {
        return new FaunaSpecies(id, group);
    }

    public FaunaSpecies biomes(String... biomes) {
        this.biomes = biomes.clone();
        return this;
    }

    /** Poids par saison (printemps, ete, automne, hiver), multiplies au poids de l'espece au moment du tirage. */
    public FaunaSpecies seasons(double spring, double summer, double autumn, double winter) {
        this.seasons = new double[]{spring, summer, autumn, winter};
        return this;
    }

    public FaunaSpecies size(int min, int max) {
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
        return this;
    }

    public FaunaSpecies weight(int weight) {
        this.weight = Math.max(0, weight);
        return this;
    }

    /**
     * 1.0.5-beta (lot 28, retour 37 de Guillaume : « il y a trop de grosses faunes dans les oceans ») : RARETE, facteur du poids (saisons
     * comprises) partout ou il compte : generation des troncons, estimation de la densite d'une zone, repeuplement. Les poids entiers ne
     * descendent pas sous 1 : la rarete affine (grande faune marine : 0,3 a 0,5).
     */
    public FaunaSpecies rarity(double rarity) {
        this.rarity = Math.max(0.0, rarity);
        return this;
    }

    public double rarity() {
        return rarity;
    }

    public FaunaSpecies cap(int cap) {
        this.cap = Math.max(0, cap);
        return this;
    }

    public FaunaSpecies nearBuildings() {
        this.nearBuildings = true;
        return this;
    }

    public FaunaSpecies listed() {
        this.listed = true;
        return this;
    }

    public FaunaSpecies persistOnly() {
        this.persistOnly = true;
        return this;
    }

    public FaunaSpecies where(Where where) {
        this.where = where;
        return this;
    }

    public FaunaSpecies rule(Rule rule) {
        this.rule = rule;
        return this;
    }

    // ------------------------------------------------------------------ lecture

    public int index() {
        return index;
    }

    public String[] biomes() {
        return biomes.clone();
    }

    /** Facteur du poids pour cette saison, rarete comprise (1.0.5-beta lot 28). */
    public double season(int season) {
        return seasons[Math.floorMod(season, 4)] * rarity;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int weight() {
        return weight;
    }

    public int cap() {
        return cap;
    }

    public boolean isListed() {
        return listed;
    }

    public boolean isPersistOnly() {
        return persistOnly;
    }

    public boolean isNearBuildings() {
        return nearBuildings;
    }

    @Nullable
    public Where declaredWhere() {
        return where;
    }

    @Nullable
    public Rule rule() {
        return rule;
    }

    @Override
    public String toString() {
        return id + " (" + group.id() + (listed ? ", listee" : "") + (persistOnly ? ", persistante seulement" : "") + ", biomes "
               + Arrays.toString(biomes) + ")";
    }
}
