package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.economy.PriceService;
import fr.pioneers.society.economy.Values;
import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.food.LampOil;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.building.home.line.Clothesline;
import fr.pioneers.building.lighting.LightFixtureBlock;
import fr.pioneers.building.lighting.PiLighting;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.society.reputation.ReputationApi;
import fr.pioneers.society.village.VillagerTrade;
import fr.pioneers.society.village.Villagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 11 (GameTests ; specification « Le monde vivant » §9) : fumaison du gibier (sechoir puis fumoir, vraies recettes
 * chargees) ; recette elaboree (ragout de gibier a la marmite, viande hachee a la planche, bareme des plats du mod) ; graisse de baleine en
 * huile de lampe (pressoir, mise en bouteille, combustible du four, applique a huile qui s'en fabrique et qui eclaire) ; gibier fume accepte
 * par un boucher au prix attendu.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot11Tests {

    private PatchP105Lot11Tests() {
    }

    static Item item(String id) {
        Item it = PiFarm.item(id.contains(":") ? id : "pioneers:" + id);
        Gt.check(it != Items.AIR, "objet absent : " + id);
        return it;
    }

    static ItemStack stack(String id, int n) {
        return new ItemStack(item(id), n);
    }

    static KitchenRecipe producing(Gt.Scope s, Station station, String result) {
        Item out = item(result);
        for (KitchenRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(station).get())) {
            for (KitchenRecipe.Result res : r.results()) {
                if (res.stack().is(out)) {
                    return r;
                }
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("aucune recette " + station.id() + " ne donne " + result);
    }

    /** Fumaison simple : toute viande du gibier seche au sechoir, la piece sechee se fume au fumoir ; requin et raie sechent comme un poisson. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_11_smoking", timeoutTicks = 20)
    public static void p105_11_fumaison_du_gibier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String raw : List.of("raw_venison", "raw_boar_meat", "raw_bison_meat", "raw_turkey", "raw_bear_meat", "raw_pigeon", "raw_big_game_meat",
              "raw_whale_meat", "raw_seal_meat")) {
            KitchenRecipe dry = Clothesline.recipe(s.level, Station.DRYING, stack(raw, 1));
            Gt.check(dry != null && dry.results().get(0).stack().is(item("dried_game_meat")), "sechoir : " + raw + " -> viande de gibier sechee");
        }
        KitchenRecipe smoke = Clothesline.recipe(s.level, Station.SMOKING, stack("dried_game_meat", 1));
        Gt.check(smoke != null && smoke.results().get(0).stack().is(item("smoked_game_meat")), "fumoir : viande sechee -> gibier fume");
        Gt.check(Clothesline.recipe(s.level, Station.SMOKING, stack("raw_venison", 1)) == null
              || !Clothesline.recipe(s.level, Station.SMOKING, stack("raw_venison", 1)).results().get(0).stack().is(item("smoked_game_meat")),
              "la viande crue ne se fume pas directement (sechee d'abord)");
        for (String fish : List.of("raw_shark_meat", "raw_ray_wing")) {
            KitchenRecipe dry = Clothesline.recipe(s.level, Station.DRYING, stack(fish, 1));
            Gt.check(dry != null && dry.results().get(0).stack().is(item("dried_fish_fillet")), "sechoir : " + fish + " -> filets de poisson seches");
        }
        FoodProperties smoked = item("smoked_game_meat").getFoodProperties(new ItemStack(item("smoked_game_meat")), null);
        FoodProperties raw = item("raw_venison").getFoodProperties(new ItemStack(item("raw_venison")), null);
        Gt.check(smoked != null && raw != null && smoked.getNutrition() > raw.getNutrition() && smoked.getSaturationModifier() > raw.getSaturationModifier(),
              "le gibier fume nourrit mieux que la viande crue");
        s.done();
    }

    /** Recette elaboree : ragout de gibier a la marmite (vraie recette, ingredients exacts), viande hachee a la planche, bareme des plats. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_11_stew", timeoutTicks = 20)
    public static void p105_11_ragout_de_gibier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        KitchenRecipe stew = producing(s, Station.COOKING, "game_stew");
        Gt.check(stew.matchesExact(List.of(stack("raw_venison", 1), stack("raw_bison_meat", 1), new ItemStack(Items.BROWN_MUSHROOM),
              stack("onion", 1), new ItemStack(Items.CARROT), stack("wine", 1))), "ragout : 2 viandes rouges, champignon, oignon, carotte, vin");
        Gt.check(!stew.matchesExact(List.of(stack("raw_turkey", 2), new ItemStack(Items.BROWN_MUSHROOM), stack("onion", 1),
              new ItemStack(Items.CARROT), stack("wine", 1))), "la dinde n'est pas une viande rouge");
        Gt.check(stew.fluid() != null && stew.results().get(0).stack().getCount() == 3, "ragout : de l'eau, 3 bols");
        KitchenRecipe mince = Clothesline.recipe(s.level, Station.CUTTING, stack("raw_bear_meat", 1));
        Gt.check(mince != null && mince.results().get(0).stack().is(item("minced_meat")) && mince.results().get(0).stack().getCount() == 2,
              "planche : la viande d'ours se hache (2 viandes hachees)");
        FoodProperties g = item("game_stew").getFoodProperties(new ItemStack(item("game_stew")), null);
        FoodProperties b = item("beef_stew").getFoodProperties(new ItemStack(item("beef_stew")), null);
        Gt.check(g != null && b != null && g.getNutrition() == b.getNutrition() && g.getSaturationModifier() == b.getSaturationModifier(),
              "ragout de gibier au bareme du boeuf bourguignon");
        s.done();
    }

    /**
     * Graisse de baleine -> huile de lampe : 2 graisses au pressoir = 250 mB d'huile de poisson, mise en bouteille = huile de lampe, qui brule
     * au four et entre dans l'applique a huile ; l'applique posee eclaire.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_11_lamp_oil", timeoutTicks = 40)
    public static void p105_11_graisse_en_huile_de_lampe(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        KitchenRecipe press = null;
        for (KitchenRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(Station.PRESSING).get())) {
            if (r.matchesExact(List.of(stack("whale_blubber", 2)))) {
                press = r;
            }
        }
        Gt.check(press != null, "pressoir : 2 graisses de baleine");
        Gt.check("pioneers:fish_oil".equals(BuiltInRegistries.FLUID.getKey(press.fluidResult().getFluid()).toString())
              && press.fluidResult().getAmount() == 250, "pressoir : 250 mB d'huile de poisson");
        boolean bottled = false;
        for (String[] b : GeneratedFarm.BOTTLING) {
            bottled |= "pioneers:fish_oil".equals(b[0]) && "pioneers:lamp_oil".equals(b[1]) && "pioneers:bottle".equals(b[2]);
        }
        Gt.check(bottled, "mise en bouteille : huile de poisson -> huile de lampe");
        ItemStack oil = new ItemStack(LampOil.item());
        Gt.check(LampOil.is(oil), "huile de lampe enregistree");
        Gt.equal(LampOil.BURN_TICKS, ForgeHooks.getBurnTime(oil, RecipeType.SMELTING), "huile de lampe : combustible du four");
        Item sconce = PiLighting.FIXTURES.get(LightFixtureBlock.Kind.BRONZE_WALL_SCONCE).asItem();
        boolean made = false;
        for (BenchRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiBronze.BENCH.get())) {
            if (r.result().is(sconce)) {
                for (BenchRecipe.Counted c : r.ingredients()) {
                    made |= c.ingredient().test(oil);
                }
            }
        }
        Gt.check(made, "l'applique a huile se fabrique avec l'huile de lampe");
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockState lamp = PiLighting.FIXTURES.get(LightFixtureBlock.Kind.BRONZE_WALL_SCONCE).getBlock().defaultBlockState();
        h.setBlock(pos, lamp);
        h.runAfterDelay(10, () -> {
            BlockState now = h.getBlockState(pos);
            Gt.check(now.getBlock() instanceof LightFixtureBlock && now.getValue(LightFixtureBlock.LIT), "l'applique a huile posee eclaire");
            Gt.check(now.getLightEmission(h.getLevel(), h.absolutePos(pos)) > 0, "lumiere de l'applique");
            s.done();
        });
    }

    /** Economie : un boucher reprend le gibier fume (il est a son catalogue d'achat) au prix de la table des valeurs. */
    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_11_butcher", timeoutTicks = 60)
    public static void p105_11_boucher_achete_le_gibier_fume(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack smoked = stack("smoked_game_meat", 1);
        long base = Values.value(smoked);
        Gt.check(base > 0, "le gibier fume a une valeur");
        Gt.check(Values.has(smoked.getItem(), "meat_cured") && Values.has(item("cooked_venison"), "meat_cooked"),
              "familles : viande fumee, viande cuite (plus « divers »)");
        long ham = Values.value(stack("ham", 1));
        Gt.check(base * 4 >= ham && base <= ham * 4, "prix borne : du meme ordre que le jambon");
        Villager v = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(6, 2, 6));
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.BUTCHER));
        Villagers.identify(s.level, v);
        ServerPlayer p = s.player("gtl11", "Chasseur", new BlockPos(6, 2, 3));
        VillagerTrade trade = new VillagerTrade(s.level, v);
        Gt.check(trade.buys(p, smoked), "le boucher reprend le gibier fume");
        PriceService.Partner partner = trade.pricing(p);
        PriceService.Quote q = PriceService.quote(p, partner, smoked, 1, PriceService.Side.SELL, 0);
        Gt.check(q.ok(), "devis de vente refuse : " + q.refusal());
        double pct = Values.partner("villager", "buys", 0.5) * partner.doctrineFactor();
        double mods = ReputationApi.tier(p, partner.people()).sell() + PioneerApi.modifier(p, "trader.sell");
        double unit = Math.min(base * pct * (1 + mods), base * pct * Values.partner("cap", "sell_max", 1.2)) * partner.eventFactor()
              * fr.pioneers.society.village.FoundingRules.priceFactor(partner.founder(), false);
        Gt.equal(Math.max(1, Math.round(unit)), q.unit(), "prix unitaire = valeur x part du villageois");
        s.done();
    }
}
