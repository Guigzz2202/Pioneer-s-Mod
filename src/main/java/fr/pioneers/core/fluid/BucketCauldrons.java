package fr.pioneers.core.fluid;

import fr.pioneers.core.registries.PiForge;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.0.1 : le chaudron vanilla avec le seau en argile (les interactions du chaudron sont des tables par objet : le seau vanilla y est en dur).
 * Seau d'eau : remplit le chaudron (vide, de lave ou de neige poudreuse, comme le seau vanilla) ; seau vide : reprend un chaudron d'eau
 * plein. La lave reste refusee (le seau en argile ne prend que les liquides froids).
 */
public final class BucketCauldrons {

    private BucketCauldrons() {
    }

    /** A appeler une fois (installation commune, file principale). */
    public static void register() {
        Item clay = PiForge.CLAY_BUCKET.get();
        for (Map<Item, CauldronInteraction> map : List.of(CauldronInteraction.EMPTY, CauldronInteraction.LAVA, CauldronInteraction.POWDER_SNOW)) {
            map.put(clay, BucketCauldrons::pour);
        }
        CauldronInteraction.WATER.put(clay, (state, level, pos, player, hand, stack) -> Buckets.isWaterBucket(stack)
              ? pour(state, level, pos, player, hand, stack) : take(state, level, pos, player, hand, stack));
    }

    /** Seau d'eau vide dans le chaudron (plein, niveau 3). */
    static InteractionResult pour(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!Buckets.isWaterBucket(stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Item item = stack.getItem();
            if (!Buckets.drainHeld(player, hand, Fluids.WATER)) {
                return InteractionResult.PASS;
            }
            player.awardStat(Stats.FILL_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1F, 1F);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Seau vide : reprend l'eau d'un chaudron plein. */
    static InteractionResult take(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (state.getValue(LayeredCauldronBlock.LEVEL) < 3 || !Buckets.isEmptyBucket(stack)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Item item = stack.getItem();
            if (!Buckets.fillHeld(player, hand, new FluidStack(Fluids.WATER, Buckets.VOLUME))) {
                return InteractionResult.PASS;
            }
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, 1F);
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
