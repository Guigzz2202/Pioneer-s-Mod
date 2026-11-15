package fr.pioneers.space.environment;

import fr.pioneers.Pioneers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Porte de sas : une porte coulissante etanche de deux blocs.
 * <ul>
 *     <li>Fermee, elle forme un bloc plein : l'air d'une piece pressurisee ne passe pas ;</li>
 *     <li>ouverte, seul le cadre reste : la piece communique avec l'exterieur et se depressurise.</li>
 * </ul>
 * Une seule porte ne suffit donc pas : il faut un sas (deux portes et un petit volume entre les deux) pour entrer
 * sans vider la base de son oxygene. La porte se referme seule apres 4 secondes, sauf si elle est alimentee en redstone.
 */
public class AirlockDoorBlock extends DoorBlock {

    public static final BlockSetType AIRLOCK = BlockSetType.register(new BlockSetType(Pioneers.MODID + ":airlock", true, SoundType.METAL,
          SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
          SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF,
          SoundEvents.STONE_BUTTON_CLICK_ON));
    /** Fermeture automatique (ticks). */
    public static final int AUTO_CLOSE = 80;

    private static final VoxelShape FRAME_X_LOWER = Shapes.or(shape(0, 0, 0, 2, 16, 16), shape(14, 0, 0, 16, 16, 16));
    private static final VoxelShape FRAME_X_UPPER = Shapes.or(FRAME_X_LOWER, shape(0, 13, 0, 16, 16, 16));
    private static final VoxelShape FRAME_Z_LOWER = Shapes.or(shape(0, 0, 0, 16, 16, 2), shape(0, 0, 14, 16, 16, 16));
    private static final VoxelShape FRAME_Z_UPPER = Shapes.or(FRAME_Z_LOWER, shape(0, 13, 0, 16, 16, 16));

    public AirlockDoorBlock(Properties properties) {
        super(properties, AIRLOCK);
    }

    @NotNull
    @Override
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (!state.getValue(OPEN)) {
            return Shapes.block();
        }
        boolean upper = state.getValue(HALF) == DoubleBlockHalf.UPPER;
        //On passe dans le sens de la facade : le cadre est sur les deux autres cotes
        if (state.getValue(FACING).getAxis() == Direction.Axis.Z) {
            return upper ? FRAME_X_UPPER : FRAME_X_LOWER;
        }
        return upper ? FRAME_Z_UPPER : FRAME_Z_LOWER;
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        InteractionResult result = super.use(state, level, pos, player, hand, hit);
        if (!level.isClientSide && result.consumesAction()) {
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() == this && now.getValue(OPEN)) {
                level.scheduleTick(pos, this, AUTO_CLOSE);
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(OPEN) && !state.getValue(POWERED)) {
            setOpen(null, level, state, pos, false);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacent, @NotNull Direction direction) {
        return adjacent.is(this) && !state.getValue(OPEN) && !adjacent.getValue(OPEN) || super.skipRendering(state, adjacent, direction);
    }

    static VoxelShape shape(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }
}
