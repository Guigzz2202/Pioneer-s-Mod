package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.bronze.machines.SteamProcessTile;
import fr.pioneers.common.PiRemaps;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.industry.GeneratedFusions;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.atelier.AtelierTile;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
import java.util.List;
import java.util.Map;
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
 * 1.0.4-beta (lot 11) : GameTests de la FUSION DES MACHINES (1/4 : broyeur, separateur de minerai, four industriel, creuset industriel, four a
 * coke). Une recette de chaque machine absorbee tourne dans sa base au MEME tier (le kit compris : voie B de KitProcess) ; les anciens ids se
 * remappent vers la base au meme tier ; le NBT d'une absorbee relu dans la base garde son contenu (IoMigration : atelier et kit) ; l'io elargie
 * de la base garde le contenu d'une base posee avant ; le fondeur d'alliages a vapeur devient le fourneau a creuset ; la pyrolyse est reservee
 * a la batterie de fours a coke.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot11Tests {

    private PatchP104Lot11Tests() {
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

    /** La recette d'une machine absorbee tourne dans la base (meme id de recette, machine = base, famille = l'absorbee). */
    private static void runs(GameTestHelper h, String absorbed, String path) {
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, path);
        IndustryMachine base = IndustryMachine.byKey(GeneratedFusions.base(absorbed));
        Gt.check(base != null && r.machine() == base, path + " : machine " + r.machine().key + " au lieu de la base");
        Gt.equal(absorbed, r.family(), path + " : famille");
        Gt.check(IndustryMachine.byKey(absorbed) == null, absorbed + " encore au catalogue");
        AtelierTile t = base(h, base, new BlockPos(2, 2, 2), r);
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            Gt.check(produced(t, r), absorbed + " -> " + base.key + " : " + path + " n'a encore rien produit");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_ball_mill", timeoutTicks = 400)
    public static void p104_11_broyage_fin_au_broyeur(GameTestHelper h) {
        runs(h, "ball_mill", "industry/ball_mill/grind_chalcopyrite");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_flotation", timeoutTicks = 400)
    public static void p104_11_flottation_au_separateur(GameTestHelper h) {
        runs(h, "flotation_cell", "industry/flotation_cell/float_chalcopyrite");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_magnetic", timeoutTicks = 400)
    public static void p104_11_aimant_au_separateur(GameTestHelper h) {
        runs(h, "magnetic_separator", "industry/magnetic_separator/magnet_ilmenite");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_table", timeoutTicks = 400)
    public static void p104_11_table_au_separateur(GameTestHelper h) {
        runs(h, "shaking_table", "industry/shaking_table/table_zircon_sand");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_optical", timeoutTicks = 400)
    public static void p104_11_tri_optique_au_separateur(GameTestHelper h) {
        runs(h, "optical_sorter", "industry/optical_sorter/sort_gravel");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_roaster", timeoutTicks = 400)
    public static void p104_11_grillage_au_four(GameTestHelper h) {
        runs(h, "roaster", "industry/roaster/roast_vanadinite");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_reduction", timeoutTicks = 400)
    public static void p104_11_reduction_au_four(GameTestHelper h) {
        runs(h, "reduction_furnace", "industry/reduction_furnace/vanadium_alumino");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_sinter", timeoutTicks = 400)
    public static void p104_11_frittage_au_four(GameTestHelper h) {
        runs(h, "sintering_furnace", "industry/sintering_furnace/carbon_anode");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_quench", timeoutTicks = 400)
    public static void p104_11_trempe_au_four(GameTestHelper h) {
        runs(h, "quench_tank", "industry/quench_tank/harden_drill_bits");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_diffusion", timeoutTicks = 400)
    public static void p104_11_diffusion_au_four(GameTestHelper h) {
        runs(h, "diffusion_furnace", "industry/diffusion_furnace/sherardize_rod");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_induction", timeoutTicks = 400)
    public static void p104_11_induction_au_creuset(GameTestHelper h) {
        //1.0.5-beta (lot 39, retour 51) : le four a induction est RENDU a lui-meme (fusion.UNFUSED, comme le four a pyrolyse au lot 22) :
        //la recette de la famille se fait au four a induction, plus au creuset
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "industry/induction_furnace/netherite_alloy");
        Gt.check(r.machine() == IndustryMachine.INDUCTION_FURNACE, "netherite : machine " + r.machine().key + " au lieu du four a induction");
        Gt.equal("induction_furnace", GeneratedFusions.base("induction_furnace"), "four a induction encore absorbe par le creuset");
        AtelierTile t = base(h, IndustryMachine.INDUCTION_FURNACE, new BlockPos(2, 2, 2), r);
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            Gt.check(produced(t, r), "four a induction : netherite_alloy n'a encore rien produit");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_vacuum", timeoutTicks = 400)
    public static void p104_11_sous_vide_au_creuset(GameTestHelper h) {
        runs(h, "vacuum_furnace", "industry/vacuum_furnace/vacuum_titanium");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_glass", timeoutTicks = 400)
    public static void p104_11_verre_au_creuset(GameTestHelper h) {
        runs(h, "tank_furnace", "industry/tank_furnace/soda_glass");
    }

    // ================================================================== mondes existants : anciens ids, NBT

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_remaps", timeoutTicks = 20)
    public static void p104_11_anciens_ids_vers_la_base(GameTestHelper h) {
        Gt.begin(h);
        for (Map.Entry<String, String> e : GeneratedFusions.REMAP.entrySet()) {
            String old = e.getKey(), now = e.getValue();
            Gt.equal(now, PiRemaps.p104Block(old), "remap de " + old);
            Gt.equal(now, PiRemaps.simplifiedBlock(old), "chaine des remaps de " + old);
            Gt.check(!ForgeRegistries.BLOCKS.containsKey(rl(old)), old + " encore enregistre");
            Gt.check(ForgeRegistries.BLOCKS.containsKey(rl(now)), now + " (remplacant de " + old + ") absent");
            for (String t : new String[]{"basic_", "advanced_", "elite_", "ultimate_"}) {
                //1.0.4-beta (lot 12) : base multibloc seul (colonne de distillation) : son controleur (id = sa cle)
                if (old.startsWith(t) && !now.startsWith("industrial_") && IndustryMachine.byKey(now) == null) {
                    Gt.check(now.startsWith(t), old + " -> " + now + " : tier perdu");
                }
            }
        }
        Gt.equal("advanced_crusher", PiRemaps.p104Block("advanced_ball_mill"), "broyeur a boulets MKII -> broyeur MKII");
        Gt.equal("elite_furnace", PiRemaps.p104Block("elite_quench_tank"), "four de traitement MKIII -> four industriel MKIII");
        Gt.equal("industrial_ore_separator", PiRemaps.p104Block("industrial_optical_sorter"), "multibloc du trieur -> multibloc du separateur");
        //1.0.4-beta (lot 22) : le four a pyrolyse est rendu : ses compacts de la 1.0.2 vont a nouveau a son controleur
        Gt.equal("industrial_pyrolysis_oven", PiRemaps.simplifiedBlock("basic_pyrolysis_oven"), "four a pyrolyse compact -> son controleur");
        Gt.equal("crucible_furnace", PiRemaps.p104Block("steam_alloy_smelter"), "fondeur d'alliages a vapeur -> fourneau a creuset");
        Gt.check(SteamProcessTile.create(BlockPos.ZERO, PiBronze.CRUCIBLE_FURNACE.getBlock().defaultBlockState()) == null,
              "tile a vapeur sur le fourneau a creuset");
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

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_nbt_atelier", timeoutTicks = 20)
    public static void p104_11_nbt_cellule_dans_le_separateur(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.ORE_SEPARATOR, CableTier.ADVANCED).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'une cellule de flottation MKII (io 2/2/1, version des cases 2) : entrees 0 et 1, sortie 0, eau dans le reservoir
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 2);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("ground_chalcopyrite"), 5)));
        items.add(slot(1, new ItemStack(item("tailings"), 3)));
        items.add(slot(2, new ItemStack(item("chalcopyrite_concentrate"), 7)));
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(fluid(new FluidStack(Fluids.WATER, 1_500)));
        tag.put("fluids", fluids);
        tag.put("gases", new ListTag());
        tag.putString("recipe", "pioneers:industry/flotation_cell/float_chalcopyrite");
        t.load(tag);
        Gt.equal(5, t.itemInputs().get(0).getCount(), "entree 0 gardee");
        Gt.equal(3, t.itemInputs().get(1).getCount(), "entree 1 gardee");
        Gt.equal(7, t.itemOutputs().get(0).getCount(), "sortie 0 gardee");
        Gt.equal(1_500, t.fluidInputs().get(0).getFluidAmount(), "eau gardee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_nbt_kit", timeoutTicks = 20)
    public static void p104_11_nbt_broyeur_a_boulets_dans_le_kit(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.CRUSHER, CableTier.ELITE).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'atelier d'un broyeur a boulets MKIII (io 1/2/1) : entree, deux sorties, eau
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 2);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(item("crushed_chalcopyrite"), 4)));
        items.add(slot(1, new ItemStack(item("ground_chalcopyrite"), 2)));
        items.add(slot(2, new ItemStack(item("tailings"), 1)));
        tag.put("items", items);
        ListTag fluids = new ListTag();
        fluids.add(fluid(new FluidStack(Fluids.WATER, 500)));
        tag.put("fluids", fluids);
        tag.put("gases", new ListTag());
        t.load(tag);
        Gt.equal(4, t.itemInputs().get(0).getCount(), "entree du kit");
        Gt.equal(2, t.itemOutputs().get(0).getCount(), "sortie du kit");
        Gt.equal(1, t.itemOutputs().get(1).getCount(), "2e sortie du kit");
        Gt.equal(500, t.fluidInputs().get(0).getFluidAmount(), "eau dans le reservoir du kit");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_nbt_base", timeoutTicks = 20)
    public static void p104_11_io_elargie_garde_le_contenu(GameTestHelper h) {
        Gt.begin(h);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, Gt.compact(IndustryMachine.ORE_SEPARATOR, CableTier.BASIC).defaultBlockState());
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        //NBT d'un crible vibrant d'avant (io 1/3, version 2) : entree, 3 sorties, batterie en case 4
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("pioneers", true);
        tag.putInt("ioVersion", 2);
        ListTag items = new ListTag();
        items.add(slot(0, new ItemStack(Items.GRAVEL, 9)));
        items.add(slot(3, new ItemStack(Items.FLINT, 6)));
        items.add(slot(4, new ItemStack(item("lead_acid_battery"), 1)));
        tag.put("items", items);
        tag.put("fluids", new ListTag());
        tag.put("gases", new ListTag());
        t.load(tag);
        Gt.equal(9, t.itemInputs().get(0).getCount(), "entree gardee");
        Gt.equal(6, t.itemOutputs().get(2).getCount(), "3e sortie gardee");
        int battery = 0;
        for (ItemSlot s : t.guiSlots()) {
            battery += s.getStack().is(item("lead_acid_battery")) ? 1 : 0;
        }
        Gt.equal(1, battery, "batterie gardee (case de batterie apres les sorties en plus)");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_11_pyrolysis", timeoutTicks = 20)
    public static void p104_11_pyrolyse_a_la_batterie_seule(GameTestHelper h) {
        //1.0.4-beta (lot 22) : le four a pyrolyse est rendu a lui-meme (fusion.UNFUSED) : ses recettes ne sont plus a la batterie
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "industry/pyrolysis_oven/slow_press_cake");
        Gt.check(r.machine() == IndustryMachine.PYROLYSIS_OVEN, "pyrolyse : machine " + r.machine().key);
        Gt.check(r.family() == null, "pyrolyse : plus de famille");
        List<RecipeChoice> multi = IndustryRecipes.choices(h.getLevel(), IndustryMachine.COKE_OVEN, true);
        Gt.check(multi.stream().noneMatch(c -> c.id().equals(r.getId())), "pyrolyse encore a la batterie");
        Gt.check(h.getLevel().getRecipeManager().byKey(rl("industry/pyrolysis_oven/pyro_coal")).isPresent(), "pyro_coal absente du four a pyrolyse");
        Gt.check(PiIndustry.controller(IndustryMachine.COKE_OVEN) != null, "batterie de fours a coke");
        Gt.check(PiIndustry.controller(IndustryMachine.PYROLYSIS_OVEN) != null, "four a pyrolyse");
        Gt.check(PioneersAPI.gas(rl("oxygen")) != null, "gaz du socle");
        h.succeed();
    }
}
