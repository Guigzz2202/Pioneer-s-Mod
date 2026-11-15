package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.bronze.foundry.MoldItem;
import fr.pioneers.tech.forge.ForgeBlock;
import fr.pioneers.core.registries.PiForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Moule pose (bloc sans objet) : pose par clic droit d'un moule cuit sur le dessus d'un bloc solide ({@link #place}, appele par
 * {@code MoldItem.useOn}) ; la tuile garde le moule. {@link #SHAPE} = l'empreinte dessinee par le modele (meme geometrie que
 * {@link Shape#cavities}, utilisee par le rendu du metal ; copie dans {@code tools/gc/forge/casting.py}). Tombe si son appui disparait (la
 * tuile rend alors le moule).
 */
public class PlacedMoldBlock extends ForgeBlock {

    /** Formes de moule qui se posent (les moules de tetes d'outils sont retires du jeu : ils ne se posent pas). */
    public enum Shape implements StringRepresentable {
        INGOT("ingot", 1, new float[][]{{4, 5, 12, 11}}),
        NUGGET("nugget", 1.5F, new float[][]{{6, 6, 10, 10}}),
        PLATE("plate", 2, new float[][]{{3, 3, 13, 13}}),
        ROD("rod", 1.5F, new float[][]{{2, 5, 14, 7}, {2, 9, 14, 11}}),
        GEAR("gear", 1, new float[][]{{5, 5, 11, 11}, {7, 3, 9, 13}, {3, 7, 13, 9}});

        private final String id;
        /** Fond de l'empreinte (px) ; le dessus du moule est a {@link #TOP}. */
        public final float floor;
        /** Rectangles de l'empreinte {x0, z0, x1, z1} en px, moule tourne vers le sud. */
        public final float[][] cavities;

        Shape(String id, float floor, float[][] cavities) {
            this.id = id;
            this.floor = floor;
            this.cavities = cavities;
        }

        @NotNull
        @Override
        public String getSerializedName() {
            return id;
        }

        @Nullable
        public static Shape of(String mold) {
            for (Shape s : values()) {
                if (s.id.equals(mold)) {
                    return s;
                }
            }
            return null;
        }
    }

    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    /** Dessus du moule (px). */
    public static final float TOP = 3;

    public PlacedMoldBlock(Properties properties) {
        super(properties, PlacedMoldTile::new, Block.box(1, 0, 1, 15, 3, 15));
        registerDefaultState(defaultBlockState().setValue(SHAPE, Shape.INGOT));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE);
    }

    /** Vrai si un moule tient sur ce bloc (dessus plein). */
    public static boolean supports(LevelReader level, BlockPos support) {
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    /** Pose un moule cuit au-dessus de {@code support} ; vrai si c'est fait. */
    public static boolean place(Level level, BlockPos support, Direction facing, ItemStack mold) {
        if (!(mold.getItem() instanceof MoldItem item)) {
            return false;
        }
        Shape shape = Shape.of(item.shape);
        BlockPos pos = support.above();
        if (shape == null || !supports(level, support) || !level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        BlockState state = PiForge.PLACED_MOLD.get().defaultBlockState().setValue(FACING, facing).setValue(SHAPE, shape);
        if (!level.setBlock(pos, state, 3)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof PlacedMoldTile tile) {
            tile.setMold(mold);
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return supports(level, pos.below());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return level.getBlockEntity(pos) instanceof PlacedMoldTile tile ? tile.mold().copy() : ItemStack.EMPTY;
    }
}
