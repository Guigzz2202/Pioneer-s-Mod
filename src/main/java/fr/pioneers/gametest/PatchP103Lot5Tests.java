package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.machine.PiMachineKit;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.3-beta, lot 5 (GameTests) : machines sur la sellette et generateurs separes.
 * <ul>
 *     <li>generateurs separes : le generateur a combustion solide ne brule que les solides, le liquide les liquides (et pas le charbon), la
 *     turbine a gaz compacte les gaz ; FE par mB du compact identiques a l'ancien generateur a combustion (gaz naturel 3 277, gazole 16 384) ;</li>
 *     <li>ancien generateur a combustion (version des cases 1 : un reservoir de fluide et un de gaz) relu dans la disposition solide ;</li>
 *     <li>remaps des blocs retires et fabrique de la tile d'atelier sur un controleur ;</li>
 *     <li>broyeur compact : la recette de l'ancienne dechiqueteuse (1 buche -> 8 copeaux), sous-produit dans la 2e sortie.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot5Tests {

    private PatchP103Lot5Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    private static AtelierTile atelier(GameTestHelper h, BlockPos rel, IndustryMachine machine, CableTier tier) {
        BlockState st = PiIndustry.ATELIERS.get(machine).get(tier).getBlock().defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier en " + rel);
        return (AtelierTile) h.getBlockEntity(rel);
    }

    @org.jetbrains.annotations.Nullable
    private static IndustrialRecipe gasRecipe(GameTestHelper h, IndustryMachine m, Gas gas) {
        for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), m)) {
            if (r.energy() > 0 && r.gases().size() == 1 && r.gases().get(0).test(fr.pioneers.api.gas.GasStack.of(gas, 1))) {
                return r;
            }
        }
        return null;
    }

    // ================================================================== trois generateurs, trois sortes de combustible
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_5_generators", timeoutTicks = 20)
    public static void p103_5_generateurs_separes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(1, IndustryMachine.SOLID_COMBUSTION_GENERATOR.itemsIn, "solide : une case de combustible");
        Gt.equal(0, IndustryMachine.SOLID_COMBUSTION_GENERATOR.fluidsIn + IndustryMachine.SOLID_COMBUSTION_GENERATOR.gasesIn, "solide : ni fluide ni gaz");
        Gt.equal(1, IndustryMachine.LIQUID_COMBUSTION_GENERATOR.fluidsIn, "liquide : un reservoir de combustible");
        Gt.equal(0, IndustryMachine.LIQUID_COMBUSTION_GENERATOR.itemsIn + IndustryMachine.LIQUID_COMBUSTION_GENERATOR.gasesIn, "liquide : ni objet ni gaz");
        Gt.equal(0, IndustryMachine.GAS_TURBINE.fluidsIn + IndustryMachine.GAS_TURBINE.itemsIn, "turbine a gaz : gaz seuls");
        Gt.equal("gas_turbine", IndustryMachine.GAS_TURBINE.multiId(), "le controleur de la turbine a gaz garde son id");
        Gt.check(PiIndustry.ATELIERS.containsKey(IndustryMachine.GAS_TURBINE), "turbines a gaz compactes MKI a MKIV");
        Gt.equal("industrial_pumpjack", IndustryMachine.PUMPJACK.multiId(), "le chevalet de pompage (multibloc seul) garde son id");
        Gt.check(!PiIndustry.ATELIERS.containsKey(IndustryMachine.PUMPJACK), "plus de chevalet de pompage compact");
        //le charbon ne va qu'au generateur solide
        AtelierTile solid = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.SOLID_COMBUSTION_GENERATOR, CableTier.BASIC);
        AtelierTile liquid = atelier(h, new BlockPos(5, 2, 2), IndustryMachine.LIQUID_COMBUSTION_GENERATOR, CableTier.BASIC);
        ItemStack coal = new ItemStack(Items.COAL);
        Gt.check(IndustryRecipes.accepts(h.getLevel(), IndustryMachine.SOLID_COMBUSTION_GENERATOR, coal), "le generateur solide prend le charbon");
        Gt.check(!IndustryRecipes.accepts(h.getLevel(), IndustryMachine.LIQUID_COMBUSTION_GENERATOR, coal), "le generateur liquide refuse le charbon");
        Gt.check(solid.fluidInputs().isEmpty() && solid.gasInputs().isEmpty(), "generateur solide sans reservoir");
        Gt.equal(1, liquid.fluidInputs().size(), "generateur liquide : un reservoir");
        Gt.equal(2048, GeneratorOutput.solidPerTick(0), "solide MKI : 2 048 FE/t");
        //FE par mB : gazole au liquide (x2), gaz naturel a la turbine compacte (x1) : les chiffres de l'ancien generateur a combustion compact
        Fluid diesel = PiIndustry.fluid("diesel");
        GeneratorOutput.Use d = GeneratorOutput.fluid(h.getLevel(), new FluidStack(diesel, 100));
        Gt.check(d != null && d.machine() == IndustryMachine.LIQUID_COMBUSTION_GENERATOR, "le gazole brule au generateur a combustion liquide");
        Gt.equal(16_384L, Math.round(GeneratorOutput.perUnit(d.recipe(), d.amount(), false)), "gazole : 16 384 FE par mB");
        Gas gas = PioneersAPI.gas(rl("natural_gas"));
        Gt.check(gas != null, "gaz naturel");
        IndustrialRecipe turbine = gasRecipe(h, IndustryMachine.GAS_TURBINE, gas);
        Gt.check(turbine != null, "la turbine a gaz brule le gaz naturel");
        Gt.equal(3_277L, Math.round(GeneratorOutput.perUnit(turbine, turbine.gases().get(0).amount(), false)), "gaz naturel compact : 3 277 FE par mB");
        Gt.check(gasRecipe(h, IndustryMachine.SOLID_COMBUSTION_GENERATOR, gas) == null, "plus de gaz au generateur a combustion");
        Gas woodGas = PioneersAPI.gas(rl("wood_gas"));
        Gt.check(woodGas != null && gasRecipe(h, IndustryMachine.GAS_TURBINE, woodGas) != null, "le gaz de bois passe a la turbine a gaz");
        s.done();
    }

    // ================================================================== ancien generateur a combustion (version des cases 1) relu
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_5_migration", timeoutTicks = 20)
    public static void p103_5_ancien_generateur_relu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(2, IndustryMachine.SOLID_COMBUSTION_GENERATOR.legacyIoVersion(), "generateur a combustion : ancienne io jusqu'a la version 2");
        Gt.equal(2, IndustryMachine.GAS_TURBINE.legacyIoVersion(), "turbine a gaz : ancienne io jusqu'a la version 2");
        AtelierTile a = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.SOLID_COMBUSTION_GENERATOR, CableTier.BASIC);
        CompoundTag tag = a.saveWithoutMetadata();
        //NBT d'avant : version 1, case 0 = charbon, case 1 = batterie, un reservoir de fluide (gazole) et un de gaz
        tag.putInt(IoMigration.NBT, 1);
        ListTag items = new ListTag();
        CompoundTag c = new ItemStack(Items.COAL, 5).save(new CompoundTag());
        c.putShort("Slot", (short) 0);
        items.add(c);
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(new FluidStack(PiIndustry.fluid("diesel"), 500).writeToNBT(new CompoundTag()));
        tag.put("fluids", fluids);
        Gt.check(IoMigration.needed(IndustryMachine.SOLID_COMBUSTION_GENERATOR, tag), "migration demandee pour la version 1");
        AtelierTile b = atelier(h, new BlockPos(5, 2, 2), IndustryMachine.SOLID_COMBUSTION_GENERATOR, CableTier.BASIC);
        b.load(tag.copy());
        Gt.check(b.itemInputs().get(0).getStack().is(Items.COAL) && b.itemInputs().get(0).getCount() == 5, "le charbon reste dans sa case");
        Gt.equal(2, b.saveWithoutMetadata().getInt(IoMigration.NBT), "la machine resauvee porte la version 2");
        s.done();
    }

    // ================================================================== remaps et tile d'atelier sur un controleur
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_5_remaps", timeoutTicks = 20)
    public static void p103_5_remaps(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal("industrial_pumpjack", PiRemaps.p103Block("basic_pumpjack"), "chevalet compact -> controleur");
        Gt.equal("industrial_pyrolysis_oven", PiRemaps.p103Block("ultimate_pyrolysis_oven"), "four a pyrolyse compact -> controleur");
        Gt.equal("elite_crusher", PiRemaps.p103Block("elite_wood_chipper"), "dechiqueteuse -> broyeur du meme tier");
        Gt.equal("industrial_compressor", PiRemaps.p103Block("industrial_pellet_mill"), "granuleuse industrielle -> compacteur industriel");
        Gt.equal("basic_solid_combustion_generator", PiRemaps.p103Block("compact_steam_turbine"), "turbine compacte -> generateur solide MKI");
        Gt.equal("basic_gas_buffer", PiRemaps.p103Block("compact_exhaust"), "echappement compact -> buffer de gaz MKI");
        Gt.check(PiRemaps.p103Block("basic_crusher") == null, "le broyeur n'est pas remappe");
        List<String> targets = new ArrayList<>();
        for (String id : new String[]{"basic_pumpjack", "advanced_fluid_pump", "elite_gas_wellhead", "ultimate_brine_well", "basic_wood_chipper",
              "basic_pellet_mill", "compact_steam_turbine", "compact_exhaust"}) {
            targets.add(PiRemaps.p103Block(id));
        }
        for (String t : targets) {
            Gt.check(ForgeRegistries.BLOCKS.containsKey(rl(t)), "bloc de remap inconnu : " + t);
        }
        //tile d'atelier relue sur un controleur : aucune (pas d'exception), le controleur cree la sienne
        BlockState ctrl = PiIndustry.CONTROLLERS.get(IndustryMachine.PUMPJACK).getBlock().defaultBlockState();
        Gt.check(AtelierTile.create(h.absolutePos(new BlockPos(1, 2, 1)), ctrl) == null, "pas de tile d'atelier sur un controleur");
        BlockState kit = PiMachineKit.MACHINES.get(MachineKind.CRUSHER).get(CableTier.BASIC).getBlock().defaultBlockState();
        Gt.check(AtelierTile.create(h.absolutePos(new BlockPos(1, 2, 1)), kit) != null, "tile du kit sur le broyeur");
        s.done();
    }

    // ================================================================== broyeur compact : une recette de l'ancienne dechiqueteuse
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_5_chipper", timeoutTicks = 600)
    public static void p103_5_broyeur_copeaux(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, PiMachineKit.MACHINES.get(MachineKind.CRUSHER).get(CableTier.BASIC).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof AtelierTile t && t.isKit(), "broyeur du kit");
        AtelierTile m = (AtelierTile) h.getBlockEntity(at);
        Gt.equal(2, m.itemOutputs().size(), "broyeur : deux cases de sortie");
        Gt.check(m.setLockedRecipe(rl("industry/crusher/chip_logs")), "recette chip_logs du broyeur");
        m.itemInputs().get(0).setStack(new ItemStack(Items.OAK_LOG, 1));
        Item chips = item("wood_chips");
        h.startSequence()
              .thenWaitUntil(() -> {
                  m.getEnergy().setStored(m.getEnergy().getCapacity());
                  Gt.check(m.itemOutputs().get(0).getStack().is(chips), "broyage en cours");
              })
              .thenExecute(() -> {
                  Gt.equal(8, m.itemOutputs().get(0).getCount(), "1 buche -> 8 copeaux");
                  ItemStack extra = m.itemOutputs().get(1).getStack();
                  Gt.check(extra.isEmpty() || extra.is(item("sawdust")), "sous-produit : sciure (chance 50 %)");
                  Gt.check(m.itemInputs().get(0).getStack().isEmpty(), "la buche est consommee");
              })
              .thenExecute(s::done);
    }
}
