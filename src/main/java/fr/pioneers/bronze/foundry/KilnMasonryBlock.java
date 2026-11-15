package fr.pioneers.bronze.foundry;

import fr.pioneers.bronze.HeatPalette;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 23, Guillaume : « donner un modele fini au four de potier et une cheminee ») : MACONNERIE FINIE du four de potier forme.
 * La structure de Guillaume est en briques, escaliers et dalle de briques de Minecraft (lettres {@code r / _} de {@code pottery_kiln}) :
 * des blocs vanilla qu'aucun etat ne peut peindre. Quand le four se forme, sa tuile ({@link KilnMasonry#dress}) remplace chacun d'eux par ce
 * bloc, qui garde la MEME FORME (et la meme collision) que le bloc d'origine ({@link Form}, {@link #FACING}) et y ajoute un role
 * ({@link Role} : socle, corps, cheminee, haut de cheminee a conduit ouvert) et le palier de chauffe commun ({@link #HEAT},
 * {@link HeatPalette}) ; quand la structure se defait ou que la bouche est cassee, les blocs d'origine reviennent
 * ({@link KilnMasonry#undress}). Casse : rend le bloc d'origine (table de butin par forme) ; choix du bloc : l'objet d'origine. Un bloc
 * orphelin (tuile disparue sans le defaire) redevient vanilla au premier tick aleatoire ({@link #randomTick}).
 */
public class KilnMasonryBlock extends Block {

    /** Forme du bloc d'origine (et sa collision). */
    public enum Form implements StringRepresentable {
        BRICKS, STAIR_BOTTOM, STAIR_TOP, SLAB_BOTTOM, SLAB_TOP, SLAB_DOUBLE;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean stair() {
            return this == STAIR_BOTTOM || this == STAIR_TOP;
        }

        public boolean slab() {
            return this == SLAB_BOTTOM || this == SLAB_TOP || this == SLAB_DOUBLE;
        }
    }

    /** Role dans le four forme (le dessin) ; meme regle que {@code T:core/p105_lot23.py kiln_role}. */
    public enum Role implements StringRepresentable {
        BASE, BODY, CHIMNEY, CHIMNEY_TOP;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);
    public static final EnumProperty<Role> ROLE = EnumProperty.create("role", Role.class);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HEAT = HeatPalette.HEAT;

    private static final Map<Direction, VoxelShape> STAIR_BOTTOM = new EnumMap<>(Direction.class), STAIR_TOP = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            //Partie haute de la marche du cote FACING (comme les escaliers de Minecraft)
            VoxelShape half = switch (d) {
                case NORTH -> Block.box(0, 0, 0, 16, 16, 8);
                case SOUTH -> Block.box(0, 0, 8, 16, 16, 16);
                case WEST -> Block.box(0, 0, 0, 8, 16, 16);
                default -> Block.box(8, 0, 0, 16, 16, 16);
            };
            STAIR_BOTTOM.put(d, Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Shapes.join(half, Block.box(0, 8, 0, 16, 16, 16), net.minecraft.world.phys.shapes.BooleanOp.AND)));
            STAIR_TOP.put(d, Shapes.or(Block.box(0, 8, 0, 16, 16, 16), Shapes.join(half, Block.box(0, 0, 0, 16, 8, 16), net.minecraft.world.phys.shapes.BooleanOp.AND)));
        }
    }

    private static final VoxelShape SLAB_BOTTOM = Block.box(0, 0, 0, 16, 8, 16), SLAB_TOP = Block.box(0, 8, 0, 16, 16, 16);

    public KilnMasonryBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(HEAT) * HeatPalette.LIGHT_PER_STEP).randomTicks());
        registerDefaultState(defaultBlockState().setValue(FORM, Form.BRICKS).setValue(ROLE, Role.BODY).setValue(FACING, Direction.NORTH).setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORM, ROLE, FACING, HEAT);
    }

    /** Forme (collision et visee) du bloc d'origine. */
    public static VoxelShape shapeOf(BlockState state) {
        return switch (state.getValue(FORM)) {
            case STAIR_BOTTOM -> STAIR_BOTTOM.get(state.getValue(FACING));
            case STAIR_TOP -> STAIR_TOP.get(state.getValue(FACING));
            case SLAB_BOTTOM -> SLAB_BOTTOM;
            case SLAB_TOP -> SLAB_TOP;
            default -> Shapes.block();
        };
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapeOf(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        Form f = state.getValue(FORM);
        return f != Form.BRICKS && f != Form.SLAB_DOUBLE;
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        Form f = state.getValue(FORM);
        return new ItemStack(f.stair() ? Items.BRICK_STAIRS : f.slab() ? Items.BRICK_SLAB : Items.BRICKS);
    }

    /** Orphelin (aucun four de potier forme ne le compte dans sa structure) : redevient le bloc d'origine. */
    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!KilnMasonry.claimed(level, pos)) {
            level.setBlock(pos, KilnMasonry.vanilla(state), Block.UPDATE_ALL);
        }
    }
}
