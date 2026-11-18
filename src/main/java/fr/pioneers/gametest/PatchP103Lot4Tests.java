package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.machine.ProcessingRecipe;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.3-beta, lot 4 (GameTests) : les voies des pieces metalliques de Guillaume (tools/gc/industry/parts_p103.py). Quelques voies
 * relues sur les recettes chargees (etabli, tréfileuse, compacteur, presse electrique ; plus de coulee de plaque de fer ni d'etabli pour
 * l'aluminium) ; l'extrudeuse a formes en vrai : 1 lingot de fer + forme « tige » = 2 tiges au MKI, 3 au MKII ; la presse electrique sans
 * fluide.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot4Tests {

    private PatchP103Lot4Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    private static AtelierTile atelier(GameTestHelper h, BlockPos rel, IndustryMachine machine, CableTier tier) {
        BlockState st = PiIndustry.ATELIERS.get(machine).get(tier).getBlock().defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier en " + rel);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
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

    private static IndustrialRecipe industrial(MinecraftServer server, String path) {
        Recipe<?> r = server.getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof IndustrialRecipe, "recette industrielle absente : " + path);
        return (IndustrialRecipe) r;
    }

    // ================================================================== voies relues sur les recettes chargees
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_4_routes", timeoutTicks = 20)
    public static void p103_4_voies_des_pieces(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        MinecraftServer server = h.getLevel().getServer();
        Recipe<?> rod = server.getRecipeManager().byKey(rl("components_bench/iron_rod_from_ingot")).orElse(null);
        Gt.check(rod instanceof BenchRecipe b && b.ingredients().size() == 1 && b.ingredients().get(0).count() == 1 && b.result().getCount() == 1,
              "etabli : marteau + 1 lingot de fer -> 1 tige");
        Recipe<?> gear = server.getRecipeManager().byKey(rl("components_bench/iron_gear_from_blank")).orElse(null);
        Gt.check(gear instanceof BenchRecipe b && b.tools().size() == 2 && b.ingredients().size() == 2, "etabli : ebauche + 3 plaques, marteau et lime");
        Gt.check(server.getRecipeManager().byKey(rl("components_bench/aluminium_plate_from_ingots")).isEmpty(),
              "l'aluminium ne se travaille plus a l'etabli");
        //1.0.5-beta (lot 43, regle permanente du cout matiere) : 1 lingot = 144 mB = 2 fils de 72 mB, jamais plus ; 1.0.6 : la copie MKI est
        //desactivee (doublon), la trefileuse MKI fait la recette a vapeur
        Gt.check(server.getRecipeManager().byKey(rl("bronze/steam/wiremill/wire_copper")).orElse(null) instanceof fr.pioneers.tech.bronze.machines.SteamRecipe w
              && w.result().getCount() == 2, "trefileuse : 1 lingot de cuivre -> 2 fils");
        IndustrialRecipe rodWire = industrial(server, "industry/wiremill/wire_copper");
        Gt.equal(1, rodWire.tier(), "trefileuse : tige -> 2 fils au MKII");
        //1.0.6 (une machine par transformation de forme) : la forme de l'anneau est retiree (Cintreuse) ; un objet deja fabrique devient la
        //filiere d'extrusion vierge. Demande de Guillaume du 17/11/2026 : l'extrudeuse garde une voie generique, un peu moins rentable, pour
        //la plaque (1 lingot), le fil (1 lingot -> 2) et l'ebauche d'engrenage (5 lingots)
        Gt.check(server.getRecipeManager().byKey(rl("industry/extruder/extrude_ring_steel")).isEmpty(), "plus d'anneau extrude");
        IndustrialRecipe extrGear = industrial(server, "industry/extruder/extrude_gear_blank_iron");
        Gt.equal(5, extrGear.items().get(0).count(), "ebauche d'engrenage extrudee : 5 lingots");
        Gt.check(server.getRecipeManager().byKey(rl("industry/power_hammer/forge_billet_gear_blank_iron")).isEmpty(), "plus de billette -> 2 ebauches");
        Gt.equal(1, industrial(server, "industry/gear_hobber/hob_gear_iron").results().size(), "decoupeuse : l'engrenage sans pepites");
        Gt.equal(2, industrial(server, "industry/extruder/extrude_wire_copper").results().get(0).stack().getCount(), "fil extrude : 1 lingot -> 2");
        Gt.equal(1, industrial(server, "industry/extruder/extrude_plate_iron").results().get(0).stack().getCount(), "plaque extrudee : 1 lingot -> 1");
        Gt.check(!ForgeRegistries.ITEMS.containsKey(rl("extrusion_shape_ring")), "forme anneau retiree du registre");
        Gt.equal("extrusion_shape_rod", fr.pioneers.core.common.PiRemaps.simplifiedItem("extrusion_shape_ring"), "forme anneau -> forme tige");
        //17/11/2026 (Guillaume) : filiere vierge supprimee (-> forme tige), forme engrenage -> forme ebauche d'engrenage
        Gt.check(!ForgeRegistries.ITEMS.containsKey(rl("extrusion_die")), "filiere vierge retiree du registre");
        Gt.equal("extrusion_shape_rod", fr.pioneers.core.common.PiRemaps.simplifiedItem("extrusion_die"), "filiere vierge -> forme tige");
        Gt.equal("extrusion_shape_gear_blank", fr.pioneers.core.common.PiRemaps.simplifiedItem("extrusion_shape_gear"), "forme engrenage -> forme ebauche");
        IndustrialRecipe press = industrial(server, "industry/press/press_sheet_steel");
        //1.0.6 (reponse de Guillaume du 16/11/2026) : 2 toles par plaque, comme la calandre (tableau du lot 4 : 1 avant)
        Gt.equal(2, press.results().get(0).stack().getCount(), "presse electrique : 1 plaque -> 2 toles");
        //1.0.6 (Guillaume, 17/11/2026 : « homogeneise [la coulee] a tous les metaux et leurs composants ») : la plaque de fer se coule de
        //nouveau (voie « casting » du tableau, parts_p103.ROUTES), matrice rendue
        Gt.check(industrial(server, "industry/casting_machine/cast_plate_iron").results().get(0).stack().is(item("iron_plate")),
              "plaque de fer coulee en machine (voie de coulee du tableau)");
        Recipe<?> slab = server.getRecipeManager().byKey(rl("compressing/slab_iron")).orElse(null);
        Gt.check(slab instanceof ProcessingRecipe p && p.getResult().is(item("iron_slab")), "compacteur : bloc de fer -> brame");
        Recipe<?> billet = server.getRecipeManager().byKey(rl("compressing/billet_iron")).orElse(null);
        Gt.check(billet instanceof ProcessingRecipe p && p.getResult().is(item("iron_billet")), "compacteur : brame -> billette");
        Gt.equal(9, industrial(server, "industry/rolling_mill/hot_roll_wire_rod_iron").results().get(0).stack().getCount(),
              "laminoir : billette -> 9 fils machine");
        //presse electrique : ni fluide ni gaz (l'ancienne presse hydraulique n'en avait pas non plus)
        Gt.equal(0, IndustryMachine.PRESS.fluidsIn + IndustryMachine.PRESS.gasesIn, "presse electrique sans fluide");
        s.done();
    }

    // ================================================================== l'extrudeuse a formes en vrai : 2 tiges au MKI, 3 au MKII
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_4_extruder", timeoutTicks = 1600)
    public static void p103_4_extrudeuse_forme_tige(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile mk1 = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.EXTRUDER, CableTier.BASIC);
        AtelierTile mk2 = atelier(h, new BlockPos(5, 2, 2), IndustryMachine.EXTRUDER, CableTier.ADVANCED);
        for (AtelierTile m : new AtelierTile[]{mk1, mk2}) {
            Gt.check(m.setLockedRecipe(rl("industry/extruder/extrude_rod_iron")), "extrusion de la tige de fer absente");
            m.itemInputs().get(0).setStack(new ItemStack(item("iron_ingot"), 1));
            Gt.tool(m, new ItemStack(item("extrusion_shape_rod"), 1));   // 1.0.5-beta (lot 44) : case Outil
        }
        h.startSequence()
              .thenWaitUntil(() -> {
                  mk1.getEnergy().setStored(mk1.getEnergy().getCapacity());
                  mk2.getEnergy().setStored(mk2.getEnergy().getCapacity());
                  Gt.check(count(mk1, item("iron_rod")) > 0 && count(mk2, item("iron_rod")) > 0, "extrusion en cours");
              })
              .thenExecute(() -> {
                  Gt.equal(2, count(mk1, item("iron_rod")), "MKI : 1 lingot + forme tige = 2 tiges");
                  //1.0.5-beta (lot 43) : aucun rendement de tier sur une forme du metal (144 mB = 2 tiges de 72 mB a tout tier)
                  Gt.equal(2, count(mk2, item("iron_rod")), "MKII : 1 lingot + forme tige = 2 tiges (pas de rendement de tier)");
                  Gt.check(mk1.itemInputs().get(0).getStack().isEmpty(), "le lingot est consomme");
              })
              .thenExecute(s::done);
    }
}
