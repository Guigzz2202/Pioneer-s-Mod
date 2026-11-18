package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 19, plan « montee en difficulte lineaire » L6, L7, L8 et demande de Guillaume) : GameTests de l'OUVERTURE DES AGES, des
 * RENDEMENTS des voies superieures, de la DUREE DES AGES et de la DISTILLERIE COMPACTE.
 * <ul>
 *     <li>la machine de base MKn contient la MKn-1 ; le caisson industriel MKII a MKIV prend le circuit de son tier ;</li>
 *     <li>graveur MKI a l'engrenage de laiton, assembleur MKI au ressort d'acier, reacteur de Kroll MKII, phenol a l'oxygene ;</li>
 *     <li>voies superieures mieux payees (diamant de synthese, cobalt electrolytique, disques aux gemmes MK2...) ;</li>
 *     <li>distillerie compacte : une recette par fraction de la colonne de distillation, une seule sortie, plus lente ; une distillerie MKI
 *     verrouillee sur le naphta tire du brut le naphta seul.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot19Tests {

    private PatchP104Lot19Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(id.contains(":") ? ResourceLocation.parse(id) : rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static IndustrialRecipe recipe(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof IndustrialRecipe, "recette industrielle absente : " + path);
        return (IndustrialRecipe) r;
    }

    private static BenchRecipe bench(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof BenchRecipe, "recette d'etabli absente : " + path);
        return (BenchRecipe) r;
    }

    private static boolean takes(List<BenchRecipe.Counted> ins, String id, int n) {
        ItemStack s = new ItemStack(item(id));
        for (BenchRecipe.Counted c : ins) {
            if (c.ingredient().test(s) && c.count() >= n) {
                return true;
            }
        }
        return false;
    }

    private static boolean takes(BenchRecipe r, String id, int n) {
        return takes(r.ingredients(), id, n);
    }

    private static int out(IndustrialRecipe r) {
        return r.results().get(0).stack().getCount();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_19_hulls", timeoutTicks = 20)
    public static void p104_19_machines_de_base_en_lignee(GameTestHelper h) {
        Gt.begin(h);
        String[] t = {"basic", "advanced", "elite", "ultimate"};
        for (int i = 1; i < t.length; i++) {
            BenchRecipe r = bench(h, "machine_hulls/machine_hull_" + t[i]);
            Gt.check(takes(r, "machine_hull_" + t[i - 1], 1), "machine de base " + t[i] + " : contient la machine de base " + t[i - 1]);
            BenchRecipe c = bench(h, "industry/crafting/industrial_casing_" + t[i]);
            Gt.check(takes(c, "circuit_mk" + (i + 1), 1), "caisson " + t[i] + " : circuit MK" + (i + 1));
        }
        Gt.check(!takes(bench(h, "machine_hulls/machine_hull_basic"), "machine_hull_basic", 1), "machine de base MKI : pas de lignee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_19_ages", timeoutTicks = 20)
    public static void p104_19_duree_des_ages(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe g = bench(h, "industry/crafting/basic_photolithograph");
        Gt.check(takes(g, "brass_gear", 1) && !takes(g, "invar_gear", 1), "graveur optique MKI : engrenage de laiton");
        BenchRecipe a = bench(h, "industry/crafting/basic_assembler");
        Gt.check(takes(a, "steel_spring", 1) && !takes(a, "nickel_spring", 1), "assembleur MKI : ressort d'acier");
        BenchRecipe k = bench(h, "industry/crafting/kroll_reactor");
        Gt.check(takes(k, "machine_hull_advanced", 1) && takes(k, "advanced_heat_exchanger", 2) && takes(k, "advanced_centrifugal_pump", 1)
              && !takes(k, "machine_hull_basic", 1), "reacteur de Kroll : machine MK2");
        IndustrialRecipe p = recipe(h, "industry/chemical_reactor/phenol_acetone");
        Gt.check(p.gases().size() == 1 && "pioneers:oxygen".equals(String.valueOf(p.gases().get(0).stack().getGas().getRegistryName())), "phenol : cumene oxyde a l'oxygene");
        Gt.check(p.fluids().size() == 1, "phenol : plus d'eau oxygenee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_19_yields", timeoutTicks = 20)
    public static void p104_19_rendements(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal(5, out(recipe(h, "industry/crystallizer/diamond_synth")), "diamant de synthese : 5 pour 8 graphites");
        Gt.equal(2, out(recipe(h, "industry/electrolyzer/electrowin_cobalt")), "cobalt electrolytique : 1 lingot par sulfate");
        Gt.equal(5, out(recipe(h, "industry/chemical_bath/electroform_nickel_foil")), "feuille de nickel electroformee : 5");
        Gt.equal(4, out(recipe(h, "industry/crystal_puller/cz_peridot")), "peridot par tirage : 1 par poudre");
        Gt.equal(2, out(recipe(h, "industry/membrane_cell/lithium_carbonate")), "carbonate de lithium electrolytique : 2");
        Gt.equal(4, out(recipe(h, "industry/sintering_furnace/graphite_from_petcoke")), "graphite au coke de petrole : 4");
        for (String gem : new String[]{"ruby", "sapphire", "topaz", "emerald"}) {
            Gt.equal(3, out(recipe(h, "industry/component_assembler/grain_abrasive_disc_" + gem)), "disque abrasif " + gem + " : 3");
        }
        Gt.equal(1, out(recipe(h, "industry/component_assembler/grain_abrasive_disc_quartz")), "disque abrasif au quartz (MK1) : inchange");
        FluidStack soda = recipe(h, "industry/chemical_reactor/caustic_soda").fluidResults().get(0);
        Gt.check(soda.getAmount() > 500, "soude au reacteur : plus que l'electrolyse");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_19_distillery", timeoutTicks = 20)
    public static void p104_19_distillerie_une_fraction(GameTestHelper h) {
        Gt.begin(h);
        int n = 0;
        for (Recipe<?> r : h.getLevel().getRecipeManager().getRecipes()) {
            if (r instanceof IndustrialRecipe ir && ir.machine() == IndustryMachine.DISTILLERY) {
                n++;
                Gt.check(ir.results().isEmpty() && ir.fluidResults().size() + ir.gasResults().size() == 1, ir.getId() + " : une seule fraction");
                Gt.check(!ir.industrialOnly(), ir.getId() + " : faisable en compacte");
            }
        }
        Gt.check(n >= 100, "distillerie : " + n + " recettes (une par fraction de la colonne)");
        IndustrialRecipe col = recipe(h, "industry/distillation_column/distill_raw_oil");
        IndustrialRecipe d = recipe(h, "distillery/distill_raw_oil_naphtha");
        Gt.check(d.time() > col.time(), "distillerie plus lente que la colonne");
        Gt.equal(col.fluids().get(0).amount(), d.fluids().get(0).amount(), "memes entrees que la colonne");
        Gt.check(d.fluidResults().get(0).getAmount() <= col.fluidResults().get(1).getAmount(), "pas plus de naphta que la colonne");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_19_distillery_run", timeoutTicks = 1200)
    public static void p104_19_distillerie_tire_le_naphta(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "distillery/distill_raw_oil_naphtha");
        Block block = Gt.compact(IndustryMachine.DISTILLERY, CableTier.BASIC);
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier : distillerie");
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(r.getId()), "recette du naphta absente du selecteur de la distillerie");
        List<FluidStack> f = r.fluids().get(0).displayed();
        Gt.check(!f.isEmpty() && !t.fluidInputs().isEmpty(), "distillerie : reservoir d'entree");
        t.fluidInputs().get(0).setFluid(new FluidStack(f.get(0).getFluid(), r.fluids().get(0).amount() * 2));
        String naphtha = r.fluidResults().get(0).getFluid().builtInRegistryHolder().key().location().getPath();
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            int got = 0;
            for (FluidTank tank : t.fluidOutputs()) {
                if (!tank.getFluid().isEmpty() && tank.getFluid().getFluid().builtInRegistryHolder().key().location().getPath().equals(naphtha)) {
                    got += tank.getFluidAmount();
                }
            }
            for (var g : t.gasOutputs()) {
                GasStack s = g.getStack();
                Gt.check(s.isEmpty(), "distillerie verrouillee sur le naphta : aucun gaz ne doit sortir");
            }
            Gt.check(got > 0, "distillerie : pas encore de naphta");
        });
    }
}
