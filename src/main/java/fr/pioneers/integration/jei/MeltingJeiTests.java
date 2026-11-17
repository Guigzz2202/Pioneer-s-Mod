package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.gametest.Gt;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.6 (Guillaume, 17/11/2026 : pages « Alliages » et « Fusion des metaux » plus simples, le liquide et le solide distingues selon l'objet
 * clique) : les DONNEES des pages de {@link MeltingCategories}, calculees sur les vraies recettes du serveur (le dessin est cote client) :
 * <ul>
 *     <li>chaque alliage a une page « fondue » qui ne produit QUE le metal fondu (creusets) et une page « lingot » qui ne produit QUE le
 *     lingot (couler le metal fondu ; four a induction ; creuset sous argon) ;</li>
 *     <li>la fusion des metaux ne produit que le metal fondu ; une piece rend la moitie aux postes du feu et tout au creuset industriel.</li>
 * </ul>
 * Dans le paquet de JEI (classes du paquet) ; ne tourne qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MeltingJeiTests {

    private MeltingJeiTests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_jei_alliages", timeoutTicks = 20)
    public static void p106_jei_alliages_liquide_et_solide(GameTestHelper h) {
        Gt.begin(h);
        List<MeltingCategories.Alloy> all = MeltingCategories.alloys(h.getLevel());
        MeltingCategories.Alloy liquid = find(all, "bronze", MeltingCategories.Kind.LIQUID);
        MeltingCategories.Alloy solid = find(all, "bronze", MeltingCategories.Kind.SOLID);
        Gt.check(liquid != null && solid != null, "bronze : une page fondue et une page lingot");
        Gt.equal(4, liquid.ingots(), "bronze fondu : 3 cuivre + 1 etain");
        // le tag forge:ingots/copper commence par le lingot vanilla, absent de la table de fusion : le creuset industriel doit etre trouve
        Gt.check(liquid.routes().size() >= 4, "bronze fondu : postes du feu et creuset industriel (" + liquid.routes().size() + ")");
        Gt.check(liquid.ways().isEmpty(), "page fondue : aucune voie vers le lingot");
        Gt.equal(2, solid.ways().size(), "lingot de bronze : couler, puis four a induction");
        Gt.check(solid.ways().get(0).parts().isEmpty() && solid.ways().get(0).fluidMb() == 144, "1re voie : couler 144 mB de bronze fondu");
        Item bronze = ForgeRegistries.ITEMS.getValue(Pioneers.rl("bronze_ingot"));
        Gt.check(solid.ways().get(1).out().is(bronze) && solid.ways().get(1).out().getCount() == 4, "2e voie : 3 cuivre + 1 etain -> 4 lingots");
        Gt.check(solid.ways().stream().allMatch(w -> w.out().is(bronze)), "page lingot : seul produit, le lingot");
        // alliages des machines : constantan (four a induction MK : page lingot seule), nichrome (creuset sous argon), babbitt (fondu + coule)
        Gt.check(find(all, "constantan", MeltingCategories.Kind.SOLID) != null && find(all, "constantan", MeltingCategories.Kind.LIQUID) == null,
              "constantan : page lingot seule (four a induction)");
        MeltingCategories.Alloy nichrome = find(all, "nichrome", MeltingCategories.Kind.SOLID);
        Gt.check(nichrome != null && nichrome.gas(), "nichrome : creuset sous argon");
        Gt.check(find(all, "babbitt", MeltingCategories.Kind.LIQUID) != null && find(all, "babbitt", MeltingCategories.Kind.SOLID) != null,
              "babbitt : fondu au creuset industriel, puis coule");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p106_jei_fusion", timeoutTicks = 20)
    public static void p106_jei_fusion_liquide_seul(GameTestHelper h) {
        Gt.begin(h);
        List<MeltingCategories.Melt> melts = MeltingCategories.melts(h.getLevel());
        MeltingCategories.Melt parts = melts.stream().filter(m -> m.metal().equals("copper") && m.group() == MeltingCategories.Group.RECYCLE)
              .findFirst().orElse(null);
        Gt.check(parts != null, "page des pieces de cuivre absente");
        Item plate = ForgeRegistries.ITEMS.getValue(Pioneers.rl("copper_plate"));
        int i = -1;
        for (int k = 0; k < parts.items().size(); k++) {
            if (parts.items().get(k).is(plate)) {
                i = k;
            }
        }
        Gt.check(i >= 0, "plaque de cuivre absente de la page des pieces");
        Gt.equal(144, (long) parts.mb().get(i), "plaque de cuivre : valeur 144 mB");
        Gt.check(Math.abs(parts.fire().get(i) - 0.5) < 1e-6, "postes du feu : la moitie");
        Gt.check(parts.electric().get(i) != null && Math.abs(parts.electric().get(i)[0] - 1) < 1e-6, "creuset industriel : tout");
        MeltingCategories.Melt ore = melts.stream().filter(m -> m.metal().equals("copper") && m.group() == MeltingCategories.Group.ORE)
              .findFirst().orElse(null);
        Gt.check(ore != null && ore.anyFire() && ore.anyElectric(), "page du minerai de cuivre : feu et creuset industriel");
        h.succeed();
    }

    private static MeltingCategories.Alloy find(List<MeltingCategories.Alloy> all, String alloy, MeltingCategories.Kind kind) {
        return all.stream().filter(a -> a.alloy().equals(alloy) && a.kind() == kind).findFirst().orElse(null);
    }
}
