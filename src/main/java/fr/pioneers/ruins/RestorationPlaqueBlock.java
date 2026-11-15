package fr.pioneers.ruins;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : plaque de restauration, posee contre la facade d'une machine remise en service. Elle porte le nom des
 * contributeurs (par part apportee) ; un clic les lit. {@code FACING} = cote vers lequel la plaque regarde.
 */
public class RestorationPlaqueBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape[] SHAPES = {
          Block.box(2, 3, 15, 14, 13, 16), Block.box(2, 3, 0, 14, 13, 1), Block.box(15, 3, 2, 16, 13, 14), Block.box(0, 3, 2, 1, 13, 14)};

    public RestorationPlaqueBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue() & 3];
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Tile tile) {
            //1.9 (MAJ RP, lot 18) : la devise gravee en tete (brief §8)
            player.sendSystemMessage(Component.translatable("ruins.pioneers.plaque.motto").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
            player.sendSystemMessage(Component.translatable("ruins.pioneers.plaque.title", "MK" + "I".repeat(Math.max(1, tile.tier)))
                  .withStyle(ChatFormatting.GOLD));
            if (tile.names.isEmpty()) {
                player.sendSystemMessage(Component.translatable("ruins.pioneers.plaque.nobody").withStyle(ChatFormatting.GRAY));
            } else {
                player.sendSystemMessage(Component.literal(String.join(", ", tile.names)).withStyle(ChatFormatting.YELLOW));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Noms graves sur la plaque (douze au plus) et tier de la machine restauree. */
    public static class Tile extends BlockEntity {

        List<String> names = List.of();
        int tier = 1;

        public Tile(BlockPos pos, BlockState state) {
            super(RuinBlocks.PLAQUE_TILE.get(), pos, state);
        }

        public void setNames(List<String> names, int tier) {
            this.names = List.copyOf(names);
            this.tier = tier;
            setChanged();
        }

        @Override
        protected void saveAdditional(@NotNull net.minecraft.nbt.CompoundTag tag) {
            super.saveAdditional(tag);
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            names.forEach(n -> list.add(net.minecraft.nbt.StringTag.valueOf(n)));
            tag.put("names", list);
            tag.putInt("tier", tier);
        }

        @Override
        public void load(@NotNull net.minecraft.nbt.CompoundTag tag) {
            super.load(tag);
            List<String> out = new java.util.ArrayList<>();
            tag.getList("names", net.minecraft.nbt.Tag.TAG_STRING).forEach(t -> out.add(t.getAsString()));
            names = out;
            tier = Math.max(1, tag.getInt("tier"));
        }
    }
}
