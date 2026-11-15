package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.vanilla.VanillaReplacements;
import fr.pioneers.world.geology.DepositType;
import fr.pioneers.world.geology.DepositTypes;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 40 (retours de jeu 5e serie, 5 : recettes et gating de l'age du bronze ; retours 50, 54, 55, 56 ; specification
 * docs/specification-age-bronze-1.0.5.md §5 ; GameTests) :
 * <ul>
 *     <li>REDSTONE seulement par les foreuses : gisement de niveau 1 (foreuse a vapeur), minerai -> 4 poudres au broyeur a vapeur, plus
 *     aucune recette d'autre source ; butins NATURELS sans redstone (coffres, creatures, minerai vanilla), butin d'un bloc pose garde (fil de
 *     redstone) ;</li>
 *     <li>COMPOSANTS DU MKI GARDES : chaque recette chargee d'un composant cle exige sa piece « bronze » (fil d'alliage rouge, boule de
 *     charbon, acier rouge, vis et boulons tournes, toles laminees, caoutchouc extrait, billes, fourreau, carter en fonte) ; les objets faits
 *     seulement en machine n'ont aucune recette d'etabli ni de grille ;</li>
 *     <li>PRESSE = APLATIR : toute recette de la presse (MKI a MKIV, industrielle, a vapeur) aplatit ; une recette partie a une autre machine
 *     y est chargee sous le meme identifiant, et le lot en cours d'une presse industrielle sur elle est RENDU (monde existant) ;</li>
 *     <li>REFRACTAIRE : argile refractaire au melangeur a vapeur (4 et 3 pour 2 argiles), brique crue au compacteur (3 -> 4).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot40Tests {

    /** = T:core/p105_lot40.REDSTONE_SOURCES : seules recettes qui donnent de la poudre de redstone. */
    static final Set<String> REDSTONE_SOURCES = Set.of("bronze/steam/macerator/redstone", "industry/ore_separator/sift_redstone",
          "industry/optical_sorter/sort_redstone_ore", "vanilla_materials/redstone_from_block");
    /** = T:core/p105_lot40.PIECES (composant cle -> au moins une de ces pieces dans chacune de ses recettes). */
    static final Map<String, String[]> PIECES = Map.ofEntries(
          Map.entry("relay", new String[]{"red_alloy_wire"}),
          Map.entry("carbon_resistor", new String[]{"coal_ball"}),
          Map.entry("motor_stator", new String[]{"redsteel_plate"}),
          Map.entry("motor_rotor", new String[]{"redsteel_rod"}),
          Map.entry("circuit_mk1", new String[]{"relay", "carbon_resistor"}),
          Map.entry("electric_motor", new String[]{"motor_stator"}),
          Map.entry("machine_frame_basic", new String[]{"steel_screw", "steel_bolt"}),
          Map.entry("machine_hull_basic", new String[]{"machine_frame_basic"}),
          Map.entry("industrial_casing_basic", new String[]{"steel_sheet"}),
          Map.entry("basic_logistic_cable", new String[]{"natural_rubber"}),
          Map.entry("bearing", new String[]{"steel_balls"}),
          Map.entry("power_supply_unit", new String[]{"copper_coil"}),
          Map.entry("centrifugal_pump", new String[]{"electric_motor", "pump_housing"}),
          Map.entry("cast_iron_valve", new String[]{"cast_iron_housing"}),
          Map.entry("planetary_gear", new String[]{"bearing", "cast_iron_housing"}),
          Map.entry("heat_exchanger", new String[]{"steel_sheet"}),
          Map.entry("piston", new String[]{"honed_cylinder"}),
          Map.entry("steel_frame", new String[]{"steel_screw", "steel_bolt"}),
          Map.entry("dynamo_unit", new String[]{"motor_stator", "motor_rotor"}));
    /** = T:core/p105_lot40.MACHINE_ONLY (sans les alliages rouges, verifies par le lot 39) : jamais a un etabli ni a la grille. */
    static final List<String> MACHINE_ONLY = List.of("natural_rubber", "resin_glue", "steel_sheet", "steel_bolt", "steel_screw", "steel_balls",
          "honed_cylinder", "perforated_board", "cast_iron_housing", "pump_housing", "copper_coil", "coal_ball");
    /** = tools/bronze/press_rules.py : formes plates et exceptions « aplatir » nommees. */
    static final Pattern FLAT = Pattern.compile("_(plate|sheet|foil|leaf|lamina)$");
    static final Set<String> FLAT_EXCEPTIONS = Set.of("pioneers:plywood", "pioneers:epoxy_board", "pioneers:etched_board", "pioneers:perforated_board",
          "pioneers:primitive_plastic");

    private PatchP105Lot40Tests() {
    }

    static ResourceLocation rl(String path) {
        return Pioneers.rl(path);
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    static String id(ItemStack st) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(st.getItem()));
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

    static int count(List<ItemStack> stacks, Item item, boolean unused) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    static boolean asks(Recipe<?> r, Item piece) {
        ItemStack st = new ItemStack(piece);
        for (Ingredient ing : r.getIngredients()) {
            if (!ing.isEmpty() && ing.test(st)) {
                return true;
            }
        }
        return false;
    }

    static BlockState facing(Block block) {
        BlockState st = block.defaultBlockState();
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : st;
    }

    // ------------------------------------------------------------------ 1. redstone (retour 50)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_40_redstone", timeoutTicks = 20)
    public static void p105_40_redstone_foreuse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        DepositType red = null;
        for (DepositType t : DepositTypes.all()) {
            if (t.result() == item("redstone_ore")) {
                red = t;
            }
        }
        Gt.check(red != null && red.tier() == 1, "gisement de redstone de niveau 1 (foreuse a vapeur)");
        //minerai -> 4 poudres au broyeur a vapeur
        var rec = s.level.getRecipeManager().byKey(rl("bronze/steam/macerator/redstone"));
        Gt.check(rec.isPresent() && rec.get() instanceof SteamRecipe, "broyeur a vapeur : minerai de redstone");
        SteamRecipe sr = (SteamRecipe) rec.get();
        Gt.check(sr.machine() == SteamMachine.MACERATOR && sr.inputs().get(0).ingredient().test(new ItemStack(item("redstone_ore"))),
              "broyeur a vapeur : entree minerai de redstone");
        Gt.check(sr.result().is(item("redstone")) && sr.result().getCount() == 4, "minerai de redstone -> 4 poudres");
        //aucune autre source de redstone dans les recettes chargees
        for (Recipe<?> r : s.level.getRecipeManager().getRecipes()) {
            ItemStack out = r.getResultItem(s.level.registryAccess());
            if (!out.isEmpty() && out.is(item("redstone"))) {
                Gt.check(Pioneers.MODID.equals(r.getId().getNamespace()) && REDSTONE_SOURCES.contains(r.getId().getPath()),
                      "source de redstone non permise : " + r.getId());
            }
        }
        //butins : naturels sans redstone (convertie sinon), bloc pose garde
        for (String table : new String[]{"chests/simple_dungeon", "chests/abandoned_mineshaft", "entities/witch", "blocks/redstone_ore",
              "gameplay/hero_of_the_village/cleric_gift"}) {
            List<ItemStack> loot = new ArrayList<>(List.of(new ItemStack(Items.REDSTONE, 3), new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.IRON_INGOT, 2)));
            VanillaReplacements.filterLoot(loot, ResourceLocation.parse("minecraft:" + table));
            Gt.equal(0, count(loot, item("redstone"), true) + count(loot, Items.REDSTONE, true), "butin " + table + " : plus de redstone");
            Gt.equal(2, count(loot, item("iron_ingot"), true), "butin " + table + " : le reste est converti");
        }
        List<ItemStack> wire = new ArrayList<>(List.of(new ItemStack(Items.REDSTONE, 1)));
        VanillaReplacements.filterLoot(wire, ResourceLocation.parse("minecraft:blocks/redstone_wire"));
        Gt.equal(1, count(wire, item("redstone"), true), "fil de redstone pose : la poudre est rendue (convertie)");
        s.done();
    }

    // ------------------------------------------------------------------ 2. composants du MKI gardes (retour 54)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_40_gating", timeoutTicks = 20)
    public static void p105_40_composants_mki_gardes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (Map.Entry<String, String[]> e : PIECES.entrySet()) {
            Item comp = item(e.getKey());
            int n = 0;
            for (Recipe<?> r : s.level.getRecipeManager().getRecipes()) {
                ItemStack out = r.getResultItem(s.level.registryAccess());
                if (out.isEmpty() || !out.is(comp)) {
                    continue;
                }
                n++;
                boolean ok = false;
                for (String p : e.getValue()) {
                    ok |= asks(r, item(p));
                }
                Gt.check(ok, e.getKey() + " : la recette " + r.getId() + " ne demande aucune piece de l'age du bronze");
            }
            Gt.check(n > 0, e.getKey() + " : aucune recette");
        }
        for (String m : MACHINE_ONLY) {
            Item it = item(m);
            boolean steam = false;
            for (Recipe<?> r : s.level.getRecipeManager().getRecipes()) {
                ItemStack out = r.getResultItem(s.level.registryAccess());
                if (out.isEmpty() || !out.is(it)) {
                    continue;
                }
                Gt.check(!(r instanceof fr.pioneers.tech.bronze.bench.BenchRecipe) && !(r instanceof CraftingRecipe)
                      && !(r instanceof fr.pioneers.tech.bronze.foundry.KilnRecipe), m + " : recette a la main " + r.getId());
                steam |= r instanceof SteamRecipe;
            }
            Gt.check(steam, m + " : une recette en machine a vapeur (tier 1)");
        }
        s.done();
    }

    // ------------------------------------------------------------------ 3. presse = aplatir (retour 56)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_40_press", timeoutTicks = 20)
    public static void p105_40_presse_aplatit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int n = 0;
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, IndustryMachine.PRESS)) {
            String out = r.results().isEmpty() ? "?" : id(r.results().get(0).stack());
            Gt.check(FLAT.matcher(out).find() || FLAT_EXCEPTIONS.contains(out), "presse : recette qui n'aplatit pas : " + r.getId() + " -> " + out);
            n++;
        }
        Gt.check(n >= 40, "presse : " + n + " recettes (les plaques, toles, feuilles et stratifies restent)");
        for (SteamRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiBronze.STEAM_PROCESSING.get())) {
            if (r.machine() == SteamMachine.PRESS) {
                String out = id(r.result());
                Gt.check(FLAT.matcher(out).find() || FLAT_EXCEPTIONS.contains(out), "presse a vapeur : " + r.getId() + " -> " + out);
            }
        }
        //une recette partie de la presse garde son identifiant chez sa machine (tiges a la matrice : extrudeuse)
        ResourceLocation rod = rl("industry/press/press_rod_nickel");
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.EXTRUDER, rod) != null, "tige de nickel a la matrice : a l'extrudeuse");
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.PRESS, rod) == null, "tige de nickel a la matrice : plus a la presse");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_40_press_refund", timeoutTicks = 20)
    public static void p105_40_presse_lot_rendu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, facing(PiIndustry.CONTROLLERS.get(IndustryMachine.PRESS).getBlock()));
        ControllerTile ctrl = (ControllerTile) h.getBlockEntity(at);
        Gt.check(ctrl != null, "controleur de presse industrielle pose");
        //monde existant : lot de 2 operations de tiges a la matrice (entrees deja prises) : rendues dans les sorties
        Gt.check(fr.pioneers.tech.industry.multi.BatchRefund.of(ctrl, rl("industry/press/press_rod_nickel"), 2), "lot de tiges rendu");
        Gt.equal(2, count(ctrl.itemOutputs(), item("nickel_ingot")), "2 operations : 2 lingots de nickel rendus");
        Gt.equal(2, count(ctrl.itemOutputs(), item("rod_die")), "2 operations : 2 matrices a tige rendues");
        Gt.check(!fr.pioneers.tech.industry.multi.BatchRefund.of(ctrl, rl("industry/press/press_plate_nickel"), 1), "plaque a la presse : rien a rendre");
        Gt.check(fr.pioneers.tech.industry.multi.BatchRefund.of(ctrl, rl("automation/potter/bronze_bench_unfired_fire_brick"), 3),
              "copie retiree de la brique crue : lot rendu");
        Gt.equal(6, count(ctrl.itemOutputs(), item("fire_clay")), "3 operations : 6 argiles refractaires rendues");
        s.done();
    }

    // ------------------------------------------------------------------ 4. refractaire (retour 55)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_40_refractory", timeoutTicks = 20)
    public static void p105_40_refractaire(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        var a = s.level.getRecipeManager().byKey(rl("bronze/steam/mixer/mason_fire_clay"));
        Gt.check(a.isPresent() && a.get() instanceof SteamRecipe, "melangeur a vapeur : argile refractaire (silex)");
        SteamRecipe ra = (SteamRecipe) a.get();
        Gt.check(ra.result().is(item("fire_clay")) && ra.result().getCount() == 4 && ra.water() > 0, "2 argiles + sable + silex + eau -> 4");
        var b = s.level.getRecipeManager().byKey(rl("bronze/steam/mixer/mason_fire_clay_sand"));
        Gt.check(b.isPresent() && ((SteamRecipe) b.get()).result().getCount() == 3, "2 argiles + 2 sables + eau -> 3");
        var c = s.level.getRecipeManager().byKey(rl("bronze/steam/compressor/unfired_fire_brick"));
        Gt.check(c.isPresent() && c.get() instanceof SteamRecipe, "compacteur a vapeur : brique refractaire crue");
        SteamRecipe rc = (SteamRecipe) c.get();
        Gt.check(rc.machine() == SteamMachine.COMPRESSOR && rc.inputs().get(0).count() == 3 && rc.result().getCount() == 4, "3 argiles refractaires -> 4");
        //la main reste : 2 argiles refractaires -> 1 brique crue au potier
        Gt.check(s.level.getRecipeManager().byKey(rl("bronze/bench/unfired_fire_brick")).isPresent(), "potier : brique crue a la main gardee");
        s.done();
    }
}
