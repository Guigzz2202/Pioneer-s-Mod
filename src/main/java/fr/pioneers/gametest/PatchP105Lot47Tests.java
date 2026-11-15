package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.api.machine.tier.TierRecipes.TierRecipe;
import fr.pioneers.bronze.machines.SteamMachine;
import fr.pioneers.bronze.machines.SteamProcessTile;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.registries.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 47 (reponse de Guillaume du 14/11/2026 : « Ouvre les recettes KI qui ne desequilibreraient pas le jeu ») :
 * <ul>
 *     <li>OUVERTE : la trefileuse a vapeur fait une recette MKI ouverte de sa famille (fibre de chanvre -> 3 ficelles, recette de la
 *     trefileuse MKI) : listee au selecteur, verrouillable, acceptee a l'entree, memes sorties qu'au MKI, deux fois plus lente ; la machine MKI
 *     la garde telle quelle (une seule fois, pas une copie) ;</li>
 *     <li>FERMEE : une recette MKI fermee (tige de nickel -> fil de nickel : materiau d'age MKI) n'est ni a la famille de la vapeur, ni au
 *     selecteur, ni acceptee ; la machine a vapeur ne la fait pas.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot47Tests {

    /** Recette MKI ouverte (lot 47) et recette MKI fermee de la famille « trefilage ». */
    static final ResourceLocation OPEN = Pioneers.rl("industry/wiremill/hemp_string"), CLOSED = Pioneers.rl("industry/wiremill/wire_nickel");

    private PatchP105Lot47Tests() {
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id.contains(":") ? id : "pioneers:" + id));
        Gt.check(it != null && it != Items.AIR, "objet inconnu " + id);
        return it;
    }

    static SteamProcessTile place(GameTestHelper h, BlockPos at, SteamMachine m) {
        h.setBlock(at, PiBronze.STEAM_MACHINES.get(m).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof SteamProcessTile, "machine a vapeur posee : " + m.id());
        return (SteamProcessTile) h.getBlockEntity(at);
    }

    static int count(List<fr.pioneers.core.inventory.ItemSlot> slots, Item item) {
        int n = 0;
        for (var s : slots) {
            if (s.getStack().is(item)) {
                n += s.getStack().getCount();
            }
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_47_open", timeoutTicks = 20)
    public static void p105_47_recette_mki_ouverte(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal("trefilage", GeneratedTierFamilies.OPEN_MKI.get(OPEN.toString()), "recette MKI ouverte a la famille trefilage");
        TierRecipe t = TierRecipes.byId(s.level, "trefilage", OPEN);
        Gt.check(t != null && t.opened(), "la famille de la trefileuse a vapeur voit la recette MKI ouverte");
        IndustrialRecipe mki = IndustryRecipes.byId(s.level, IndustryMachine.WIREMILL, OPEN);
        Gt.check(mki != null && !mki.converted(), "la trefileuse MKI garde sa recette (pas une copie)");
        Gt.equal(1L, IndustryRecipes.of(s.level, IndustryMachine.WIREMILL).stream().filter(r -> r.getId().equals(OPEN)).count(),
              "une seule fois au MKI");
        //plus lente qu'au MKI : duree a la vapeur (T1) = duree au MKI x 2 ; memes sorties, aucun rendement
        Gt.equal((long) mki.time() * GeneratedTierFamilies.OPEN_SLOWDOWN, t.duration(1), "deux fois plus lente qu'au MKI");
        Gt.check(t.yieldFactor(1) == 1.0, "sans rendement");
        Gt.check(TierRecipes.choices(s.level, "trefilage").stream().map(RecipeChoice::id).anyMatch(OPEN::equals), "au selecteur");
        Gt.check(TierRecipes.accepts(s.level, "trefilage", new ItemStack(item("hemp_fiber"))), "fibre de chanvre acceptee a l'entree");
        SteamProcessTile mill = place(h, new BlockPos(2, 1, 2), SteamMachine.WIREMILL);
        Gt.check(mill.process().setLockedRecipe(OPEN), "verrou du selecteur sur la recette ouverte");
        mill.inputs().get(0).setStack(new ItemStack(item("hemp_fiber"), 1));
        Gt.check(mill.process().craftNow(), "la trefileuse a vapeur fait la recette MKI ouverte");
        Gt.equal(3, count(mill.guiSlots(), Items.STRING), "fibre de chanvre -> 3 ficelles, comme au MKI");
        Gt.equal(0, count(mill.inputs(), item("hemp_fiber")), "la fibre est prise");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_47_closed", timeoutTicks = 20)
    public static void p105_47_recette_mki_fermee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!GeneratedTierFamilies.OPEN_MKI.containsKey(CLOSED.toString()), "recette fermee (materiau d'age MKI)");
        Gt.check(IndustryRecipes.byId(s.level, IndustryMachine.WIREMILL, CLOSED) != null, "la trefileuse MKI la fait toujours");
        Gt.check(TierRecipes.byId(s.level, "trefilage", CLOSED) == null, "absente de la famille de la vapeur");
        Gt.check(TierRecipes.choices(s.level, "trefilage").stream().map(RecipeChoice::id).noneMatch(CLOSED::equals), "absente du selecteur");
        Gt.check(!TierRecipes.accepts(s.level, "trefilage", new ItemStack(item("nickel_rod"))), "tige de nickel refusee a l'entree");
        SteamProcessTile mill = place(h, new BlockPos(2, 1, 2), SteamMachine.WIREMILL);
        Gt.check(!mill.process().setLockedRecipe(CLOSED), "verrou refuse");
        mill.inputs().get(0).setStack(new ItemStack(item("nickel_rod"), 1));
        Gt.check(!mill.process().craftNow(), "la trefileuse a vapeur ne fait pas la recette fermee");
        Gt.equal(1, count(mill.inputs(), item("nickel_rod")), "la tige reste dans la machine");
        //aucune recette MKI d'acier ouverte (acier = haut fourneau ; l'acier est un materiau du MKI)
        for (TierRecipe r : TierRecipes.of(s.level, "trefilage")) {
            if (r.opened()) {
                Gt.check(!r.main().is(item("steel_wire")), "aucun fil d'acier par une recette ouverte : " + r.id());
            }
        }
        s.done();
    }
}
