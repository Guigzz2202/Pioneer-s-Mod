package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.GeneratedFusions;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IndustryModule;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.PiIndustry;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 12) : GameTests de la FUSION DES MACHINES (2/4 : chimie et petrochimie). Une recette de chaque machine absorbee a compacts
 * tourne dans le compact de sa base au MEME tier (catalyse et son catalyseur rendu, lit fluidise, polymerisation au reacteur chimique ;
 * vulcanisation a l'autoclave ; electrolyse a membrane et electroraffinage a la cellule d'electrolyse ; methanisation au fermenteur) ; les
 * recettes des usines (multiblocs seuls) et de la colonne de stripping sont reservees au multibloc de la base (Craqueur industriel, reacteur
 * chimique industriel, cellule d'electrolyse industrielle, colonne de distillation) ; les anciens ids (et les modules propres retires) se
 * remappent vers la base ; le NBT d'une absorbee relu dans la base et l'io elargie d'une base posee au lot 11 (version des cases 3) gardent
 * leur contenu ; l'autoclave MKI se monte au joint en cuir.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot12Tests {

    private PatchP104Lot12Tests() {
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

    /** Compact ULTIME de la base, recette verrouillee, entrees de la recette x2 (objets, fluides, gaz), energie pleine. */
    private static AtelierTile base(GameTestHelper h, IndustryMachine machine, BlockPos rel, IndustrialRecipe r) {
        Block block = Gt.compact(machine, CableTier.ULTIMATE);
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
            Gt.check(stacks.length > 0, "ingredient vide : " + r.getId());
            if (r.tool() != null && r.tool().input() == i) {
                //1.0.5-beta (lot 44, retour 62) : l'outil (matrice, catalyseur...) dans la case Outil, jamais dans une case d'entree
                Gt.tool(t, stacks[0].copyWithCount(Math.min(64, r.items().get(i).count() * 2)));
                continue;
            }
            Gt.check(i < t.itemInputs().size(), machine.key + " : pas assez de cases d'entree pour " + r.getId());
            //1.0.6 : une recette « impure_only » (electroraffinage) ne prend que des lingots impurs
            ItemStack in = stacks[0].copyWithCount(Math.min(64, r.items().get(i).count() * 2));
            t.itemInputs().get(i).setStack(i == 0 && r.impureOnly() ? fr.pioneers.tech.industry.Impurity.set(in, 1) : in);
        }
        for (int i = 0; i < r.fluids().size(); i++) {
            List<FluidStack> f = r.fluids().get(i).displayed();
            Gt.check(!f.isEmpty() && i < t.fluidInputs().size(), machine.key + " : pas de reservoir de fluide pour " + r.getId());
            t.fluidInputs().get(i).setFluid(new FluidStack(f.get(0).getFluid(), r.fluids().get(i).amount() * 2));
        }
        for (int i = 0; i < r.gases().size(); i++) {
            GasStack g = r.gases().get(i).stack();
            Gt.check(!g.isEmpty() && i < t.gasInputs().size(), machine.key + " : pas de reservoir de gaz pour " + r.getId());
            t.gasInputs().get(i).setStack(GasStack.of(g.getGas(), g.getAmount() * 2));
        }
        return t;
    }

    /**
     * Quelque chose est sorti : un objet des resultats, un fluide ou un gaz produit. 1.0.6 : l'outil rendu (catalyseur, adsorbant) reste dans
     * sa case Outil (lot 44 du 1.0.5-beta) et un resultat a chance peut tarder : on ne regarde plus seulement le premier resultat.
     */
    private static boolean produced(AtelierTile t, IndustrialRecipe r) {
        for (ItemSlot s : t.itemOutputs()) {
            for (var res : r.results()) {
                if (!s.getStack().isEmpty() && s.getStack().is(res.stack().getItem())) {
                    return true;
                }
            }
        }
        for (FluidTank tank : t.fluidOutputs()) {
            if (!tank.isEmpty()) {
                return true;
            }
        }
        for (BasicGasTank tank : t.gasOutputs()) {
            if (!tank.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** La recette d'une machine absorbee tourne dans la base (meme id de recette, machine = base, famille = l'absorbee). */
    private static void runs(GameTestHelper h, String absorbed, String path) {
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, path);
        IndustryMachine base = IndustryMachine.byKey(GeneratedFusions.base(absorbed));
        Gt.check(base != null && r.machine() == base, path + " : machine " + r.machine().key + " au lieu de la base");
        Gt.equal(absorbed, r.family(), path + " : famille");
        Gt.check(IndustryMachine.byKey(absorbed) == null, absorbed + " encore au catalogue");
        Gt.check(!r.industrialOnly(), path + " : recette d'un compact reservee au multibloc");
        AtelierTile t = base(h, base, new BlockPos(2, 2, 2), r);
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            if (!produced(t, r)) {   // message construit seulement en cas d'echec (une machine sans case Outil n'a pas de toolSlot)
                Gt.check(false, absorbed + " -> " + base.key + " : " + path + " n'a encore rien produit (entrees " + t.presentItems() + ", outil "
                      + (t.toolSlot() == null ? "aucune case" : t.toolSlot().getStack()) + ", gaz entree " + t.presentGases() + ", sorties de gaz "
                      + t.gasOutputs().size() + ", sorties " + t.itemOutputs().stream().map(x -> x.getStack().toString()).toList() + ", energie "
                      + t.getEnergy().getStored() + ")");
            }
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_catalysis", timeoutTicks = 400)
    public static void p104_12_catalyse_au_reacteur_chimique(GameTestHelper h) {
        //le catalyseur (case d'entree) revient a 95 % : c'est le premier produit de la recette
        runs(h, "catalytic_reactor", "industry/catalytic_reactor/ammonia_small");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_fluidized", timeoutTicks = 400)
    public static void p104_12_lit_fluidise_au_reacteur_chimique(GameTestHelper h) {
        runs(h, "fluidized_bed_reactor", "industry/fluidized_bed_reactor/uo3_reduction");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_polymer", timeoutTicks = 400)
    public static void p104_12_polymerisation_au_reacteur_chimique(GameTestHelper h) {
        runs(h, "polymerizer", "industry/polymerizer/polyethylene_pellets_polymerizer");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_vulcan", timeoutTicks = 400)
    public static void p104_12_vulcanisation_a_l_autoclave(GameTestHelper h) {
        runs(h, "vulcanizer", "industry/vulcanizer/vulcanize");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_membrane", timeoutTicks = 400)
    public static void p104_12_membrane_a_la_cellule(GameTestHelper h) {
        //meme entree (saumure) que le chlore-alcali propre de la cellule : recette verrouillee (fusion.SELECTOR)
        runs(h, "membrane_cell", "industry/membrane_cell/chlor_alkali");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_refine", timeoutTicks = 400)
    public static void p104_12_electroraffinage_a_la_cellule(GameTestHelper h) {
        runs(h, "electrorefiner", "industry/electrorefiner/refine_copper");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_digester", timeoutTicks = 400)
    public static void p104_12_methanisation_au_fermenteur(GameTestHelper h) {
        runs(h, "anaerobic_digester", "industry/anaerobic_digester/digest_glycerol");
    }

    // ================================================================== usines : recettes reservees au multibloc de la base

    /** Recette d'une usine absorbee (ou de la colonne de stripping) : chez la base, reservee au multibloc s'il y a un compact. */
    private static void multiOnly(GameTestHelper h, String absorbed, String path) {
        IndustrialRecipe r = recipe(h, path);
        IndustryMachine base = IndustryMachine.byKey(GeneratedFusions.base(absorbed));
        Gt.check(base != null && r.machine() == base, path + " : machine " + r.machine().key);
        Gt.equal(absorbed, r.family(), path + " : famille");
        List<RecipeChoice> multi = IndustryRecipes.choices(h.getLevel(), base, true);
        Gt.check(multi.stream().anyMatch(c -> c.id().equals(r.getId())), path + " absente du multibloc de " + base.key);
        Gt.check(PiIndustry.controller(base) != null, "controleur de " + base.key);
        if (base.atelier != IndustryMachine.Atelier.NONE) {
            Gt.check(r.industrialOnly(), path + " : recette d'usine pas reservee au multibloc");
            List<RecipeChoice> compact = IndustryRecipes.choices(h.getLevel(), base, false);
            Gt.check(compact.stream().noneMatch(c -> c.id().equals(r.getId())), path + " au compact de " + base.key);
        }
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_factories", timeoutTicks = 20)
    public static void p104_12_usines_au_multibloc_de_la_base(GameTestHelper h) {
        Gt.begin(h);
        multiOnly(h, "steam_cracker", "industry/steam_cracker/crack_naphtha");
        multiOnly(h, "delayed_coker", "industry/delayed_coker/coke_vacuum_residue");
        multiOnly(h, "fcc_unit", "industry/fcc_unit/fcc_vgo");
        multiOnly(h, "hydrocracker", "industry/hydrocracker/desulfurize");
        multiOnly(h, "catalytic_reformer", "industry/catalytic_reformer/reform_naphtha");
        multiOnly(h, "fischer_tropsch_reactor", "industry/fischer_tropsch_reactor/ft_iron");
        multiOnly(h, "ammonia_plant", "industry/ammonia_plant/haber_bosch");
        multiOnly(h, "nitric_acid_plant", "industry/nitric_acid_plant/ostwald");
        multiOnly(h, "steam_reformer", "industry/steam_reformer/reform_methane");
        multiOnly(h, "gasifier", "industry/gasifier/gasify_coal");
        multiOnly(h, "fluorine_plant", "industry/fluorine_plant/fluorine_electrolysis");
        multiOnly(h, "potline", "industry/potline/soderberg_fluorite_bath");
        multiOnly(h, "distillation_tower", "industry/distillation_tower/ethanol_rectification");
        multiOnly(h, "vacuum_column", "industry/vacuum_column/vacuum_atm_residue");
        multiOnly(h, "stripper", "industry/stripper/girdler_recovery");
        multiOnly(h, "stripper", "industry/stripper/kitchen_distilling_vodka");
        //une usine sous pression le reste chez la base (Haber-Bosch : 5 bar au MKI)
        Gt.check(recipe(h, "industry/ammonia_plant/haber_bosch").pressure() > 0, "Haber-Bosch : pression perdue");
        h.succeed();
    }

    // ================================================================== mondes existants : anciens ids, modules, NBT

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_remaps", timeoutTicks = 20)
    public static void p104_12_anciens_ids_vers_la_base(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal("advanced_autoclave", PiRemaps.p104Block("advanced_vulcanizer"), "vulcanisateur MKII -> autoclave MKII");
        Gt.equal("ultimate_chemical_reactor", PiRemaps.p104Block("ultimate_polymerizer"), "polymeriseur MKIV -> reacteur chimique MKIV");
        Gt.equal("elite_electrolyzer", PiRemaps.p104Block("elite_membrane_cell"), "electrolyseur a membrane MKIII -> cellule MKIII");
        Gt.equal("basic_fermenter", PiRemaps.p104Block("basic_anaerobic_digester"), "digesteur MKI -> fermenteur MKI");
        Gt.equal("industrial_cracker", PiRemaps.p104Block("steam_cracker"), "vapocraqueur -> craqueur industriel");
        Gt.equal("industrial_electrolyzer", PiRemaps.p104Block("potline"), "halle Hall-Heroult -> cellule d'electrolyse industrielle");
        Gt.equal("industrial_chemical_reactor", PiRemaps.p104Block("ammonia_plant"), "usine d'ammoniac -> reacteur chimique industriel");
        Gt.equal("distillation_column", PiRemaps.p104Block("basic_stripper"), "colonne de stripping compacte -> colonne de distillation");
        Gt.equal("distillation_column", PiRemaps.p104Block("industrial_stripper"), "colonne de stripping industrielle -> colonne");
        Gt.equal("distillation_column", PiRemaps.p104Block("vacuum_column"), "colonne sous vide -> colonne de distillation");
        //compacts deja retires en 1.0.3-beta (lot 5) d'une usine : le controleur de la base
        Gt.equal("industrial_cracker", PiRemaps.simplifiedBlock("basic_steam_cracker"), "compact d'usine -> controleur de la base");
        //modules propres aux absorbees : module de cuve
        for (String m : new String[]{"catalyst_bed_module", "fluidized_bed_module", "reforming_module"}) {
            Gt.equal("vessel_module", PiRemaps.p104Block(m), m + " -> module de cuve");
            //1.0.4-beta (lot 23) : le module de cuve est retire a son tour : la chaine finit sur la carcasse MKI
            Gt.equal("industrial_casing_basic", PiRemaps.simplifiedItem(m), m + " (objet) -> module de cuve -> carcasse MKI");
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(m)), m + " encore enregistre");
        }
        Gt.check(!PiIndustry.MODULES.containsKey(IndustryModule.VESSEL), "module de cuve retire (1.0.4-beta lot 23)");
        //anciennes usines deja remappees (1.0) vers une absorbee : la base
        for (String old : new String[]{"basic_vulcanizer", "industrial_polymerizer", "fluorine_plant", "distillation_tower"}) {
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(old)), old + " encore enregistre");
        }
        h.succeed();
    }

    private static CompoundTag slot(int index, ItemStack stack) {
        CompoundTag c = stack.save(new CompoundTag());
        c.putShort("Slot", (short) index);
        return c;
    }

    private static CompoundTag fluid(FluidStack f) {
        return f.writeToNBT(new CompoundTag());
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_nbt_vulcan", timeoutTicks = 20)
    public static void p104_12_nbt_vulcanisateur_dans_l_autoclave(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.AUTOCLAVE, CableTier.ADVANCED).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'un vulcanisateur MKII d'un monde du lot 11 (io 2/1, sans fluide ni gaz, version des cases 3) : 2 entrees, sortie, batterie
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 3);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("rubber_compound"), 5)));
        items.add(slot(1, new ItemStack(item("canvas"), 3)));
        items.add(slot(2, new ItemStack(item("vulcanized_rubber"), 7)));
        items.add(slot(3, new ItemStack(item("lead_acid_battery"), 1)));
        tag.put("items", items);
        tag.put("fluids", new ListTag());
        tag.put("gases", new ListTag());
        tag.putString("recipe", "pioneers:industry/vulcanizer/vulcanize");
        t.load(tag);
        Gt.equal(5, t.itemInputs().get(0).getCount(), "entree 0 gardee");
        Gt.equal(3, t.itemInputs().get(1).getCount(), "entree 1 gardee");
        Gt.equal(7, t.itemOutputs().get(0).getCount(), "sortie gardee");
        int battery = 0;
        for (ItemSlot s : t.guiSlots()) {
            battery += s.getStack().is(item("lead_acid_battery")) ? 1 : 0;
        }
        Gt.equal(1, battery, "batterie gardee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_nbt_refiner", timeoutTicks = 20)
    public static void p104_12_nbt_electroraffinage_dans_la_cellule(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.ELECTROLYZER, CableTier.ELITE).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'une cuve d'electroraffinage MKIII (io 1/2/1 : entree, 2 sorties, batterie ; electrolyte dans le reservoir)
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 3);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("blister_copper"), 4)));
        items.add(slot(1, new ItemStack(item("copper_ingot"), 6)));
        items.add(slot(2, new ItemStack(item("anode_slime"), 2)));
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(fluid(new FluidStack(Fluids.WATER, 700)));
        tag.put("fluids", fluids);
        tag.put("gases", new ListTag());
        tag.putString("recipe", "pioneers:industry/electrorefiner/refine_blister");
        t.load(tag);
        Gt.equal(4, t.itemInputs().get(0).getCount(), "entree gardee");
        Gt.equal(6, t.itemOutputs().get(0).getCount(), "sortie 0 gardee");
        Gt.equal(2, t.itemOutputs().get(1).getCount(), "sortie 1 gardee");
        Gt.equal(700, t.fluidInputs().get(0).getFluidAmount(), "reservoir garde");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_nbt_catalyst", timeoutTicks = 20)
    public static void p104_12_nbt_catalyseur_garde(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.CHEMICAL_REACTOR, CableTier.BASIC).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'un reacteur catalytique MKI (io 1/2/1/1/2/2) : catalyseur en entree, catalyseur use en sortie 1, batterie en case 3
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 3);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("iron_catalyst"), 1)));
        items.add(slot(2, new ItemStack(item("spent_iron_catalyst"), 3)));
        items.add(slot(3, new ItemStack(item("lead_acid_battery"), 1)));
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(new CompoundTag());
        fluids.add(new CompoundTag());
        tag.put("fluids", fluids);
        ListTag gases = new ListTag();
        for (int i = 0; i < 4; i++) {
            gases.add(new CompoundTag());
        }
        tag.put("gases", gases);
        tag.putString("lockedRecipe", "pioneers:industry/catalytic_reactor/ammonia_small");
        t.load(tag);
        //1.0.5-beta (lot 44, retour 62) : le catalyseur d'un monde d'avant passe de sa case d'entree a la case Outil (rien de perdu)
        Gt.check(t.itemInputs().get(0).isEmpty(), "plus de catalyseur dans la case d'entree");
        Gt.equal(1, Gt.toolSlot(t).getCount(), "catalyseur garde dans la case Outil");
        Gt.equal(3, t.itemOutputs().get(1).getCount(), "catalyseur use garde");
        int battery = 0;
        for (ItemSlot s : t.guiSlots()) {
            battery += s.getStack().is(item("lead_acid_battery")) ? 1 : 0;
        }
        Gt.equal(1, battery, "batterie gardee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_nbt_base", timeoutTicks = 20)
    public static void p104_12_io_elargie_du_craqueur(GameTestHelper h) {
        Gt.begin(h);
        //1.0.6 : le craqueur est multibloc seulement (decision de Guillaume du 16/11/2026) : plus de compact, donc plus de NBT de compact a
        //relire ; un compact d'un monde existant devient le controleur du multibloc
        Gt.equal(IndustryMachine.Atelier.NONE, IndustryMachine.CRACKER.atelier, "craqueur : multibloc seulement");
        Gt.check(!PiIndustry.ATELIERS.containsKey(IndustryMachine.CRACKER), "plus de compact du craqueur");
        for (String t : List.of("basic", "advanced", "elite", "ultimate")) {
            Gt.equal("industrial_cracker", GeneratedFusions.REMAP.get(t + "_cracker"), "ancien compact " + t + " : controleur du craqueur");
        }
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_12_autoclave", timeoutTicks = 20)
    public static void p104_12_autoclave_mki_au_joint_en_cuir(GameTestHelper h) {
        Gt.begin(h);
        //l'autoclave fait le caoutchouc vulcanise des joints en caoutchouc : son MKI se monte au joint en cuir (pas de boucle)
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl("industry/crafting/basic_autoclave")).orElse(null);
        Gt.check(r != null, "recette de l'autoclave MKI absente");
        boolean leather = false, rubber = false;
        for (net.minecraft.world.item.crafting.Ingredient ing : r.getIngredients()) {
            leather |= ing.test(new ItemStack(item("leather_gasket")));
            rubber |= ing.test(new ItemStack(item("gasket")));
        }
        Gt.check(leather && !rubber, "autoclave MKI : joint en cuir attendu, sans joint en caoutchouc");
        h.succeed();
    }
}
