package fr.pioneers.gametest;

import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 8 (Saturne, Uranus, Neptune) :
 * <ul>
 *     <li>l'Anneau-Rotor sur Saturne, la Boucle sur Uranus, la Descente sur Neptune : leurs ports, leurs trois recettes uniques, leurs
 *     composants gates ; la liste de la Descente demande la banque de flux (Boucle) ;</li>
 *     <li>le broyeur en structure MKV passe au MKV avec le coeur de centrifugation (rotor annulaire de l'Anneau-Rotor).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot8Tests {

    private PatchP107Lot8Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_8_data", timeoutTicks = 20)
    public static void p107_8_anneau_boucle_descente(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (Body giant : new Body[]{Body.SATURN, Body.URANUS, Body.NEPTUNE}) {
            int i = Megastructures.byBody(giant);
            Gt.check(i >= 0, "aucune megastructure sur " + giant);
            IndustryMachine port = IndustryMachine.byKey(Megastructures.port(i));
            Gt.check(port != null && IndustryRecipes.of(h.getLevel(), port).size() >= 3, "recettes du port de " + giant);
            Gt.equal(3, Megastructures.gated(i).length, "composants gates de " + giant);
        }
        int descent = Megastructures.byBody(Body.NEPTUNE);
        boolean flux = false;
        for (int k = 0; k < Megastructures.ingredientCount(descent); k++) {
            flux |= ForgeRegistries.ITEMS.getKey(Megastructures.ingredient(descent, k)).toString().equals("pioneers:flux_bank");
        }
        Gt.check(flux, "la Descente demande la banque de flux de la Boucle");
        Recipe<?> core = h.getLevel().getRecipeManager().byKey(new ResourceLocation("pioneers", "au_dela/cores/centrifugation_mkv_core")).orElse(null);
        Gt.check(core != null, "fabrication du coeur de centrifugation");
        boolean rotor = false;
        for (var ing : core.getIngredients()) {
            for (var st : ing.getItems()) {
                rotor |= ForgeRegistries.ITEMS.getKey(st.getItem()).toString().equals("pioneers:annular_rotor");
            }
        }
        Gt.check(rotor, "coeur de centrifugation : rotor annulaire exige");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_8_core", timeoutTicks = 200)
    public static void p107_8_coeur_de_centrifugation_du_broyeur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ControllerTile tile = PatchP107Lot4Tests.build(h, IndustryMachine.byKey("crusher"), true);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "broyeur MKV : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            Gt.equal(3, tile.tier(), "structure MKV sans coeur : reste MKIV");
            Gt.check(tile.mkvCore.install(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("pioneers:centrifugation_mkv_core")))),
                  "pose du coeur de centrifugation");
            Gt.equal(4, tile.tier(), "structure MKV et coeur de centrifugation : MKV");
            s.done();
        });
    }
}
