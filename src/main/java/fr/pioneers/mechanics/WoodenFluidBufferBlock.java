package fr.pioneers.mechanics;

import fr.pioneers.kinetics.machines.MachineType;
import fr.pioneers.kinetics.machines.MechanicalMachineBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 2, notes de Guillaume : « ajouter un buffer de fluide en bois, a une texture connectee si place au-dessus de la noria pour
 * ameliorer la coherence et l'immersion ») : BUFFER DE FLUIDE EN BOIS de l'age mecanique : tonneau debout de douelles de chene cercle de fer
 * ({@link WoodenFluidBufferTile#CAPACITY} mB), fenetre de jauge en facade (niveau dessine par {@code WoodenFluidBufferRenderer}). Prend et
 * rend un liquide par toutes ses faces (entonnoirs a fluides, cables, seaux par clic droit) ; pose sur une NORIA ({@link #NORIA} : bloc du
 * dessous = noria, mis a jour a chaque voisin), son pied devient le bac de reception de la noria (modele {@code wooden_fluid_buffer_noria} :
 * goulotte et jambes posees sur ses paliers) et la noria le remplit. Le contenu reste dans l'objet quand on le casse ({@code BlockEntityTag}).
 */
public class WoodenFluidBufferBlock extends BaseEntityBlock {

    public static final BooleanProperty NORIA = BooleanProperty.create("noria");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public WoodenFluidBufferBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NORIA, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORIA);
    }

    /** Vrai si le bloc sous {@code pos} est une noria. */
    public static boolean onNoria(BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof MechanicalMachineBlock machine && machine.type == MachineType.NORIA;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(NORIA, onNoria(context.getLevel(), context.getClickedPos()));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        if (direction == Direction.DOWN) {
            return state.setValue(NORIA, onNoria(level, pos));
        }
        return state;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WoodenFluidBufferTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof WoodenFluidBufferTile tile && FluidUtil.interactWithFluidHandler(player, hand, tile.tank())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof WoodenFluidBufferTile tile) || tile.tank().isEmpty()) {
            return 0;
        }
        return 1 + Math.round(14F * tile.tank().getFluidAmount() / WoodenFluidBufferTile.CAPACITY);
    }

    /** Le tonneau casse garde son liquide (dans l'objet, {@code BlockEntityTag}, relu par Minecraft a la pose). */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(@NotNull BlockState state, @NotNull LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof WoodenFluidBufferTile tile && !tile.tank().isEmpty()) {
            for (ItemStack stack : drops) {
                if (stack.getItem() == asItem()) {
                    CompoundTag be = stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG);
                    be.put(WoodenFluidBufferTile.KEY, tile.tank().writeToNBT(new CompoundTag()));
                }
            }
        }
        return drops;
    }
}
