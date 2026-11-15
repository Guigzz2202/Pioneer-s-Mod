package fr.pioneers.gametest;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.bronze.foundry.CastingTableTile;
import fr.pioneers.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.bronze.foundry.KilnTile;
import fr.pioneers.bronze.machines.SteamMachine;
import fr.pioneers.bronze.machines.SteamProcessTile;
import fr.pioneers.bronze.steam.SteamEngineTile;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.kinetics.kitchen.KitchenMachineTile;
import fr.pioneers.kinetics.kitchen.KitchenMachineType;
import fr.pioneers.kinetics.kitchen.PiKitchenMachines;
import fr.pioneers.kinetics.sources.BeamEngineTile;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * API Machine (lot 8b, GameTests) : dernieres machines de l'age mecanique et du bronze sur leurs unites (postes de cuisine, machine a
 * balancier, machine a vapeur, fourneau a creuset, table de coulee, four de potier, ateliers a vapeur) : aller-retour
 * de sauvegarde au format d'avant (memes cles, cases relues dans le meme ordre), valeurs NOMMEES aux indices d'avant, interface v2 (zone de
 * {@link StandardLayout}), selecteur standard (cuisine, ateliers a vapeur) dont le verrou garde la cle d'avant.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineApi8bTests {

    private MachineApi8bTests() {
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, BlockPos rel, Block block, Class<T> type) {
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName());
        return type.cast(be);
    }

    /** Sauve {@code a}, relit dans {@code b} (meme bloc) : cles attendues presentes, sauvegarde identique apres relecture. */
    private static CompoundTag roundTrip(BlockEntity a, BlockEntity b, String... keys) {
        CompoundTag tag = a.saveWithoutMetadata();
        for (String k : keys) {
            Gt.check(tag.contains(k), "cle « " + k + " » manquante (format d'avant le lot 8b)");
        }
        b.load(tag.copy());
        Gt.equal(tag, b.saveWithoutMetadata(), "sauvegarde identique apres relecture");
        return tag;
    }

    private static void v2(MachineGui gui, String what) {
        Gt.equal(StandardLayout.ZONE, gui.guiHeight(), what + " : zone v2");
        Gt.equal(StandardLayout.INVENTORY, gui.guiInventory(), what + " : inventaire dans le cadre");
    }

    // ================================================================== postes de cuisine
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8b_kitchen", timeoutTicks = 40)
    public static void api8b_cuisine_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block kneader = PiKitchenMachines.MACHINES.get(KitchenMachineType.KNEADER).getBlock();
        KitchenMachineTile a = place(h, new BlockPos(2, 2, 2), kneader, KitchenMachineTile.class);
        a.inputs().get(0).setStack(new ItemStack(Items.WHEAT, 5));
        a.tankIn().fill(new FluidStack(Fluids.WATER, 750), IFluidHandler.FluidAction.EXECUTE);
        KitchenMachineTile b = place(h, new BlockPos(5, 2, 2), kneader, KitchenMachineTile.class);
        roundTrip(a, b, "slots", "progress", "in");
        Gt.equal(5, b.inputs().get(0).getStack().getCount(), "ingredient relu");
        Gt.equal(750, b.tankIn().getFluidAmount(), "liquide d'entree relu");
        Gt.equal(KitchenMachineType.KNEADER.inputs() + KitchenMachineType.KNEADER.outputs(), b.guiSlots().size(), "cases : entrees puis sorties");
        UiValues ui = b.uiValues();
        Gt.equal(10, ui.size(), "valeurs d'interface");
        Gt.equal(0, ui.index(UiValues.SPEED), "vitesse a l'indice 0");
        Gt.equal(3, ui.index(UiValues.STATUS), "etat a l'indice 3");
        Gt.equal(8, ui.index(UiValues.FLUID), "liquide d'entree a l'indice 8");
        Gt.equal(9, ui.index(UiValues.FLUID_OUT), "liquide de sortie a l'indice 9");
        Gt.equal((long) KitchenMachineType.KNEADER.ordinal(), b.guiValue(5), "sorte de poste a l'indice 5");
        v2(b, "petrin");
        //Charge a vide : 5 % du couple de travail (comme avant)
        Gt.check(Math.abs(b.loadTorque() - KitchenMachineType.KNEADER.torque * 0.05) < 1e-6, "charge a vide : " + b.loadTorque());
        //Selecteur standard : la recette fixee = le verrou, sauve sous la cle d'avant « locked »
        Gt.check(!b.recipeSource().isEmpty(), "le petrin n'a pas de selecteur");
        if (!b.recipeChoices().isEmpty()) {
            Gt.check(b.setLockedRecipe(b.recipeChoices().get(0).id()), "verrou refuse");
            CompoundTag tag = b.saveWithoutMetadata();
            Gt.check(tag.contains("locked") && !tag.contains("lockedRecipe"), "verrou sous la cle d'avant « locked »");
        }
        s.done();
    }

    // ================================================================== sources de rotation (1.0.4-beta lot 27 : injecteur et condenseur retires)
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8b_sources", timeoutTicks = 40)
    public static void api8b_sources_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BeamEngineTile a = place(h, new BlockPos(2, 2, 2), PiKinetics.BEAM_ENGINE.getBlock(), BeamEngineTile.class);
        a.guiSlots().get(0).setStack(new ItemStack(Items.CHARCOAL, 6));
        a.engine().water().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        BeamEngineTile b = place(h, new BlockPos(5, 2, 2), PiKinetics.BEAM_ENGINE.getBlock(), BeamEngineTile.class);
        roundTrip(a, b, "slots", "water", "temperature", "burn", "burnTotal");
        Gt.equal(6, b.guiSlots().get(0).getStack().getCount(), "combustible relu (premiere case)");
        Gt.equal(2000, b.engine().water().getFluidAmount(), "eau relue");
        Gt.equal(8, b.uiValues().size(), "valeurs de la machine a balancier");
        Gt.equal(3000L, b.guiValue(2), "chaudiere 300 K x10 a l'indice 2");
        Gt.equal(5, b.uiValues().index(UiValues.WATER_MAX), "contenance de l'eau a l'indice 5");
        v2(b, "machine a balancier");
        SteamEngineTile e = place(h, new BlockPos(2, 2, 5), PiBronze.STEAM_ENGINE.getBlock(), SteamEngineTile.class);
        e.steam(e.facing().getOpposite()).add(400, 0);
        SteamEngineTile e2 = place(h, new BlockPos(5, 2, 5), PiBronze.STEAM_ENGINE.getBlock(), SteamEngineTile.class);
        roundTrip(e, e2, "steam");
        Gt.check(e2.steam(e2.facing().getOpposite()).steam() > 399, "vapeur relue");
        //1.0.4-beta (lot 27) : plus de condenseur (valeur 6 retiree) ; injecteur et condenseur retires de ce test
        Gt.check(e2.uiValues().index(UiValues.STEAM_MAX) == e2.uiValues().size() - 1, "contenance du coffre en derniere valeur");
        Gt.check(e2.uiValues().index(UiValues.WET) == e2.uiValues().size() - 2, "humidite juste avant la contenance (plus de condenseur)");
        v2(e2, "machine a vapeur");
        s.done();
    }

    // ================================================================== fonderie
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8b_foundry", timeoutTicks = 40)
    public static void api8b_fonderie_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        CrucibleFurnaceTile a = place(h, new BlockPos(2, 2, 2), PiBronze.CRUCIBLE_FURNACE.getBlock(), CrucibleFurnaceTile.class);
        a.guiSlots().get(1).setStack(new ItemStack(Items.CHARCOAL, 9));
        a.guiSlots().get(4).setStack(new ItemStack(Items.RAW_COPPER, 2));   // 1.0.5-beta (lot 39) : 3 cases de metal (2..4)
        a.mix().put("copper", 144);
        a.setAutoPour(false);
        CrucibleFurnaceTile b = place(h, new BlockPos(5, 2, 2), PiBronze.CRUCIBLE_FURNACE.getBlock(), CrucibleFurnaceTile.class);
        roundTrip(a, b, "slots", "mix", "t", "auto", "melting");
        Gt.equal(9, b.guiSlots().get(1).getStack().getCount(), "combustible relu (deuxieme case)");
        Gt.equal(2, b.guiSlots().get(4).getStack().getCount(), "derniere case de metal relue (ordre des cases)");
        Gt.equal(144, b.total(), "bain relu");
        Gt.check(!b.autoPour(), "interrupteur « Auto » relu");
        Gt.equal(9, b.uiValues().size(), "valeurs du fourneau (lot 39 : une barre de fusion par case, 3 cases)");
        Gt.equal(8, b.uiValues().index(UiValues.AUTO), "coulee auto a l'indice 8");
        Gt.equal(0L, b.guiValue(8), "coulee auto coupee");
        v2(b, "fourneau a creuset");
        CastingTableTile t = place(h, new BlockPos(2, 2, 5), PiBronze.CASTING_TABLE.getBlock(), CastingTableTile.class);
        t.outputSlot().setStack(new ItemStack(Items.COPPER_INGOT, 3));
        CastingTableTile t2 = place(h, new BlockPos(5, 2, 5), PiBronze.CASTING_TABLE.getBlock(), CastingTableTile.class);
        roundTrip(t, t2, "slots", "metal", "amount", "cool", "coolMax", "pending");
        Gt.equal(3, t2.outputSlot().getStack().getCount(), "piece relue");
        Gt.equal(2, t2.guiSlots().size(), "cases : moule, piece");
        Gt.equal(0, t2.guiValueCount(), "table de coulee : aucune valeur (comme avant)");
        v2(t2, "table de coulee");
        KilnTile k = place(h, new BlockPos(7, 2, 2), PiBronze.POTTERY_KILN.getBlock(), KilnTile.class);
        k.guiSlots().get(0).setStack(new ItemStack(Items.CHARCOAL, 4));
        k.guiSlots().get(5).setStack(new ItemStack(Items.CLAY_BALL, 1));
        KilnTile k2 = place(h, new BlockPos(7, 2, 5), PiBronze.POTTERY_KILN.getBlock(), KilnTile.class);
        roundTrip(k, k2, "slots", "temperature", "progress");
        Gt.equal(4, k2.guiSlots().get(0).getStack().getCount(), "combustible du four relu");
        Gt.check(k2.guiSlots().get(5).getStack().is(Items.CLAY_BALL), "cendres : derniere case (ordre des cases)");
        Gt.equal(6, k2.uiValues().size(), "valeurs du four");
        Gt.equal(2, k2.uiValues().index(UiValues.firing(0)), "cuisson de la premiere place a l'indice 2");
        v2(k2, "four de potier");
        s.done();
    }

    // ================================================================== ateliers a vapeur
    //1.0.5-beta (lot 38, retour 52) : plus d'ateliers a vapeur : le marteau-pilon est supprime (remappe vers la scie a vapeur) et la presse a
    //vapeur est une machine de traitement de la famille « presse » (PatchP105Lot38Tests verifie la relecture des anciens ateliers)
    @GameTest(template = Gt.SMALL, batch = "pioneers_api8b_workshop", timeoutTicks = 40)
    public static void api8b_ateliers_vapeur_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block press = PiBronze.STEAM_MACHINES.get(SteamMachine.PRESS).getBlock();
        SteamProcessTile a = place(h, new BlockPos(2, 2, 2), press, SteamProcessTile.class);
        a.inputs().get(0).setStack(new ItemStack(Items.IRON_INGOT, 12));
        a.steam(Direction.DOWN).add(300, 0);
        SteamProcessTile b = place(h, new BlockPos(5, 2, 2), press, SteamProcessTile.class);
        CompoundTag tag = roundTrip(a, b, "slots", "progress", "steam", "config", SteamProcessTile.FORMAT);
        Gt.equal(12, b.inputs().get(0).getStack().getCount(), "matiere relue");
        //1.0.5-beta (lot 44, retour 62) : + la case Outil (matrice) en derniere case
        Gt.equal(SteamMachine.PRESS.inputs + SteamMachine.PRESS.outputs + 1, b.guiSlots().size(), "cases : entrees, sorties, puis la case Outil");
        Gt.check(b.guiSlots().get(b.guiSlots().size() - 1) == Gt.toolSlot(b), "la case Outil est la derniere case");
        Gt.check(b.recipeSource().startsWith("family:"), "presse a vapeur : selecteur de la famille « presse »");
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(fr.pioneers.Pioneers.rl("steam_hammer")), "marteau-pilon a vapeur retire");
        v2(b, "presse a vapeur");
        s.done();
    }
}
