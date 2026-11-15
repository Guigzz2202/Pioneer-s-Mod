package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.society.economy.PriceService;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.basket.BasketHeap;
import fr.pioneers.building.home.basket.BasketTile;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.society.village.FoundingRules;
import fr.pioneers.society.village.VillageData;
import fr.pioneers.society.village.VillageFounding;
import fr.pioneers.society.village.VillagerLore;
import fr.pioneers.society.village.Villagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Patch 1.0.3-beta, lot 11b (GameTests) : beton et melangeur a vapeur, panier en osier, village fonde par le joueur.
 * <ul>
 *   <li>beton : poudre gris clair au macon (chaux vive), couleurs depuis la poudre gris clair, melangeur industriel ; melangeur a vapeur :
 *   gravier + sable + chaux + colorant + eau -> 8 betons, sans eau -> 8 poudres, eau au seau / cable seulement en entree ;</li>
 *   <li>panier : une seule sorte, filtre des produits, 4 piles, paliers du tas, capacite d'objets, contenu sauve ;</li>
 *   <li>village : fondation hors village, villageois rattaches, refus dans le village d'un autre, prix du fondateur, reputation x1,5.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot11bTests {

    private PatchP103Lot11bTests() {
    }

    private static boolean loaded(Gt.Scope s, String id) {
        return s.level.getRecipeManager().byKey(new ResourceLocation(id)).isPresent();
    }

    // ================================================================== beton
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11b_concrete", timeoutTicks = 260)
    public static void p103_11b_beton_et_melangeur_a_vapeur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //recettes : poudre gris clair a la chaux vive (avant : colorant gris clair), couleurs depuis la poudre, melangeur industriel
        BenchRecipe lg = null;
        for (BenchRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiBronze.BENCH.get())) {
            if (r.result().is(Items.LIGHT_GRAY_CONCRETE_POWDER)) {
                lg = r;
            }
        }
        Gt.check(lg != null, "poudre de beton gris clair a l'etabli");
        boolean lime = false;
        for (BenchRecipe.Counted c : lg.ingredients()) {
            lime |= c.ingredient().test(new ItemStack(PiBronze.QUICKLIME.get()));
        }
        Gt.check(lime, "poudre gris clair : chaux vive comme liant");
        Gt.check(loaded(s, "pioneers:vanilla/red_concrete_powder"), "poudre rouge (poudre gris clair + colorant) chargee");
        Gt.check(loaded(s, "pioneers:industry/mixer/concrete_red") && loaded(s, "pioneers:industry/mixer/concrete_from_powder_white"),
              "betons du melangeur industriel charges");
        //melangeur a vapeur : avec eau -> beton rouge ; sans eau -> poudre gris clair
        BlockPos wet = new BlockPos(2, 2, 2), dry = new BlockPos(5, 2, 2);
        for (BlockPos p : List.of(wet, dry)) {
            h.setBlock(p, PiBronze.STEAM_MACHINES.get(SteamMachine.MIXER).getBlock().defaultBlockState().setValue(BronzeBlock.FACING, Direction.SOUTH));
        }
        SteamProcessTile a = (SteamProcessTile) h.getBlockEntity(wet), b = (SteamProcessTile) h.getBlockEntity(dry);
        Item quick = PiBronze.QUICKLIME.get();
        for (SteamProcessTile t : List.of(a, b)) {
            Gt.check(t.waterTank() != null && t.inputs().size() == 4, "melangeur : 4 entrees et un reservoir");
            t.inputs().get(0).setStack(new ItemStack(Items.GRAVEL, 4));
            t.inputs().get(1).setStack(new ItemStack(Items.SAND, 4));
            t.inputs().get(2).setStack(new ItemStack(quick, 1));
            t.steam(Direction.DOWN).add(3000, 0);
        }
        a.inputs().get(3).setStack(new ItemStack(Items.RED_DYE, 1));
        IFluidHandler cap = a.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElse(null);
        Gt.check(cap != null, "eau par les faces");
        Gt.equal(1000, cap.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE), "1 seau d'eau entre");
        Gt.equal(0, cap.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE), "pas de lave");
        Gt.check(cap.drain(500, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "l'eau ne ressort pas");
        h.succeedWhen(() -> {
            ItemStack outA = a.process().itemOutputs().get(0).getStack(), outB = b.process().itemOutputs().get(0).getStack();
            Gt.check(outA.is(Items.RED_CONCRETE) && outA.getCount() == 8, "8 betons rouges : " + outA);
            Gt.check(outB.is(Items.LIGHT_GRAY_CONCRETE_POWDER) && outB.getCount() == 8, "sans eau : 8 poudres gris clair : " + outB);
            Gt.equal(0, a.waterTank().getFluidAmount(), "l'eau de la gachee est prise");
        });
    }

    // ================================================================== panier
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_11b_basket", timeoutTicks = 20)
    public static void p103_11b_panier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, PiHome.BASKET.getBlock().defaultBlockState());
        BasketTile t = (BasketTile) h.getBlockEntity(at);
        Gt.check(!BasketTile.accepts(new ItemStack(Items.STICK)), "un baton n'est pas un produit agricole");
        Gt.check(BasketTile.accepts(new ItemStack(Items.CARROT)) && BasketTile.accepts(new ItemStack(Items.EGG))
              && BasketTile.accepts(new ItemStack(Items.WHITE_WOOL)), "carotte, oeuf, laine acceptes");
        Gt.equal(2, t.insert(new ItemStack(Items.CARROT, 2), 2, false), "2 carottes");
        Gt.equal(0, t.insert(new ItemStack(Items.APPLE, 10), 10, false), "une seule sorte : pas de pomme");
        Gt.equal(8, t.capacity(), "8 objets (1.0.3-beta : decoration)");
        Gt.equal(2, t.level(), "2 / 8 : palier 2 sur 6");
        IItemHandler items = t.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        ItemStack rest = items.insertItem(0, new ItemStack(Items.CARROT, 64), false);
        Gt.equal(58, rest.getCount(), "entonnoir : jusqu'a 8, le reste ressort");
        Gt.equal(6, t.level(), "plein : palier 6");
        Gt.equal(8, items.extractItem(0, 64, false).getCount(), "tout le panier sort par la capacite");
        t.insert(new ItemStack(Items.CARROT, 3), 3, false);
        CompoundTag tag = t.saveWithoutMetadata();
        BasketTile copy = new BasketTile(h.absolutePos(at), h.getBlockState(at));
        copy.load(tag);
        Gt.check(copy.kind().is(Items.CARROT) && copy.count() == 3, "contenu sauve (sorte et nombre)");
        int prev = 0;
        for (int l = 1; l <= BasketTile.LEVELS; l++) {
            Gt.check(BasketHeap.shown(l) > prev, "le tas grandit a chaque palier");
            prev = BasketHeap.shown(l);
        }
        Gt.equal(BasketHeap.places().size(), (long) BasketHeap.shown(BasketTile.LEVELS), "plein : toutes les places");
        s.done();
    }

    // ================================================================== village fonde
    @GameTest(template = Gt.LARGE, batch = "pioneers_p103_11b_village", timeoutTicks = 100)
    public static void p103_11b_village_fonde(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos boardRel = new BlockPos(16, 2, 16);
        List<Villager> vs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Villager v = h.spawnWithNoFreeWill(EntityType.VILLAGER, boardRel.offset(-4 + i * 4, 0, 3));
            Villagers.identify(s.level, v);
            vs.add(v);
        }
        ServerPlayer founder = s.player("gtfound", "Fondatrice", new BlockPos(16, 2, 12));
        ServerPlayer other = s.player("gtother", "Voisin", new BlockPos(12, 2, 12));
        BlockPos abs = h.absolutePos(boardRel);
        VillageData data = VillageData.get(s.level);
        //nettoyage : le village fonde ne doit pas rester dans le monde des GameTests (il attirerait les villageois des autres tests)
        s.onCleanup(() -> data.remove(abs.asLong()));
        //villages laisses par d'autres GameTests au meme endroit (les lots reutilisent la zone) : retires, la fondation part de rien
        for (VillageData.Village old : data.within(abs, 128)) {
            data.remove(old.id);
        }
        h.setBlock(boardRel, fr.pioneers.society.village.VillageBlocks.NEEDS_BOARD.getBlock().defaultBlockState());
        FoundingRules.Decision d = VillageFounding.onBoardPlaced(s.level, abs, founder);
        Gt.equal(FoundingRules.Decision.FOUND, d, "loin de tout village : fondation");
        VillageData.Village v = data.owned(abs);
        Gt.check(v != null && founder.getUUID().equals(v.owner) && v.founded, "village fonde, a la fondatrice");
        Gt.equal(FoundingRules.RADIUS, (long) VillageData.radius(v), "rayon de 64 blocs");
        for (Villager x : vs) {
            Gt.equal(v.id, VillagerLore.get(x).village, "villageois rattache au village fonde");
            Gt.check(v.register.containsKey(x.getUUID()), "villageois au registre");
        }
        //un autre joueur ne fonde pas dans ce village
        BlockPos second = boardRel.offset(4, 0, 0);
        h.setBlock(second, fr.pioneers.society.village.VillageBlocks.NEEDS_BOARD.getBlock().defaultBlockState());
        Gt.equal(FoundingRules.Decision.REFUSED, VillageFounding.onBoardPlaced(s.level, h.absolutePos(second), other), "pas de fondation chez un autre");
        Gt.equal(founder.getUUID(), v.owner, "un seul fondateur");
        //la fondatrice agrandit son village
        Gt.equal(FoundingRules.Decision.GROW, VillageFounding.onBoardPlaced(s.level, h.absolutePos(second), founder), "second tableau : agrandit");
        Gt.equal(FoundingRules.RADIUS + FoundingRules.PER_BLOCK, (long) VillageData.radius(v), "rayon +4");
        //reputation : gain x1,5 (arrondi au-dessus) pour la fondatrice seulement
        Villager x = vs.get(0);
        Villagers.change(s.level, x, founder, 4, "gametest");
        Villagers.change(s.level, x, other, 4, "gametest");
        Gt.equal(6, v.affinity(x.getUUID(), founder.getUUID()), "fondatrice : +4 -> +6");
        Gt.equal(4, v.affinity(x.getUUID(), other.getUUID()), "autre joueur : +4");
        Villagers.change(s.level, x, founder, -2, "gametest");
        Gt.equal(4, v.affinity(x.getUUID(), founder.getUUID()), "une perte ne change pas");
        //prix : -15 % a l'achat, +15 % a la vente pour la fondatrice
        Item raw = SaleBinTests.pick("raw", false);
        Gt.check(raw != null, "objet a prix");
        ItemStack one = new ItemStack(raw);
        long buy0 = PriceService.quote(other, new PriceService.Partner(PriceService.Kind.VILLAGER, v.people, 0, 1, 1, 1), one, 1, PriceService.Side.BUY, 0).unit();
        long buy1 = PriceService.quote(founder, new PriceService.Partner(PriceService.Kind.VILLAGER, v.people, 0, 1, 1, 1, true), one, 1,
              PriceService.Side.BUY, 0).unit();
        long sell0 = PriceService.quote(other, new PriceService.Partner(PriceService.Kind.VILLAGER, v.people, 0, 1, 1, 1), one, 1, PriceService.Side.SELL, 0).unit();
        long sell1 = PriceService.quote(founder, new PriceService.Partner(PriceService.Kind.VILLAGER, v.people, 0, 1, 1, 1, true), one, 1,
              PriceService.Side.SELL, 0).unit();
        Gt.check(Math.abs(buy1 - Math.round(buy0 * FoundingRules.BUY_FACTOR)) <= 1, "achat -15 % : " + buy0 + " -> " + buy1);
        Gt.check(Math.abs(sell1 - Math.round(sell0 * FoundingRules.SELL_FACTOR)) <= 1, "vente +15 % : " + sell0 + " -> " + sell1);
        //sauvegarde
        VillageData back = Gt.reload(data);
        VillageData.Village r = back.village(v.id);
        Gt.check(r != null && founder.getUUID().equals(r.owner) && r.founded && r.ownerName.equals(v.ownerName), "fondateur relu");
        s.done();
    }
}
