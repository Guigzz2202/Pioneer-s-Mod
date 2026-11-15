package fr.pioneers.warehouse;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : BASE des pieces de la caisse d'entrepot (cadre, panneau, port, trappe). La trappe PEINT a la formation l'etat
 * {@link #PART} (place de la piece dans la grande caisse, {@link CrateLayout#partIndex}) et {@link #AXIS} (sens de la longueur) : chaque
 * bloc montre son morceau de la caisse cerclee (modeles {@code tools/gc/warehouse/warehouse.py}) ; 0 = piece libre (modele propre).
 * Le joueur n'a AUCUN acces au contenu : clic droit = une ligne d'etat (fiche detaillee au regard, {@link WarehouseLook}).
 */
public abstract class CrateBlock extends BaseEntityBlock {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, CrateLayout.PARTS - 1);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    protected CrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 0).setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, AXIS);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return null;
    }

    /** Trappe de la caisse formee qui contient cette piece, ou null. */
    @Nullable
    public static CrateHatchTile host(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CrateHatchTile hatch) {
            return hatch;
        }
        CrateHatchTile host = fr.pioneers.api.multiblock.MultiblockPort.house(level, pos, CrateHatchTile.class);
        return host != null && host.formed() ? host : null;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            CrateHatchTile host = host(level, pos);
            Component line;
            if (host != null && host.formed()) {
                CrateStorage s = host.storage();
                line = Component.translatable("warehouse.pioneers.crate.status", host.length().size(), fr.pioneers.core.Texts.format(s.total()),
                      s.used(), s.maxTypes()).withStyle(ChatFormatting.GOLD);
            } else {
                line = Component.translatable("warehouse.pioneers.crate.unformed").withStyle(ChatFormatting.YELLOW);
            }
            player.displayClientMessage(line, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return state.getValue(PART) > 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        CrateHatchTile host = host(level, pos);
        return host == null || !host.formed() ? 0 : host.storage().comparator();
    }
}
