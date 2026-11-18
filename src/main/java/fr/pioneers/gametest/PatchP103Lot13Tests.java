package fr.pioneers.gametest;

import fr.pioneers.society.economy.LedgerLabels;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.MushroomCropBlock;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 13 (GameTests) : champignons cultives, champagne et cidre bouche, libelles de la bourse.
 * <ul>
 *   <li>champignons : trois cultures a l'archetype « mushroom » (bloc {@link MushroomCropBlock}) ; tiennent sur le substrat, le mycelium et le
 *   podzol, jamais sur la terre labouree ni l'herbe ; facteur du sol (substrat x1,25, mycelium x0,75) ; ombre : lumiere 15 refusee, 0 admise ;
 *   le substrat compte comme irrigue ;</li>
 *   <li>recettes chargees : spores a la planche, tirage au saladier, prise de mousse a la cuve (champagne, cidre bouche) ;</li>
 *   <li>bourse : {@link LedgerLabels} : sorte avec partie (« Virement de », « Don au clan »), ancienne cle brute de PNJ traduite, partie du
 *   joueur lui-meme omise, sorte inconnue en clair, nom traduit stocke en « #cle ».</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot13Tests {

    private PatchP103Lot13Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_13_mushrooms", timeoutTicks = 20)
    public static void p103_13_champignons_cultives(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String id : new String[]{"brown_mushroom", "red_mushroom", "button_mushroom"}) {
            FarmCropBlock b = PiFarm.CROPS.get(id).get();
            Gt.check(b instanceof MushroomCropBlock, "culture de champignon : " + id);
            Gt.check(PiFarm.SEEDS.containsKey(id), "spores : " + id);
        }
        MushroomCropBlock crop = (MushroomCropBlock) PiFarm.CROPS.get("button_mushroom").get();
        BlockState plant = crop.defaultBlockState();
        BlockPos soil = new BlockPos(1, 1, 1);
        BlockPos above = soil.above();
        h.setBlock(soil, PiFarm.MUSHROOM_SUBSTRATE.get().defaultBlockState());
        Gt.check(plant.canSurvive(s.level, h.absolutePos(above)), "tient sur le substrat");
        Gt.check(Math.abs(crop.soilFactor(s.level, h.absolutePos(above)) - MushroomCropBlock.SUBSTRATE_GROWTH) < 1e-4, "substrat x1,25");
        Gt.check(FarmCropBlock.irrigated(s.level, h.absolutePos(above)), "substrat toujours humide");
        h.setBlock(soil, Blocks.MYCELIUM.defaultBlockState());
        Gt.check(plant.canSurvive(s.level, h.absolutePos(above)), "tient sur le mycelium");
        Gt.check(Math.abs(crop.soilFactor(s.level, h.absolutePos(above)) - MushroomCropBlock.WILD_GROWTH) < 1e-4, "mycelium x0,75");
        h.setBlock(soil, Blocks.PODZOL.defaultBlockState());
        Gt.check(plant.canSurvive(s.level, h.absolutePos(above)), "tient sur le podzol");
        h.setBlock(soil, Blocks.FARMLAND.defaultBlockState());
        Gt.check(!plant.canSurvive(s.level, h.absolutePos(above)), "jamais sur la terre labouree");
        h.setBlock(soil, Blocks.GRASS_BLOCK.defaultBlockState());
        Gt.check(!plant.canSurvive(s.level, h.absolutePos(above)), "jamais sur l'herbe");
        //ombre : une case fermee de tous cotes (lumiere 0) pousse, une torche collee (lumiere 14) non
        BlockPos dark = new BlockPos(3, 1, 3);
        for (BlockPos p : BlockPos.betweenClosed(dark.offset(-1, -1, -1), dark.offset(1, 1, 1))) {
            if (!p.equals(dark)) {
                h.setBlock(p, Blocks.STONE.defaultBlockState());
            }
        }
        h.setBlock(dark, Blocks.AIR.defaultBlockState());
        Gt.check(MushroomCropBlock.shaded(s.level, h.absolutePos(dark)), "case noire : a l'ombre");
        h.setBlock(dark, Blocks.TORCH.defaultBlockState());
        h.setBlock(dark.above(), Blocks.AIR.defaultBlockState());
        //1.0.6 : la lumiere de la torche se propage au tick suivant (moteur de lumiere) : verdict quelques ticks plus tard
        h.runAfterDelay(5, () -> {
            Gt.check(!MushroomCropBlock.shaded(s.level, h.absolutePos(dark.above())), "contre une torche : trop clair");
            h.succeed();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_13_drinks", timeoutTicks = 20)
    public static void p103_13_recettes_champignons_et_bulles(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Set<String> found = new HashSet<>();
        for (Station st : new Station[]{Station.CUTTING, Station.MIXING, Station.FERMENTING, Station.COOKING, Station.FRYING, Station.BAKING}) {
            RecipeType<KitchenRecipe> type = PiKitchen.TYPES.get(st).get();
            for (KitchenRecipe r : s.level.getRecipeManager().getAllRecipesFor(type)) {
                found.add(st.id() + ":" + BuiltInRegistries.ITEM.getKey(r.getResultItem(s.level.registryAccess()).getItem()).getPath());
            }
        }
        for (String need : new String[]{"cutting:button_mushroom_spores", "cutting:brown_mushroom_spores", "cutting:red_mushroom_spores",
              "mixing:champagne_tirage", "fermenting:champagne", "mixing:sparkling_cider_tirage", "fermenting:sparkling_cider",
              "cooking:chicken_with_mushrooms", "frying:royal_omelette", "baking:stuffed_mushrooms", "baking:mushroom_pie",
              "frying:sauteed_mushrooms"}) {
            Gt.check(found.contains(need), "recette chargee : " + need);
        }
        h.succeed();
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_13_ledger", timeoutTicks = 20)
    public static void p103_13_libelles_de_la_bourse(GameTestHelper h) {
        Gt.begin(h);
        java.util.function.Predicate<String> all = k -> LedgerLabels.known(k) || k.startsWith("entity.") || k.startsWith("village.");
        Gt.equal("economy.pioneers.kind.pay_in.party", key(LedgerLabels.label("pay_in", "Bob", all, "Alice")), "virement recu : phrase par sorte");
        Gt.equal("economy.pioneers.kind.clan_donation.party", key(LedgerLabels.label("clan_donation", "Les Gaulois", all, "Alice")), "don au clan");
        Gt.equal("economy.pioneers.kind.clan_payment.party", key(LedgerLabels.label("clan_payment", "Les Gaulois", all, "Alice")), "versement du clan");
        Gt.equal("economy.pioneers.kind.sale_bin", key(LedgerLabels.label("sale_bin", "Alice", all, "Alice")), "bac de vente : le joueur lui-meme omis");
        Gt.equal("economy.pioneers.kind.other", key(LedgerLabels.label("mystery_kind", "", all, "Alice")), "sorte inconnue : en clair");
        Gt.equal("entity.pioneers.peddler", key(LedgerLabels.party("entity.pioneers.peddler", all)), "ancienne cle brute de PNJ : traduite");
        Gt.equal("entity.pioneers.peddler", key(LedgerLabels.party("#entity.pioneers.peddler", all)), "nouvelle partie « #cle »");
        Gt.equal("#entity.pioneers.peddler", LedgerLabels.store(Component.translatable("entity.pioneers.peddler")), "nom traduit stocke en #cle");
        Gt.equal("Bob", LedgerLabels.store(Component.literal("Bob")), "nom propre stocke tel quel");
        for (String k : LedgerLabels.KINDS) {
            Gt.check(LedgerLabels.known(LedgerLabels.PREFIX + k), "sorte connue : " + k);
        }
        h.succeed();
    }
}
