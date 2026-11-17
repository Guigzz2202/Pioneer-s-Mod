package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.forge.smithing.HearthUnit;
import fr.pioneers.tech.industry.GeneratedFusions;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IndustryModule;
import fr.pioneers.tech.industry.IoMigration;
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
import net.minecraft.world.item.crafting.Ingredient;
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
 * 1.0.4-beta (lot 14) : GameTests de la FUSION DES MACHINES (4/4 : mise en forme, usinage, assemblage, revetement). Une recette de chaque
 * machine absorbee a compacts tourne dans le compact de sa base au MEME tier (Marteau-pilon : frappe a froid ; Laminoir : calandrage ;
 * Trefileuse : fibrage, toronnage ; Cintreuse : enroulage ; Machine de coulee : injection ; Centre d'usinage : fraisage, rectification,
 * taillage ; Scie industrielle : galettes ; Bain chimique : projection plasma, gravure ; Assembleur : composants, pieces logistiques, soudage) ;
 * les recettes du marteau-pilon industriel (ex-forge) et de la coulee continue sont reservees au multibloc de la base, et le foyer de forge
 * garde ses recettes de lopin ; les anciens ids (compacts, controleurs, module de la presse a injection, imprimante 3D metal et decoupeuse
 * supprimees) se remappent ; le NBT d'une absorbee ou d'une machine supprimee est reconnu par sa forme ; io elargies ; corrections (outil de
 * coupe en carbure a la poudre, « bloc -> 9 plaques » au Laminoir).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot14Tests {

    private PatchP104Lot14Tests() {
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
            t.itemInputs().get(i).setStack(stacks[0].copyWithCount(Math.min(64, r.items().get(i).count() * 2)));
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

    /** Quelque chose est sorti : un objet du premier resultat, ou un fluide / gaz produit. */
    private static boolean produced(AtelierTile t, IndustrialRecipe r) {
        Item main = r.results().isEmpty() ? null : r.results().get(0).stack().getItem();
        for (ItemSlot s : t.itemOutputs()) {
            if (main != null && s.getStack().is(main)) {
                return true;
            }
        }
        if (main == null) {
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

    // ================================================================== une recette de chaque absorbee a compacts, au compact de la base

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_cold_header", timeoutTicks = 400)
    public static void p104_14_frappe_a_froid_au_marteau_pilon(GameTestHelper h) {
        runs(h, "cold_header", "industry/cold_header/head_aluminium_rivets");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_calender", timeoutTicks = 400)
    public static void p104_14_calandrage_au_laminoir(GameTestHelper h) {
        runs(h, "calender", "industry/calender/foil_rod_copper");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_fiber", timeoutTicks = 400)
    public static void p104_14_fibrage_a_la_trefileuse(GameTestHelper h) {
        runs(h, "fiber_tower", "industry/fiber_tower/borosilicate_fiber");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_strander", timeoutTicks = 400)
    public static void p104_14_toronnage_a_la_trefileuse(GameTestHelper h) {
        //1.0.6 : les cordes de fibres se filent a l'Atelier de confection ; le toronnage des fils de metal reste a la trefileuse
        runs(h, "wire_strander", "industry/wire_strander/strand_rope_copper");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_coiler", timeoutTicks = 400)
    public static void p104_14_enroulage_a_la_cintreuse(GameTestHelper h) {
        runs(h, "spring_coiler", "industry/spring_coiler/coil_rod_spring_aluminium");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_injection", timeoutTicks = 400)
    public static void p104_14_injection_a_la_machine_de_coulee(GameTestHelper h) {
        runs(h, "injection_molder", "industry/injection_molder/wax_pattern_beeswax");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_mill", timeoutTicks = 400)
    public static void p104_14_fraisage_au_centre_d_usinage(GameTestHelper h) {
        runs(h, "cnc_mill", "industry/cnc_mill/mill_wax_pattern_beeswax");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_grinder", timeoutTicks = 400)
    public static void p104_14_rectification_au_centre_d_usinage(GameTestHelper h) {
        //1.0.6 : la taille des gemmes passe a la Decoupeuse ; la rectification (billes, visserie) reste au Centre d'usinage
        runs(h, "grinder", "industry/grinder/grind_steel_balls");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_hobber", timeoutTicks = 400)
    public static void p104_14_taillage_au_centre_d_usinage(GameTestHelper h) {
        //1.0.6 : la tailleuse est rendue (Decoupeuse) : la taille des engrenages se fait chez elle, sans famille
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "industry/gear_hobber/cut_gear_aluminium");
        Gt.check(r.machine() == IndustryMachine.GEAR_HOBBER && r.family() == null, "taillage : Decoupeuse, sans famille");
        Gt.check(recipe(h, "industry/grinder/cut_peridot").machine() == IndustryMachine.GEAR_HOBBER, "taille des gemmes : Decoupeuse");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_wafer", timeoutTicks = 400)
    public static void p104_14_galettes_a_la_scie_industrielle(GameTestHelper h) {
        runs(h, "wafer_saw", "industry/wafer_saw/rc_silicon_wafers");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_plasma", timeoutTicks = 600)
    public static void p104_14_projection_plasma_au_bain_chimique(GameTestHelper h) {
        runs(h, "plasma_sprayer", "industry/plasma_sprayer/spray_diamond_discs");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_etcher", timeoutTicks = 400)
    public static void p104_14_gravure_au_bain_chimique(GameTestHelper h) {
        runs(h, "acid_etcher", "industry/acid_etcher/chem_mill_aluminium_foil");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_components", timeoutTicks = 400)
    public static void p104_14_composants_a_l_assembleur(GameTestHelper h) {
        runs(h, "component_assembler", "industry/component_assembler/rc_advanced_heat_exchanger");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_logistics", timeoutTicks = 600)
    public static void p104_14_pieces_logistiques_a_l_assembleur(GameTestHelper h) {
        //1.0.6 : plus aucune machine a l'Assembleur (le port est une machine : etabli de montage) ; une piece logistique y reste
        runs(h, "logistics_assembler", "automation/logistics/naval_harbor_crane");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_welder", timeoutTicks = 400)
    public static void p104_14_soudage_a_l_assembleur(GameTestHelper h) {
        runs(h, "arc_welder", "industry/arc_welder/weld_beam_galvanized_steel");
    }

    // ================================================================== multiblocs seuls absorbes : au multibloc de la base ; foyer de forge

    /** Recette d'une absorbee multibloc seul au multibloc de sa base, reservee au multibloc (la base a des compacts). */
    private static void multi(GameTestHelper h, String absorbed, String path) {
        IndustrialRecipe r = recipe(h, path);
        IndustryMachine base = IndustryMachine.byKey(GeneratedFusions.base(absorbed));
        Gt.check(base != null && r.machine() == base, path + " : machine " + r.machine().key);
        Gt.equal(absorbed, r.family(), path + " : famille");
        Gt.check(r.industrialOnly(), path + " : recette d'un multibloc seul pas reservee au multibloc");
        List<RecipeChoice> multi = IndustryRecipes.choices(h.getLevel(), base, true);
        Gt.check(multi.stream().anyMatch(c -> c.id().equals(r.getId())), path + " absente du multibloc de " + base.key);
        List<RecipeChoice> compact = IndustryRecipes.choices(h.getLevel(), base, false);
        Gt.check(compact.stream().noneMatch(c -> c.id().equals(r.getId())), path + " au compact de " + base.key);
        Gt.check(PiIndustry.controller(base) != null, "controleur de " + base.key);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_multi", timeoutTicks = 20)
    public static void p104_14_recettes_au_multibloc_de_la_base(GameTestHelper h) {
        Gt.begin(h);
        multi(h, "forge", "industry/forge/steel_bloom_cold");
        //1.0.6 (demande de Guillaume du 16/11/2026) : les tetes d'outils des metaux de base se forgent au Marteau-pilon compact ; celles des
        //metaux avances (fraisees jusqu'ici) au multibloc seulement
        IndustrialRecipe axe = recipe(h, "industry/forge/forge_brass_axe");
        Gt.check(!axe.industrialOnly() && IndustryRecipes.choices(h.getLevel(), IndustryMachine.POWER_HAMMER, false).stream()
              .anyMatch(c -> c.id().equals(axe.getId())), "tete d'outil en laiton au Marteau-pilon compact");
        //1.0.6 : metaux avances aux tiers superieurs (MKII a MKIV, compacte ou multibloc des carcasses de ce tier)
        IndustrialRecipe ti = recipe(h, "industry/cnc_mill/mill_titanium_pickaxe_heads");
        Gt.check(ti.machine() == IndustryMachine.POWER_HAMMER && !ti.industrialOnly() && ti.tier() >= 1, "tete en titane : Marteau-pilon, MKII et plus");
        multi(h, "continuous_caster", "industry/continuous_caster/cc_beam_blank_aluminium");
        //le foyer de forge garde ses deux recettes de lopin (famille « forge » du Marteau-pilon, reservees au multibloc)
        IndustrialRecipe bloom = recipe(h, "industry/forge/steel_bloom_cold");
        Gt.check(bloom.machine() == IndustryMachine.POWER_HAMMER && bloom.industrialOnly(), "lopin : Marteau-pilon, multibloc seul");
        Gt.check(HearthUnit.hearthRecipe(bloom), "lopin : recette du foyer de forge");
        Gt.check(HearthUnit.hearthRecipe(recipe(h, "industry/forge/steel_bloom")), "lopin du lingot : recette du foyer de forge");
        Gt.check(!HearthUnit.hearthRecipe(recipe(h, "industry/forge/forge_brass_axe")), "une tete d'outil n'est pas une recette du foyer");
        h.succeed();
    }

    // ================================================================== mondes existants : anciens ids, module, machines supprimees

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_remaps", timeoutTicks = 20)
    public static void p104_14_anciens_ids_vers_la_base(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal("advanced_press", PiRemaps.p104Block("advanced_forming_station"), "poste de faconnage MKII -> Presse MKII");
        Gt.equal("elite_power_hammer", PiRemaps.p104Block("elite_cold_header"), "frappeuse MKIII -> Marteau-pilon MKIII");
        Gt.equal("basic_rolling_mill", PiRemaps.p104Block("basic_calender"), "calandre MKI -> Laminoir MKI");
        Gt.equal("ultimate_wiremill", PiRemaps.p104Block("ultimate_fiber_tower"), "tour de fibrage MKIV -> Trefileuse MKIV");
        Gt.equal("advanced_wiremill", PiRemaps.p104Block("advanced_wire_strander"), "toronneuse MKII -> Trefileuse MKII");
        Gt.equal("basic_bender", PiRemaps.p104Block("basic_spring_coiler"), "enrouleuse MKI -> Cintreuse MKI");
        Gt.equal("elite_casting_machine", PiRemaps.p104Block("elite_injection_molder"), "presse a injection MKIII -> Machine de coulee MKIII");
        Gt.equal("basic_machining_center", PiRemaps.p104Block("basic_cnc_mill"), "fraiseuse MKI -> Centre d'usinage MKI");
        Gt.equal("advanced_machining_center", PiRemaps.p104Block("advanced_grinder"), "rectifieuse MKII -> Centre d'usinage MKII");
        //1.0.6 : la tailleuse est rendue (Decoupeuse) et reprend la scie a galettes
        Gt.check(PiRemaps.p104Block("ultimate_gear_hobber") == null, "tailleuse MKIV : machine rendue, plus de remap");
        Gt.equal("elite_gear_hobber", PiRemaps.p104Block("elite_wafer_saw"), "scie a galettes MKIII -> Decoupeuse MKIII");
        Gt.equal("advanced_chemical_bath", PiRemaps.p104Block("advanced_plasma_sprayer"), "projection plasma MKII -> Bain chimique MKII");
        Gt.equal("basic_chemical_bath", PiRemaps.p104Block("basic_acid_etcher"), "graveur MKI -> Bain chimique MKI");
        Gt.equal("elite_assembler", PiRemaps.p104Block("elite_component_assembler"), "assembleur de composants MKIII -> Assembleur MKIII");
        Gt.equal("basic_assembler", PiRemaps.p104Block("basic_logistics_assembler"), "assembleur logistique MKI -> Assembleur MKI");
        Gt.equal("ultimate_assembler", PiRemaps.p104Block("ultimate_arc_welder"), "soudeuse MKIV -> Assembleur MKIV");
        //controleurs (multiblocs seuls absorbes compris)
        Gt.equal("industrial_power_hammer", PiRemaps.p104Block("industrial_forge"), "marteau-pilon industriel (ex-forge) -> Marteau-pilon industriel");
        Gt.equal("industrial_casting_machine", PiRemaps.p104Block("continuous_caster"), "coulee continue -> Machine de coulee industrielle");
        Gt.equal("industrial_assembler", PiRemaps.p104Block("industrial_component_assembler"), "assembleur de composants industriel -> Assembleur industriel");
        //machines supprimees : compacts -> compacts du remplacant au meme tier, controleur -> controleur
        for (String t : new String[]{"basic", "advanced", "elite", "ultimate"}) {
            Gt.equal(t + "_machining_center", PiRemaps.p104Block(t + "_metal_printer"), t + " imprimante 3D metal -> Centre d'usinage");
            Gt.equal(t + "_sawmill", PiRemaps.p104Block(t + "_cutting_table"), t + " decoupeuse -> Scie industrielle");
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(t + "_metal_printer")), t + "_metal_printer encore enregistre");
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(t + "_cutting_table")), t + "_cutting_table encore enregistre");
        }
        Gt.equal("industrial_machining_center", PiRemaps.p104Block("industrial_metal_printer"), "imprimante industrielle -> Centre d'usinage industriel");
        Gt.equal("industrial_sawmill", PiRemaps.p104Block("industrial_cutting_table"), "decoupeuse industrielle -> Scie industrielle");
        Gt.equal("machining_center", GeneratedFusions.RETIRED.get("metal_printer"), "imprimante supprimee : remplacant");
        Gt.equal("sawmill", GeneratedFusions.RETIRED.get("cutting_table"), "decoupeuse supprimee : remplacant");
        Gt.check(IndustryMachine.byKey("metal_printer") == null && IndustryMachine.byKey("cutting_table") == null, "machines supprimees au catalogue");
        //module de la presse a injection -> module de cuve de la Machine de coulee
        Gt.equal("vessel_module", PiRemaps.p104Block("plastics_module"), "module de plastique -> module de cuve");
        Gt.equal("industrial_casing_basic", PiRemaps.simplifiedItem("plastics_module"), "module de plastique (objet) -> module de cuve -> carcasse MKI (lot 23)");
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl("plastics_module")), "plastics_module encore enregistre");
        Gt.check(!PiIndustry.MODULES.containsKey(IndustryModule.VESSEL), "module de cuve retire (1.0.4-beta lot 23)");
        //le marteau-pilon a vapeur gardait son bloc (age vapeur du Marteau-pilon) ; 1.0.5-beta (lot 38, retour 52) : il est SUPPRIME (remappe
        //vers la scie a vapeur)
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl("steam_hammer")), "marteau-pilon a vapeur retire");
        Gt.equal("steam_saw", PiRemaps.simplifiedBlock("steam_hammer"), "marteau-pilon a vapeur -> scie a vapeur");
        h.succeed();
    }

    private static CompoundTag slot(int index, ItemStack stack) {
        CompoundTag c = stack.save(new CompoundTag());
        c.putShort("Slot", (short) index);
        return c;
    }

    /** NBT d'atelier (ou de controleur) : cases, n reservoirs de fluide et de gaz vides, recette en cours (indice du dossier). */
    private static CompoundTag nbt(int version, ListTag items, int fluids, int gases, String recipe) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", version);
        tag.put("items", items);
        ListTag f = new ListTag();
        for (int i = 0; i < fluids; i++) {
            f.add(new CompoundTag());
        }
        tag.put("fluids", f);
        ListTag g = new ListTag();
        for (int i = 0; i < gases; i++) {
            g.add(new CompoundTag());
        }
        tag.put("gases", g);
        if (recipe != null) {
            tag.putString("recipe", recipe);
        }
        return tag;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_nbt_welder", timeoutTicks = 20)
    public static void p104_14_nbt_soudeuse_dans_l_assembleur(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.ASSEMBLER, CableTier.ADVANCED).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'une soudeuse a l'arc MKII d'un monde du lot 13 (io 6/2/0/0/1/0, version des cases 5) : 6 entrees, 2 sorties, batterie ; argon
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("steel_sheet"), 6)));
        items.add(slot(6, new ItemStack(item("steel_pipe"), 4)));
        items.add(slot(8, new ItemStack(item("lead_acid_battery"), 1)));
        CompoundTag tag = nbt(5, items, 0, 1, "pioneers:industry/arc_welder/weld_beam_galvanized_steel");
        Gt.equal("arc_welder", IoMigration.source(IndustryMachine.ASSEMBLER, tag, false).key(), "NBT de la soudeuse reconnu par l'Assembleur");
        t.load(tag);
        Gt.equal(6, t.itemInputs().get(0).getCount(), "entree gardee");
        Gt.equal(4, t.itemOutputs().get(0).getCount(), "sortie gardee (apres les 9 entrees de l'Assembleur)");
        Gt.check(t.guiSlots().stream().anyMatch(s -> s.getStack().is(item("lead_acid_battery"))), "batterie gardee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_nbt_controllers", timeoutTicks = 20)
    public static void p104_14_nbt_controleurs_absorbes_reconnus(GameTestHelper h) {
        Gt.begin(h);
        //controleur de la coulee continue (io 1/2/2/1/0/1 : 6 + 6 cases, 2 fluides + caloporteur + 1 sortie + chaud, 1 gaz + vapeur) relu dans
        //la Machine de coulee industrielle : meme forme que la base, l'indice du dossier de la recette departage
        ListTag cc = new ListTag();
        cc.add(slot(6, new ItemStack(item("steel_slab"), 2)));
        Gt.equal("continuous_caster", IoMigration.source(IndustryMachine.CASTING_MACHINE,
              nbt(5, cc, 5, 2, "pioneers:industry/continuous_caster/cc_slab_aluminium"), true).key(), "coulee continue reconnue par la Machine de coulee");
        //controleur du marteau-pilon industriel (ex-forge, io 2/1/0/0/0/0) relu dans le Marteau-pilon industriel
        ListTag fg = new ListTag();
        fg.add(slot(0, new ItemStack(Items.IRON_INGOT, 3)));
        Gt.equal("forge", IoMigration.source(IndustryMachine.POWER_HAMMER, nbt(5, fg, 2, 1, "pioneers:industry/forge/forge_brass_axe"), true).key(),
              "marteau-pilon industriel (ex-forge) reconnu par le Marteau-pilon");
        //machines supprimees : compact d'imprimante 3D metal (io 4/1/0/0/1/0) relu dans le Centre d'usinage, decoupeuse (2/2/0/0/1/0) dans la
        //Scie industrielle (reconnus par la forme : un reservoir de gaz, aucun de fluide)
        ListTag mp = new ListTag();
        mp.add(slot(0, new ItemStack(item("stellite_dust"), 5)));
        mp.add(slot(5, new ItemStack(item("lead_acid_battery"), 1)));
        Gt.equal("metal_printer", IoMigration.source(IndustryMachine.MACHINING_CENTER, nbt(5, mp, 0, 1, null), false).key(), "imprimante reconnue par le Centre d'usinage");
        Gt.check(IoMigration.absorbed(IndustryMachine.MACHINING_CENTER).contains("metal_printer"), "imprimante relue par le Centre d'usinage");
        ListTag ct = new ListTag();
        ct.add(slot(0, new ItemStack(Items.IRON_BLOCK, 1)));
        ct.add(slot(4, new ItemStack(item("lead_acid_battery"), 1)));
        Gt.equal("cutting_table", IoMigration.source(IndustryMachine.SAWMILL, nbt(5, ct, 0, 1, null), false).key(), "decoupeuse reconnue par la Scie industrielle");
        h.succeed();
    }

    // ================================================================== bases : io, version des cases, corrections

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_bases", timeoutTicks = 20)
    public static void p104_14_bases_io_elargies(GameTestHelper h) {
        Gt.begin(h);
        //1.0.4-beta (lot 17) : version 7 (io elargies hors fusion : Assembleur a 2 fluides) ; la version 6 reste l'epoque du lot 14
        Gt.check(IndustryMachine.IO_VERSION >= 6, "version des cases 6 au moins (fusion 4/4)");
        Gt.equal(9, IndustryMachine.ASSEMBLER.itemsIn, "Assembleur : 9 entrees");
        Gt.equal(2, IndustryMachine.ASSEMBLER.itemsOut, "Assembleur : 2 sorties (soudeuse)");
        Gt.equal(1, IndustryMachine.ASSEMBLER.gasesIn, "Assembleur : argon (soudeuse)");
        Gt.equal(6, IndustryMachine.CHEMICAL_BATH.itemsIn, "Bain chimique : 6 entrees (projection plasma)");
        Gt.equal(3, IndustryMachine.MACHINING_CENTER.itemsIn, "Centre d'usinage : 3 entrees (fraiseuse, rectifieuse)");
        //1.0.6 (un role par machine) : le centre d'usinage travaille a sec (plus de lubrifiant ni d'eau)
        Gt.equal(0, IndustryMachine.MACHINING_CENTER.fluidsIn, "Centre d'usinage : a sec");
        Gt.equal(3, IndustryMachine.WIREMILL.itemsIn, "Trefileuse : 3 entrees (tour de fibrage)");
        Gt.equal(2, IndustryMachine.CASTING_MACHINE.itemsIn, "Machine de coulee : 2 entrees (presse a injection)");
        Gt.equal(1, IndustryMachine.BENDER.fluidsIn, "Cintreuse : huile de trempe (enrouleuse)");
        Gt.check(IndustryMachine.byKey("forming_station") == null && IndustryMachine.byKey("arc_welder") == null, "absorbees hors du catalogue");
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

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_14_crafting", timeoutTicks = 20)
    public static void p104_14_corrections(GameTestHelper h) {
        Gt.begin(h);
        //l'outil de coupe en carbure se fritte a la poudre de carbure (la voie a la poudre de l'imprimante est partie)
        IndustrialRecipe tool = recipe(h, "industry/induction_furnace/rc_carbide_cutting_tools");
        Gt.check(uses(tool, item("tungsten_carbide_dust")) && !uses(tool, item("tungsten_carbide_plate")), "outil de coupe en carbure : poudre, sans plaque");
        //« 1 bloc -> 9 plaques » (exemple impose de Guillaume) au Laminoir ; 1.0.6 : la recette MKII etait le doublon de celle du laminoir a
        //vapeur (desactivee, verrou suivi) : le Laminoir fait celle-ci des le MKI (recette basse convertie)
        ResourceLocation steamBlock = rl("bronze/steam/rolling_mill/block_plate_iron");
        Gt.equal(steamBlock, PiRemaps.recipeId(rl("industry/rolling_mill/roll_block_plate_iron")), "bloc -> 9 plaques : verrou suivi");
        IndustrialRecipe block = IndustryRecipes.of(h.getLevel(), IndustryMachine.ROLLING_MILL).stream().filter(r -> r.getId().equals(steamBlock))
              .findFirst().orElse(null);
        Gt.check(block != null && block.converted() && block.tier() == 0, "bloc -> 9 plaques : Laminoir des le MKI");
        Gt.equal(9, block.results().get(0).stack().getCount(), "bloc -> 9 plaques");
        h.succeed();
    }
}
