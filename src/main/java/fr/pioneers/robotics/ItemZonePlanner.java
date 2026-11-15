package fr.pioneers.robotics;

import fr.pioneers.client.robotics.ClientRobotics;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Planificateur de zone : clic droit sur un premier coin, puis sur le coin oppose ; un menu propose la tache (agriculture,
 * bucheronnage, minage...) et l'affectation. Accroupi + clic droit : efface le coin choisi. En main, les zones sont visibles.
 */
public class ItemZonePlanner extends Item {

    private static final String CORNER = "corner";

    public ItemZonePlanner() {
        super(new Properties().stacksTo(1));
    }

    @Nullable
    public static BlockPos corner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(CORNER) ? NbtUtils.readBlockPos(tag.getCompound(CORNER)) : null;
    }

    /** Avant l'action du bloc : un coffre ou une machine vises ne s'ouvrent pas, ils servent de coin. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            clear(stack, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos pos = context.getClickedPos();
        BlockPos first = corner(stack);
        if (first == null) {
            stack.getOrCreateTag().put(CORNER, NbtUtils.writeBlockPos(pos));
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("robotics.pioneers.planner.first", pos.toShortString())
                      .withStyle(ChatFormatting.AQUA), true);
            }
        } else {
            stack.removeTagKey(CORNER);
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.openNewZone(first, pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            clear(stack, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        //Clic droit dans le vide : le planificateur 3D s'ouvre en vue libre, sur ce que l'on regarde
        if (corner(stack) == null) {
            if (level.isClientSide) {
                HitResult hit = player.pick(96, 1, false);
                Vec3 at = hit.getType() == HitResult.Type.MISS ? player.position() : hit.getLocation();
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.planZoneAt(null, at.x, at.y, at.z));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static void clear(ItemStack stack, Player player) {
        if (corner(stack) != null) {
            stack.removeTagKey(CORNER);
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("robotics.pioneers.planner.cleared").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.zone_planner.desc");
        BlockPos first = corner(stack);
        if (first != null) {
            tips.state("robotics.pioneers.planner.corner", first.toShortString());
        }
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return corner(stack) != null;
    }
}
