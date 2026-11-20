package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.star.GeneratedStars;
import fr.pioneers.tech.industry.star.Stars;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 12 : les MULTIBLOCS EXTRAORDINAIRES (★). Leur table (etapes dont les parts font la duree entiere,
 * rendu, centre de l'effet dans le gabarit sculpte) et un extraordinaire bati (l'accelerateur annulaire) qui se forme et fait sa recette
 * en grand : six cellules de deuterium enrichi d'un coup.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot12Tests {

    private PatchP107Lot12Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_12_table", timeoutTicks = 20)
    public static void p107_12_table_des_extraordinaires(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(GeneratedStars.ALL.length >= 5, "extraordinaires : " + GeneratedStars.ALL.length);
        for (GeneratedStars.Star st : GeneratedStars.ALL) {
            Gt.check(IndustryMachine.byKey(st.key()) != null, st.key() + " : machine absente du catalogue");
            float sum = 0;
            for (float x : st.stages()) {
                sum += x;
            }
            Gt.check(Math.abs(sum - 1) < 1e-4F && st.stages().length >= 2, st.key() + " : etapes");
            Gt.equal(0, Stars.stage(st, 0), st.key() + " : etape au depart");
            Gt.equal(st.stages().length - 1, Stars.stage(st, 0.999), st.key() + " : etape a la fin");
        }
        s.done();
    }

    /** Chaque extraordinaire bati (toute la structure MKV) se forme : sinon son controleur reste au tier 0 et son effet ne se dessine pas. */
    @GameTest(template = Gt.TALL, batch = "pioneers_p107_12_formed", timeoutTicks = 400)
    public static void p107_12_extraordinaires_se_forment(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        StringBuilder bad = new StringBuilder();
        formNext(h, s, 0, bad);
    }

    private static void formNext(GameTestHelper h, Gt.Scope s, int i, StringBuilder bad) {
        if (i >= GeneratedStars.ALL.length) {
            Gt.check(bad.length() == 0, "extraordinaires qui ne se forment pas :" + bad);
            s.done();
            return;
        }
        String key = GeneratedStars.ALL[i].key();
        ControllerTile tile = PatchP107Lot4Tests.build(h, IndustryMachine.byKey(key), true);
        h.runAfterDelay(8, () -> {
            var st = tile.getBlockState();
            int style = st.hasProperty(fr.pioneers.tech.industry.multi.IndustryStyle.TIER)
                        ? st.getValue(fr.pioneers.tech.industry.multi.IndustryStyle.TIER) : -1;
            if (!tile.formed() || style <= 0) {
                bad.append(' ').append(key).append(" (forme ").append(tile.formed()).append(", tier ").append(tile.tier()).append(", style ")
                   .append(style).append(", ").append(tile.guiValue(5)).append(')');
            }
            for (int x = 0; x < 32; x++) {
                for (int y = 1; y < 32; y++) {
                    for (int z = 0; z < 32; z++) {
                        h.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.AIR);
                    }
                }
            }
            h.runAfterDelay(2, () -> formNext(h, s, i + 1, bad));
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_12_accelerator", timeoutTicks = 1200)
    public static void p107_12_accelerateur_fait_six_cellules(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        IndustryMachine m = IndustryMachine.byKey("annular_accelerator");
        ControllerTile tile = PatchP107Lot4Tests.build(h, m, true);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "accelerateur annulaire : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            IndustrialRecipe cell = null;
            for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), tile.machine)) {
                if (r.getId().getPath().endsWith("aa_deuterium_cell")) {
                    cell = r;
                }
            }
            Gt.check(cell != null, "recette des cellules de deuterium");
            var inputs = tile.itemInputs();
            int n = 0;
            for (var ing : cell.items()) {
                ItemStack[] options = ing.ingredient().getItems();
                Gt.check(options.length > 0, "entree vide");
                inputs.get(n++).setStack(new ItemStack(options[0].getItem(), ing.count()));
            }
            tile.gasInTanks().get(0).setStack(GasStack.of(PioneersAPI.gas(new ResourceLocation("pioneers", "deuterium")), 24_000));
            h.onEachTick(() -> tile.getEnergy().setStored(tile.getEnergy().getCapacity()));
            h.succeedWhen(() -> {
                int made = 0;
                for (var slot : tile.itemOutputs()) {
                    if (ForgeRegistries.ITEMS.getKey(slot.getStack().getItem()).toString().equals("pioneers:enriched_deuterium_cell")) {
                        made += slot.getStack().getCount();
                    }
                }
                Gt.equal(6, made, "cellules de deuterium enrichi");
                s.done();
            });
        });
    }
}
