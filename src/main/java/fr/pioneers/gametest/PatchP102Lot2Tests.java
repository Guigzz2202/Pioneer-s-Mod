package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.foundry.CastingTableBlock;
import fr.pioneers.tech.bronze.foundry.CastingTableTile;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.CrucibleUnit;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.casting.AnvilMoldBlock;
import fr.pioneers.tech.forge.casting.AnvilMoldTile;
import fr.pioneers.tech.forge.casting.PlacedMoldTile;
import fr.pioneers.tech.forge.smithing.ForgeHearthTile;
import fr.pioneers.tech.forge.smithing.HearthUnit;
import fr.pioneers.tech.forge.smithing.ToolRackTile;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.tech.mechanics.WoodenFluidBufferBlock;
import fr.pioneers.tech.mechanics.WoodenFluidBufferTile;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiForge;
import fr.pioneers.core.registries.PiKinetics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.2-beta, lot 2 (GameTests) : foyer de forge a l'interface (limites de chargement bornees, relues, appliquees aux entonnoirs) ;
 * fourneau a creuset sans impurete (alliage dans ses fourchettes ou rien, verrou d'alliage : metaux etrangers refuses, dosage) et table de
 * coulee raccordee sur n'importe quel cote ; moule d'enclume rempli en plusieurs coulees (le metal partiel reste au-dela du delai des moules
 * poses) jusqu'a l'enclume ; porte-outils (3 outils, les objets empilables refuses) ; buffer de fluide en bois (raccorde a la noria, liquide
 * sauve) ; remaps des objets retires.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP102Lot2Tests {

    private PatchP102Lot2Tests() {
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, BlockPos rel, Block block, Class<T> type) {
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName());
        return type.cast(be);
    }

    // ================================================================== foyer de forge
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_hearth", timeoutTicks = 20)
    public static void p102_foyer_limites(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ForgeHearthTile a = place(h, new BlockPos(2, 2, 2), PiForge.FORGE_HEARTH.getBlock(), ForgeHearthTile.class);
        HearthUnit unit = a.hearth();
        Gt.equal(HearthUnit.MAX_INGOTS, unit.ingotLimit(), "limite de lingots par defaut");
        a.handleSetting(null, HearthUnit.SETTING_FUEL, 8);
        a.handleSetting(null, HearthUnit.SETTING_INGOTS, 999);
        Gt.equal(8, unit.fuelLimit(), "limite de charbon reglee");
        Gt.equal(HearthUnit.MAX_INGOTS, unit.ingotLimit(), "limite de lingots bornee a 16");
        a.handleSetting(null, HearthUnit.SETTING_INGOTS, 3);
        //Entonnoir par les cotes (charbon) et par le haut (lingots) : pas plus que les limites
        IItemHandler side = a.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElse(null);
        IItemHandler top = a.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        Gt.check(side != null && top != null, "entonnoirs du foyer");
        ItemStack restCoal = side.insertItem(0, new ItemStack(Items.CHARCOAL, 32), false);
        Gt.equal(8, a.fuelStack().getCount(), "charbon garde a la limite");
        Gt.equal(24, restCoal.getCount(), "le reste du charbon est refuse");
        ItemStack restIngots = top.insertItem(0, ForgeMetals.ingot("copper", 10), false);
        Gt.equal(3, a.waiting().getCount() + a.hotPiece().getCount(), "lingots gardes a la limite");
        Gt.check(restIngots.getCount() >= 7, "le reste des lingots est refuse");
        //Aller-retour de sauvegarde : les limites restent
        CompoundTag tag = a.saveWithoutMetadata();
        ForgeHearthTile b = place(h, new BlockPos(5, 2, 2), PiForge.FORGE_HEARTH.getBlock(), ForgeHearthTile.class);
        b.load(tag);
        Gt.equal(8, b.hearth().fuelLimit(), "limite de charbon relue");
        Gt.equal(3, b.hearth().ingotLimit(), "limite de lingots relue");
        Gt.equal(8, b.uiValues().size(), "valeurs nommees du foyer");
        s.done();
    }

    // ================================================================== fourneau a creuset sans impurete, table raccordee
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_crucible", timeoutTicks = 20)
    public static void p102_creuset_sans_impurete(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Map<String, Integer> mix = new LinkedHashMap<>();
        mix.put("copper", 432);
        mix.put("tin", 144);
        Gt.equal("bronze", Metals.resolve(mix), "3 cuivre + 1 etain = bronze");
        mix.put("zinc", 16);
        Gt.equal("", Metals.resolve(mix), "un peu de zinc en trop : rien (plus de melange impur)");
        Gt.check(Metals.mixed(mix), "melange sans alliage signale");
        Gt.equal("copper", Metals.resolve(Map.of("copper", 288)), "cuivre seul");
        //Fourneau verrouille sur le bronze : le zinc est refuse, le cuivre et l'etain entrent ; dosage
        BlockPos at = new BlockPos(4, 2, 4);
        h.setBlock(at, PiBronze.CRUCIBLE_FURNACE.getBlock().defaultBlockState().setValue(BronzeBlock.FACING, Direction.SOUTH));
        CrucibleFurnaceTile f = (CrucibleFurnaceTile) h.getBlockEntity(at);
        Gt.check(f != null, "fourneau a creuset");
        Gt.check(!f.recipeChoices().isEmpty(), "choix d'alliages du selecteur");
        Gt.check(f.setLockedRecipe(CrucibleUnit.choiceId("bronze")), "verrou sur le bronze");
        Gt.equal("bronze", f.furnace().lockedMetal(), "alliage choisi");
        ItemStack zinc = ForgeMetals.ingot("zinc", 1);
        Gt.check(!f.guiSlots().get(2).isItemValid(zinc), "zinc refuse quand le bronze est choisi");
        Gt.check(f.guiSlots().get(2).isItemValid(ForgeMetals.ingot("copper", 1)), "cuivre accepte");
        //Table de coulee sur le cote EST du fourneau (pas devant) : elle se raccorde et se tourne vers lui
        BlockPos east = at.east();
        h.setBlock(east, PiBronze.CASTING_TABLE.getBlock().defaultBlockState());
        h.setBlock(east, CastingTableBlock.linked(h.getBlockState(east), h.getLevel(), h.absolutePos(east)));
        Gt.check(h.getBlockState(east).getValue(CastingTableBlock.LINKED), "table raccordee");
        Gt.equal(Direction.EAST, h.getBlockState(east).getValue(BronzeBlock.FACING), "table tournee dos au fourneau");
        Gt.check(f.table() instanceof CastingTableTile, "le fourneau trouve la table sur son cote");
        s.done();
    }

    // ================================================================== moule d'enclume en plusieurs coulees
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_anvil_mold", timeoutTicks = 1300)
    public static void p102_moule_enclume(GameTestHelper h) {
        Gt.begin(h);
        BlockPos support = new BlockPos(4, 1, 4);
        h.setBlock(support, Blocks.STONE);
        BlockPos at = support.above();
        Gt.check(AnvilMoldBlock.place(h.getLevel(), h.absolutePos(support), Direction.SOUTH, new ItemStack(PiBronze.ANVIL_MOLD.get())), "moule pose");
        Gt.check(h.getBlockEntity(at) instanceof AnvilMoldTile, "tuile du moule d'enclume");
        AnvilMoldTile mold = (AnvilMoldTile) h.getBlockEntity(at);
        //Deux coulees de creuset en argile (2 x 288 mB), puis on attend au-dela du delai des moules poses (lot 5 : l'enclume est en fer)
        Gt.equal(288, mold.pour("iron", 288, 1600), "premiere coulee");
        Gt.equal(288, mold.pour("iron", 288, 1600), "deuxieme coulee");
        Gt.equal(0, mold.pour("bronze", 288, 1100), "un autre metal est refuse");
        int[] t = {0};
        h.onEachTick(() -> {
            t[0]++;
            if (t[0] == PlacedMoldTile.PARTIAL_TICKS + 40) {
                Gt.equal(576, mold.amount(), "le metal partiel reste dans le moule d'enclume");
                Gt.check(!mold.done(), "pas encore d'enclume");
                for (int i = 0; i < 3; i++) {
                    Gt.equal(288, mold.pour("iron", 288, 1600), "coulee " + (i + 3));
                }
            }
        });
        h.succeedWhen(() -> Gt.check(t[0] > PlacedMoldTile.PARTIAL_TICKS + 40 && mold.done(), "enclume coulee"));
    }

    // ================================================================== porte-outils
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_tool_rack", timeoutTicks = 20)
    public static void p102_porte_outils(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ToolRackTile rack = place(h, new BlockPos(3, 2, 3), PiForge.TOOL_RACK.getBlock(), ToolRackTile.class);
        IItemHandler items = rack.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        Gt.check(items != null, "cases du porte-outils");
        Gt.check(!items.insertItem(0, new ItemStack(Items.COBBLESTONE, 4), true).isEmpty(), "un objet empilable n'est pas un outil");
        for (int i = 0; i < ToolRackTile.SLOTS; i++) {
            Gt.check(items.insertItem(i, new ItemStack(Items.IRON_PICKAXE), false).isEmpty(), "outil " + i + " range");
        }
        Gt.equal(3, rack.count(), "trois outils ranges");
        CompoundTag tag = rack.saveWithoutMetadata();
        ToolRackTile b = place(h, new BlockPos(6, 2, 3), PiForge.TOOL_RACK.getBlock(), ToolRackTile.class);
        b.load(tag);
        Gt.equal(3, b.count(), "outils relus");
        s.done();
    }

    // ================================================================== buffer de fluide en bois sur la noria
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_wooden_buffer", timeoutTicks = 20)
    public static void p102_buffer_bois(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos noria = new BlockPos(4, 2, 4), above = noria.above();
        h.setBlock(noria, PiKinetics.machine(MachineType.NORIA).getBlock().defaultBlockState());
        h.setBlock(above, PiMechanics.WOODEN_FLUID_BUFFER.getBlock().defaultBlockState()
              .setValue(WoodenFluidBufferBlock.NORIA, WoodenFluidBufferBlock.onNoria(h.getLevel(), h.absolutePos(above))));
        Gt.check(h.getBlockState(above).getValue(WoodenFluidBufferBlock.NORIA), "raccorde a la noria");
        WoodenFluidBufferTile tank = (WoodenFluidBufferTile) h.getBlockEntity(above);
        IFluidHandler fluid = tank.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).orElse(null);
        Gt.check(fluid != null, "liquide par le dessous (la noria)");
        Gt.equal(WoodenFluidBufferTile.CAPACITY, fluid.fill(new FluidStack(Fluids.WATER, 20_000), IFluidHandler.FluidAction.EXECUTE), "8 000 mB");
        Gt.equal(0, fluid.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.SIMULATE), "pas de lave dans le bois");
        CompoundTag tag = tank.saveWithoutMetadata();
        h.setBlock(new BlockPos(1, 2, 1), PiMechanics.WOODEN_FLUID_BUFFER.getBlock().defaultBlockState());
        WoodenFluidBufferTile b = (WoodenFluidBufferTile) h.getBlockEntity(new BlockPos(1, 2, 1));
        b.load(tag);
        Gt.equal(WoodenFluidBufferTile.CAPACITY, b.tank().getFluidAmount(), "liquide relu");
        Gt.check(!h.getBlockState(new BlockPos(1, 2, 1)).getValue(WoodenFluidBufferBlock.NORIA), "seul : pas raccorde");
        s.done();
    }

    // ================================================================== remaps des objets retires
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_remaps", timeoutTicks = 20)
    public static void p102_remaps(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String gone : List.of("spring_barrel", "mechanical_brake", "rotation_regulator", "gold_pan", "coal_forge", "stone_anvil", "trip_hammer",
              "impure_ingot")) {
            Gt.check(!ForgeRegistries.ITEMS.containsKey(Pioneers.rl(gone)), "objet retire encore enregistre : " + gone);
        }
        Map<String, String> expected = Map.of("spring_barrel", "iron_flywheel", "mechanical_brake", "governor", "rotation_regulator", "governor",
              "coal_forge", "forge_hearth", "stone_anvil", "iron_anvil", "trip_hammer", "wood_lathe");
        expected.forEach((from, to) -> {
            Gt.equal(to, PiRemaps.simplifiedBlock(from), "remap du bloc " + from);
            Gt.check(ForgeRegistries.BLOCKS.containsKey(Pioneers.rl(to)), "remplacant absent : " + to);
        });
        Gt.equal("hand_sieve", PiRemaps.simplifiedItem("gold_pan"), "la batee devient le tamis a main");
        Gt.equal("slag", PiRemaps.simplifiedItem("impure_ingot"), "le lingot impur devient une scorie");
        s.done();
    }
}
