package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 17, plan « montee en difficulte lineaire » L1 a L3) : GameTests du CAOUTCHOUC ET DU SOUFRE, de la LIGNEE DES CIRCUITS et
 * des BOBINES (recettes : T:industry/machines_gen.py, T:chemistry/recipes_chem.py, T:components/refonte/plans.py ; io de l'Assembleur :
 * T:industry/io_legacy.py WIDEN).
 * <ul>
 *     <li>le soufre de cokerie sort du reacteur chimique MKI (gaz de cokerie + poudre de fer) ; le soufre fondu se fige en soufre ;</li>
 *     <li>le caoutchouc naturel se vulcanise a l'autoclave MKI ; la voie synthetique rend 2 ;</li>
 *     <li>chaque circuit contient 2 circuits de la generation d'avant ;</li>
 *     <li>la bobine supraconductrice se fait a l'Assembleur MKIII avec DEUX fluides (epoxy + helium liquide) ; un Assembleur d'un monde
 *     d'avant (un seul reservoir de fluide, version des cases 6) garde son contenu.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot17Tests {

    private PatchP104Lot17Tests() {
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
        Fluid f = ForgeRegistries.FLUIDS.getValue(rl(id));
        Gt.check(f != null, "fluide absent : pioneers:" + id);
        return f;
    }

    private static IndustrialRecipe recipe(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof IndustrialRecipe, "recette industrielle absente : " + path);
        return (IndustrialRecipe) r;
    }

    /** Vrai si une entree d'objet de la recette accepte l'objet avec au moins n exemplaires. */
    private static boolean takes(IndustrialRecipe r, String id, int n) {
        ItemStack s = new ItemStack(item(id));
        for (BenchRecipe.Counted c : r.items()) {
            if (c.ingredient().test(s) && c.count() >= n) {
                return true;
            }
        }
        return false;
    }

    /** Compact de la machine au tier donne, recette verrouillee, entrees de la recette x2, energie pleine. */
    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine, CableTier tier, BlockPos rel, IndustrialRecipe r) {
        Block block = Gt.compact(machine, tier);
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier : " + machine.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(r.getId()), "recette " + r.getId() + " absente du selecteur de " + machine.key);
        for (int i = 0; i < r.items().size(); i++) {
            ItemStack[] stacks = r.items().get(i).ingredient().getItems();
            Gt.check(stacks.length > 0 && i < t.itemInputs().size(), machine.key + " : entree " + i + " de " + r.getId());
            t.itemInputs().get(i).setStack(stacks[0].copyWithCount(Math.min(64, r.items().get(i).count() * 2)));
        }
        for (int i = 0; i < r.fluids().size(); i++) {
            List<FluidStack> f = r.fluids().get(i).displayed();
            Gt.check(!f.isEmpty() && i < t.fluidInputs().size(), machine.key + " : pas de reservoir de fluide " + i + " pour " + r.getId());
            t.fluidInputs().get(i).setFluid(new FluidStack(f.get(0).getFluid(), r.fluids().get(i).amount() * 2));
        }
        for (int i = 0; i < r.gases().size(); i++) {
            GasStack g = r.gases().get(i).stack();
            Gt.check(!g.isEmpty() && i < t.gasInputs().size(), machine.key + " : pas de reservoir de gaz pour " + r.getId());
            t.gasInputs().get(i).setStack(GasStack.of(g.getGas(), g.getAmount() * 2));
        }
        return t;
    }

    private static int count(AtelierTile t, Item it) {
        int n = 0;
        for (ItemSlot s : t.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getStack().getCount();
            }
        }
        return n;
    }

    /** La recette tourne dans le compact au tier donne et sort au moins un objet attendu. */
    private static void runs(GameTestHelper h, IndustryMachine m, CableTier tier, String path, String out) {
        IndustrialRecipe r = recipe(h, path);
        Gt.check(r.machine() == m, path + " : machine " + r.machine().key);
        AtelierTile t = atelier(h, m, tier, new BlockPos(2, 2, 2), r);
        Item o = item(out);
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            Gt.check(count(t, o) > 0, path + " : rien produit encore dans " + m.key + " " + tier);
        });
    }

    // ================================================================== L1 : soufre et caoutchouc

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_sulfur", timeoutTicks = 600)
    public static void p104_17_soufre_de_cokerie(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "industry/catalytic_reactor/coke_oven_gas_desulfurization");
        Gt.equal(0, r.tier(), "soufre de cokerie : reacteur chimique MKI");
        Gt.equal("catalytic_reactor", r.family(), "soufre de cokerie : famille de la catalyse");
        runs(h, IndustryMachine.CHEMICAL_REACTOR, CableTier.BASIC, "industry/catalytic_reactor/coke_oven_gas_desulfurization", "sulfur");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_molten", timeoutTicks = 400)
    public static void p104_17_soufre_fondu_fige(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "industry/plate_heat_exchanger/sulfur_solidify");
        Gt.check(!r.results().isEmpty() && r.results().get(0).stack().is(item("sulfur")) && r.results().get(0).stack().getCount() == 4,
              "soufre fondu : 4 soufres pour 1 000 mB");
        runs(h, IndustryMachine.CRYOGENIC_UNIT, CableTier.BASIC, "industry/plate_heat_exchanger/sulfur_solidify", "sulfur");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_rubber", timeoutTicks = 800)
    public static void p104_17_vulcanisation_naturelle(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe nat = recipe(h, "industry/vulcanizer/vulcanize_natural");
        Gt.check(takes(nat, "natural_rubber", 3) && takes(nat, "sulfur", 1), "vulcanisation naturelle : 3 caoutchoucs naturels + 1 soufre");
        Gt.equal(1, nat.results().get(0).stack().getCount(), "voie naturelle : 1 caoutchouc vulcanise");
        IndustrialRecipe syn = recipe(h, "industry/vulcanizer/vulcanize_synthetic");
        Gt.equal(2, syn.results().get(0).stack().getCount(), "voie synthetique : 2 caoutchoucs vulcanises (meilleur rendement)");
        runs(h, IndustryMachine.AUTOCLAVE, CableTier.BASIC, "industry/vulcanizer/vulcanize_natural", "vulcanized_rubber");
    }

    // ================================================================== L2 : lignee des circuits

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_circuits", timeoutTicks = 20)
    public static void p104_17_lignee_des_circuits(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe mk2 = recipe(h, "industry/pick_and_place/rc_circuit_mk2");
        Gt.check(takes(mk2, "circuit_mk1", 2) && !takes(mk2, "brass_connector", 1), "circuit MKII : 2 circuits a tubes a la place des connecteurs");
        Gt.check(takes(recipe(h, "industry/pick_and_place/rc_circuit_mk3"), "circuit_mk2", 2), "circuit MKIII : 2 circuits a transistors");
        Gt.check(takes(recipe(h, "industry/pick_and_place/rc_circuit_mk4"), "circuit_mk3", 2), "circuit MKIV : 2 circuits a microcontroleur");
        h.succeed();
    }

    // ================================================================== L3 : bobines, transformateurs, Assembleur a 2 fluides

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_coils", timeoutTicks = 20)
    public static void p104_17_lignee_des_bobines(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe ec = recipe(h, "industry/component_assembler/rc_enameled_coil");
        Gt.check(takes(ec, "copper_coil", 1) && takes(ec, "enameled_wire", 4), "bobine emaillee : bobine de cuivre + fil emaille");
        Gt.check(ec.results().get(0).stack().is(item("enameled_coil")), "bobine emaillee : resultat");
        IndustrialRecipe dry = recipe(h, "industry/component_assembler/rc_dry_transformer");
        Gt.check(takes(dry, "oil_transformer", 2) && takes(dry, "enameled_coil", 2), "transformateur sec : transformateurs a bain d'huile + bobines emaillees");
        IndustrialRecipe sct = recipe(h, "industry/component_assembler/rc_superconducting_transformer");
        Gt.check(takes(sct, "dry_transformer", 2) && !takes(sct, "electrical_steel_sheet", 1), "transformateur supraconducteur : transformateurs secs");
        IndustrialRecipe sc = recipe(h, "industry/component_assembler/rc_superconducting_coil_epoxy");
        Gt.equal(2, sc.tier(), "bobine supraconductrice : Assembleur MKIII");
        Gt.check(takes(sc, "nbti_cable", 4) && takes(sc, "enameled_coil", 2), "bobine supraconductrice : cable Nb-Ti + bobines emaillees");
        Gt.equal(2, sc.fluids().size(), "bobine supraconductrice : epoxy + helium liquide");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_sc_coil", timeoutTicks = 800)
    public static void p104_17_bobine_supraconductrice_a_l_assembleur(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal(2, IndustryMachine.ASSEMBLER.fluidsIn, "Assembleur : 2 entrees de fluide");
        runs(h, IndustryMachine.ASSEMBLER, CableTier.ELITE, "industry/component_assembler/rc_superconducting_coil_epoxy", "superconducting_coil");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_17_io", timeoutTicks = 20)
    public static void p104_17_assembleur_d_avant_relu(GameTestHelper h) {
        Gt.begin(h);
        //1.0.6 : version 7 au lot 17, 8 depuis le lot 39 du 1.0.5-beta (creuset) : au moins 7
        Gt.check(IndustryMachine.IO_VERSION >= 7, "version des cases au moins 7 (io elargies du lot 17) : " + IndustryMachine.IO_VERSION);
        int[] old = IndustryMachine.ASSEMBLER.legacyIo(6);
        Gt.check(old != null && old[2] == 1 && old[4] == 1, "Assembleur : io d'avant (1 fluide, 1 gaz) relue jusqu'a la version 7");
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.ASSEMBLER, CableTier.ADVANCED).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'un Assembleur MKII d'un monde du lot 14 a 16 (io 9/2/1/0/1/0, version des cases 6) : epoxy dans l'unique reservoir de fluide
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 6);
        ListTag items = new ListTag();
        CompoundTag s0 = new ItemStack(item("copper_coil"), 5).save(new CompoundTag());
        s0.putShort("Slot", (short) 0);
        items.add(s0);
        tag.put("items", items);
        FluidTank tank = new FluidTank(64000);
        tank.setFluid(new FluidStack(fluid("epoxy_resin"), 500));
        ListTag fl = new ListTag();
        fl.add(tank.writeToNBT(new CompoundTag()));
        tag.put("fluids", fl);
        ListTag gs = new ListTag();
        gs.add(new CompoundTag());
        tag.put("gases", gs);
        Gt.check(IoMigration.needed(IndustryMachine.ASSEMBLER, tag), "NBT de version 6 : migration attendue");
        t.load(tag);
        Gt.equal(5, t.itemInputs().get(0).getCount(), "entree gardee");
        Gt.equal(2, t.fluidInputs().size(), "2 reservoirs de fluide");
        Gt.check(t.fluidInputs().get(0).getFluid().getFluid() == fluid("epoxy_resin") && t.fluidInputs().get(0).getFluidAmount() == 500,
              "epoxy gardee dans le premier reservoir");
        Gt.check(t.fluidInputs().get(1).isEmpty(), "second reservoir vide");
        h.succeed();
    }
}
