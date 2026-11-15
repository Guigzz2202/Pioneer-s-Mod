package fr.pioneers.tech.kinetics.parts;

import fr.pioneers.core.block.Covers;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticMaterial;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Arbre de transmission (bois, fer) : transmet la rotation en ligne droite, bout a bout. 1.0.4-beta (lot 27) : cache ({@link Covers} :
 * l'arbre tourne toujours dedans, il n'est plus dessine ; forme, lumiere et durete du bloc de cache) ; lot 30 : seulement dans un cadre de
 * cache (clic droit avec le cadre, puis avec un bloc plein ; lime : retire la cache puis le cadre ; casser : cache, cadre, puis l'arbre).
 */
public class ShaftBlock extends AxisKineticBlock {

    static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Direction.Axis.class);

    static {
        SHAPES.put(Direction.Axis.X, Block.box(0, 6, 6, 16, 10, 10));
        SHAPES.put(Direction.Axis.Y, Block.box(6, 0, 6, 10, 16, 10));
        SHAPES.put(Direction.Axis.Z, Block.box(6, 6, 0, 10, 10, 16));
    }

    public final KineticMaterial material;

    public ShaftBlock(KineticMaterial material, Properties properties) {
        super(properties);
        this.material = material;
        registerDefaultState(defaultBlockState().setValue(Covers.COVERED, false).setValue(Covers.FRAMED, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Covers.COVERED, Covers.FRAMED);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Covers.framed(state) ? Shapes.block() : SHAPES.get(axis(state));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ShaftTile(pos, state);
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 27) : cache
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult covered = Covers.use(state, level, pos, player, hand, hit);
        return covered != null ? covered : super.interact(state, level, pos, player, hand, hit);
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return !state.getValue(Covers.COVERED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return state.getValue(Covers.COVERED) ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return !Covers.breakCover(state, level, pos, player) && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return Covers.progress(state, player, level, pos, super.getDestroyProgress(state, player, level, pos));
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return Covers.sound(state, level, pos, entity, super.getSoundType(state, level, pos, entity));
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        ItemStack cover = Covers.pick(state, level, pos);
        return cover != null ? cover : super.getCloneItemStack(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        Covers.dropOnRemove(state, level, pos, next);
        super.onRemove(state, level, pos, next, moving);
    }
}
