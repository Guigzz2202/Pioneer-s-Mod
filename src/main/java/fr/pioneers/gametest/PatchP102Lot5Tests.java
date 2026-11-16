package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldUnit;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.casting.AnvilMoldBlock;
import fr.pioneers.tech.forge.casting.AnvilMoldTile;
import fr.pioneers.tech.forge.casting.PlacedMoldTile;
import fr.pioneers.tech.forge.smithing.AnvilTile;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.forge.PiForge;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.2-beta, lot 5 (GameTests) : plus d'enclume en bronze. Le moule d'enclume ne donne plus que l'enclume en fer (10 lingots, 1 440 mB,
 * en plusieurs coulees de creuset en argile ; le bronze est refuse) ; un moule d'avant qui gardait du bronze partiel le rend en lingots ;
 * l'empreinte « enclume » du moule automatique refuse le bronze et demande 1 440 mB de fer ; l'enclume en pierre et l'enclume en bronze
 * (bloc et objet) sont remappees vers l'enclume en fer, seule enclume, qui forge tous les metaux forgeables a la main et garde la piece posee
 * (meme tile {@code forge_anvil}).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP102Lot5Tests {

    private PatchP102Lot5Tests() {
    }

    /** Piece figee du moule (cle NBT {@code result}, sans accesseur public). */
    private static ItemStack result(PlacedMoldTile mold) {
        return ItemStack.of(mold.saveWithoutMetadata().getCompound("result"));
    }

    // ================================================================== moule d'enclume : fer seulement, en 5 coulees
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_5_anvil_mold", timeoutTicks = 700)
    public static void p102_5_moule_enclume_fer(GameTestHelper h) {
        Gt.begin(h);
        BlockPos support = new BlockPos(4, 1, 4);
        h.setBlock(support, Blocks.STONE);
        BlockPos at = support.above();
        Gt.check(AnvilMoldBlock.place(h.getLevel(), h.absolutePos(support), Direction.SOUTH, new ItemStack(PiBronze.ANVIL_MOLD.get())), "moule pose");
        AnvilMoldTile mold = (AnvilMoldTile) h.getBlockEntity(at);
        Gt.check(mold.recipeFor("bronze") == null, "plus de coulee d'enclume en bronze");
        CastingRecipe iron = mold.recipeFor(AnvilMoldTile.METAL);
        Gt.check(iron != null, "coulee de l'enclume en fer");
        Gt.equal(1440, iron.amount(), "10 lingots de fer");
        Gt.check(iron.result().is(PiForge.IRON_ANVIL.asItem()), "le moule donne l'enclume en fer");
        Gt.equal(0, mold.pour("bronze", 288, 1100), "le bronze est refuse");
        //Le fer fond au creuset en argile avec le soufflet (feu de camp + charbon de bois + soufflet)
        Gt.check(ForgeMetals.crucible("iron"), "le fer se fond au creuset en argile");
        Gt.check(ForgeHeat.CAMPFIRE_C + ForgeHeat.BELLOWS_MAX_C >= ForgeMetals.meltingC("iron"),
              "le soufflet suffit a fondre le fer");
        for (int i = 0; i < 5; i++) {
            Gt.equal(288, mold.pour("iron", 288, 1600), "coulee de creuset " + (i + 1));
        }
        Gt.equal(0, mold.pour("iron", 288, 1600), "moule plein");
        h.succeedWhen(() -> {
            Gt.check(mold.done(), "enclume en fer pas encore figee");
            Gt.check(result(mold).is(PiForge.IRON_ANVIL.asItem()), "enclume en fer");
        });
    }

    // ================================================================== bronze partiel d'un monde d'avant : rendu en lingots, rien ne se perd
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_5_anvil_mold_old", timeoutTicks = 60)
    public static void p102_5_moule_enclume_bronze_d_avant(GameTestHelper h) {
        Gt.begin(h);
        BlockPos support = new BlockPos(4, 1, 4);
        h.setBlock(support, Blocks.STONE);
        BlockPos at = support.above();
        Gt.check(AnvilMoldBlock.place(h.getLevel(), h.absolutePos(support), Direction.SOUTH, new ItemStack(PiBronze.ANVIL_MOLD.get())), "moule pose");
        AnvilMoldTile mold = (AnvilMoldTile) h.getBlockEntity(at);
        CompoundTag tag = mold.saveWithoutMetadata();
        tag.putString("metal", "bronze");
        tag.putInt("mb", 576);
        tag.putInt("amount", 1440);
        tag.putInt("wait", PlacedMoldTile.PARTIAL_TICKS);
        mold.load(tag);
        Gt.equal(576, mold.amount(), "bronze partiel relu");
        h.succeedWhen(() -> {
            Gt.check(mold.done(), "le bronze partiel n'a pas encore fige");
            Gt.check(ItemStack.isSameItem(result(mold), ForgeMetals.ingot("bronze", 1)), "rendu en lingots de bronze");
            Gt.equal(4, result(mold).getCount(), "576 mB = 4 lingots");
        });
    }

    // ================================================================== moule automatique, empreinte enclume : fer seulement
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_5_auto_anvil", timeoutTicks = 20)
    public static void p102_5_moule_auto_enclume(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, PiBronze.AUTO_MOLD.getBlock().defaultBlockState());
        AutoMoldUnit u = ((AutoMoldTile) h.getBlockEntity(at)).mold();
        Gt.check(u.setForm(AutoMoldBlock.Form.ANVIL), "empreinte enclume");
        Gt.equal(0, u.wants("bronze"), "le bronze est refuse");
        Gt.equal(0, u.receive("bronze", 1440), "aucune coulee de bronze");
        Gt.equal(1440, u.wants("iron"), "1 440 mB de fer");
        CastingRecipe r = u.recipeFor("iron");
        Gt.check(r != null && r.result().is(PiForge.IRON_ANVIL.asItem()), "l'empreinte donne l'enclume en fer");
        s.done();
    }

    // ================================================================== remaps et enclume unique
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_5_remaps", timeoutTicks = 20)
    public static void p102_5_remaps_enclume(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(!ForgeRegistries.ITEMS.containsKey(Pioneers.rl("bronze_anvil")), "objet enclume en bronze encore enregistre");
        Gt.check(!ForgeRegistries.BLOCKS.containsKey(Pioneers.rl("bronze_anvil")), "bloc enclume en bronze encore enregistre");
        for (String gone : List.of("bronze_anvil", "stone_anvil")) {
            Gt.equal("iron_anvil", PiRemaps.simplifiedBlock(gone), "remap du bloc " + gone);
            Gt.equal("iron_anvil", PiRemaps.simplifiedItem(gone), "remap de l'objet " + gone);
        }
        Gt.equal(List.of(PiForge.IRON_ANVIL), PiForge.ANVILS, "une seule enclume");
        //L'enclume en fer forge tous les metaux forgeables a la main (cuivre, or, etain, plomb, zinc, soudure, bronze, laiton, fer, acier)
        BlockPos at = new BlockPos(3, 2, 3);
        h.setBlock(at, PiForge.IRON_ANVIL.getBlock().defaultBlockState());
        AnvilTile anvil = (AnvilTile) h.getBlockEntity(at);
        for (String metal : ForgeMetals.forgeableMetals()) {
            Gt.check(ForgeMetals.anvilTier(metal) <= anvil.tier(), "l'enclume en fer ne forge pas : " + metal);
        }
        //Une enclume en bronze remappee garde sa piece : meme tile (forge_anvil), cle « piece » relue
        CompoundTag tag = anvil.saveWithoutMetadata();
        tag.put("piece", HeatedMetal.create("copper", ForgeMetals.INGOT_MB, 700, h.getLevel()).save(new CompoundTag()));
        anvil.load(tag);
        Gt.check(!anvil.piece().isEmpty() && "copper".equals(HeatedMetal.metal(anvil.piece())), "piece en cours relue sur l'enclume en fer");
        s.done();
    }
}
