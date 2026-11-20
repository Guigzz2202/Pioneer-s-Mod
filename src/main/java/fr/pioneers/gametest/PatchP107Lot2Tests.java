package fr.pioneers.gametest;

import fr.pioneers.core.registries.GeneratedComponents;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1.0.7 « Plus loin et au-dela », lot 2 (composants MKV et metaux des astres) :
 * <ul>
 *     <li>la famille « au-dela » de la nomenclature est enregistree (onglet Composants) : 63 composants au lot 2, 78 depuis le lot 11, chacun avec son objet ;</li>
 *     <li>chaque composant a sa recette de la nomenclature (rc_&lt;id&gt;), faite aux machines MKIV (tier 3) ;</li>
 *     <li>le chassis en alliage stellaire se fait a l'assembleur (composants), avec l'alliage du lot 1.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot2Tests {

    private PatchP107Lot2Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_2_composants", timeoutTicks = 20)
    public static void p107_2_composants_mkv(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        List<String> ids = null;
        for (GeneratedComponents.Family f : GeneratedComponents.FAMILIES) {
            if ("audela".equals(f.name())) {
                ids = f.ids();
            }
        }
        Gt.check(ids != null, "famille au-dela absente de l'onglet Composants");
        //63 au lot 2, 15 de plus au lot 11 (systemes lointains) : la famille ne fait que grandir
        Gt.check(ids.size() >= 78, "composants MKV : " + ids.size());
        Set<String> made = new HashSet<>();
        for (Recipe<?> r : h.getLevel().getRecipeManager().getRecipes()) {
            String path = r.getId().getPath();
            int k = path.lastIndexOf("/rc_");
            if (k >= 0 && r instanceof IndustrialRecipe ir) {
                Gt.check(ir.tier() == 3 || !ids.contains(path.substring(k + 4)), "composant MKV hors des machines MKIV : " + path);
                made.add(path.substring(k + 4));
            }
        }
        for (String id : ids) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pioneers", id));
            Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
            Gt.check(made.contains(id), "composant sans recette : " + id);
        }
        boolean chassis = IndustryRecipes.of(s.level, IndustryMachine.ASSEMBLER).stream()
              .anyMatch(r -> r.getId().getPath().endsWith("/rc_stellar_alloy_chassis") && r.tier() == 3);
        Gt.check(chassis, "chassis en alliage stellaire a l'assembleur MKIV");
        s.done();
    }
}
