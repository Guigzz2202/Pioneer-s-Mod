package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.GeneratedFusions;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IndustryModule;
import fr.pioneers.tech.industry.IoMigration;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.atelier.OrphanAteliers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.space.environment.Environments;
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
import net.minecraft.world.item.crafting.Ingredient;
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
 * 1.0.4-beta (lot 13) : GameTests de la FUSION DES MACHINES (3/4 : separation, gaz, nucleaire, cultures, puits, energie). Une recette de
 * chaque machine absorbee a compacts tourne dans le compact de sa base au MEME tier (extracteur : lixiviation, extraction par solvant,
 * pressage ; centrifugeuse : decantation, dessalage, cyclone ; cristalliseur : tirage ; Separateur de gaz : PSA ; Unite cryogenique :
 * refrigeration, echange de chaleur) ; les recettes des multiblocs seuls absorbes (air, gaz, retraitement, plantation) et des absorbees d'une
 * base multibloc seul (laser, vitrification, microalgues) sont au multibloc de la base ; les anciens ids, les modules propres, les compacts de
 * la Cellule blindee (multibloc seul) et la centrale a cycle combine (supprimee) se remappent ; le NBT d'une absorbee relu dans la base garde son
 * contenu ; le chevalet de pompage pompe tous les gisements ; corrections de fabrication (controleurs, module de manipulation, centrifugeuse MKI).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot13Tests {

    private PatchP104Lot13Tests() {
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

    /** La recette d'une machine absorbee tourne dans le compact de la base (meme id de recette, machine = base, famille = l'absorbee). */
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
            Gt.check(produced(t, r), absorbed + " -> " + base.key + " : " + path + " n'a encore rien produit");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_leach", timeoutTicks = 400)
    public static void p104_13_lixiviation_a_l_extracteur(GameTestHelper h) {
        runs(h, "leaching_tank", "industry/leaching_tank/calcium_fluoride");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_solvent", timeoutTicks = 400)
    public static void p104_13_extraction_par_solvant_a_l_extracteur(GameTestHelper h) {
        runs(h, "solvent_extractor", "industry/solvent_extractor/thorium_extraction");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_press", timeoutTicks = 400)
    public static void p104_13_pressage_a_l_extracteur(GameTestHelper h) {
        runs(h, "oil_press", "industry/oil_press/press_algae");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_decanter", timeoutTicks = 400)
    public static void p104_13_decantation_a_la_centrifugeuse(GameTestHelper h) {
        runs(h, "decanter", "industry/decanter/desliming");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_desalter", timeoutTicks = 400)
    public static void p104_13_dessalage_a_la_centrifugeuse(GameTestHelper h) {
        runs(h, "desalter", "industry/desalter/desalt_light");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_cyclone", timeoutTicks = 400)
    public static void p104_13_cyclone_a_la_centrifugeuse(GameTestHelper h) {
        runs(h, "cyclone_separator", "industry/cyclone_separator/flue_coke_oven");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_puller", timeoutTicks = 400)
    public static void p104_13_tirage_au_cristalliseur(GameTestHelper h) {
        runs(h, "crystal_puller", "industry/crystal_puller/cz_fluorite");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_psa", timeoutTicks = 400)
    public static void p104_13_psa_au_separateur_de_gaz(GameTestHelper h) {
        runs(h, "psa_unit", "industry/psa_unit/psa_coke_oven_hydrogen");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_chiller", timeoutTicks = 400)
    public static void p104_13_refrigeration_a_l_unite_cryogenique(GameTestHelper h) {
        runs(h, "chiller", "industry/chiller/chill_chlorine");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_phe", timeoutTicks = 400)
    public static void p104_13_echange_de_chaleur_a_l_unite_cryogenique(GameTestHelper h) {
        runs(h, "plate_heat_exchanger", "industry/plate_heat_exchanger/heat_recovery");
    }

    // ================================================================== multiblocs : recettes au multibloc de la base

    /** Recette d'une absorbee au multibloc de sa base (reservee au multibloc si la base a des compacts). */
    private static void multi(GameTestHelper h, String absorbed, String path) {
        IndustrialRecipe r = recipe(h, path);
        IndustryMachine base = IndustryMachine.byKey(GeneratedFusions.base(absorbed));
        Gt.check(base != null && r.machine() == base, path + " : machine " + r.machine().key);
        Gt.equal(absorbed, r.family(), path + " : famille");
        List<RecipeChoice> multi = IndustryRecipes.choices(h.getLevel(), base, true);
        Gt.check(multi.stream().anyMatch(c -> c.id().equals(r.getId())), path + " absente du multibloc de " + base.key);
        Gt.check(PiIndustry.controller(base) != null, "controleur de " + base.key);
        if (base.atelier != IndustryMachine.Atelier.NONE) {
            Gt.check(r.industrialOnly(), path + " : recette d'un multibloc seul pas reservee au multibloc");
            List<RecipeChoice> compact = IndustryRecipes.choices(h.getLevel(), base, false);
            Gt.check(compact.stream().noneMatch(c -> c.id().equals(r.getId())), path + " au compact de " + base.key);
        }
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_multi", timeoutTicks = 20)
    public static void p104_13_recettes_au_multibloc_de_la_base(GameTestHelper h) {
        Gt.begin(h);
        multi(h, "air_separation_unit", "industry/air_separation_unit/air_gases");
        multi(h, "gas_processing_plant", "industry/gas_processing_plant/process_natural_gas");
        multi(h, "laser_isotope_separator", "industry/laser_isotope_separator/laser_enrichment");
        multi(h, "reprocessing_plant", "industry/reprocessing_plant/dissolve_spent_fuel_assembly");
        multi(h, "vitrification_furnace", "industry/vitrification_furnace/vitrification");
        //1.0.4-beta (lot 22) : la plantation forestiere est rendue a elle-meme (PatchP104Lot22Tests)
        multi(h, "photobioreactor", "industry/photobioreactor/algae_culture");
        //la separation de l'air ne travaille que dans une atmosphere (comme l'ancienne unite) : famille gardee chez le Separateur de gaz
        boolean air = IndustryRecipes.of(h.getLevel(), IndustryMachine.GAS_SEPARATOR).stream()
              .anyMatch(r -> IndustryRecipes.AIR_FAMILY.equals(r.family()));
        Gt.check(Environments.get(h.getLevel()).nitrogen() <= 0 || air, "separation de l'air absente dans une atmosphere");
        h.succeed();
    }

    // ================================================================== mondes existants : anciens ids, modules, NBT

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_remaps", timeoutTicks = 20)
    public static void p104_13_anciens_ids_vers_la_base(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal("advanced_extractor", PiRemaps.p104Block("advanced_leaching_tank"), "cuve de lixiviation MKII -> extracteur MKII");
        Gt.equal("ultimate_extractor", PiRemaps.p104Block("ultimate_oil_press"), "presse a huile MKIV -> extracteur MKIV");
        Gt.equal("elite_centrifuge", PiRemaps.p104Block("elite_desalter"), "dessaleur MKIII -> centrifugeuse MKIII");
        Gt.equal("basic_crystallizer", PiRemaps.p104Block("basic_crystal_puller"), "tireur de cristal MKI -> cristalliseur MKI");
        Gt.equal("advanced_gas_separator", PiRemaps.p104Block("advanced_psa_unit"), "PSA MKII -> Separateur de gaz MKII");
        Gt.equal("basic_cryogenic_unit", PiRemaps.p104Block("basic_chiller"), "groupe frigorifique MKI -> Unite cryogenique MKI");
        Gt.equal("industrial_gas_separator", PiRemaps.p104Block("air_separation_unit"), "unite de separation d'air -> separateur industriel");
        Gt.equal("industrial_gas_separator", PiRemaps.p104Block("gas_processing_plant"), "usine de traitement du gaz -> separateur industriel");
        //bases multibloc seul : compacts des absorbees -> controleur
        Gt.equal("centrifuge_cascade", PiRemaps.p104Block("basic_laser_isotope_separator"), "separateur laser compact -> cascade");
        Gt.equal("hydroponic_farm", PiRemaps.p104Block("elite_photobioreactor"), "photobioreacteur compact -> ferme hydroponique");
        Gt.equal("industrial_hot_cell", PiRemaps.p104Block("advanced_vitrification_furnace"), "four de vitrification compact -> Cellule blindee");
        Gt.equal("industrial_hot_cell", PiRemaps.p104Block("reprocessing_plant"), "usine de retraitement -> Cellule blindee");
        Gt.check(PiRemaps.p104Block("tree_plantation") == null, "plantation forestiere rendue (lot 22) : plus de remap");
        //Cellule blindee multibloc seul : ses propres compacts -> son controleur
        for (String t : new String[]{"basic", "advanced", "elite", "ultimate"}) {
            Gt.equal("industrial_hot_cell", PiRemaps.p104Block(t + "_hot_cell"), t + "_hot_cell -> controleur de la Cellule blindee");
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(t + "_hot_cell")), t + "_hot_cell encore enregistre");
        }
        //puits -> chevalet de pompage (compacts deja retires en 1.0.3-beta lot 5 : controleur)
        Gt.equal("industrial_pumpjack", PiRemaps.p104Block("industrial_brine_well"), "puits de saumure -> chevalet de pompage");
        Gt.equal("industrial_pumpjack", PiRemaps.p104Block("derrick"), "derrick -> chevalet de pompage");
        Gt.equal("industrial_pumpjack", PiRemaps.p104Block("industrial_gas_wellhead"), "tete de puits de gaz -> chevalet de pompage");
        Gt.equal("industrial_pumpjack", PiRemaps.simplifiedBlock("basic_gas_wellhead"), "tete de puits compacte (1.0.3) -> chevalet de pompage");
        //centrale a cycle combine supprimee -> turbine a gaz (controleur)
        Gt.equal("gas_turbine", PiRemaps.p104Block("combined_cycle_plant"), "centrale a cycle combine -> turbine a gaz");
        Gt.equal("gas_turbine", GeneratedFusions.RETIRED.get("combined_cycle_plant"), "machine supprimee : remplacant");
        Gt.check(IndustryMachine.byKey("combined_cycle_plant") == null, "centrale a cycle combine encore au catalogue");
        //modules propres aux absorbees -> module de la base
        String[][] modules = {{"extraction_module", "pressing_module"}, {"cryogenic_module", "membrane_module"},
              {"gas_treatment_module", "membrane_module"}, {"laser_module", "gas_centrifuge_module"}, {"pulsed_column_module", "hot_cell_module"},
              {"forestry_module", "growing_module"}, {"photobio_module", "growing_module"}};
        for (String[] m : modules) {
            Gt.equal(m[1], PiRemaps.p104Block(m[0]), m[0] + " -> " + m[1]);
            //1.0.4-beta (lot 23) : les modules de la base sont retires a leur tour : la chaine finit sur leur remplacant
            Gt.equal(PiRemaps.p104Lot23Block(m[1]), PiRemaps.simplifiedItem(m[0]), m[0] + " (objet) -> " + m[1] + " -> remplacant du lot 23");
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(m[0])), m[0] + " encore enregistre");
        }
        Gt.check(!PiIndustry.MODULES.containsKey(IndustryModule.GROWING), "module de culture retire (1.0.4-beta lot 23)");
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

    private static int count(AtelierTile t, Item it) {
        int n = 0;
        for (ItemSlot s : t.guiSlots()) {
            n += s.getStack().is(it) ? s.getStack().getCount() : 0;
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_nbt_decanter", timeoutTicks = 20)
    public static void p104_13_nbt_decanteur_dans_la_centrifugeuse(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.CENTRIFUGE, CableTier.ADVANCED).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'un decanteur MKII d'un monde du lot 12 (io 1/1/1/3/1/0, version des cases 4) : entree, sortie, batterie ; eau en entree,
        //eau decantee en sortie 0
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 4);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("tailings"), 5)));
        items.add(slot(1, new ItemStack(Items.CLAY_BALL, 3)));
        items.add(slot(2, new ItemStack(item("lead_acid_battery"), 1)));
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(fluid(new FluidStack(Fluids.WATER, 500)));
        fluids.add(fluid(new FluidStack(Fluids.WATER, 300)));
        fluids.add(new CompoundTag());
        fluids.add(new CompoundTag());
        tag.put("fluids", fluids);
        ListTag gases = new ListTag();
        gases.add(new CompoundTag());
        tag.put("gases", gases);
        tag.putString("recipe", "pioneers:industry/decanter/desliming");
        t.load(tag);
        Gt.equal(5, t.itemInputs().get(0).getCount(), "entree gardee");
        Gt.equal(3, t.itemOutputs().get(0).getCount(), "sortie gardee");
        Gt.equal(1, count(t, item("lead_acid_battery")), "batterie gardee");
        Gt.equal(500, t.fluidInputs().get(0).getFluidAmount(), "reservoir d'entree garde");
        Gt.equal(300, t.fluidOutputs().get(0).getFluidAmount(), "reservoir de sortie garde (reservoir d'entree en plus avant lui)");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_nbt_chiller", timeoutTicks = 20)
    public static void p104_13_nbt_groupe_frigorifique_dans_l_unite_cryogenique(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.CRYOGENIC_UNIT, CableTier.ELITE).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'un groupe frigorifique MKIII (io 0/1/1/1/1/1) : sortie, batterie ; saumure froide en entree, fluide de sortie
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 4);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("salt"), 6)));
        items.add(slot(1, new ItemStack(item("lead_acid_battery"), 1)));
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(fluid(new FluidStack(Fluids.WATER, 400)));
        fluids.add(fluid(new FluidStack(Fluids.WATER, 250)));
        tag.put("fluids", fluids);
        ListTag gases = new ListTag();
        gases.add(new CompoundTag());
        gases.add(new CompoundTag());
        tag.put("gases", gases);
        tag.putString("recipe", "pioneers:industry/chiller/chill_chlorine");
        t.load(tag);
        Gt.equal(6, t.itemOutputs().get(0).getCount(), "sortie gardee");
        Gt.equal(1, count(t, item("lead_acid_battery")), "batterie gardee");
        Gt.equal(400, t.fluidInputs().get(0).getFluidAmount(), "reservoir d'entree garde");
        Gt.equal(250, t.fluidOutputs().get(0).getFluidAmount(), "reservoir de sortie garde");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_nbt_ccp", timeoutTicks = 20)
    public static void p104_13_nbt_cycle_combine_reconnu_par_la_turbine(GameTestHelper h) {
        Gt.begin(h);
        //NBT du controleur d'une centrale a cycle combine (io 0/0/2/0/1/1) : fluides = 2 entrees + caloporteur + caloporteur chaud ;
        //gaz = entree, sortie, vapeur : la turbine a gaz (remplacant) le reconnait par sa forme et remet le combustible par noms
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 4);
        ListTag fluids = new ListTag();
        for (int i = 0; i < 4; i++) {
            fluids.add(new CompoundTag());
        }
        tag.put("fluids", fluids);
        ListTag gases = new ListTag();
        for (int i = 0; i < 3; i++) {
            gases.add(new CompoundTag());
        }
        tag.put("gases", gases);
        tag.put("items", new ListTag());
        IoMigration.Candidate c = IoMigration.source(IndustryMachine.GAS_TURBINE, tag, true);
        Gt.equal("combined_cycle_plant", c.key(), "NBT de la centrale reconnu par la turbine a gaz");
        Gt.check(IoMigration.absorbed(IndustryMachine.GAS_TURBINE).contains("combined_cycle_plant"), "machine supprimee relue par la turbine");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_orphan", timeoutTicks = 20)
    public static void p104_13_objets_d_un_compact_retire_rendus(GameTestHelper h) {
        Gt.begin(h);
        //NBT d'atelier d'une Cellule blindee compacte (bloc remappe vers le controleur) : ses objets sont lus pour etre rendus au sol
        CompoundTag tag = new CompoundTag();
        tag.putString("id", OrphanAteliers.ATELIER_TYPE);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("irradiated_lithium_target"), 2)));
        items.add(slot(3, new ItemStack(item("zircaloy_plate"), 5)));
        tag.put("items", items);
        List<ItemStack> out = OrphanAteliers.items(tag);
        Gt.equal(2, out.size(), "deux piles a rendre");
        Gt.equal(7, out.stream().mapToInt(ItemStack::getCount).sum(), "tous les objets");
        Gt.equal("pioneers:industry_atelier", ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(PiIndustry.ATELIER_TILE.get()).toString(),
              "type de tile des ateliers");
        h.succeed();
    }

    // ================================================================== bases : io, puits, multibloc seul, corrections de fabrication

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_bases", timeoutTicks = 20)
    public static void p104_13_bases_io_puits_et_cellule_blindee(GameTestHelper h) {
        Gt.begin(h);
        IndustryMachine pj = IndustryMachine.PUMPJACK;
        Gt.check(pj.wells.contains("oil") && pj.wells.contains("brine") && pj.wells.contains("gas"), "chevalet : petrole, saumure et gaz");
        Gt.equal(2, pj.gasesOut, "chevalet : deux sorties de gaz (gaz associe, helium)");
        Gt.check(GeneratedFusions.WELLS.get("derrick").contains("oil"), "progres du derrick : gisement de petrole");
        Gt.equal(2, IndustryMachine.CENTRIFUGE.fluidsIn, "centrifugeuse : deux entrees de fluide (dessaleur)");
        //1.0.6 (audit des cases) : 6 jauges de sortie partagees (liquide ou gaz : fractions de l'air)
        Gt.equal(6, IndustryMachine.GAS_SEPARATOR.fluidsOut, "Separateur de gaz : 6 jauges de sortie (air liquide et gaz)");
        Gt.check(fr.pioneers.api.machine.resource.ResourceRows.pairedOut(IndustryMachine.GAS_SEPARATOR), "Separateur de gaz : sorties partagees");
        Gt.equal(IndustryMachine.Atelier.NONE, IndustryMachine.HOT_CELL.atelier, "Cellule blindee : multibloc seul");
        Gt.equal("industrial_hot_cell", IndustryMachine.HOT_CELL.multiId(), "Cellule blindee : id du controleur garde");
        Gt.check(!PiIndustry.ATELIERS.containsKey(IndustryMachine.HOT_CELL), "Cellule blindee : plus de compact");
        Gt.check(PiIndustry.controller(IndustryMachine.HOT_CELL) != null, "Cellule blindee : controleur");
        h.succeed();
    }

    private static boolean uses(Recipe<?> r, Item it) {
        for (Ingredient ing : r.getIngredients()) {
            if (ing.test(new ItemStack(it))) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_13_crafting", timeoutTicks = 20)
    public static void p104_13_corrections_de_fabrication(GameTestHelper h) {
        Gt.begin(h);
        var rm = h.getLevel().getRecipeManager();
        Recipe<?> hot = rm.byKey(rl("industry/crafting/industrial_hot_cell")).orElse(null);
        //1.0.4-beta (lot 18, T:balance/difficulte_data.py) : telemanipulateurs a servomoteurs (les moteurs pas a pas du lot 13 remplaces)
        Gt.check(hot != null && uses(hot, item("servo_motor")) && !uses(hot, item("stepper_motor")),
              "controleur de la Cellule blindee : servomoteurs (multibloc seul)");
        Recipe<?> module = rm.byKey(rl("industry/crafting/hot_cell_module")).orElse(null);
        //1.0.4-beta (lot 23) : le poste de manipulation (module) est retire : plus de recette
        Gt.check(module == null, "poste de manipulation retire (lot 23) : recette encore presente");
        Recipe<?> sep = rm.byKey(rl("industry/crafting/industrial_gas_separator")).orElse(null);
        Gt.check(sep != null && uses(sep, item("centrifugal_pump")) && !uses(sep, item("compressor_unit")) && !uses(sep, item("basic_gas_separator")),
              "Separateur de gaz industriel : controleur de l'ancienne usine de traitement du gaz");
        Recipe<?> cen = rm.byKey(rl("industry/crafting/basic_centrifuge")).orElse(null);
        Gt.check(cen != null && uses(cen, item("leather_belt")) && !uses(cen, item("brake_unit")), "centrifugeuse MKI : courroie de cuir, sans frein");
        h.succeed();
    }
}
