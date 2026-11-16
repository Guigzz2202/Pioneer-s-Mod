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
        IndustrialRecipe wire = industrial(server, "industry/wiremill/wire_ingot_copper");
        //1.0.5-beta (lot 43, regle permanente du cout matiere) : 1 lingot = 144 mB = 2 fils de 72 mB, jamais plus
        Gt.equal(2, wire.results().get(0).stack().getCount(), "trefileuse : 1 lingot de cuivre -> 2 fils");
        IndustrialRecipe rodWire = industrial(server, "industry/wiremill/wire_copper");
        Gt.equal(1, rodWire.tier(), "trefileuse : tige -> 2 fils au MKII");
        IndustrialRecipe ring = industrial(server, "industry/extruder/extrude_ring_steel");
        Gt.equal(1, ring.tier(), "extrudeuse : forme anneau au MKII");
        Gt.check(ring.items().stream().anyMatch(c -> c.ingredient().test(new ItemStack(item("extrusion_shape_ring")))), "forme anneau en entree");
        Gt.equal(2, industrial(server, "industry/extruder/extrude_gear_iron").tier(), "extrudeuse : engrenage au MKIII");
        IndustrialRecipe press = industrial(server, "industry/press/press_sheet_steel");
        Gt.equal(1, press.results().get(0).stack().getCount(), "presse electrique : 1 plaque -> 1 tole");
        Gt.check(server.getRecipeManager().byKey(rl("industry/casting_machine/cast_plate_iron")).isEmpty(), "plus de plaque de fer coulee en machine");
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
