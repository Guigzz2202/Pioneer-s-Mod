package fr.pioneers.farm.device;

import fr.pioneers.core.block.DescribedBlockItem;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Objet de l'arroseur : vise une source d'eau (le trou d'eau d'un champ), il se pose dessus et pompera seul ; sinon il se pose comme un bloc.
 */
public class SprinklerItem extends DescribedBlockItem {

    public SprinklerItem(Block block, Properties properties, String description) {
        super(block, properties, description);
    }

    /** Source d'eau visee plus pres que le bloc vise : le coup sur la surface de l'eau. */
    private static BlockHitResult water(Level level, Player player) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return hit;
        }
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null) {
            BlockHitResult w = water(context.getLevel(), player);
            if (w != null) {
                BlockHitResult above = w.withPosition(w.getBlockPos().above()).withDirection(Direction.UP);
                return super.useOn(new UseOnContext(player, context.getHand(), above));
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        BlockHitResult w = water(level, player);
        if (w != null) {
            BlockHitResult above = w.withPosition(w.getBlockPos().above()).withDirection(Direction.UP);
            InteractionResult r = super.useOn(new UseOnContext(player, hand, above));
            return new InteractionResultHolder<>(r, player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }
}
