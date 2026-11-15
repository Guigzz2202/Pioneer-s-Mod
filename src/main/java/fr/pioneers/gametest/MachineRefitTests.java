package fr.pioneers.gametest;

import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.atelier.AtelierTile;
import fr.pioneers.industry.engraving.EngravingObjectiveItem;
import fr.pioneers.machine.MachineKind;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.registries.PiMachineKit;
import fr.pioneers.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 8a, GameTests du brief §6) : une recette de chacune des 5 machines refondues, et l'usure puis le reconditionnement d'un
 * objectif de gravure.
 * <ul>
 *     <li>compacteur : concentre de chromite + argile (case du liant) -> boulettes de chromite ;</li>
 *     <li>extracteur : buche de chene + eau chaude -> tanin (et copeaux) ;</li>
 *     <li>graveur optique : feuilles de cuivre + objectif en verre + photoresine -> grillage de cuivre, l'objectif perd un point par gravure ;</li>
 *     <li>four de traitement thermique : tige d'acier au vanadium trempee a l'huile -> forets, huile rendue ;</li>
 *     <li>cuve de traitement de surface : 8 anneaux d'acier + 2 poudres de zinc -> 8 anneaux galvanises ;</li>
 *     <li>objectif : au dernier point il devient l'objectif use, que la rectifieuse remet a neuf (2 lentilles, poudre de diamant).</li>
 * </ul>
 * Recettes de tools/gc/industry/machines_gen.py. Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineRefitTests {

    private MachineRefitTests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(id.contains(":") ? ResourceLocation.parse(id) : rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static Fluid fluid(String id) {
        Fluid f = PiIndustry.fluid(id);
        Gt.check(f != Fluids.EMPTY, "fluide absent : " + id);
        return f;
    }

    /** Machine d'atelier ultime posee en rel, energie pleine, recette verrouillee. */
    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine, BlockPos rel, String recipe) {
        Block block = Gt.compact(machine, CableTier.ULTIMATE);   // 1.0.4-beta (lot 11) : kit compris (machines absorbees)
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier : " + machine.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(rl(recipe)), "recette " + recipe + " absente de " + machine.key);
        return t;
    }

    private static int count(AtelierTile m, Item it) {
        int n = 0;
        for (ItemSlot s : m.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8a_compacteur", timeoutTicks = 600)
    public static void compacteur_liant(GameTestHelper h) {
        Gt.begin(h);
        Block block = PiMachineKit.MACHINES.get(MachineKind.COMPRESSOR).get(CableTier.ULTIMATE).getBlock();
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, block.defaultBlockState());
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile du compacteur");
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        Item pellets = item("chromite_pellets");
        t.itemInputs().get(0).setStack(new ItemStack(item("chromite_concentrate"), 4));
        t.itemInputs().get(1).setStack(new ItemStack(Items.CLAY_BALL, 2));
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            Gt.check(t.itemOutputs().get(0).getStack().is(pellets) && t.itemOutputs().get(0).getCount() >= 4,
                  "boulettes de chromite : " + t.itemOutputs().get(0).getCount() + " / 4");
            Gt.check(t.itemInputs().get(1).isEmpty(), "le liant (argile) doit etre consomme");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8a_extracteur", timeoutTicks = 600)
    public static void extracteur_tanin(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.EXTRACTOR, new BlockPos(2, 2, 2), "industry/extractor/tannin_oak");
        m.itemInputs().get(0).setStack(new ItemStack(Items.OAK_LOG, 2));
        m.fluidInputs().get(0).setFluid(new FluidStack(Fluids.WATER, 2_000));
        Item tannin = item("tannin");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.check(count(m, tannin) >= 4, "tanin : " + count(m, tannin) + " / 4");
            Gt.check(count(m, item("wood_chips")) >= 4, "copeaux (residu) : " + count(m, item("wood_chips")) + " / 4");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8a_graveur", timeoutTicks = 800)
    public static void graveur_objectif_s_use(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.PHOTOLITHOGRAPH, new BlockPos(2, 2, 2), "industry/photolithograph/pcm_copper_mesh");
        m.itemInputs().get(0).setStack(new ItemStack(item("copper_foil"), 4));
        ItemStack objective = new ItemStack(item("glass_engraving_objective"));
        Gt.tool(m, objective);                                            // 1.0.5-beta (lot 44) : l'objectif dans la case Outil
        m.fluidInputs().get(0).setFluid(new FluidStack(fluid("photoresist"), 1_000));
        Item mesh = item("copper_wire_mesh");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.check(count(m, mesh) >= 2, "grillage photograve : " + count(m, mesh) + " / 2");
            ItemStack obj = Gt.toolSlot(m).getStack();
            Gt.check(obj.getItem() instanceof EngravingObjectiveItem, "l'objectif doit rester dans sa case Outil");
            Gt.equal(2, obj.getDamageValue(), "points d'usure de l'objectif apres deux gravures");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8a_four", timeoutTicks = 600)
    public static void four_trempe_forets(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.FURNACE, new BlockPos(2, 2, 2), "industry/quench_tank/harden_drill_bits");
        m.itemInputs().get(0).setStack(new ItemStack(item("vanadium_steel_rod"), 2));
        m.fluidInputs().get(0).setFluid(new FluidStack(fluid("quench_oil"), 1_000));
        Item bit = item("drill_bit");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.check(count(m, bit) >= 4, "forets trempes : " + count(m, bit) + " / 4");
            int back = 0;
            for (var tank : m.fluidOutputs()) {
                back += tank.getFluidAmount();
            }
            Gt.check(back >= 180, "huile de trempe rendue : " + back + " / 180 mB");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8a_surface", timeoutTicks = 600)
    public static void cuve_galvanise_anneaux(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.CHEMICAL_BATH, new BlockPos(2, 2, 2), "industry/chemical_bath/galv_rings");
        m.itemInputs().get(0).setStack(new ItemStack(item("steel_ring"), 8));
        m.itemInputs().get(1).setStack(new ItemStack(item("zinc_dust"), 2));
        Item ring = item("galvanized_steel_ring");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(8, count(m, ring), "anneaux galvanises");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8a_objectif", timeoutTicks = 1000)
    public static void objectif_use_puis_reconditionne(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile engraver = atelier(h, IndustryMachine.PHOTOLITHOGRAPH, new BlockPos(1, 2, 2), "industry/photolithograph/pcm_copper_mesh");
        engraver.itemInputs().get(0).setStack(new ItemStack(item("copper_foil"), 8));
        ItemStack last = new ItemStack(item("glass_engraving_objective"));
        last.setDamageValue(last.getMaxDamage() - 1);
        Gt.tool(engraver, last);                                          // 1.0.5-beta (lot 44) : case Outil
        engraver.fluidInputs().get(0).setFluid(new FluidStack(fluid("photoresist"), 1_000));
        AtelierTile grinder = atelier(h, IndustryMachine.MACHINING_CENTER, new BlockPos(5, 2, 2), "industry/grinder/recondition_glass_objective");
        grinder.itemInputs().get(1).setStack(new ItemStack(item("glass_lens"), 2));
        grinder.itemInputs().get(2).setStack(new ItemStack(item("diamond_dust"), 1));
        grinder.fluidInputs().get(0).setFluid(new FluidStack(Fluids.WATER, 1_000));
        Item worn = item("worn_glass_engraving_objective"), fresh = item("glass_engraving_objective");
        h.succeedWhen(() -> {
            engraver.getEnergy().setStored(engraver.getEnergy().getCapacity());
            grinder.getEnergy().setStored(grinder.getEnergy().getCapacity());
            //1.0.5-beta (lot 44) : use jusqu'au bout, l'objectif (forme usee : plus un outil) quitte la case Outil pour les sorties
            if (count(engraver, worn) > 0) {
                //l'objectif use passe a la rectifieuse
                for (ItemSlot o : engraver.itemOutputs()) {
                    if (o.getStack().is(worn)) {
                        grinder.itemInputs().get(0).setStack(o.getStack().copy());
                        o.setStack(ItemStack.EMPTY);
                    }
                }
                Gt.check(Gt.toolSlot(engraver).isEmpty(), "la case Outil est liberee de l'objectif use");
            }
            Gt.equal(1, count(engraver, item("copper_wire_mesh")), "une seule gravure avec le dernier point");
            Gt.check(count(grinder, fresh) == 1, "objectif reconditionne : " + count(grinder, fresh) + " / 1");
            for (ItemSlot s : grinder.itemOutputs()) {
                if (s.getStack().is(fresh)) {
                    Gt.equal(0, s.getStack().getDamageValue(), "l'objectif reconditionne est neuf");
                }
            }
        });
    }
}
