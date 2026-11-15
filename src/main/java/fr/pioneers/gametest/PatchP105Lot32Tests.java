package fr.pioneers.gametest;

import fr.pioneers.core.vanilla.VanillaRecipeFilter;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.device.ApiaryTile;
import fr.pioneers.farm.device.DeviceBlock;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.farm.kitchen.HoneyExtractorTile;
import fr.pioneers.farm.kitchen.HoneyFill;
import fr.pioneers.farm.kitchen.StationBlock;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.kinetics.kitchen.KitchenMachineTile;
import fr.pioneers.kinetics.kitchen.KitchenMachineType;
import fr.pioneers.kinetics.kitchen.PiKitchenMachines;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.5-beta, lot 32 (retours de jeu 4e serie, 2 : ruches et bateaux ; GameTests) :
 * <ul>
 *     <li>CADRE PLEIN (retour 41) : la ruche habitee remplit un cadre (de jour, sans pluie) ; l'extracteur MANUEL, par son menu (bouton de la
 *     manivelle = vrai {@code clickMenuButton}) : chaque cadre donne 250 mB de miel, 1 cire, le cadre vide, un rayon une fois sur deux (tirage
 *     sur 40 cadres) ; chaque produit dans sa case ; aller-retour de sauvegarde ;</li>
 *     <li>FIOLE -> BOUTEILLE (retour 41) : fioles et seau poses par un entonnoir (capacite d'objets du dessus) dans la case « Remplir » :
 *     bouteilles de miel (250 mB chacune), seau de miel (1 000 mB) ; reprise par le dessous ;</li>
 *     <li>EXTRACTEUR MECANIQUE AUTOMATISE (retour 41) : cadres et fioles par le dessus, arbre a 120 tr/min (vrai {@code onNetworkTick}) :
 *     cire, rayon, cadre vide dans leurs sorties, miel en bouteilles, tout repris par le dessous ; cases de l'interface (2 entrees, 4 sorties) ;</li>
 *     <li>BATEAUX (retour 47) : apres le filtre du serveur, aucune recette de bateau a la grille (5 planches, bateau + coffre) ni ailleurs
 *     qu'aux ateliers ; chaque bateau, bateau a coffre et radeau a sa recette au chantier naval.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot32Tests {

    private PatchP105Lot32Tests() {
    }

    static HoneyExtractorTile extractor(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, PiKitchen.STATIONS.get(StationBlock.Kind.HONEY_EXTRACTOR).get().defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(be instanceof HoneyExtractorTile, "pas de tuile d'extracteur de miel");
        return (HoneyExtractorTile) be;
    }

    static ItemStack frames(int n) {
        return new ItemStack(PiFarmDevices.HONEY_FRAME.get(), n);
    }

    // ------------------------------------------------------------------ 1. ruche -> cadre plein -> cire + rayon + miel

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_32_frame", timeoutTicks = 200)
    public static void p105_32_cadre_plein(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p32c", "Melissa", new BlockPos(4, 1, 4));
        //Ruche habitee, 12 fleurs, cadre presque plein : il se remplit de jour, sans pluie
        s.level.setDayTime(1000);
        s.level.setWeatherParameters(6000, 0, false, false);
        h.setBlock(new BlockPos(1, 1, 1), PiFarmDevices.DEVICES.get(DeviceBlock.Kind.APIARY).get().defaultBlockState());
        //12 fleurs autour (le releve des fleurs, toutes les 10 s, doit les trouver)
        for (int i = 0; i < 12; i++) {
            h.setBlock(new BlockPos(i % 4, 1, 3 + i / 4), i % 2 == 0 ? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
        }
        ApiaryTile hive = (ApiaryTile) h.getBlockEntity(new BlockPos(1, 1, 1));
        CompoundTag t = hive.saveWithoutMetadata();
        t.putBoolean("colony", true);
        t.putInt("flowers", 12);
        t.putFloat("fill0", 99.9F);
        hive.load(t);
        hive.items().set(0, new ItemStack(PiFarmDevices.HIVE_FRAME.get()));
        //Extracteur manuel
        HoneyExtractorTile ex = extractor(h, new BlockPos(5, 1, 5));
        ex.items().set(HoneyExtractorTile.FRAMES, frames(16));
        //1.0.5-beta (lot 41, retour 60) : interface standard de la ferme (MachineMenu) ; bouton de la manivelle = action CUSTOM du menu
        MachineMenu menu = (MachineMenu) ex.standardMenu(0, p.getInventory());
        Gt.check(menu.stillValid(p), "menu de l'extracteur valide a cote du poste");
        Gt.equal((long) HoneyExtractorTile.READY, (long) ex.status(), "etat : pret");
        int clicks = 0;
        while (ex.items().get(HoneyExtractorTile.FRAMES).getCount() == 16 && clicks < 20) {
            ex.handleAction(p, MachineGui.CUSTOM);
            clicks++;
        }
        Gt.check(clicks >= HoneyFill.STROKES / 2 && clicks <= HoneyFill.STROKES, "un cadre en " + clicks + " tours (6, ou 3 en Cuisine rapide)");
        Gt.equal((long) HoneyFill.HONEY_MB, ex.outTank().getFluidAmount(), "250 mB de miel par cadre");
        Gt.equal(1, ex.items().get(HoneyExtractorTile.WAX).getCount(), "1 cire par cadre, dans sa case");
        Gt.check(ex.items().get(HoneyExtractorTile.WAX).is(PiFarmDevices.BEESWAX.get()), "cire dans la case de la cire");
        Gt.check(ex.items().get(HoneyExtractorTile.EMPTY_FRAME).is(PiFarmDevices.HIVE_FRAME.get()), "cadre vide dans sa case");
        ItemStack comb = ex.items().get(HoneyExtractorTile.COMB);
        Gt.check(comb.isEmpty() || comb.is(Items.HONEYCOMB), "rayon de miel dans sa case");
        //40 cadres : rayons une fois sur deux (tirage large : 8 a 32), cire et cadres exacts ; on vide la cuve et les sorties au fur et a mesure
        int combs = comb.getCount(), wax = 1, done = 1, honey = HoneyFill.HONEY_MB;
        ex.items().set(HoneyExtractorTile.COMB, ItemStack.EMPTY);
        ex.items().set(HoneyExtractorTile.WAX, ItemStack.EMPTY);
        ex.items().set(HoneyExtractorTile.EMPTY_FRAME, ItemStack.EMPTY);
        for (int k = 0; k < 4000 && done < 40; k++) {
            if (ex.items().get(HoneyExtractorTile.FRAMES).isEmpty()) {
                ex.items().set(HoneyExtractorTile.FRAMES, frames(16));
            }
            ex.outTank().drain(4000, IFluidHandler.FluidAction.EXECUTE);
            int before = ex.items().get(HoneyExtractorTile.FRAMES).getCount();
            ex.crank(p);
            if (ex.items().get(HoneyExtractorTile.FRAMES).getCount() < before) {
                done++;
                combs += ex.items().get(HoneyExtractorTile.COMB).getCount();
                wax += ex.items().get(HoneyExtractorTile.WAX).getCount();
                honey += ex.outTank().getFluidAmount();
                ex.items().set(HoneyExtractorTile.COMB, ItemStack.EMPTY);
                ex.items().set(HoneyExtractorTile.WAX, ItemStack.EMPTY);
                ex.items().set(HoneyExtractorTile.EMPTY_FRAME, ItemStack.EMPTY);
            }
        }
        Gt.equal(40, done, "40 cadres extraits");
        Gt.equal(40, wax, "une cire par cadre");
        Gt.equal(40L * HoneyFill.HONEY_MB, honey, "250 mB par cadre");
        Gt.check(combs >= 8 && combs <= 32, "un rayon une fois sur deux environ (" + combs + " sur 40)");
        //Aller-retour de sauvegarde
        ex.items().set(HoneyExtractorTile.COMB, new ItemStack(Items.HONEYCOMB, 3));
        ex.items().set(HoneyExtractorTile.CONTAINER, new ItemStack(Items.BUCKET));
        CompoundTag saved = ex.saveWithoutMetadata();
        HoneyExtractorTile back = extractor(h, new BlockPos(7, 1, 5));
        back.load(saved);
        Gt.equal(3, back.items().get(HoneyExtractorTile.COMB).getCount(), "rayons relus");
        Gt.check(back.items().get(HoneyExtractorTile.CONTAINER).is(Items.BUCKET), "contenant relu");
        Gt.equal((long) ex.outTank().getFluidAmount(), back.outTank().getFluidAmount(), "cuve relue");
        //La ruche a rempli son cadre (attente bornee)
        h.runAfterDelay(140, () -> {
            Gt.check(hive.items().get(0).is(PiFarmDevices.HONEY_FRAME.get()), "la ruche habitee a rempli son cadre de jour (fill " + hive.items().get(0) + ")");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 2. fiole -> bouteille de miel, seau -> seau de miel

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_32_bottle", timeoutTicks = 20)
    public static void p105_32_fiole_bouteille(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p32f", "Apis", new BlockPos(4, 1, 4));
        HoneyExtractorTile ex = extractor(h, new BlockPos(5, 1, 5));
        ex.outTank().fill(new FluidStack(PiKitchen.FLUIDS.get("honey").source.get(), 500), IFluidHandler.FluidAction.EXECUTE);
        IItemHandler up = ex.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        Gt.check(up != null, "capacite d'objets de l'extracteur");
        int container = HoneyExtractorTile.CONTAINER;
        Gt.equal(0, up.insertItem(container, new ItemStack(Items.GLASS_BOTTLE, 3), false).getCount(), "3 fioles posees par l'entonnoir");
        Gt.equal(2, ex.items().get(HoneyExtractorTile.FILLED).getCount(), "2 bouteilles de miel (500 mB)");
        Gt.check(ex.items().get(HoneyExtractorTile.FILLED).is(Items.HONEY_BOTTLE), "bouteilles de miel");
        Gt.equal(1, ex.items().get(container).getCount(), "une fiole attend du miel");
        Gt.equal(0, ex.outTank().getFluidAmount(), "cuve videe");
        Gt.equal(2, up.extractItem(HoneyExtractorTile.FILLED, 64, false).getCount(), "bouteilles reprises par l'automatisation");
        Gt.equal(1, up.insertItem(HoneyExtractorTile.WAX, new ItemStack(PiFarmDevices.BEESWAX.get()), true).getCount(), "rien n'entre dans une sortie");
        //Le miel arrive : la fiole en attente se remplit seule
        ex.items().set(container, ItemStack.EMPTY);
        ex.outTank().fill(new FluidStack(PiKitchen.FLUIDS.get("honey").source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        Gt.equal(0, up.insertItem(container, new ItemStack(Items.BUCKET), false).getCount(), "seau pose");
        Gt.check(ex.items().get(HoneyExtractorTile.FILLED).is(PiKitchen.FLUIDS.get("honey").bucket.get()), "seau de miel (1 000 mB)");
        Gt.equal(0, ex.outTank().getFluidAmount(), "cuve videe par le seau");
        Gt.check(!HoneyFill.isContainer(new ItemStack(Items.STICK)), "un baton n'est pas un contenant");
        Gt.check(ItemStack.isSameItem(HoneyFill.filled(new ItemStack(Items.GLASS_BOTTLE), new FluidStack(PiKitchen.FLUIDS.get("honey").source.get(), 250)),
              new ItemStack(Items.HONEY_BOTTLE)), "fiole -> bouteille de miel");
        p.getInventory().clearContent();
        s.done();
    }

    // ------------------------------------------------------------------ 3. extracteur mecanique automatise

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_32_mech", timeoutTicks = 20)
    public static void p105_32_extracteur_mecanique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, PiKitchenMachines.MACHINES.get(KitchenMachineType.HONEY_EXTRACTOR).getBlock().defaultBlockState());
        KitchenMachineTile m = (KitchenMachineTile) h.getBlockEntity(rel);
        Gt.equal(2 + 4, m.guiSlots().size(), "interface : cadres + contenant vide, cire + rayon + cadre vide + contenant plein");
        IItemHandler up = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        IItemHandler down = m.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        Gt.check(up != null && down != null, "capacites haut et bas");
        Gt.equal(2, up.getSlots(), "par le haut : cadres et contenants");
        Gt.equal(4, down.getSlots(), "par le bas : 3 produits et contenants pleins");
        Gt.equal(0, up.insertItem(0, frames(4), false).getCount(), "4 cadres pleins par l'entonnoir");
        Gt.equal(0, up.insertItem(1, new ItemStack(Items.GLASS_BOTTLE, 8), false).getCount(), "8 fioles par l'entonnoir");
        //Arbre a 120 tr/min (regime nominal) : 12 tours par cadre a 0,1 tour / tick
        for (int k = 0; k < 600 && !m.process().all().get(0).isEmpty(); k++) {
            m.onNetworkTick(null, 120, 50, false);
        }
        for (int k = 0; k < 5; k++) {
            m.onNetworkTick(null, 120, 50, false);
        }
        Gt.check(m.process().all().get(0).isEmpty(), "les 4 cadres sont extraits");
        Gt.equal(4, m.outputs().get(0).getCount(), "4 cires dans la sortie de la cire");
        Gt.check(m.outputs().get(0).getStack().is(PiFarmDevices.BEESWAX.get()), "cire dans la 1re sortie");
        Gt.check(m.outputs().get(1).isEmpty() || m.outputs().get(1).getStack().is(Items.HONEYCOMB), "rayons dans la 2e sortie");
        Gt.equal(4, m.outputs().get(2).getCount(), "4 cadres vides dans la 3e sortie");
        Gt.equal(4, m.process().containerOut().getCount(), "4 bouteilles de miel (1 000 mB)");
        Gt.equal(4, m.process().containerIn().getCount(), "4 fioles attendent");
        int out = 0;
        for (int i = 0; i < down.getSlots(); i++) {
            out += down.extractItem(i, 64, false).getCount();
        }
        Gt.check(out >= 12, "tout se reprend par le dessous (" + out + ")");
        Gt.equal(0, up.extractItem(1, 64, false).getCount(), "les fioles vides ne ressortent pas par l'automatisation");
        s.done();
    }

    // ------------------------------------------------------------------ 4. aucun bateau a l'etabli vanilla

    static Optional<CraftingRecipe> grid(GameTestHelper h, ItemStack... cells) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(@NotNull Player player) {
                return true;
            }
        };
        TransientCraftingContainer g = new TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < cells.length && i < 9; i++) {
            g.setItem(i, cells[i]);
        }
        return h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, g, h.getLevel());
    }

    /** La grille donne-t-elle un vehicule (une autre recette du mod qui prendrait ces cases ne compte pas) ? */
    static boolean vehicle(GameTestHelper h, Optional<CraftingRecipe> recipe) {
        return recipe.map(r -> VanillaRecipeFilter.isVehicle(r.getResultItem(h.getLevel().registryAccess()))).orElse(false);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_32_boats", timeoutTicks = 20)
    public static void p105_32_bateaux_au_chantier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        var server = s.level.getServer();
        VanillaRecipeFilter.filter(server);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS), none = ItemStack.EMPTY;
        Gt.check(!vehicle(h, grid(h, plank, none, plank, plank, plank, plank)), "pas de bateau en chene a la grille (5 planches)");
        Gt.check(!vehicle(h, grid(h, new ItemStack(Items.CHEST), new ItemStack(Items.OAK_BOAT))), "pas de bateau a coffre a la grille");
        ItemStack bamboo = new ItemStack(Items.BAMBOO_PLANKS);
        Gt.check(!vehicle(h, grid(h, bamboo, none, bamboo, bamboo, bamboo, bamboo)), "pas de radeau a la grille");
        Set<Item> shipyard = new HashSet<>();
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            Gt.check(!VanillaRecipeFilter.vehicleCraft(r, server), "recette d'un vehicule hors des ateliers : " + r.getId());
            if (r instanceof IndustrialRecipe ir && ir.machine() == IndustryMachine.SHIPYARD) {
                ir.results().forEach(res -> shipyard.add(res.stack().getItem()));
            }
        }
        int boats = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            if (it instanceof BoatItem && "minecraft".equals(BuiltInRegistries.ITEM.getKey(it).getNamespace())) {
                boats++;
                Gt.check(shipyard.contains(it), "bateau sans recette au chantier naval : " + it);
            }
        }
        Gt.equal(18, boats, "8 bois x (bateau, bateau a coffre) + radeau et radeau a coffre de bambou");
        s.done();
    }
}
