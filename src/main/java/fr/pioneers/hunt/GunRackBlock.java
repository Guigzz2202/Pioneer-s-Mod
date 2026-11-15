package fr.pioneers.hunt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.3) : le PORTE-FUSIL, bloc mural en bois (11 bois des meubles, {@code fr.pioneers.home.HomeWoods}) qui
 * expose jusqu'a TROIS armes ({@code pioneers:gun_rack_weapons} : mousquets, revolvers, harpons), couchees l'une au-dessus de l'autre sur
 * leurs taquets ({@link GunRackTile}, rendu {@code GunRackRenderer}). Clic droit avec une arme : elle se pose sur le rang vise (ou le premier
 * libre) ; main vide : on reprend l'arme du rang vise. {@link #FACING} = cote vers lequel il regarde (dos au mur).
 */
public class GunRackBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Epaisseur de la planche et des taquets (px), rangs (hauteur du milieu de chaque rang, px, du haut vers le bas). */
    public static final float DEPTH = 4.5F;
    public static final float[] ROWS = {12.5F, 8.0F, 3.5F};
    static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = wall(d, 0, 0.5F, 16, 15.5F, DEPTH);
        }
    }

    /** Boite contre le mur : x de gauche a droite vu de face, y, profondeur depuis le mur. */
    static VoxelShape wall(Direction facing, float x0, float y0, float x1, float y1, float depth) {
        return switch (facing) {
            case SOUTH -> Block.box(x0, y0, 0, x1, y1, depth);
            case NORTH -> Block.box(16 - x1, y0, 16 - depth, 16 - x0, y1, 16);
            case EAST -> Block.box(0, y0, 16 - x1, depth, y1, 16 - x0);
            default -> Block.box(16 - depth, y0, x0, 16, y1, x1);
        };
    }

    public GunRackBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Direction f = clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection().getOpposite();
        BlockState s = defaultBlockState().setValue(FACING, f);
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return level.getBlockState(back).isFaceSturdy(level, back, f);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        return dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                                                                                              : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new GunRackTile(pos, state);
    }

    /** Rang vise (0 = haut) d'apres la hauteur du point clique. */
    public static int rowAt(double yInBlock) {
        double px = yInBlock * 16;
        int best = 0;
        for (int i = 1; i < ROWS.length; i++) {
            if (Math.abs(px - ROWS[i]) < Math.abs(px - ROWS[best])) {
                best = i;
            }
        }
        return best;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GunRackTile tile)) {
            return InteractionResult.PASS;
        }
        int row = rowAt(hit.getLocation().y - pos.getY());
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            if (!GunRackTile.accepts(held)) {
                return InteractionResult.PASS;
            }
            int slot = tile.get(row).isEmpty() ? row : tile.firstFree();
            if (slot < 0) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                tile.set(slot, held.split(1));
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        int slot = !tile.get(row).isEmpty() ? row : tile.firstFull();
        if (slot < 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack out = tile.take(slot);
            player.setItemInHand(hand, out);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof GunRackTile tile) {
            for (int i = 0; i < GunRackTile.SLOTS; i++) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.get(i));
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GunRackTile t ? t.count() * 5 : 0;
    }
}
