package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ 1.0.6, « un role par machine » (demandes de Guillaume du 16/11/2026 : « une machine par metier », « le craqueur devient un multi
 * seulement », « toute la taille », « aller au bout de la demarche ») :
 * <ul>
 *     <li>les sept MACHINES DE METIER (Menuiserie, Atelier de confection, Conditionneuse, Chaudronnerie, Atelier de verrerie, Banc
 *     electromecanique, Taillerie de pierre) : compactes MKI a MKIV et multibloc ; les copies d'etabli vont a la machine de leur metier ;</li>
 *     <li>la TAILLERIE fait toutes les recettes du tailleur de pierre, avec le rendement de tier compte depuis la main (x1,75 au MKI) ;</li>
 *     <li>le CRAQUEUR est multibloc seulement ; la pyrolyse des solides passe au four a pyrolyse, la calcination au Calcinateur ;</li>
 *     <li>le FILAGE passe a l'Atelier de confection ; les anneaux et ressorts du tour se font au MKI a la Cintreuse ; une machine par
 *     transformation de forme (tiges a l'extrudeuse, plaques et toles a la presse).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP106MetiersTests {

    private PatchP106MetiersTests() {
    }

    private static Optional<? extends Recipe<?>> byKey(GameTestHelper h, String id) {
        return h.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
    }

    private static IndustryMachine machineOf(GameTestHelper h, String id) {
        Optional<? extends Recipe<?>> r = byKey(h, id);
        Gt.check(r.isPresent() && r.get() instanceof IndustrialRecipe, "recette industrielle absente : " + id);
        return ((IndustrialRecipe) r.get()).machine();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_metiers", timeoutTicks = 20)
    public static void p106_machines_de_metier(GameTestHelper h) {
        Gt.begin(h);
        for (IndustryMachine m : List.of(IndustryMachine.JOINERY, IndustryMachine.TAILORING_WORKSHOP, IndustryMachine.PACKAGING_MACHINE,
              IndustryMachine.SHEET_METAL_SHOP, IndustryMachine.GLASSWORKS, IndustryMachine.ELECTROMECHANICAL_BENCH, IndustryMachine.STONEWORKS)) {
            Gt.check(m.atelier == IndustryMachine.Atelier.NEW && m.industrial, m.key + " : compactes et multibloc");
            for (String t : List.of("basic_", "advanced_", "elite_", "ultimate_", "industrial_")) {
                Gt.check(ForgeRegistries.ITEMS.containsKey(Pioneers.rl(t + m.key)), "objet absent : " + t + m.key);
            }
        }
        Gt.equal(IndustryMachine.Source.STONECUTTING, IndustryMachine.STONEWORKS.source, "la Taillerie lit les recettes du tailleur de pierre");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_taille", timeoutTicks = 20)
    public static void p106_taillerie_toute_la_taille(GameTestHelper h) {
        Gt.begin(h);
        List<IndustrialRecipe> list = IndustryRecipes.of(h.getLevel(), IndustryMachine.STONEWORKS);
        IndustrialRecipe slab = list.stream().filter(r -> r.getId().equals(ResourceLocation.parse("minecraft:stone_slab_from_stone_stonecutting")))
              .findFirst().orElse(null);
        Gt.check(slab != null, "pierre -> 2 dalles absente de la Taillerie");
        Gt.equal(2, slab.results().get(0).stack().getCount(), "2 dalles par pierre (recette du tailleur de pierre)");
        Gt.check(Math.abs(slab.tierYield(0) - 1.75) < 1e-9 && Math.abs(slab.tierYield(3) - 3.5) < 1e-9, "rendement x1,75 au MKI, x3,5 au MKIV");
        Gt.check(list.stream().noneMatch(r -> !r.results().isEmpty() && String.valueOf(ForgeRegistries.ITEMS.getKey(
              r.results().get(0).stack().getItem())).contains("copper")), "le cuivre ne se taille pas a la Taillerie (Chaudronnerie)");
        Gt.check(IndustryRecipes.of(h.getLevel(), IndustryMachine.SAWMILL).stream().noneMatch(r -> !r.results().isEmpty() && r.results().get(0).stack().is(Items.STONE_SLAB)
              || r.getId().getPath().startsWith("bronze/steam/saw/") && !r.getId().getPath().endsWith("sticks")),
              "la scie industrielle ne taille plus la pierre (la scie a vapeur est le tier 1 de la Taillerie)");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_craqueur", timeoutTicks = 20)
    public static void p106_craqueur_multibloc_seul(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal(IndustryMachine.Atelier.NONE, IndustryMachine.CRACKER.atelier, "craqueur : multibloc seulement");
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(Pioneers.rl("basic_cracker")), "le craqueur compact n'existe plus");
        Gt.equal(IndustryMachine.PYROLYSIS_OVEN, machineOf(h, "industry/cracker/tholin_fiber"), "fibre de tholins : four a pyrolyse");
        Gt.equal(IndustryMachine.REDUCTION_FURNACE, machineOf(h, "industry/cracker/activated_carbon"), "charbon actif : Calcinateur");
        Gt.equal(IndustryMachine.REDUCTION_FURNACE, machineOf(h, "processes/gold/amalgam_4"), "amalgame distille : Calcinateur");
        Gt.equal(1, IndustryMachine.REDUCTION_FURNACE.fluidsOut, "Calcinateur : une sortie de fluide (eau, mercure)");
        Gt.check(byKey(h, "industry/cracker/plastic_pyrolysis").isEmpty(), "pyrolyse du polyethylene au craqueur retiree");
        Gt.equal(Pioneers.rl("industry/pyrolysis_oven/recycle_polyethylene_pellets"),
              PiRemaps.recipeId(Pioneers.rl("industry/cracker/plastic_pyrolysis")), "verrou suivi vers le four a pyrolyse");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_roles", timeoutTicks = 20)
    public static void p106_un_role_par_machine(GameTestHelper h) {
        Gt.begin(h);
        Gt.equal(IndustryMachine.TAILORING_WORKSHOP, machineOf(h, "industry/wiremill/cotton_thread"), "filage du coton : Atelier de confection");
        Gt.equal(IndustryMachine.JOINERY, machineOf(h, "automation/carpenter/vanilla_white_bed"), "copie du charpentier : Menuiserie");
        Gt.equal(IndustryMachine.SHEET_METAL_SHOP, machineOf(h, "automation/coppersmith/vanilla_lightning_rod"), "copie du chaudronnier : Chaudronnerie");
        Gt.equal(IndustryMachine.PACKAGING_MACHINE, machineOf(h, "automation/agricultural/farm_storage_apple_crate"), "caisse de pommes : Conditionneuse");
        // anneaux du tour (T0, T1) : au MKI a la Cintreuse (geste de la machine), plus au centre d'usinage ; la Cintreuse a sa propre
        // recette d'anneau de fer (tige -> 2 anneaux), qui domine la recette basse convertie
        ResourceLocation ring = Pioneers.rl("materials/turning_iron_ring");
        Gt.equal("bender", GeneratedTierFamilies.LOW_MOVED.get("pioneers:materials/turning_iron_ring"), "anneau de fer du tour : Cintreuse au MKI");
        Gt.check(IndustryRecipes.of(h.getLevel(), IndustryMachine.BENDER).stream().anyMatch(r -> !r.results().isEmpty()
              && r.results().get(0).stack().is(ForgeRegistries.ITEMS.getValue(Pioneers.rl("iron_ring")))), "la Cintreuse fait les anneaux de fer");
        Gt.check(IndustryRecipes.of(h.getLevel(), IndustryMachine.MACHINING_CENTER).stream().noneMatch(r -> r.getId().equals(ring)),
              "anneau de fer du tour : plus au centre d'usinage");
        // une machine par transformation de forme : les tiges a l'extrudeuse, plus au centre d'usinage (le tour bas y est converti)
        Item rod = ForgeRegistries.ITEMS.getValue(Pioneers.rl("iron_rod"));
        Gt.equal("extruder", GeneratedTierFamilies.LOW_MOVED.get("pioneers:materials/turning_iron_rod"), "tige de fer du tour : Extrudeuse au MKI");
        Gt.check(IndustryRecipes.of(h.getLevel(), IndustryMachine.EXTRUDER).stream().anyMatch(r -> !r.results().isEmpty()
              && r.results().get(0).stack().is(rod)), "l'Extrudeuse fait les tiges de fer");
        Gt.check(IndustryRecipes.of(h.getLevel(), IndustryMachine.MACHINING_CENTER).stream().noneMatch(r -> !r.results().isEmpty()
              && r.results().get(0).stack().is(rod)), "tiges de fer : plus au centre d'usinage");
        h.succeed();
    }
}
