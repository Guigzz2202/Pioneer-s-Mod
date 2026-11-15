package fr.pioneers.farm.fish;

import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.farm.PiFishing;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Amas d'oeufs de poisson dans un herbier (toujours sous l'eau) : {@link #SPECIES} = espece, {@link #HATCH} = incubation (0..3). Eclot apres
 * longtemps (ticks aleatoires) en un poisson adulte de l'espece, si le coin et l'etendue d'eau ne sont pas surpeuples (1.0.3-beta :
 * {@link PiFish#BREED_CAP}, {@link PiFish#bodyCap}) ; se recolte a la fiole ou au seau (oeufs de
 * l'espece, qui se replantent sur un herbier) ; cassé, il rend ses oeufs.
 */
public class FishRoeBlock extends Block implements LiquidBlockContainer {

    public static final IntegerProperty SPECIES = IntegerProperty.create("species", 0, 63);
    public static final IntegerProperty HATCH = IntegerProperty.create("hatch", 0, 3);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 6, 13);

    public FishRoeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SPECIES, 0).setValue(HATCH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPECIES, HATCH);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return !canSurvive(state, level, pos) ? Blocks.WATER.defaultBlockState() : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }
        int h = state.getValue(HATCH);
        if (h < 3) {
            level.setBlock(pos, state.setValue(HATCH, h + 1), 2);
            return;
        }
        if (PiFish.crowded(level, pos, PiFish.BREED_CAP)) {
            return;
        }
        PiFish fish = PiFishing.speciesType(Math.min(state.getValue(SPECIES), GeneratedFish.SPECIES.length - 1)).create(level);
        if (fish != null) {
            fish.set(Math.min(state.getValue(SPECIES), GeneratedFish.SPECIES.length - 1), 0.8F + random.nextFloat() * 0.4F);
            fish.moveTo(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, random.nextFloat() * 360, 0);
            fish.setPersistenceRequired();
            level.addFreshEntity(fish);
            level.sendParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
            level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), 3);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.BUBBLE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.4, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                  0, 0.04, 0);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        //1.0.1 : flacon ou tout seau vide (vanilla, en argile...) ; le contenant revient
        if (!held.is(Items.GLASS_BOTTLE) && !Buckets.isEmptyBucket(held)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack roe = FishRoeItem.of(state.getValue(SPECIES), 1 + state.getValue(HATCH) / 2);
            if (!player.getInventory().add(roe)) {
                player.drop(roe, false);
            }
            if (!player.getAbilities().instabuild) {
                ItemStack back = held.copyWithCount(1);
                held.shrink(1);
                if (held.isEmpty()) {
                    player.setItemInHand(hand, back);
                } else if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
            }
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1, 1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(FishRoeItem.of(state.getValue(SPECIES), 1));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return FishRoeItem.of(state.getValue(SPECIES), 1);
    }
}
