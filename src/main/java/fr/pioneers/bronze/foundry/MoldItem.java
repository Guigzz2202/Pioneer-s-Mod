package fr.pioneers.bronze.foundry;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.forge.casting.AnvilMoldBlock;
import fr.pioneers.forge.casting.AnvilMoldTile;
import fr.pioneers.forge.casting.PlacedMoldBlock;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Moule de coulee (argile cuite) : se pose sur la table de coulee, ou a meme le sol (clic droit sur le dessus d'un bloc plein : bloc
 * {@code placed_mold}, forge primaire) ; s'use a chaque piece. Le creuset est un « moule » particulier (il ne se pose pas).
 */
public class MoldItem extends Item {

    public final String shape;

    public MoldItem(String shape, Properties properties) {
        super(properties);
        this.shape = shape;
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        boolean anvil = AnvilMoldTile.SHAPE.equals(shape);
        if (context.getClickedFace() != Direction.UP || PlacedMoldBlock.Shape.of(shape) == null && !anvil) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            Direction facing = context.getHorizontalDirection().getOpposite();
            //1.0.2-beta (lot 2) : le moule d'enclume se pose en bloc complet (AnvilMoldBlock)
            if (!(anvil ? AnvilMoldBlock.place(level, context.getClickedPos(), facing, stack)
                        : PlacedMoldBlock.place(level, context.getClickedPos(), facing, stack))) {
                return InteractionResult.FAIL;
            }
            level.playSound(null, context.getClickedPos().above(), SoundEvents.DECORATED_POT_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        } else if (!anvil && !PlacedMoldBlock.supports(level, context.getClickedPos()) || !level.getBlockState(context.getClickedPos().above()).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(shape.equals("crucible") ? "tooltip.pioneers.crucible" : AnvilMoldTile.SHAPE.equals(shape) ? "tooltip.pioneers.anvil_mold" : "tooltip.pioneers.mold");
        tips.spec("tooltip.pioneers.uses_left", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage());
    }

    /** Use le moule d'un point ; renvoie la pile restante (vide s'il casse). */
    public static ItemStack wear(ItemStack mold) {
        ItemStack used = mold.copy();
        used.setDamageValue(used.getDamageValue() + 1);
        return used.getDamageValue() >= used.getMaxDamage() ? ItemStack.EMPTY : used;
    }
}
