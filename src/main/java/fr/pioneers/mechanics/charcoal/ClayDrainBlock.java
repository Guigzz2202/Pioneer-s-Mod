package fr.pioneers.mechanics.charcoal;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.mechanics.PiMechanics;
import fr.pioneers.registries.PiFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drain d'argile cuite : pose sous la couche du bas d'une fosse a charbon, il recueille la creosote qui coule des buches (4 000 mB).
 * Seau vide : on la recupere ; les tuyaux et buffers la tirent par toutes les faces.
 */
public class ClayDrainBlock extends BaseEntityBlock {

    public ClayDrainBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Tile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        //1.0.1 : tout seau vide qui accepte la creosote (le seau en argile la refuse si elle est trop chaude)
        if (tile.tank.getFluidAmount() >= Buckets.VOLUME && Buckets.canFill(held, tile.tank.getFluid().getFluid())) {
            if (!level.isClientSide && Buckets.fillHeld(player, hand, tile.tank.getFluid())) {
                tile.tank.drain(Buckets.VOLUME, IFluidHandler.FluidAction.EXECUTE);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("block.pioneers.clay_drain.content", tile.tank.getFluidAmount(), tile.tank.getCapacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends PiBlockEntity {

        final FluidTank tank = new FluidTank(4_000, stack -> stack.getFluid().isSame(PiFluids.CREOSOTE.source.get())) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        private final LazyOptional<IFluidHandler> cap = LazyOptional.of(() -> tank);

        public Tile(BlockPos pos, BlockState state) {
            super(PiMechanics.CLAY_DRAIN_TILE.get(), pos, state);
        }

        /** 1.4 : creosote recueillie (mB), pour la fiche au regard. */
        public int creosote() {
            return tank.getFluidAmount();
        }

        public int capacity() {
            return tank.getCapacity();
        }

        /** Verse jusqu'a {@code amount} mB ; renvoie ce qui est entre. */
        int collect(int amount) {
            return tank.fill(new FluidStack(PiFluids.CREOSOTE.source.get(), amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return capability == ForgeCapabilities.FLUID_HANDLER ? cap.cast() : super.getCapability(capability, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            cap.invalidate();
        }

        @Override
        protected void saveAdditional(@NotNull CompoundTag tag) {
            super.saveAdditional(tag);
            tag.put("tank", tank.writeToNBT(new CompoundTag()));
        }

        @Override
        public void load(@NotNull CompoundTag tag) {
            super.load(tag);
            tank.readFromNBT(tag.getCompound("tank"));
        }
    }
}
