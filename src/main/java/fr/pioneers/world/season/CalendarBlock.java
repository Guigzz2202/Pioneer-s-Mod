package fr.pioneers.world.season;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 1, specification §9) : le CALENDRIER MURAL. Accroche a un mur comme une echelle ({@link #FACING} = cote ou regarde la
 * page) ; sa page ({@link #SEASON}, 4 dessins) suit la saison du serveur, relue toutes les {@link #REFRESH} ticks (un tick programme par
 * calendrier, rien d'autre). Un COMPARATEUR le lit : 1 printemps, 2 ete, 3 automne, 4 hiver (aussi a travers le mur, comme un cadre). Son
 * objet ({@link CalendarItem}) dit la date en infobulle et en main. Geometrie : {@code tools/gc/seasons/calendar_assets.py} ({@link #BOX}).
 */
public class CalendarBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty SEASON = IntegerProperty.create("season", 0, 3);
    /** Relecture de la saison (ticks). */
    public static final int REFRESH = 100;
    /** Panneau (page au nord, accroche au mur sud) ; memes cotes que le modele ({@code calendar_assets.py BOX}). */
    public static final double[] BOX = {3, 1, 14.4, 13, 15.6, 16};
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = rotated(BOX, d);
        }
    }

    static VoxelShape rotated(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
            case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
            case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
            default -> Block.box(x0, b[1], z0, x1, b[4], z1);
        };
    }

    public CalendarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(SEASON, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEASON);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @NotNull
    @Override
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    /** Saison du serveur (0 a 3), ou celle de l'etat si la date est inconnue. */
    public static int seasonNow(Level level, BlockState state) {
        Seasons.Date d = Seasons.calendar(level);
        return d == null ? state.getValue(SEASON) : d.season().ordinal();
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        Level level = ctx.getLevel();
        int season = seasonNow(level, state);
        for (Direction d : ctx.getNearestLookingDirections()) {
            if (d.getAxis().isHorizontal()) {
                BlockState s = state.setValue(FACING, d.getOpposite()).setValue(SEASON, season);
                if (s.canSurvive(level, ctx.getClickedPos())) {
                    return s;
                }
            }
        }
        return null;
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
              : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, REFRESH);
        }
    }

    /** Page a jour (saison du serveur), puis prochain passage. */
    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        int s = seasonNow(level, state);
        if (s != state.getValue(SEASON)) {
            level.setBlock(pos, state.setValue(SEASON, s), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, REFRESH);
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    /** 1 printemps, 2 ete, 3 automne, 4 hiver. */
    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return state.getValue(SEASON) + 1;
    }

    @NotNull
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Infobulle de l'objet : la date du jour et la saison suivante (client : calendrier recu du serveur). */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        Seasons.Date d = level instanceof Level l ? Seasons.calendar(l) : null;
        if (d == null) {
            tips.state("season.pioneers.unknown");
            return;
        }
        tips.stat("season.pioneers.label.today", SeasonTexts.dateLong(d));
        tips.stat("season.pioneers.label.next", SeasonTexts.next(d));
        tips.stat("season.pioneers.label.year", d.year());
        tips.hint("season.pioneers.calendar.hint");
    }
}
