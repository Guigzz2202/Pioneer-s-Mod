package fr.pioneers.building.home.beam;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 28, Guillaume : « ajouter des poutres en bois pour la decoration ») : POUTRE DECORATIVE d'une essence. Section carree de
 * {@link #SECTION} px (de {@link #FROM} a {@link #TO}) le long de l'axe choisi a la pose (comme une buche : face cliquee) ; deux poutres
 * alignees se raccordent bout a bout (la forme d'occlusion est la section : les faces de bout qui se touchent ne sont pas dessinees) et
 * les arretes sont chanfreinees dans la texture. Modeles et textures : {@code tools/gc/home/beams.py} (memes cotes).
 * <p>
 * 1.0.4-beta (lot 33, Guillaume : « La poutre doit mieux se connecter entre elles quand elles se croisent ») : JOINTS ({@link #joint}).
 * Vers une case voisine perpendiculaire a son axe qui tient une poutre d'un AUTRE axe, la poutre pousse un TENON de section pleine jusqu'a
 * la limite de la case ({@link #GAP} px) : une poutre qui arrive en bout (croix, T, angle, poteau qui traverse) ferme le jour ; deux poutres
 * croisees a des hauteurs differentes ou un poteau contre une poutre se rejoignent par leurs deux tenons. Une poutre HORIZONTALE posee sur
 * (sous) une face pleine qui n'est pas une poutre a une CALE de {@link #GAP} px sur toute sa longueur (le jour sous les poutres). Etats :
 * {@code north..west} (tenon), {@code up} / {@code down} ({@link Joint}) ; formes = poutre + tenons + cales (occlusion comprise : les faces
 * qui se touchent ne sont pas dessinees) ; regle et boites = {@code beams.py joint / boxes} (controle {@code tools/check_p104_33.py}).
 */
public class TimberBeamBlock extends RotatedPillarBlock {

    public static final double FROM = 2, TO = 14, SECTION = TO - FROM, GAP = FROM;

    /** Raccord vers le haut ou le bas : rien, tenon (poutre d'un autre axe), cale (face pleine, poutre horizontale). */
    public enum Joint implements StringRepresentable {
        NONE, JOINT, SEAT;

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
          WEST = BlockStateProperties.WEST;
    public static final EnumProperty<Joint> UP = EnumProperty.create("up", Joint.class), DOWN = EnumProperty.create("down", Joint.class);

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public TimberBeamBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
              .setValue(UP, Joint.NONE).setValue(DOWN, Joint.NONE));
        for (BlockState s : getStateDefinition().getPossibleStates()) {
            shapes.put(s, build(s));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /**
     * REGLE DES JOINTS (pure ; = {@code beams.py joint}) : poutre d'axe {@code axis}, direction {@code d}, voisine : poutre ({@code beam},
     * son axe {@code other}) ou bloc dont la face tournee vers nous est pleine ({@code sturdy}). Bout de la poutre : rien (raccord bout a
     * bout) ; poutre d'un autre axe : tenon ; poutre parallele : rien ; face pleine en haut / en bas d'une poutre horizontale : cale.
     */
    public static Joint joint(Direction.Axis axis, Direction d, boolean beam, @Nullable Direction.Axis other, boolean sturdy) {
        if (d.getAxis() == axis) {
            return Joint.NONE;
        }
        if (beam) {
            return other != axis ? Joint.JOINT : Joint.NONE;
        }
        return d.getAxis() == Direction.Axis.Y && sturdy ? Joint.SEAT : Joint.NONE;
    }

    /** Raccord d'une poutre vers {@code d} d'apres le monde. */
    public static Joint joint(BlockState self, LevelAccessor level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        BlockState ns = level.getBlockState(n);
        boolean beam = ns.getBlock() instanceof TimberBeamBlock;
        return joint(self.getValue(AXIS), d, beam, beam ? ns.getValue(AXIS) : null, !beam && ns.isFaceSturdy(level, n, d.getOpposite()));
    }

    /** Etat avec tous ses raccords recalcules. */
    public static BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = with(state, d, joint(state, level, pos, d));
        }
        return state;
    }

    static BlockState with(BlockState state, Direction d, Joint j) {
        return switch (d) {
            case NORTH -> state.setValue(NORTH, j == Joint.JOINT);
            case EAST -> state.setValue(EAST, j == Joint.JOINT);
            case SOUTH -> state.setValue(SOUTH, j == Joint.JOINT);
            case WEST -> state.setValue(WEST, j == Joint.JOINT);
            case UP -> state.setValue(UP, j);
            case DOWN -> state.setValue(DOWN, j);
        };
    }

    static Joint of(BlockState state, Direction d) {
        return switch (d) {
            case NORTH -> state.getValue(NORTH) ? Joint.JOINT : Joint.NONE;
            case EAST -> state.getValue(EAST) ? Joint.JOINT : Joint.NONE;
            case SOUTH -> state.getValue(SOUTH) ? Joint.JOINT : Joint.NONE;
            case WEST -> state.getValue(WEST) ? Joint.JOINT : Joint.NONE;
            case UP -> state.getValue(UP);
            case DOWN -> state.getValue(DOWN);
        };
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : connect(s, ctx.getLevel(), ctx.getClickedPos());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction d, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos from) {
        return with(state, d, joint(state, level, pos, d));
    }

    @NotNull
    @Override
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rot) {
        BlockState out = super.rotate(state, rot);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = with(out, rot.rotate(d), of(state, d));
        }
        return out;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = with(out, mirror.mirror(d), of(state, d));
        }
        return out;
    }

    public static VoxelShape shapeOf(Direction.Axis axis) {
        return switch (axis) {
            case X -> Block.box(0, FROM, FROM, 16, TO, TO);
            case Y -> Block.box(FROM, 0, FROM, TO, 16, TO);
            case Z -> Block.box(FROM, FROM, 0, TO, TO, 16);
        };
    }

    /** Tenon vers {@code d} (= {@code beams.py stub_box}). */
    public static VoxelShape stub(Direction d) {
        double[] lo = {FROM, FROM, FROM}, hi = {TO, TO, TO};
        int i = d.getAxis().ordinal();
        if (d.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            lo[i] = 0;
            hi[i] = FROM;
        } else {
            lo[i] = TO;
            hi[i] = 16;
        }
        return Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    /** Cale d'une poutre horizontale d'axe {@code axis} vers le bas ou le haut (= {@code beams.py seat_box}). */
    public static VoxelShape seat(Direction.Axis axis, Direction d) {
        double y0 = d == Direction.DOWN ? 0 : TO, y1 = d == Direction.DOWN ? FROM : 16;
        return axis == Direction.Axis.X ? Block.box(0, y0, FROM, 16, y1, TO) : Block.box(FROM, y0, 0, TO, y1, 16);
    }

    private static VoxelShape build(BlockState s) {
        Direction.Axis axis = s.getValue(AXIS);
        VoxelShape out = shapeOf(axis);
        for (Direction d : Direction.values()) {
            Joint j = of(s, d);
            if (j == Joint.JOINT) {
                out = Shapes.or(out, stub(d));
            } else if (j == Joint.SEAT && axis != Direction.Axis.Y) {
                out = Shapes.or(out, seat(axis, d));
            }
        }
        return out.optimize();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapes.getOrDefault(state, shapeOf(state.getValue(AXIS)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOcclusionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return shapes.getOrDefault(state, shapeOf(state.getValue(AXIS)));
    }

    @Override
    public boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return true;
    }
}
