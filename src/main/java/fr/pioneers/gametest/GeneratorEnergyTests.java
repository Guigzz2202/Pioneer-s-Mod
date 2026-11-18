package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.11.2 (lot E2) : valeurs FE exposees par {@link GeneratorOutput} (JEI, infobulles des combustibles et des generateurs).
 * <ul>
 *     <li>charbon au generateur a combustion : 1 024 FE/t au MKI, x4 par tier, meme energie totale (duree de combustion x 1 024) ;</li>
 *     <li>chaque recette de generateur : la puissance affichee fois la duree arrondie redonne l'energie totale affichee, a tous les tiers ;</li>
 *     <li>turbine a vapeur : FE par mB de vapeur tires de sa recette (64 en atelier, 80 en multibloc).</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class GeneratorEnergyTests {

    private GeneratorEnergyTests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e2_fe_charbon", timeoutTicks = 20)
    public static void fe_combustible_solide(GameTestHelper h) {
        Gt.begin(h);
        ItemStack coal = new ItemStack(PiVanillaMaterials.COAL.get());
        int burn = ForgeHooks.getBurnTime(coal, null);
        Gt.check(burn > 0, "le charbon doit bruler");
        IndustrialRecipe fuel = GeneratorOutput.solidFuel(coal);
        Gt.check(fuel != null, "recette de combustion du charbon");
        //MAJ simplification : rendement du generateur a combustion x2 (FE/t et FE au total, meme duree)
        long base = Math.round(Tiers.BASE_POWER * GeneratorOutput.COMBUSTION_YIELD);
        Gt.equal(base, GeneratorOutput.perTick(fuel, 0, false), "FE/t au MKI");
        Gt.equal(burn * base, GeneratorOutput.total(fuel, false), "FE au total");
        for (int t = 1; t < 4; t++) {
            Gt.equal(base << (2 * t), GeneratorOutput.perTick(fuel, t, false), "FE/t au tier " + t);
            Gt.equal(GeneratorOutput.solidPerTick(t), GeneratorOutput.perTick(fuel, t, false), "puissance nominale du tier " + t);
        }
        Gt.equal(burn * base, GeneratorOutput.total(fuel, true), "FE au total en multibloc (meme rendement x2, pas de cumul)");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e2_fe_recettes", timeoutTicks = 20)
    public static void fe_recettes_coherentes(GameTestHelper h) {
        Gt.begin(h);
        int checked = 0;
        for (IndustryMachine m : IndustryMachine.values()) {
            if (!m.isGenerator()) {
                continue;
            }
            for (IndustrialRecipe r : IndustryRecipes.of(h.getLevel(), m)) {
                if (r.energy() <= 0) {
                    continue;
                }
                for (int t = 0; t < 4; t++) {
                    for (boolean multi : new boolean[]{false, true}) {
                        int d = GeneratorOutput.duration(r.time(), t);
                        long shown = GeneratorOutput.perTick(r, t, multi) * d;
                        long total = GeneratorOutput.total(r, multi);
                        Gt.check(Math.abs(shown - total) <= d, r.getId() + " tier " + t + " : " + shown + " FE produits pour " + total + " affiches");
                    }
                }
                checked++;
            }
        }
        Gt.check(checked > 10, "trop peu de recettes de generateurs : " + checked);
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e2_fe_vapeur", timeoutTicks = 20)
    public static void fe_turbine_vapeur(GameTestHelper h) {
        Gt.begin(h);
        Gas steam = PioneersAPI.gas(Pioneers.rl("steam"));
        Gt.check(steam != null, "gaz vapeur");
        GeneratorOutput.Use use = GeneratorOutput.gas(h.getLevel(), GasStack.of(steam, 1));
        Gt.check(use != null, "un generateur brule la vapeur");
        double perMb = GeneratorOutput.perUnit(use.recipe(), use.amount(), false);
        Gt.check(Math.abs(perMb - 64) < 0.01, "64 FE par mB de vapeur (" + perMb + ")");
        Gt.check(Math.abs(GeneratorOutput.perUnit(use.recipe(), use.amount(), true) - 80) < 0.01, "80 FE par mB en multibloc");
        h.succeed();
    }
}
