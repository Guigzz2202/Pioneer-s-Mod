package fr.pioneers.bronze.autofoundry;

import fr.pioneers.api.multiblock.MultiblockPort;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4) : PIECE de la fonderie automatique : foyer ({@code fire_brick_firebox}, couche du bas) ou cuve ({@code foundry_vat}, au-dessus).
 * La tile ({@link FoundryPartTile}) prete a l'entonnoir et au cable logistique les cases de la fonderie formee (combustible par un foyer,
 * metal par la cuve : chaque objet va dans la case qui l'accepte) et passe l'air des soufflets au controleur. Le controleur PEINT l'etat :
 * {@link #HEAT} (0 froid, 1 tiede, 2 au rouge : la cuve rougeoie, le foyer flambe) et {@link #TOP} (cuve du dessus : le bain se voit sous la
 * grille). Clic droit : l'interface de la fonderie (structure formee).
 */
public class FoundryPartBlock extends BaseEntityBlock {

    public static final IntegerProperty HEAT = fr.pioneers.bronze.HeatPalette.HEAT;
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    /** Cuve (sinon foyer). */
    public final boolean vat;

    public FoundryPartBlock(Properties properties, boolean vat) {
        //1.0.5-beta (lot 23) : palier 0..3 et meme lumiere que les briques refractaires (chauffe homogene, HeatPalette)
        super(properties.lightLevel(s -> s.getValue(HEAT) * fr.pioneers.bronze.HeatPalette.LIGHT_PER_STEP));
        this.vat = vat;
        registerDefaultState(defaultBlockState().setValue(HEAT, 0).setValue(TOP, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT, TOP);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new FoundryPartTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        AutoFoundryTile host = MultiblockPort.house(level, pos, AutoFoundryTile.class);
        if (host == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, host, host.getBlockPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Peint l'etat d'une piece (chaleur, cuve du dessus) sans rien d'autre ; rien si le bloc n'est pas une piece. */
    public static void paint(Level level, BlockPos pos, int heat, boolean top) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof FoundryPartBlock part)) {
            return;
        }
        BlockState want = s.setValue(HEAT, Math.max(0, Math.min(fr.pioneers.bronze.HeatPalette.MAX, heat))).setValue(TOP, part.vat && top);
        if (want != s) {
            level.setBlock(pos, want, Block.UPDATE_CLIENTS);
        }
    }
}
