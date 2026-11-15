package fr.pioneers.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : Tableau des besoins, planche a petits papiers sur la place du village. {@code NOTICES} = papiers d'evenement
 * (0, 1 ou 2) ; les cinq icones de service sont dessinees par le rendu. {@code FACING} = facade (cote lecture).
 */
public class NeedsBoardBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty NOTICES = IntegerProperty.create("notices", 0, 2);
    private static final VoxelShape NS = Block.box(0, 0, 6, 16, 16, 10), EW = Block.box(6, 0, 0, 10, 16, 16);

    public NeedsBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(NOTICES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NOTICES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    /** 1.0.3-beta (lot 11b) : le Tableau des besoins est le bloc coeur d'un village fonde par un joueur ({@link VillageFounding}). */
    @Override
    public void setPlacedBy(@NotNull net.minecraft.world.level.Level level, @NotNull BlockPos pos, @NotNull BlockState state,
          @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, @NotNull net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof net.minecraft.server.level.ServerLevel sl && placer instanceof net.minecraft.server.level.ServerPlayer sp) {
            VillageFounding.onBoardPlaced(sl, pos, sp);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull net.minecraft.world.InteractionResult use(@NotNull BlockState state, @NotNull net.minecraft.world.level.Level level,
          @NotNull BlockPos pos, @NotNull net.minecraft.world.entity.player.Player player, @NotNull net.minecraft.world.InteractionHand hand,
          @NotNull net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && level instanceof net.minecraft.server.level.ServerLevel sl
              && VillageFounding.rename(sl, pos, sp, player.getItemInHand(hand))) {
            //1.0.3-beta (lot 11b) : etiquette nommee : le fondateur renomme son village
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putLong("pos", pos.asLong());
            Villages.boardRequest(sp, t);
            level.playSound(null, pos, fr.pioneers.core.profile.RpSounds.PAPER_PIN.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Village du tableau et services actifs (copie client pour le rendu des icones). */
    public static class Tile extends BlockEntity {

        private long village;
        private final java.util.Set<String> services = new java.util.LinkedHashSet<>();

        public Tile(BlockPos pos, BlockState state) {
            super(VillageBlocks.BOARD_TILE.get(), pos, state);
        }

        public java.util.Set<String> services() {
            return services;
        }

        void update(long village, java.util.Set<String> now) {
            if (this.village == village && services.equals(now)) {
                return;
            }
            this.village = village;
            services.clear();
            services.addAll(now);
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected void saveAdditional(@NotNull net.minecraft.nbt.CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putLong("village", village);
            tag.putString("services", String.join(",", services));
        }

        @Override
        public void load(@NotNull net.minecraft.nbt.CompoundTag tag) {
            super.load(tag);
            village = tag.getLong("village");
            services.clear();
            for (String s : tag.getString("services").split(",")) {
                if (!s.isEmpty()) {
                    services.add(s);
                }
            }
        }

        @Override
        public @NotNull net.minecraft.nbt.CompoundTag getUpdateTag() {
            return saveWithoutMetadata();
        }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
            return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
