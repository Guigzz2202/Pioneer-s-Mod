package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.steam.AccessoryBlock;
import fr.pioneers.tech.bronze.steam.AccessoryTile;
import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.bronze.steam.SteamAccessory;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.building.home.HomeWoods;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.bag.BagContents;
import fr.pioneers.building.home.bag.BagEvents;
import fr.pioneers.building.home.bag.BagItem;
import fr.pioneers.building.home.bag.BagKind;
import fr.pioneers.building.home.bag.BagUpgradeRecipe;
import fr.pioneers.building.home.furniture.CabinetBlock;
import fr.pioneers.building.home.furniture.TableBlock;
import fr.pioneers.building.home.line.Clothesline;
import fr.pioneers.building.home.line.HookBlock;
import fr.pioneers.building.home.line.HookTile;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.kinetics.PiKinetics;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Patch 1.0.3-beta, lot 7 (GameTests) : surete de chaudiere (coupe sous 20 % d'eau, repart a 25 %, sans elle rien ne change) ; sacs (NBT exact,
 * pas de sac dans un sac, ramassage automatique, semis de la meilleure graine, amelioration qui garde le contenu) ; meubles (pieds aux angles,
 * comptoir / placard) ; etageres par bois ; corde a linge (accrocher, reprendre, regles du sechage, crochet casse = corde rendue).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot7Tests {

    private PatchP103Lot7Tests() {
    }

    // ================================================================== surete de chaudiere
    private static FireboxTile boiler(GameTestHelper h, BlockPos at, boolean cutoff) {
        //1.0.6 : forme de Guillaume du lot 24 (l'ancienne colonne n'est plus formee) ; at = coin de la forme
        FireboxTile f = MultiblockApi6Tests.boiler(h, at, 0, cutoff ? PiBronze.ACCESSORIES.get(SteamAccessory.SAFETY_VALVE).getBlock()
              .defaultBlockState().setValue(AccessoryBlock.FACING, Direction.EAST) : null);
        //1.0.6 : charbon mis APRES le premier tick de la surete (comme en jeu : l'accessoire est deja la quand on charge le foyer) ; avant,
        //le foyer pose avant la surete brulait un charbon a son premier tick
        h.runAfterDelay(2, () -> f.guiSlots().get(0).setStack(new ItemStack(Items.COAL, 16)));
        return f;
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p103_7_cutoff", timeoutTicks = 200)
    public static void p103_7_surete_coupe_et_repart(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!AccessoryTile.trip(false, 0.21, 20) && AccessoryTile.trip(false, 0.19, 20), "coupe sous 20 %");
        Gt.check(AccessoryTile.trip(true, 0.24, 20) && !AccessoryTile.trip(true, 0.25, 20), "reprise a 25 % seulement (pas de clignotement)");
        FireboxTile cut = boiler(h, new BlockPos(3, 1, 3), true), free = boiler(h, new BlockPos(14, 1, 14), false);
        //1.0.6 : quantites en part de la contenance (la chaudiere de Guillaume a 11 corps, 4 000 mB chacun ; la colonne d'avant, un seul)
        int cap = cut.waterCapacity();
        cut.fillWater(cap / 10, false);                              // 10 %
        free.fillWater(cap / 10, false);
        h.runAtTickTime(60, () -> {
            Gt.check(cut.cut(), "eau a 10 % : la surete a coupe le foyer");
            Gt.check(cut.guiSlots().get(0).getStack().getCount() >= 15, "foyer coupe : le charbon ne brule plus");
            Gt.check(!free.cut(), "sans surete : rien ne coupe (comportement d'avant)");
            Gt.check(free.guiSlots().get(0).getStack().getCount() < 16, "sans surete : le foyer brule son charbon");
            cut.fillWater(cap / 5, false);                           // 30 %
        });
        h.runAtTickTime(120, () -> {
            Gt.check(!cut.cut(), "eau remise a 30 % : la chaudiere repart");
            s.done();
        });
    }

    // ================================================================== sacs
    private static ItemStack seed(int yield) {
        ItemStack st = new ItemStack(PiFarm.SEEDS.values().iterator().next().get());
        return new Genes(yield, 2, 3, 4).apply(st);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7_bags", timeoutTicks = 20)
    public static void p103_7_sacs_nbt_exact(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack bag = new ItemStack(PiHome.BAGS.get(BagKind.SEED_BAG).get());
        Gt.check(BagContents.insert(bag, BagKind.SEED_BAG, seed(7).copyWithCount(10), false).isEmpty(), "10 graines ★ rangees");
        Gt.check(BagContents.insert(bag, BagKind.SEED_BAG, seed(2).copyWithCount(5), false).isEmpty(), "5 graines d'autres genes rangees");
        ItemStackHandler hd = BagContents.read(bag, BagKind.SEED_BAG);
        Gt.equal(2, (long) hd.getStackInSlot(0).getCount() == 10 && hd.getStackInSlot(1).getCount() == 5 ? 2 : -1, "deux piles, une par genes");
        Gt.equal(7, Genes.of(hd.getStackInSlot(0)).yield(), "genes exacts gardes (pas de moyenne)");
        Gt.equal(0, BagItem.bestSeed(hd), "meilleure graine : celle de plus haut total");
        Gt.check(!BagKind.SEED_BAG.accepts(new ItemStack(Items.COBBLESTONE)), "le sac a graines refuse la pierre");
        Gt.check(!BagKind.TANNED_BACKPACK.accepts(bag), "pas de sac dans un sac");
        Gt.check(!BagKind.LEATHER_BACKPACK.accepts(new ItemStack(Items.SHULKER_BOX)), "pas de boite de Shulker dans un sac");
        Gt.check(BagKind.LEATHER_BACKPACK.accepts(new ItemStack(Items.COBBLESTONE)), "le sac a dos prend tout le reste");
        // ramassage : le sac a graines dans l'inventaire absorbe les graines, pas la pierre
        Player p = h.makeMockPlayer();
        p.getInventory().setItem(5, bag);
        ItemStack rest = BagEvents.absorb(p, seed(7).copyWithCount(4));
        Gt.check(rest.isEmpty(), "ramassage automatique dans le sac");
        Gt.equal(14, (long) BagContents.read(bag, BagKind.SEED_BAG).getStackInSlot(0).getCount(), "empilees avec les graines de memes genes");
        Gt.equal(3, (long) BagEvents.absorb(p, new ItemStack(Items.COBBLESTONE, 3)).getCount(), "la pierre n'entre pas");
        // amelioration simple -> tanne : le contenu passe de 16 a 32 cases sans rien perdre
        ItemStack small = new ItemStack(PiHome.BAGS.get(BagKind.LEATHER_BACKPACK).get());
        for (int i = 0; i < 16; i++) {
            BagContents.insert(small, BagKind.LEATHER_BACKPACK, new ItemStack(i % 2 == 0 ? Items.OAK_LOG : Items.APPLE, 64), false);
        }
        ItemStack big = new ItemStack(PiHome.BAGS.get(BagKind.TANNED_BACKPACK).get());
        big.setTag(small.getTag().copy());
        ItemStackHandler grown = BagContents.read(big, BagKind.TANNED_BACKPACK);
        Gt.equal(32, (long) grown.getSlots(), "32 cases");
        Gt.equal(16 * 64, (long) BagContents.count(grown), "tout le contenu garde");
        Gt.check(h.getLevel().getRecipeManager().byKey(new ResourceLocation("pioneers", "home/tanned_backpack_upgrade"))
              .filter(r -> r instanceof BagUpgradeRecipe).isPresent(), "recette d'amelioration pioneers:bag_upgrade chargee");
        s.done();
    }

    // ================================================================== meubles et etageres
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7_furniture", timeoutTicks = 20)
    public static void p103_7_meubles(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockState table = PiHome.TABLES.get("oak").getBlock().defaultBlockState();
        BlockPos a = new BlockPos(2, 2, 2), b = a.east();
        h.setBlock(a.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(b.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(a, table);
        h.setBlock(b, table);
        h.setBlock(a, ((TableBlock) table.getBlock()).connect(h.getBlockState(a), h.getLevel(), h.absolutePos(a)));
        BlockState sa = h.getBlockState(a), sb = h.getBlockState(b);
        Gt.check(sa.getValue(TableBlock.EAST) && sb.getValue(TableBlock.WEST), "deux tables voisines reliees");
        Gt.check(TableBlock.leg(sa, -1, -1) && !TableBlock.leg(sa, 1, -1), "pieds seulement aux angles de la grande table");
        // meuble : sur la pierre = comptoir ; dans le vide = placard haut ; voisins relies
        BlockState cab = PiHome.CABINETS.get("spruce").getBlock().defaultBlockState();
        BlockPos c = new BlockPos(5, 2, 2), u = new BlockPos(5, 5, 2);
        h.setBlock(c.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(c.east().below(), Blocks.STONE.defaultBlockState());
        h.setBlock(c, CabinetBlock.connect(cab, h.getLevel(), h.absolutePos(c)));
        h.setBlock(c.east(), CabinetBlock.connect(cab, h.getLevel(), h.absolutePos(c.east())));
        h.setBlock(c, CabinetBlock.connect(h.getBlockState(c), h.getLevel(), h.absolutePos(c)));
        Gt.check(!h.getBlockState(c).getValue(CabinetBlock.UPPER), "pose sur un bloc : comptoir");
        Gt.check(h.getBlockState(c).getValue(CabinetBlock.LEFT) || h.getBlockState(c).getValue(CabinetBlock.RIGHT), "deux meubles voisins relies");
        h.setBlock(u, CabinetBlock.connect(cab, h.getLevel(), h.absolutePos(u)));
        Gt.check(h.getBlockState(u).getValue(CabinetBlock.UPPER), "sans rien dessous : placard haut");
        // etageres : une par bois, le chene garde dish_shelf, une seule tuile
        Gt.equal(HomeWoods.ALL.size(), (long) DishShelfBlock.VARIANTS.size(), "une etagere par bois");
        Gt.check(DishShelfBlock.VARIANTS.get("oak") == DishShelfBlock.ENTRY, "etagere de chene = dish_shelf d'avant (mondes existants)");
        for (var e : DishShelfBlock.VARIANTS.values()) {
            Gt.check(DishShelfBlock.TILE.get().isValid(e.getBlock().defaultBlockState()), "tuile commune : " + e.getBlock());
        }
        s.done();
    }

    // ================================================================== corde a linge
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_7_line", timeoutTicks = 40)
    public static void p103_7_corde_a_linge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos wa = new BlockPos(1, 2, 4), wb = new BlockPos(7, 2, 4);
        h.setBlock(wa, Blocks.STONE.defaultBlockState());
        h.setBlock(wb, Blocks.STONE.defaultBlockState());
        BlockState hook = PiHome.ROPE_HOOK.getBlock().defaultBlockState();
        BlockPos ha = wa.east(), hb = wb.west();
        h.setBlock(ha, hook.setValue(HookBlock.FACING, Direction.EAST));
        h.setBlock(hb, hook.setValue(HookBlock.FACING, Direction.WEST));
        Gt.check(HookTile.check(h.getLevel(), h.absolutePos(ha), h.absolutePos(hb)) == null, "deux crochets face a face : reliables");
        HookTile a = (HookTile) h.getBlockEntity(ha), b = (HookTile) h.getBlockEntity(hb);
        a.link(h.absolutePos(hb), LinkType.FIBER_ROPE, true);
        b.link(h.absolutePos(ha), LinkType.FIBER_ROPE, false);
        Gt.check(a.isMaster() && b.master() == a, "le premier crochet porte la corde");
        Gt.check(a.places() >= 6, "une place tous les demi-blocs : " + a.places());
        ItemStack fillet = new ItemStack(Items.COD, 3);
        Gt.check(a.hang(null, fillet, 0.5), "le poisson s'accroche");
        Gt.equal(2, (long) fillet.getCount(), "un seul objet par place");
        Gt.check(Clothesline.hangable(h.getLevel(), new ItemStack(Items.LEATHER_CHESTPLATE)), "un vetement s'accroche (deco)");
        Gt.check(!Clothesline.hangable(h.getLevel(), new ItemStack(Items.COBBLESTONE)), "la pierre ne s'accroche pas");
        // regles du sechage : soleil x 1,5, pluie 0, feu x 2, fumage sur un feu si la recette de fumoir existe
        Clothesline.Air sun = new Clothesline.Air(false, true, 1, false), rain = new Clothesline.Air(true, false, 1, false),
              fire = new Clothesline.Air(true, false, 1, true);
        Gt.check(Clothesline.rate(Clothesline.Mode.DRY, sun) == 1.5 && Clothesline.rate(Clothesline.Mode.DRY, rain) == 0, "soleil x 1,5, pluie : arret");
        Gt.check(Clothesline.rate(Clothesline.Mode.DRY, fire) == 2, "feu de camp dessous : x 2 meme sous la pluie");
        Gt.equal(Clothesline.Mode.SMOKE, Clothesline.mode(true, false, true, fire), "sur un feu, ce qui a une recette de fumoir se fume");
        Gt.equal(Clothesline.Mode.NONE, Clothesline.mode(true, true, false, sun), "recette qui exige le feu : rien sans feu");
        Gt.check(Clothesline.recipe(h.getLevel(), fr.pioneers.farm.kitchen.Station.DRYING, new ItemStack(Items.COD)) != null,
              "un poisson entier a sa recette de sechage (dried_whole_fish)");
        // reprendre, puis casser un crochet : la corde et la piece tombent
        Gt.check(a.hang(null, new ItemStack(Items.LEATHER_HELMET), 0.2), "deuxieme piece");
        Gt.check(a.take(null, 0.2) && a.hung().size() == 1, "main vide : la piece la plus proche revient");
        h.setBlock(hb, Blocks.AIR.defaultBlockState());
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(hb)).inflate(2));
        Gt.check(drops.stream().anyMatch(e -> e.getItem().is(PiKinetics.FIBER_ROPE.get())), "crochet casse : la corde est rendue");
        Gt.check(drops.stream().anyMatch(e -> e.getItem().is(Items.COD)), "crochet casse : les pieces tombent");
        Gt.check(!a.isMaster() && a.hung().isEmpty(), "l'autre crochet est libere");
        s.done();
    }
}
