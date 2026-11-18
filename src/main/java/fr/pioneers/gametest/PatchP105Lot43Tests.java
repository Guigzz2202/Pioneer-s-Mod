package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.BatchRefund;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 43 (retours de jeu 6e serie, 1 : rentabilite des metaux ; retours 68 a 71 et 73, et reponse de Guillaume du
 * 14/11 17:31 « seul le blastfurnace doit produire de l'acier » ; controle tools/check_p105_43.py ; GameTests) :
 * <ul>
 *     <li>BOULONS (retour 71) : centre d'usinage 1 tige d'acier -> 4 boulons (toute la tige), tour a vapeur 1 tige -> 3 boulons + 1 pepite
 *     (vrai tour a vapeur, recette verrouillee), frappe a froid 1 fil machine -> 8 ; jamais a un etabli ni a la grille ;</li>
 *     <li>POUTRELLE EN MACHINE (retour 68) : laminoir a vapeur 1 lingot d'acier -> 1 poutrelle (vrai laminoir a vapeur), meme recette au
 *     laminoir MKI (famille « laminage », sans rendement de famille sur une forme du metal), soudeuse 3 plaques -> 2 poutrelles + 9 pepites,
 *     etabli du metallier 2 plaques -> 1 ;</li>
 *     <li>CHASSIS ET CADRE (retours 69 et 70) : une seule recette du chassis en acier a l'assembleur (la soudee), lot en cours de la recette
 *     retiree rendu ; cadre en acier : 1 a l'etabli de montage, 2 a l'assembleur pour les memes pieces ;</li>
 *     <li>ACIER = HAUT FOURNEAU : ni le convertisseur ni le four a arc ne font d'acier ordinaire ; l'affinage de la fonte (thomas_6) et le
 *     soufflage a l'oxygene (oxygen_casting_7) sont au haut fourneau sous le meme identifiant ; lot en cours d'un convertisseur ou d'un
 *     four a arc sur une recette d'acier : entrees rendues ; les deux machines gardent leurs autres recettes (aucun bloc retire).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot43Tests {

    private PatchP105Lot43Tests() {
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

    static SteamProcessTile steam(GameTestHelper h, BlockPos at, SteamMachine m) {
        h.setBlock(at, PiBronze.STEAM_MACHINES.get(m).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof SteamProcessTile, "machine a vapeur posee : " + m.id());
        return (SteamProcessTile) h.getBlockEntity(at);
    }

    static ControllerTile controller(GameTestHelper h, IndustryMachine machine, BlockPos at) {
        h.setBlock(at, facing(PiIndustry.CONTROLLERS.get(machine).getBlock()));
        Gt.check(h.getBlockEntity(at) instanceof ControllerTile, "controleur pose : " + machine);
        return (ControllerTile) h.getBlockEntity(at);
    }

    /** Recette industrielle : une seule sortie (l'objet, le nombre), aucune autre. */
    static void only(IndustrialRecipe r, String what, Item out, int n) {
        Gt.check(r != null, what + " : recette absente");
        Gt.equal(1, r.results().size(), what + " : une seule sortie (" + r.results() + ")");
        Gt.check(r.results().get(0).stack().is(out) && r.results().get(0).stack().getCount() == n, what + " : " + n + " x " + out);
    }

    static int resultCount(IndustrialRecipe r, Item out) {
        int n = 0;
        for (IndustrialRecipe.Result res : r.results()) {
            if (res.stack().is(out) && res.chance() >= 1F) {
                n += res.stack().getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 1. boulons et vis (retour 71)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_43_bolts", timeoutTicks = 20)
    public static void p105_43_boulons_centre_usinage(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item rod = item("steel_rod"), bolt = item("steel_bolt"), screw = item("steel_screw"), nugget = item("steel_nugget");
        //centre d'usinage : 1 tige (72 mB) -> 4 boulons (4 x 18 mB), plus 2 boulons + 2 pepites
        IndustrialRecipe mc = IndustryRecipes.byId(s.level, IndustryMachine.MACHINING_CENTER, rl("industry/machining_center/bolt_steel"));
        only(mc, "centre d'usinage, boulons", bolt, 4);
        Gt.check(mc.items().size() == 1 && mc.items().get(0).count() == 1 && mc.items().get(0).ingredient().test(new ItemStack(rod)),
              "centre d'usinage : 1 tige d'acier");
        //vis au centre d'usinage : 1.0.6 (Guillaume, 18/11/2026) : la recette du tour a vapeur reprise au MKI (1 boulon -> 1 vis, toute la
        //matiere), plus de 2 pour 2 propre au centre d'usinage
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.MACHINING_CENTER, rl("industry/machining_center/screw_steel")) == null,
              "centre d'usinage : plus de recette 2 boulons -> 2 vis");
        IndustrialRecipe sc = null;
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, IndustryMachine.MACHINING_CENTER)) {
            if (!r.results().isEmpty() && r.results().get(0).stack().is(screw)) {
                sc = r;
            }
        }
        only(sc, "centre d'usinage, vis", screw, 1);
        //frappe a froid (marteau-pilon, famille) : 1 fil machine -> 8 boulons
        IndustrialRecipe ch = IndustryRecipes.byId(s.level, IndustryMachine.POWER_HAMMER, rl("industry/cold_header/head_bolt_steel"));
        only(ch, "frappe a froid", bolt, 8);
        //tour a vapeur, pour de vrai : 1 tige -> 3 boulons + 1 pepite rendue
        SteamProcessTile lathe = steam(h, new BlockPos(2, 1, 2), SteamMachine.LATHE);
        var lr = s.level.getRecipeManager().byKey(rl("bronze/steam/lathe/steel_bolt"));
        Gt.check(lr.isPresent() && lr.get() instanceof SteamRecipe, "tour a vapeur : recette des boulons");
        SteamRecipe sr = (SteamRecipe) lr.get();
        Gt.check(sr.result().is(bolt) && sr.result().getCount() == 3 && sr.byproduct().is(nugget) && sr.byproduct().getCount() == 1
              && sr.chance() >= 1F, "tour a vapeur : 3 boulons + 1 pepite");
        lathe.recipeLock().set(rl("bronze/steam/lathe/steel_bolt"));
        lathe.inputs().get(0).setStack(new ItemStack(rod, 1));
        Gt.check(lathe.process().craftNow(), "tour a vapeur : tige tournee");
        Gt.equal(0, lathe.inputs().get(0).getStack().getCount(), "tour a vapeur : la tige est prise");
        Gt.equal(3, count(lathe.guiSlots(), bolt), "tour a vapeur : 3 boulons");
        Gt.equal(1, count(lathe.guiSlots(), nugget), "tour a vapeur : 1 pepite de chute");
        //jamais a la main (lot 40, retour 54) : ni etabli ni grille
        for (Recipe<?> r : s.level.getRecipeManager().getRecipes()) {
            ItemStack out = r.getResultItem(s.level.registryAccess());
            if (!out.isEmpty() && (out.is(bolt) || out.is(screw))) {
                Gt.check(!(r instanceof BenchRecipe) && !(r instanceof CraftingRecipe), "boulon ou vis d'acier a la main : " + r.getId());
            }
        }
        s.done();
    }

    // ------------------------------------------------------------------ 2. poutrelle en machine (retour 68)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_43_beam", timeoutTicks = 20)
    public static void p105_43_poutrelle_en_machine(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item ingot = item("steel_ingot"), beam = item("steel_beam"), nugget = item("steel_nugget");
        //laminoir a vapeur (T1), pour de vrai : 1 lingot -> 1 poutrelle
        var br = s.level.getRecipeManager().byKey(rl("bronze/steam/rolling_mill/steel_beam"));
        Gt.check(br.isPresent() && br.get() instanceof SteamRecipe, "laminoir a vapeur : recette de la poutrelle");
        SteamRecipe sr = (SteamRecipe) br.get();
        Gt.check(sr.machine() == SteamMachine.ROLLING_MILL && sr.result().is(beam) && sr.result().getCount() == 1 && sr.byproduct().isEmpty(),
              "laminoir a vapeur : 1 lingot -> 1 poutrelle");
        SteamProcessTile mill = steam(h, new BlockPos(2, 1, 2), SteamMachine.ROLLING_MILL);
        mill.recipeLock().set(rl("bronze/steam/rolling_mill/steel_beam"));
        mill.inputs().get(0).setStack(new ItemStack(ingot, 2));
        Gt.check(mill.process().craftNow(), "laminoir a vapeur : lingot lamine");
        Gt.equal(1, mill.inputs().get(0).getStack().getCount(), "laminoir a vapeur : un lingot pris");
        Gt.equal(1, count(mill.guiSlots(), beam), "laminoir a vapeur : une poutrelle");
        //laminoir MKI : la meme recette par la famille « laminage », sans rendement de famille (forme du metal)
        IndustrialRecipe conv = null;
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, IndustryMachine.ROLLING_MILL)) {
            if (r.converted() && r.getId().equals(rl("bronze/steam/rolling_mill/steel_beam"))) {
                conv = r;
            }
        }
        Gt.check(conv != null, "laminoir MKI : poutrelle du laminoir a vapeur recue par la famille");
        Gt.check(Math.abs(conv.tierYield(0) - 1.0) < 1e-9 && Math.abs(conv.tierYield(3) - 1.0) < 1e-9,
              "laminoir MKI a MKIV : 1 poutrelle par lingot, jamais plus (aucun rendement sur une forme du metal)");
        //soudeuse (assembleur, famille arc_welder) : 3 plaques -> 2 poutrelles + 9 pepites (le 3e lingot revient)
        IndustrialRecipe weld = IndustryRecipes.byId(s.level, IndustryMachine.ASSEMBLER, rl("industry/arc_welder/weld_beam_steel"));
        Gt.check(weld != null, "soudeuse : recette de la poutrelle");
        Gt.equal(2, resultCount(weld, beam), "soudeuse : 2 poutrelles");
        Gt.equal(9, resultCount(weld, nugget), "soudeuse : 9 pepites de chute");
        //etabli du metallier : 2 plaques -> 1 poutrelle (50 %, moins rentable que les machines)
        var bench = s.level.getRecipeManager().byKey(rl("components/bench/form_steel_beam"));
        Gt.check(bench.isPresent() && bench.get() instanceof BenchRecipe && bench.get().getResultItem(s.level.registryAccess()).getCount() == 1,
              "etabli du metallier : 1 poutrelle");
        s.done();
    }

    // ------------------------------------------------------------------ 3. chassis et cadre en acier (retours 69 et 70)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_43_frames", timeoutTicks = 20)
    public static void p105_43_chassis_et_cadre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item frame = item("machine_frame_basic"), steelFrame = item("steel_frame");
        //chassis : une seule recette a l'assembleur, la soudee (4 plaques, 4 tiges, 2 vis -> 2)
        int n = 0;
        for (IndustrialRecipe r : IndustryRecipes.of(s.level, IndustryMachine.ASSEMBLER)) {
            if (!r.results().isEmpty() && r.results().get(0).stack().is(frame)) {
                n++;
                Gt.equal(rl("industry/arc_welder/rc_weld"), r.getId(), "chassis en acier : seule la recette soudee reste");
                Gt.equal(2, resultCount(r, frame), "chassis soude : 2 chassis");
            }
        }
        Gt.equal(1, n, "chassis en acier : une recette a l'assembleur");
        Gt.check(s.level.getRecipeManager().byKey(rl("industry/component_assembler/rc_machine_frame_basic_1")).isEmpty(),
              "recette des composants retiree");
        //monde existant : lot en cours sur la recette retiree -> entrees rendues
        ControllerTile asm = controller(h, IndustryMachine.ASSEMBLER, new BlockPos(3, 2, 3));
        Gt.check(BatchRefund.of(asm, rl("industry/component_assembler/rc_machine_frame_basic_1"), 1), "lot du chassis retire rendu");
        //2 cases de sortie a l'assembleur : plaques et tiges (premiers objets du mod de leurs tags) dedans, vis et boulons au sol
        Gt.equal(4, count(asm.itemOutputs(), item("steel_plate")), "4 plaques rendues");
        Gt.equal(4, count(asm.itemOutputs(), item("steel_rod")), "4 tiges rendues");
        //cadre en acier : etabli de montage 1, assembleur 2 pour les memes pieces
        var bench = s.level.getRecipeManager().byKey(rl("industry/crafting/steel_frame"));
        Gt.check(bench.isPresent() && bench.get() instanceof BenchRecipe, "etabli de montage : cadre en acier");
        Gt.equal(1, bench.get().getResultItem(s.level.registryAccess()).getCount(), "etabli de montage : 1 cadre");
        IndustrialRecipe copy = IndustryRecipes.byId(s.level, IndustryMachine.ASSEMBLER, rl("automation/assembly/industry_crafting_steel_frame"));
        Gt.check(copy != null, "assembleur : copie du cadre en acier");
        Gt.equal(2, resultCount(copy, steelFrame), "assembleur : 2 cadres");
        Gt.equal(bench.get().getIngredients().size(), copy.items().size(), "memes pieces a l'etabli et a l'assembleur");
        s.done();
    }

    // ------------------------------------------------------------------ 4. acier = haut fourneau (reponse de Guillaume du 14/11)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_43_steel", timeoutTicks = 20)
    public static void p105_43_acier_haut_fourneau(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Item[] steel = {item("steel_ingot"), item("steel_nugget"), item("steel_dust"), item("steel_block"), item("crude_steel"),
              item("steel_billet"), item("steel_slab")};
        for (IndustryMachine m : new IndustryMachine[]{IndustryMachine.CONVERTER, IndustryMachine.ARC_FURNACE}) {
            List<IndustrialRecipe> all = IndustryRecipes.of(s.level, m);
            Gt.check(!all.isEmpty(), m + " : garde ses autres recettes (aucun bloc retire)");
            for (IndustrialRecipe r : all) {
                for (IndustrialRecipe.Result res : r.results()) {
                    for (Item it : steel) {
                        Gt.check(!res.stack().is(it), m + " : fait encore de l'acier ordinaire : " + r.getId());
                    }
                }
            }
        }
        //memes identifiants, au haut fourneau
        IndustrialRecipe thomas = IndustryRecipes.byId(s.level, IndustryMachine.BLAST_FURNACE, rl("processes/steel/thomas_6"));
        Gt.check(thomas != null && resultCount(thomas, item("steel_ingot")) == 4, "haut fourneau : fonte affinee (4 lingots d'acier)");
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.CONVERTER, rl("processes/steel/thomas_6")) == null, "plus au convertisseur");
        IndustrialRecipe oxy = IndustryRecipes.byId(s.level, IndustryMachine.BLAST_FURNACE, rl("processes/steel/oxygen_casting_7"));
        Gt.check(oxy != null && oxy.tier() == 0, "haut fourneau MKI : soufflage a l'oxygene (scorie vanadifere)");
        for (String gone : new String[]{"processes/steel/arc_scrap_2", "processes/steel/arc_scrap_2b", "processes/steel/oxygen_casting_9",
              "processes/steel/oxygen_casting_9b", "industry/converter/scrap_cooled_converter"}) {
            Gt.check(s.level.getRecipeManager().byKey(rl(gone)).isEmpty(), "recette d'acier retiree : " + gone);
        }
        //mondes existants : lots en cours rendus
        ControllerTile conv = controller(h, IndustryMachine.CONVERTER, new BlockPos(2, 2, 2));
        Gt.check(BatchRefund.of(conv, rl("processes/steel/thomas_6"), 2), "convertisseur : lot Thomas rendu");
        Gt.equal(8, count(conv.itemOutputs(), item("pig_iron")), "convertisseur : 8 fontes rendues");
        Gt.equal(2, count(conv.itemOutputs(), item("quicklime")), "convertisseur : 2 chaux rendues");
        ControllerTile arc = controller(h, IndustryMachine.ARC_FURNACE, new BlockPos(5, 2, 5));
        Gt.check(BatchRefund.of(arc, rl("processes/steel/arc_scrap_2"), 1), "four a arc : lot de ferraille rendu");
        Gt.equal(5, count(arc.itemOutputs(), item("iron_scrap_bale")), "four a arc : 5 balles rendues");
        s.done();
    }
}
