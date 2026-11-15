package fr.pioneers.farm.crustacean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 11, Guillaume : « le filet de peche a une texture connectee quand ils se collent ») : filet de peche dont les voisins DANS
 * SON PLAN (meme axe, quel que soit le sens de la facade) se raccordent en un seul grand filet : pas de piquet du cote d'un voisin, filet
 * jusqu'au bord ; pas de ralingue (flotteurs, plombs) du cote d'un filet au-dessus / au-dessous. {@link #LEFT} / {@link #RIGHT} dans le
 * repere du modele (gauche = sens antihoraire de la facade, comme les meubles) ; modeles multipart de T:farm2/art_crustaceans.py net_parts.
 * Recalcule a la pose et a chaque changement de voisin. Les filets poses avant prennent leurs liaisons au premier changement voisin (etat
 * par defaut : seul, le dessin d'avant).
 */
public class FishingNetBlock extends TrapBlock {

    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public FishingNetBlock(Properties properties) {
        super(Kind.NET, properties);
        registerDefaultState(defaultBlockState().setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEFT, RIGHT, UP, DOWN);
    }

    /** Le voisin est-il un filet du meme plan (meme axe de facade) ? */
    public static boolean joins(BlockState self, BlockState other) {
        return other.getBlock() instanceof FishingNetBlock && other.getValue(FACING).getAxis() == self.getValue(FACING).getAxis();
    }

    /** Liaisons de {@code s} d'apres ses quatre voisins dans son plan. */
    public static BlockState connect(BlockState s, LevelReader level, BlockPos pos) {
        Direction f = s.getValue(FACING);
        return s.setValue(LEFT, joins(s, level.getBlockState(pos.relative(f.getCounterClockWise()))))
              .setValue(RIGHT, joins(s, level.getBlockState(pos.relative(f.getClockWise()))))
              .setValue(UP, joins(s, level.getBlockState(pos.above())))
              .setValue(DOWN, joins(s, level.getBlockState(pos.below())));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        return s == null ? null : connect(s, context.getLevel(), context.getClickedPos());
    }

    @NotNull
    @Override
    public BlockState updateShape(BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        BlockState s = super.updateShape(state, dir, other, level, pos, otherPos);
        return s.getBlock() instanceof FishingNetBlock ? connect(s, level, pos) : s;
    }
}
