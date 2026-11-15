package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.unit.IndustryProcess;
import fr.pioneers.api.machine.unit.ProcessUnit;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.GeneratedLot46;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.RockSamples;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 46 (retours de jeu 6e serie, 4 ; controle tools/check_p105_46.py) :
 * <ul>
 *     <li>62 bis (reponse de Guillaume du 14/11 23:37 : « Non les moules doivent etre incassables dans les machines ») : UN moule en sable dans
 *     la case Outil de la machine de coulee coule k fois de suite : le moule est toujours la, jamais un de moins, plus de sable use ;</li>
 *     <li>62 bis, monde existant : des moules en sable restes dans une case d'entree (sauvegarde d'avant) passent dans la case Outil ;</li>
 *     <li>72 : le bloc a eau MKI remplit son reservoir d'eau sans entree, au debit de sa table (au plus le quart de la pompe multibloc) ;</li>
 *     <li>72 : le generateur de roches MKI, un echantillon de pave et 1 000 mB d'eau et de lave : du pave sort, l'echantillon, l'eau et la lave
 *     sont intacts ; un minerai et une roche profonde sont refuses au MKI, la roche profonde est acceptee au MKIII ; sans lave : rien.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot46Tests {

    private PatchP105Lot46Tests() {
    }

    static ResourceLocation rl(String path) {
        return Pioneers.rl(path);
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    static int count(List<ItemSlot> slots, Item item) {
        int n = 0;
        for (ItemSlot s : slots) {
            if (s.getStack().is(item)) {
                n += s.getStack().getCount();
            }
        }
        return n;
    }

    static BlockState facing(Block block) {
        BlockState st = block.defaultBlockState();
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : st;
    }

    static AtelierTile atelier(GameTestHelper h, IndustryMachine m, CableTier tier, BlockPos at) {
        h.setBlock(at, facing(PiIndustry.ATELIERS.get(m).get(tier).getBlock()));
        Gt.check(h.getBlockEntity(at) instanceof AtelierTile, "pas d'atelier : " + m.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(at);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
    }

    // ------------------------------------------------------------------ 62 bis : moule en sable incassable

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_46_mold", timeoutTicks = 1600)
    public static void p105_46_moule_sable_incassable(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.CASTING_MACHINE, CableTier.BASIC, new BlockPos(2, 2, 2));
        Gt.check(m.setLockedRecipe(rl("industry/casting_machine/sand_cast_cauldron_iron")), "chaudron coule au sable absent");
        Item mold = item("sand_mold"), spent = item("spent_foundry_sand");
        Gt.check(Remanents.isTool(IndustryMachine.CASTING_MACHINE.key, new ItemStack(mold)), "le moule en sable est un outil de la machine de coulee");
        m.fluidInputs().get(0).setFluid(new FluidStack(ForgeRegistries.FLUIDS.getValue(rl("molten_iron")), 3 * 1_260));
        Gt.tool(m, new ItemStack(mold, 1));
        for (ItemSlot in : m.itemInputs()) {
            Gt.check(!in.isItemValid(new ItemStack(mold)), "une case d'entree refuse le moule (sa place : la case Outil)");
        }
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.check(count(m.itemOutputs(), Items.CAULDRON) >= 3, "3 coulees avec le meme moule : " + count(m.itemOutputs(), Items.CAULDRON) + " / 3");
            Gt.check(Gt.toolSlot(m).getStack().is(mold) && Gt.toolSlot(m).getCount() == 1, "le moule est toujours la, jamais un de moins");
            Gt.equal(0, count(m.itemOutputs(), spent), "plus de sable use : le moule ne casse plus");
            Gt.equal(0, count(m.itemOutputs(), mold), "aucun moule aux sorties");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_46_mold_world", timeoutTicks = 40)
    public static void p105_46_moules_d_avant_en_case_outil(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item mold = item("sand_mold");
        AtelierTile fresh = atelier(h, IndustryMachine.CASTING_MACHINE, CableTier.BASIC, new BlockPos(2, 2, 2));
        //Sauvegarde d'avant le lot 46 : 12 moules en sable dans la case d'entree 0 (ingredients consommes a chaque coulee)
        CompoundTag old = fresh.saveWithoutMetadata();
        ListTag items = new ListTag();
        CompoundTag entry = new ItemStack(mold, 12).save(new CompoundTag());
        entry.putShort("Slot", (short) 0);
        items.add(entry);
        old.put("items", items);
        AtelierTile a = atelier(h, IndustryMachine.CASTING_MACHINE, CableTier.BASIC, new BlockPos(5, 2, 2));
        a.load(old);
        Gt.check(a.itemInputs().get(0).isEmpty(), "les moules d'avant quittent la case d'entree");
        Gt.check(Gt.toolSlot(a).getStack().is(mold) && Gt.toolSlot(a).getCount() == 12, "les 12 moules d'avant sont dans la case Outil (rien de perdu)");
        s.done();
    }

    // ------------------------------------------------------------------ 72 : bloc a eau

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_46_water", timeoutTicks = 400)
    public static void p105_46_bloc_a_eau_remplit_son_reservoir(GameTestHelper h) {
        Gt.begin(h);
        for (int t = 0; t < 4; t++) {
            Gt.check(GeneratedLot46.WATER_RATE[t] * 4 <= GeneratedLot46.PUMP_RATE[t], "bloc a eau : au plus le quart de la pompe multibloc au tier " + t);
        }
        AtelierTile w = atelier(h, IndustryMachine.WATER_SOURCE, CableTier.BASIC, new BlockPos(2, 2, 2));
        Gt.equal(0, w.itemInputs().size(), "bloc a eau : aucune entree");
        Gt.equal(1, w.fluidOutputs().size(), "bloc a eau : un reservoir de sortie");
        long start = h.getTick();
        h.succeedWhen(() -> {
            w.getEnergy().setStored(w.getEnergy().getCapacity());
            FluidStack out = w.fluidOutputs().get(0).getFluid();
            Gt.check(!out.isEmpty() && out.getFluid().isSame(Fluids.WATER), "le reservoir se remplit d'eau");
            Gt.check(out.getAmount() >= 500, "eau : " + out.getAmount() + " / 500 mB");
            Gt.check(out.getAmount() <= (h.getTick() - start + 2) * GeneratedLot46.WATER_RATE[0], "jamais plus que le debit de la table");
            Gt.equal(ProcessUnit.WORKING, w.unit().status(), "bloc a eau en marche");
        });
    }

    // ------------------------------------------------------------------ 72 : generateur de roches

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_46_rocks", timeoutTicks = 600)
    public static void p105_46_generateur_de_roches(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile g = atelier(h, IndustryMachine.ROCK_GENERATOR, CableTier.BASIC, new BlockPos(2, 2, 2));
        ItemSlot sample = g.toolSlot();
        Gt.check(sample != null, "case d'echantillon presente");
        Gt.check(!sample.isItemValid(new ItemStack(Items.IRON_ORE)), "un minerai est refuse");
        Gt.equal(RockSamples.ORE, RockSamples.refusal(new ItemStack(Items.IRON_ORE), 0), "raison : minerai");
        Gt.equal(RockSamples.RESOURCE, RockSamples.refusal(new ItemStack(Items.OBSIDIAN), 3), "raison : roche a valeur de ressource");
        Gt.check(!sample.isItemValid(new ItemStack(Items.DEEPSLATE)), "roche profonde refusee au MKI");
        Gt.equal(RockSamples.TIER, RockSamples.refusal(new ItemStack(Items.DEEPSLATE), 0), "raison : tier");
        AtelierTile elite = atelier(h, IndustryMachine.ROCK_GENERATOR, CableTier.ELITE, new BlockPos(6, 2, 2));
        Gt.check(elite.toolSlot().isItemValid(new ItemStack(Items.DEEPSLATE)), "roche profonde acceptee au MKIII");
        //Sans lave : rien ne sort
        AtelierTile dry = atelier(h, IndustryMachine.ROCK_GENERATOR, CableTier.BASIC, new BlockPos(2, 2, 6));
        dry.toolSlot().setStack(new ItemStack(Items.COBBLESTONE));
        dry.fluidInputs().get(0).setFluid(new FluidStack(Fluids.WATER, 1_000));
        sample.setStack(new ItemStack(Items.COBBLESTONE));
        g.fluidInputs().get(0).setFluid(new FluidStack(Fluids.WATER, 1_000));
        g.fluidInputs().get(1).setFluid(new FluidStack(Fluids.LAVA, 1_000));
        h.succeedWhen(() -> {
            g.getEnergy().setStored(g.getEnergy().getCapacity());
            dry.getEnergy().setStored(dry.getEnergy().getCapacity());
            Gt.check(count(g.itemOutputs(), Items.COBBLESTONE) >= 3, "pave produit : " + count(g.itemOutputs(), Items.COBBLESTONE) + " / 3");
            Gt.check(g.toolSlot().getStack().is(Items.COBBLESTONE) && g.toolSlot().getCount() == 1, "l'echantillon reste, jamais consomme");
            Gt.equal(1_000, g.fluidInputs().get(0).getFluidAmount(), "eau jamais prelevee");
            Gt.equal(1_000, g.fluidInputs().get(1).getFluidAmount(), "lave jamais prelevee");
            Gt.equal(0, count(dry.itemOutputs(), Items.COBBLESTONE), "sans lave : rien");
            Gt.equal(IndustryProcess.NEED_FLUIDS, dry.unit().status(), "sans lave : l'ecran le dit");
        });
    }
}
