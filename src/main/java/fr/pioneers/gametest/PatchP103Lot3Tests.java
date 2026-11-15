package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.OreYields;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.tech.machine.ProcessingRecipe;
import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiMachineKit;
import fr.pioneers.logistics.transport.CableTier;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.3-beta, lot 3 (GameTests) : la chaine de fusion des metaux de tier 1. Le tableau des rendements de Guillaume calcule sur les
 * recettes chargees ({@link OreYields}) pour les six metaux ; le multiplicateur du creuset electrique applique en vrai par une machine MKI et
 * une MK4 ; le four electrique compact qui cuit le minerai brut ; la lixiviation acide (refusee au MKI, 250 mB par concasse au MK2) ; le
 * recyclage a 50 % (table de fusion et creuset electrique), les broyeurs a 2 concasses, le compacteur des fragments et des blocs de minerai,
 * plus d'alliage de poudres.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot3Tests {

    /** Le tableau de Guillaume (lingots par minerai brut ; NaN : pas de voie a ce tier). */
    static final double N = Double.NaN;
    /**
     * 1.0.5-beta (lot 38, familles de tiers, decision D1) : le broyeur MKI fait la recette du concasseur a machoires (famille « broyage ») au
     * rendement du tier plafonne a T1 (regle 1) : 3 concasses par minerai brut a tous les tiers MK (avant : 2) ; les voies du broyeur x 1,5.
     */
    static final double[][] EXPECTED = {{1, 1, 1, 1}, {1.5, 2, 2.5, 3}, {3, 3, 3, 3}, {4.5, 6, 7.5, 9}, {N, 7.5, 9, 10.5}};

    private PatchP103Lot3Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    private static Fluid fluid(String id) {
        Fluid f = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(id));
        Gt.check(f != null, "fluide inconnu " + id);
        return f;
    }

    private static AtelierTile place(GameTestHelper h, BlockPos rel, Block block) {
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier en " + rel);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
    }

    private static AtelierTile atelier(GameTestHelper h, BlockPos rel, IndustryMachine machine, CableTier tier) {
        return place(h, rel, PiIndustry.ATELIERS.get(machine).get(tier).getBlock());
    }

    private static int count(AtelierTile m, Item it) {
        int n = 0;
        for (var s : m.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ================================================================== le tableau des rendements
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_3_yields", timeoutTicks = 20)
    public static void p103_3_tableau_des_rendements(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String m : OreYields.METALS) {
            double[][] t = OreYields.table(h.getLevel(), m);
            for (int r = 0; r < OreYields.ROWS; r++) {
                for (int tier = 0; tier < OreYields.TIERS; tier++) {
                    double want = EXPECTED[r][tier], got = t[r][tier];
                    boolean ok = Double.isNaN(want) ? Double.isNaN(got) : Math.abs(want - got) < 1e-6;
                    Gt.check(ok, String.format(Locale.ROOT, "%s, voie %d, MK%d : %s lingots (attendu %s)", m, r, tier + 1, got, want));
                }
            }
        }
        s.done();
    }

    // ================================================================== creuset electrique : x1,5 au MKI, x3 au MK4 (en vrai)
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_3_crucible", timeoutTicks = 1200)
    public static void p103_3_creuset_multiplie_le_minerai(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile mk1 = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.ELECTRIC_CRUCIBLE, CableTier.BASIC);
        AtelierTile mk4 = atelier(h, new BlockPos(5, 2, 2), IndustryMachine.ELECTRIC_CRUCIBLE, CableTier.ULTIMATE);
        Gt.check(mk1.setLockedRecipe(rl("industry/electric_crucible/melt_raw_copper")), "fusion du minerai brut absente du creuset MKI");
        Gt.check(mk4.setLockedRecipe(rl("industry/electric_crucible/melt_crushed_copper")), "fusion du concasse absente du creuset MK4");
        mk1.itemInputs().get(0).setStack(new ItemStack(item("raw_copper"), 1));
        mk4.itemInputs().get(0).setStack(new ItemStack(item("crushed_copper"), 1));
        h.startSequence()
              .thenWaitUntil(() -> {
                  mk1.getEnergy().setStored(mk1.getEnergy().getCapacity());
                  mk4.getEnergy().setStored(mk4.getEnergy().getCapacity());
                  Gt.check(mk1.fluidOutputs().get(0).getFluidAmount() > 0 && mk4.fluidOutputs().get(0).getFluidAmount() > 0, "fusion en cours");
              })
              .thenExecute(() -> {
                  Gt.equal(216, mk1.fluidOutputs().get(0).getFluidAmount(), "MKI : 1 minerai brut = 144 x 1,5 mB");
                  Gt.equal(432, mk4.fluidOutputs().get(0).getFluidAmount(), "MK4 : 1 concasse = 144 x 3 mB");
              })
              .thenExecute(s::done);
    }

    // ================================================================== four electrique compact : 1 minerai brut = 1 lingot
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_3_furnace", timeoutTicks = 1200)
    public static void p103_3_four_electrique_cuit_le_minerai(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile furnace = place(h, new BlockPos(2, 2, 2), PiMachineKit.MACHINES.get(MachineKind.FURNACE).get(CableTier.BASIC).getBlock());
        furnace.itemInputs().get(0).setStack(new ItemStack(item("raw_tin"), 2));
        h.startSequence()
              .thenWaitUntil(() -> {
                  furnace.getEnergy().setStored(furnace.getEnergy().getCapacity());
                  Gt.check(count(furnace, item("tin_ingot")) >= 2, "four MKI : " + count(furnace, item("tin_ingot")) + " / 2 lingots d'etain");
              })
              .thenExecute(() -> Gt.equal(2, count(furnace, item("tin_ingot")), "un lingot par minerai brut, pas plus"))
              .thenExecute(s::done);
    }

    // ================================================================== lixiviation acide : refusee au MKI, 250 mB par concasse au MK2
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_3_leach", timeoutTicks = 1200)
    public static void p103_3_lixiviation_mk2(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        Recipe<?> leach = server.getRecipeManager().byKey(rl("processes/iron/acid_leach_2")).orElse(null);
        Gt.check(leach instanceof fr.pioneers.tech.industry.recipe.IndustrialRecipe r && r.tier() == 1, "lixiviation du fer : recette MK2 absente");
        //1.0.4-beta (lot 13) : la cuve de lixiviation est fondue dans l'extracteur (famille leaching_tank, meme tier)
        AtelierTile tank = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.EXTRACTOR, CableTier.ADVANCED);
        Gt.check(tank.setLockedRecipe(rl("processes/iron/acid_leach_2")), "lixiviation absente de l'extracteur MK2");
        tank.itemInputs().get(0).setStack(new ItemStack(item("crushed_iron"), 1));
        tank.fluidInputs().get(0).setFluid(new FluidStack(fluid("pioneers:sulfuric_acid"), 1000));
        h.startSequence()
              .thenWaitUntil(() -> {
                  tank.getEnergy().setStored(tank.getEnergy().getCapacity());
                  Gt.check(tank.fluidOutputs().get(0).getFluidAmount() > 0, "lixiviation en cours");
              })
              .thenExecute(() -> {
                  FluidStack out = tank.fluidOutputs().get(0).getFluid();
                  Gt.check(out.getFluid().isSame(fluid("pioneers:iron_acid_solution")), "solution acide de fer attendue");
                  Gt.equal(250, out.getAmount(), "MK2 : 1 concasse = 100 x 2,5 mB de solution (2,5 poudres)");
                  Gt.equal(750, tank.fluidInputs().get(0).getFluidAmount(), "250 mB d'acide par concasse");
              })
              .thenExecute(s::done);
    }

    // ================================================================== recyclage, broyeurs, compacteur, alliages
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_3_rules", timeoutTicks = 20)
    public static void p103_3_regles_des_formes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        //recyclage : une plaque de fer coulee en 144 mB rend 72 mB (table de fusion de l'age du bronze et creuset electrique)
        int plate = -1, ingot = -1;
        for (GeneratedMaterials.Melt m : GeneratedMaterials.MELTS) {
            if (m.item().equals("pioneers:iron_plate")) {
                plate = m.mb();
            } else if (m.item().equals("pioneers:iron_ingot")) {
                ingot = m.mb();
            }
        }
        Gt.equal(72, plate, "plaque de fer fondue : 50 %");
        Gt.equal(144, ingot, "lingot de fer fondu : 100 %");
        Recipe<?> rec = server.getRecipeManager().byKey(rl("industry/electric_crucible/recycle_iron_plate")).orElse(null);
        Gt.check(rec instanceof fr.pioneers.tech.industry.recipe.IndustrialRecipe r && r.fluidResults().get(0).getAmount() == 72,
              "creuset electrique : plaque de fer -> 72 mB");
        //les trois broyeurs : 1 minerai brut -> 2 concasses, sans tirage en plus
        Recipe<?> jaw = server.getRecipeManager().byKey(rl("materials/jaw_crushing_crushed_iron")).orElse(null);
        Gt.check(jaw instanceof MechanicalRecipe m && m.results().size() == 1 && m.results().get(0).stack().getCount() == 2
                 && m.results().get(0).chance() >= 1, "concasseur a machoires : 2 concasses");
        Recipe<?> kit = server.getRecipeManager().byKey(rl("crushing/raw_iron")).orElse(null);
        Gt.check(kit instanceof ProcessingRecipe p && p.getResult().getCount() == 2, "recette du kit : 2 concasses (gardee en JSON)");
        //1.0.5-beta (lot 38) : la recette du kit est DOMINEE par celle du concasseur (famille broyage, 3 au MKI) : le broyeur MKI ne la fait plus
        Gt.check(fr.pioneers.tech.industry.recipe.IndustryRecipes.dominated(h.getLevel(), fr.pioneers.tech.industry.IndustryMachine.CRUSHER).contains(rl("crushing/raw_iron")),
              "broyeur MKN : crushing/raw_iron dominee");
        fr.pioneers.tech.industry.recipe.IndustrialRecipe crush = OreYields.crushOf(h.getLevel(), "iron", new ItemStack(item("crushed_iron")));
        Gt.check(crush != null && crush.converted() && crush.results().get(0).stack().getCount() * crush.tierYield(0) == 3
                 && crush.results().get(0).stack().getCount() * crush.tierYield(3) == 3, "broyeur MKN : 3 concasses (recette du concasseur, plafond T1)");
        //lot 38 : la recette a vapeur « minerai brut -> 2 concasses » est retiree (doublon sans gain) : le broyeur a vapeur fait celle du
        //concasseur a x 1,5 -> 3
        Gt.check(server.getRecipeManager().byKey(rl("bronze/steam/macerator/crushed_iron")).isEmpty(), "broyeur a vapeur : doublon retire");
        fr.pioneers.api.machine.tier.TierRecipes.TierRecipe t1 = fr.pioneers.api.machine.tier.TierRecipes.byId(h.getLevel(), "broyage",
              rl("materials/jaw_crushing_crushed_iron"));
        Gt.check(t1 != null && t1.minTier() == 0 && Math.abs(t1.expectedMain() * t1.yieldFactor(1) - 3) < 1e-9, "broyeur a vapeur : 3 concasses");
        //concasse et poudre a l'etabli de composants de base
        Recipe<?> bench = server.getRecipeManager().byKey(rl("materials/bench/crushed_iron")).orElse(null);
        Gt.check(bench instanceof BenchRecipe b && b.bench().key().equals("components"), "concasse au marteau : etabli de composants de base");
        //compacteur : 4 fragments = 1 minerai brut, 9 minerais = 1 bloc
        Recipe<?> frag = server.getRecipeManager().byKey(rl("compressing/fragments_magnetite")).orElse(null);
        Gt.check(frag instanceof ProcessingRecipe p && p.getCount() == 4 && p.getResult().is(item("raw_iron")), "compacteur : 4 fragments -> 1 minerai");
        Recipe<?> block = server.getRecipeManager().byKey(rl("compressing/raw_block_iron")).orElse(null);
        Gt.check(block instanceof ProcessingRecipe p && p.getCount() == 9 && p.getResult().is(item("raw_iron_block")), "compacteur : 9 minerais -> 1 bloc");
        //alliages : jamais par melange de poudres
        Gt.check(server.getRecipeManager().byKey(rl("bronze/steam/alloy_smelter/bronze_dusts")).isEmpty(), "fondeur d'alliages : plus de bronze de poudres");
        Gt.check(server.getRecipeManager().byKey(rl("bronze/steam/alloy_smelter/brass_dusts")).isEmpty(), "fondeur d'alliages : plus de laiton de poudres");
        s.done();
    }
}
