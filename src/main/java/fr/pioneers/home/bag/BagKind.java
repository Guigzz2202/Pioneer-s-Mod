package fr.pioneers.home.bag;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.crop.GeneticSeedItem;
import fr.pioneers.farm.fish.FishItem;
import fr.pioneers.farm.tree.FruitSaplingBlock;
import fr.pioneers.space.farm.ItemSpaceSeeds;
import java.util.function.Predicate;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SaplingBlock;

/**
 * Patch 1.0.3-beta, lot 7 : les sacs. Deux sacs a dos (Guillaume : « des backpacks en 2 tiers : cuir simple et cuir tanne (16 et 32 cases
 * d'inventaire) ») qui prennent tout sauf un autre sac ; trois sacs de rangement (« pour eviter de flood l'inventaire avec ces objets ayant des
 * stats ») qui ne prennent que leur sorte, chaque objet garde son NBT exact (genes, taille), et qui RAMASSENT tout seuls leur sorte quand ils
 * sont dans l'inventaire. Lignes de {@code columns} cases (8 pour les sacs a dos : 2 et 4 rangees pleines).
 */
public enum BagKind {
    LEATHER_BACKPACK("leather_backpack", 16, 8, false, null, new int[]{16}),
    TANNED_BACKPACK("tanned_backpack", 32, 8, false, null, new int[]{32}),
    SEED_BAG("seed_bag", 36, 9, true, Tags.SEEDS, new int[]{36, 54, 72}),
    SAPLING_BAG("sapling_bag", 27, 9, true, Tags.SAPLINGS, new int[]{27, 45, 63}),
    FISH_BAG("fish_bag", 36, 9, true, Tags.FISH, new int[]{36, 54, 72});

    public final String id;
    public final int slots, columns;
    /** Ramasse sa sorte tout seul quand il est dans l'inventaire (sacs de rangement). */
    public final boolean pickup;
    private final TagKey<Item> tag;
    /** 1.0.5-beta (lot 31) : sacs de rangement : nombre de CASES (sortes) par niveau I, II, III ({@link BagLedger}). */
    private final int[] cases;

    BagKind(String id, int slots, int columns, boolean pickup, TagKey<Item> tag, int[] cases) {
        this.id = id;
        this.slots = slots;
        this.columns = columns;
        this.pickup = pickup;
        this.tag = tag;
        this.cases = cases;
    }

    /**
     * 1.0.5-beta (lot 31, retour 42) : sac de RANGEMENT (graines, pousses, poissons) : contenu en registre {@link BagLedger} (cases a grosse
     * capacite, tri automatique, filtre, interrupteur de ramassage, niveaux) et ecran a cases virtuelles ({@link StorageBagMenu}) ; les sacs a
     * dos gardent leurs cases d'inventaire ({@link BagMenu}).
     */
    public boolean storage() {
        return pickup;
    }

    /** Cases (sortes) du sac au niveau {@code tier}. */
    public int cases(int tier) {
        return cases[Math.max(0, Math.min(cases.length - 1, tier))];
    }

    /** Niveaux possibles (1 pour les sacs a dos). */
    public int tiers() {
        return cases.length;
    }

    /** Variante gardee par case : genes (graines, pousses), taille (poissons). */
    public BagLedger.Mode mode() {
        return this == FISH_BAG ? BagLedger.Mode.SIZE : this == SEED_BAG || this == SAPLING_BAG ? BagLedger.Mode.GENES : BagLedger.Mode.NONE;
    }

    public int rows() {
        return (slots + columns - 1) / columns;
    }

    public boolean backpack() {
        return this == LEATHER_BACKPACK || this == TANNED_BACKPACK;
    }

    /**
     * Ce que le sac accepte. Jamais un sac (pas de sac dans un sac, ni de sac a graines dans un sac a dos : pas de contenu imbrique) ni un objet
     * qui refuse d'entrer dans un contenant (boites de Shulker). Sacs de rangement : leur tag ({@code pioneers:bags/<sorte>}) ou leur classe
     * (graines genetiques et spatiales ; pousses ; poissons du mod). 1.0.5-beta (lot 31) : plus de tags vanilla larges dans le code
     * ({@code minecraft:fishes} contient les poissons CUITS) : les tags {@code bags/*} sont complets ({@code T:core/p105_lot31.py}).
     */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof BagItem || !stack.getItem().canFitInsideContainerItems()) {
            return false;
        }
        return switch (this) {
            case SEED_BAG -> stack.is(tag) || stack.getItem() instanceof GeneticSeedItem || stack.getItem() instanceof ItemSpaceSeeds;
            case SAPLING_BAG -> stack.is(tag) || stack.getItem() instanceof BlockItem b
                  && (b.getBlock() instanceof SaplingBlock || b.getBlock() instanceof FruitSaplingBlock);
            case FISH_BAG -> stack.is(tag) || stack.getItem() instanceof FishItem;
            default -> true;
        };
    }

    public static BagKind byOrdinal(int i) {
        BagKind[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    /** Tags des sacs de rangement (generes par {@code tools/gc/home/data.py}). */
    public static final class Tags {

        private Tags() {
        }

        public static final TagKey<Item> SEEDS = ItemTags.create(Pioneers.rl("bags/seeds"));
        public static final TagKey<Item> SAPLINGS = ItemTags.create(Pioneers.rl("bags/saplings"));
        public static final TagKey<Item> FISH = ItemTags.create(Pioneers.rl("bags/fish"));
    }

    /** Filtre pour une case du sac. */
    public Predicate<ItemStack> filter() {
        return this::accepts;
    }
}
