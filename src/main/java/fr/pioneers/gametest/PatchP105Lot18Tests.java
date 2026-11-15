package fr.pioneers.gametest;

import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 18 (GameTests ; retours de jeu de Guillaume, point 21) : un sandwich s'assemble au saladier (vraie recette chargee,
 * ingredients exacts) et le panini se dore a la poele ; un bonbon se cuit a la marmite ; manger un bonbon donne son effet court et doux.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot18Tests {

    private PatchP105Lot18Tests() {
    }

    static ItemStack stack(String id, int n) {
        return PatchP105Lot11Tests.stack(id, n);
    }

    static boolean tagged(String id, String tag) {
        return stack(id, 1).is(TagKey.create(Registries.ITEM, new ResourceLocation("pioneers", tag)));
    }

    /** Club sandwich au saladier (6 garnitures, 2 sandwichs) ; le panini cru se dore a la poele ; tags et bareme des sandwichs. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_18_sandwich", timeoutTicks = 20)
    public static void p105_18_sandwich_au_saladier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        KitchenRecipe club = PatchP105Lot11Tests.producing(s, Station.MIXING, "club_sandwich");
        Gt.check(club.matchesExact(List.of(stack("bread", 1), stack("roast_chicken", 1), stack("bacon", 1), stack("lettuce", 1),
              stack("tomato_slices", 1), stack("mayonnaise", 1))), "club sandwich : pain, poulet, lard, laitue, tomate, mayonnaise");
        Gt.check(!club.matchesExact(List.of(stack("bread", 1), stack("bacon", 1), stack("lettuce", 1), stack("tomato_slices", 1),
              stack("mayonnaise", 1))), "sans poulet, ce n'est pas un club sandwich");
        Gt.equal(2, club.results().get(0).stack().getCount(), "un assemblage = 2 sandwichs");
        KitchenRecipe cheese = PatchP105Lot11Tests.producing(s, Station.MIXING, "cheese_sandwich");
        Gt.check(cheese.matchesExact(List.of(stack("bread", 1), stack("cheese", 1), stack("butter", 1))), "sandwich au fromage : pain, fromage, beurre");
        KitchenRecipe panini = PatchP105Lot11Tests.producing(s, Station.FRYING, "panini");
        Gt.check(panini.matchesExact(List.of(stack("raw_panini", 1))), "poele : panini a griller -> panini");
        for (String id : List.of("cheese_sandwich", "tuna_sandwich", "club_sandwich", "egg_sandwich", "salmon_sandwich", "veggie_sandwich",
              "chicken_sandwich", "pate_sandwich", "panini")) {
            Gt.check(tagged(id, "food/sandwiches"), "tag pioneers:food/sandwiches : " + id);
        }
        Item ham = stack("sandwich", 1).getItem();
        FoodProperties ref = ham.getFoodProperties(new ItemStack(ham), null);
        FoodProperties club2 = stack("club_sandwich", 1).getItem().getFoodProperties(stack("club_sandwich", 1), null);
        Gt.check(ref != null && club2 != null && club2.getNutrition() >= ref.getNutrition(), "le club sandwich nourrit au moins comme le jambon-beurre");
        s.done();
    }

    /** Bonbons a la marmite : sucette (sucre x2, jus d'orange, baton -> 4), guimauves (sucre x2, oeuf, eau -> 6) ; tag des bonbons. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_18_sweets", timeoutTicks = 20)
    public static void p105_18_bonbon_a_la_marmite(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        KitchenRecipe lolly = PatchP105Lot11Tests.producing(s, Station.COOKING, "lollipop");
        Gt.check(lolly.matchesExact(List.of(new ItemStack(Items.SUGAR, 2), stack("orange_juice", 1), new ItemStack(Items.STICK))),
              "sucette : 2 sucres, jus d'orange, baton");
        Gt.equal(4, lolly.results().get(0).stack().getCount(), "une marmite = 4 sucettes");
        KitchenRecipe mallow = PatchP105Lot11Tests.producing(s, Station.COOKING, "marshmallows");
        Gt.check(mallow.matchesExact(List.of(new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG))) && mallow.fluid() != null,
              "guimauves : 2 sucres, un oeuf, de l'eau");
        for (String id : List.of("barley_sugar", "butter_caramels", "fruit_jellies", "marshmallows", "nougat", "lollipop", "sour_drops",
              "chocolate_truffles")) {
            Gt.check(tagged(id, "food/sweets"), "tag pioneers:food/sweets : " + id);
        }
        s.done();
    }

    /** Manger une sucette : vitesse 15 s (effet court et doux, niveau I), peu nourrissante, se mange vite. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_18_effect", timeoutTicks = 20)
    public static void p105_18_effet_du_bonbon(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack lolly = stack("lollipop", 1);
        FoodProperties fp = lolly.getItem().getFoodProperties(lolly, null);
        Gt.check(fp != null && fp.getNutrition() <= 2 && fp.isFastFood(), "sucette : 1 a 2 de faim, se mange vite");
        ServerPlayer p = s.player("gtl18", "Gourmand", new BlockPos(2, 2, 2));
        p.getFoodData().setFoodLevel(10);
        lolly.getItem().finishUsingItem(lolly, s.level, p);
        MobEffectInstance speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
        Gt.check(speed != null && speed.getAmplifier() == 0 && speed.getDuration() > 0 && speed.getDuration() <= 15 * 20,
              "la sucette donne vitesse I pour 15 s au plus");
        Gt.check(p.getFoodData().getFoodLevel() > 10, "la sucette nourrit un peu");
        s.done();
    }
}
