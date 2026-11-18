package fr.pioneers.tech.forge.casting;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStackSimple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.2.0 (retour alpha) : seau en argile cuite. Il prend une source de liquide FROID (temperature du fluide au plus {@link #MAX_KELVIN} :
 * eau, boue... jamais la lave), la repose, et se vide ou se remplit dans les reservoirs comme un seau (capacite fluide 1 000 mB dans le NBT).
 * Il ne s'empile pas plein ; un seau vide s'empile par 16.
 */
public class ClayBucketItem extends Item {

    public static final int CAPACITY = 1000;
    public static final int MAX_KELVIN = 400;

    public ClayBucketItem(Properties properties) {
        super(properties);
    }

    public static boolean cold(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().getFluidType().getTemperature(fluid) <= MAX_KELVIN;
    }

    public static FluidStack contents(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return contents(stack).isEmpty() ? 16 : 1;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidHandlerItemStackSimple(stack, CAPACITY) {
            @Override
            public boolean canFillFluidType(FluidStack fluid) {
                return cold(fluid);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                //Un seau se remplit d'un coup (1 000 mB) ou pas du tout, comme un seau vanilla
                return container.getCount() != 1 || resource.getAmount() < CAPACITY ? 0 : super.fill(resource, action);
            }
        };
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        FluidStack inside = contents(held);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, inside.isEmpty() ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(held);
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.mayInteract(player, pos)) {
            return InteractionResultHolder.fail(held);
        }
        if (inside.isEmpty()) {
            BlockState state = level.getBlockState(pos);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isSource() || !(state.getBlock() instanceof BucketPickup pickup)) {
                return InteractionResultHolder.pass(held);
            }
            FluidStack taken = new FluidStack(fluid.getType(), CAPACITY);
            if (!cold(taken)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.pioneers.clay_bucket.too_hot"), true);
                }
                return InteractionResultHolder.fail(held);
            }
            if (!level.isClientSide) {
                pickup.pickupBlock(level, pos, state);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1F, 0.9F);
                ItemStack full = new ItemStack(this);
                FluidUtil.getFluidHandler(full).ifPresent(h -> h.fill(taken, IFluidHandler.FluidAction.EXECUTE));
                //1.3.2 : on renvoie la pile qui doit rester en main (le jeu remplace la main par la pile renvoyee : un seau seul disparaissait)
                return InteractionResultHolder.success(give(player, held, full));
            }
            return InteractionResultHolder.sidedSuccess(held, true);
        }
        Fluid fluid = inside.getFluid();
        BlockPos target = level.getBlockState(pos).canBeReplaced(fluid) ? pos : pos.relative(hit.getDirection());
        if (fluid.getBucket() instanceof BucketItem bucket && fluid != Fluids.EMPTY) {
            if (level.isClientSide) {
                return InteractionResultHolder.sidedSuccess(held, true);
            }
            if (bucket.emptyContents(player, level, target, hit, held)) {
                return InteractionResultHolder.success(player.getAbilities().instabuild ? held : new ItemStack(this));
            }
            return InteractionResultHolder.fail(held);
        }
        return InteractionResultHolder.pass(held);
    }

    /** Seau plein donne au joueur ; renvoie la pile a garder en main. */
    private static ItemStack give(Player player, ItemStack held, ItemStack full) {
        if (player.getAbilities().instabuild) {
            if (!player.getInventory().contains(full)) {
                player.getInventory().add(full);
            }
            return held;
        }
        if (held.getCount() <= 1) {
            return full;
        }
        held.shrink(1);
        if (!player.getInventory().add(full)) {
            player.drop(full, false);
        }
        return held;
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        FluidStack inside = contents(stack);
        return inside.isEmpty() ? super.getName(stack) : Component.translatable("item.pioneers.clay_bucket.filled", inside.getDisplayName());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.clay_bucket.desc");
    }
}
