package fr.pioneers.naval;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.14 : dalle de quai : bloc plein en beton. {@link #FACING} (vers l'eau) sert a la capitainerie pour tourner les equipements.
 * <p>
 * 0.20.25 : dalle <b>connectee</b> (demande de Guillaume : « parfaitement connectee, notamment dans les coins ; les quais sous d'autres quais
 * se connectent pour ne pas afficher la bande jaune ») : la bande jaune et noire court le long de chaque bord du dessus qui donne sur l'eau
 * (ou le vide) — {@link #NORTH}... ; aux angles rentrants, un carre de bande ({@link #NE}...) ; une defense en caoutchouc sur chaque flanc
 * a l'eau. Une dalle surmontee d'une piece du port ({@link #COVERED}) n'a ni bande ni defense ni rebord : un mur de quai uni. Etats
 * recalcules a chaque changement d'un voisin direct ; les diagonales sont prevenues a la pose et a la casse d'une dalle, et la capitainerie
 * repasse sur tout son plan a chaque releve.
 */
public class QuayBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
          WEST = BlockStateProperties.WEST;
    public static final BooleanProperty NE = BooleanProperty.create("ne"), NW = BooleanProperty.create("nw"), SE = BooleanProperty.create("se"),
          SW = BooleanProperty.create("sw");
    public static final BooleanProperty COVERED = BooleanProperty.create("covered");

    public QuayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(NORTH, false).setValue(EAST, false)
              .setValue(SOUTH, false).setValue(WEST, false).setValue(NE, false).setValue(NW, false).setValue(SE, false).setValue(SW, false)
              .setValue(COVERED, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, EAST, SOUTH, WEST, NE, NW, SE, SW, COVERED);
    }

    /** Piece du port : dalle, equipement de quai ou capitainerie. */
    public static boolean part(BlockState st) {
        return st.getBlock() instanceof QuayBlock || st.getBlock() instanceof HarborEquipmentBlock || st.getBlock() instanceof HarborBlock;
    }

    /** Case qui ne cache pas un bord : ni piece du port, ni bloc plein (eau, air, plante...). */
    private static boolean open(BlockGetter level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (part(st)) {
            return false;
        }
        return st.isAir() || !st.getFluidState().isEmpty() || st.getCollisionShape(level, p).isEmpty();
    }

    /** Etat connecte de la dalle en {@code pos}. */
    public static BlockState connect(BlockGetter level, BlockPos pos, BlockState state) {
        boolean covered = part(level.getBlockState(pos.above()));
        boolean n = !covered && open(level, pos.north()), e = !covered && open(level, pos.east()), s = !covered && open(level, pos.south());
        boolean w = !covered && open(level, pos.west());
        return state.setValue(COVERED, covered).setValue(NORTH, n).setValue(EAST, e).setValue(SOUTH, s).setValue(WEST, w)
              .setValue(NE, !covered && !n && !e && open(level, pos.north().east()))
              .setValue(NW, !covered && !n && !w && open(level, pos.north().west()))
              .setValue(SE, !covered && !s && !e && open(level, pos.south().east()))
              .setValue(SW, !covered && !s && !w && open(level, pos.south().west()));
    }

    /** Recalcule la dalle en {@code pos} si c'en est une (sans prevenir les voisins). */
    public static void refresh(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof QuayBlock) {
            BlockState next = connect(level, pos, st);
            if (next != st) {
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Dalles autour d'une case changee (diagonales et dessous : les voisins directs le font eux-memes). */
    public static void refreshAround(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) {
                    refresh(level, pos.offset(dx, 0, dz));
                }
            }
        }
        refresh(level, pos.below());
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        return connect(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return connect(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            refreshAround(level, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!level.isClientSide && !next.is(this)) {
            refreshAround(level, pos);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
}
