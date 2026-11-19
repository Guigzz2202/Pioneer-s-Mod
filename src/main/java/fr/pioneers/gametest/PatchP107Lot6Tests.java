package fr.pioneers.gametest;

import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.mining.SpaceDrill;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.MkvCoreSlot;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.7 « Plus loin et au-dela », lot 6 (Venus et la foreuse spatiale) :
 * <ul>
 *     <li>le Grand Forage sur Venus, son port et ses recettes uniques ; la fabrication de la foreuse spatiale demande le mat de forage
 *     colossal (Ascenseur de Mars) et le trepan de manteau (Grand Forage) ;</li>
 *     <li>la foreuse trouve un filon profond sur un astre de chaque systeme (Lune, Mercure, Venus, Mars et la Ceinture, Jupiter, Saturne,
 *     Uranus, Neptune, Pluton, Alpha du Centaure, TRAPPIST-1) ; aucun sur la Terre ;</li>
 *     <li>posee en structure MKV sur Mars, elle fore (minerai du filon) ; hors d'un astre : aucun filon.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot6Tests {

    private PatchP107Lot6Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_6_data", timeoutTicks = 20)
    public static void p107_6_grand_forage_et_foreuse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int bore = Megastructures.byBody(Body.VENUS);
        Gt.check(bore >= 0, "Grand Forage sur Venus");
        IndustryMachine port = IndustryMachine.byKey(Megastructures.port(bore));
        Gt.check(port != null && !IndustryRecipes.of(h.getLevel(), port).isEmpty(), "recettes du Grand Forage");
        IndustryMachine drill = IndustryMachine.byKey(SpaceDrill.KEY);
        Gt.check(drill != null && PiIndustry.controller(drill) != null, "foreuse spatiale");
        Recipe<?> craft = h.getLevel().getRecipeManager().byKey(new ResourceLocation("pioneers", "industry/crafting/space_drill")).orElse(null);
        Gt.check(craft != null, "fabrication de la foreuse spatiale");
        boolean mast = false, bit = false;
        for (var ing : craft.getIngredients()) {
            for (var st : ing.getItems()) {
                String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(st.getItem()).toString();
                mast |= id.equals("pioneers:colossal_drill_mast");
                bit |= id.equals("pioneers:mantle_drill_bit");
            }
        }
        Gt.check(mast && bit, "foreuse : mat de forage colossal et trepan de manteau exiges");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_6_veins", timeoutTicks = 20)
    public static void p107_6_filon_sur_chaque_systeme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Function<Level, Body> old = Megastructures.bodyOf;
        s.onCleanup(() -> Megastructures.bodyOf = old);
        try {
            BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
            for (Body body : new Body[]{Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.CERES, Body.IO, Body.TITAN, Body.MIRANDA,
                  Body.TRITON, Body.PLUTO, Body.PROXIMA_B, Body.TRAPPIST_1E}) {
                Megastructures.bodyOf = l -> body;
                Gt.check(SpaceDrill.vein(h.getLevel(), at) != null, "foreuse : aucun filon sur " + body);
                Gt.check(SpaceDrill.left(h.getLevel(), at) > 0, "foreuse : reserve vide sur " + body);
            }
            Megastructures.bodyOf = l -> Body.EARTH;
            Gt.check(SpaceDrill.vein(h.getLevel(), at) == null, "foreuse : un filon sur la Terre");
        } finally {
            Megastructures.bodyOf = old;
        }
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_6_drill", timeoutTicks = 1200)
    public static void p107_6_foreuse_fore_sur_mars(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Function<Level, Body> old = Megastructures.bodyOf;
        s.onCleanup(() -> Megastructures.bodyOf = old);
        ControllerTile tile = PatchP107Lot4Tests.build(h, IndustryMachine.byKey(SpaceDrill.KEY), true);
        Megastructures.bodyOf = l -> null;
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "foreuse : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            tile.getEnergy().setStored(tile.getEnergy().getCapacity());
            h.runAfterDelay(20, () -> {
                Gt.equal(ControllerTile.NO_DEPOSIT, MkvCoreSlot.status(tile), "hors d'un astre : aucun filon");
                Megastructures.bodyOf = l -> Body.MARS;
                h.onEachTick(() -> tile.getEnergy().setStored(tile.getEnergy().getCapacity()));
                h.succeedWhen(() -> {
                    boolean ore = false;
                    for (var slot : tile.itemOutputs()) {
                        ore |= !slot.getStack().isEmpty();
                    }
                    Gt.check(ore, "foreuse : rien fore sur Mars (etat " + MkvCoreSlot.status(tile) + ")");
                    Megastructures.bodyOf = old;
                    s.done();
                });
            });
        });
    }
}
