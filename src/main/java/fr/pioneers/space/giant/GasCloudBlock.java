package fr.pioneers.space.giant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Nuage d'une geante gazeuse : on le traverse, on ne peut ni le viser ni le casser, et un bloc pose a sa place le remplace
 * (comme de l'air). Translucide ; deux nuages voisins ne dessinent pas leur face commune. Les nuages d'orage, plus denses,
 * freinent ce qui les traverse.
 */
public class GasCloudBlock extends Block {

    private final boolean storm;

    public GasCloudBlock(Properties properties, boolean storm) {
        super(properties);
        this.storm = storm;
    }

    public static Properties properties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 0.0F).noCollission().noOcclusion().noLootTable().replaceable()
              .sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY).isViewBlocking((state, level, pos) -> false)
              .isSuffocating((state, level, pos) -> false).isValidSpawn((state, level, pos, type) -> false);
    }

    @NotNull
    @Override
    @Deprecated
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty();
    }

    @NotNull
    @Override
    @Deprecated
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty();
    }

    @NotNull
    @Override
    @Deprecated
    public VoxelShape getVisualShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @Deprecated
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState neighbor, @NotNull Direction direction) {
        return neighbor.getBlock() instanceof GasCloudBlock || super.skipRendering(state, neighbor, direction);
    }

    @Override
    @Deprecated
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Override
    @Deprecated
    public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
        if (storm && entity instanceof LivingEntity) {
            entity.makeStuckInBlock(state, new Vec3(0.9, 0.85, 0.9));
        }
    }
}
