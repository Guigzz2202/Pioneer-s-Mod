package fr.pioneers.farm.device;

import java.util.Locale;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc fonctionnel de la ferme animee (1.3.1) : arroseurs, composteur, station meteo, ruche, pondoir, couveuse, distributeur automatique.
 * Une seule sorte de tuile ({@link PiFarmDevices#DEVICE_TILE}) ; la classe de tuile vient de {@link Kind}. 1.0.5-beta (lot 41) : clic
 * droit = l'interface ({@link #use}). Modeles dessines facade au nord ({@code tools/gc/farm2/art_devices.py}) ; les parties animees par
 * {@code client/farm/device/DeviceRenderer}.
 */
public class DeviceBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Sortes : tuile, forme (px, facade au nord). */
    public enum Kind {
        BRONZE_SPRINKLER(SprinklerTile::new, 2, 0, 2, 14, 13, 14),
        STEEL_SPRINKLER(SprinklerTile::new, 2, 0, 2, 14, 16, 14),
        COMPOST_BIN(CompostBinTile::new, 1, 0, 1, 15, 14, 15),
        WEATHER_STATION(WeatherStationTile::new, 5, 0, 5, 11, 16, 11),
        APIARY(ApiaryTile::new, 1, 0, 1, 15, 15, 15),
        NEST_BOX(NestBoxTile::new, 1, 0, 2, 15, 11, 15),
        INCUBATOR(IncubatorTile::new, 1, 0, 2, 15, 10, 14),
        AUTO_FEEDER(AutoFeederTile::new, 1, 0, 1, 15, 16, 15),
        /** 1.0.4-beta (lot 9) : clapier (lapins sans entite, portees, peaux, laine angora, fumier). */
        RABBIT_HUTCH(HutchTile::new, 0, 0, 0, 16, 16, 16);

        final BiFunction<BlockPos, BlockState, DeviceTile> factory;
        final VoxelShape[] shapes = new VoxelShape[4];

        Kind(BiFunction<BlockPos, BlockState, DeviceTile> factory, double x0, double y0, double z0, double x1, double y1, double z1) {
            this.factory = factory;
            for (int i = 0; i < 4; i++) {
                double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
                for (int r = 0; r < i; r++) {
                    double nx0 = 16 - az1, nx1 = 16 - az0, nz0 = ax0, nz1 = ax1;
                    ax0 = nx0;
                    ax1 = nx1;
                    az0 = nz0;
                    az1 = nz1;
                }
                shapes[i] = Block.box(ax0, y0, az0, ax1, y1, az1);
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean sprinkler() {
            return this == BRONZE_SPRINKLER || this == STEEL_SPRINKLER;
        }
    }

    final Kind kind;

    public DeviceBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        return kind.shapes[i];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind.factory.apply(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != PiFarmDevices.DEVICE_TILE.get()) {
            return null;
        }
        return level.isClientSide ? (l, p, s, t) -> ((DeviceTile) t).clientTick() : (l, p, s, t) -> ((DeviceTile) t).serverTick();
    }

    /**
     * 1.0.5-beta (lot 41, retour 60 de Guillaume : « plus d'interactions directes, une vraie interface ») : clic droit = l'interface de
     * l'appareil ({@link FarmDeviceTile}, interface standard v2) ; seul geste garde en monde : les lapins tenus en laisse entrent au clapier
     * ({@link FarmDeviceTile#worldGesture}). Ouvrir la ruche ou la station meteo a ses effets ({@link FarmDeviceTile#opened}).
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof FarmDeviceTile tile)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (tile.worldGesture(player)) {
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, tile, tile.getBlockPos());
            tile.opened(player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DeviceTile tile) {
            tile.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof DeviceTile tile) {
            tile.particles(random);
        }
    }

    /** 1.6 : comparateur contre le composteur (niveau 0..8, comme le composteur vanilla). */
    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return kind == Kind.COMPOST_BIN;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CompostBinTile c ? c.comparator() : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return kind == Kind.WEATHER_STATION;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (kind != Kind.WEATHER_STATION || !(level instanceof Level l)) {
            return 0;
        }
        return l.isThundering() && l.isRainingAt(pos.above()) ? 15 : l.isRainingAt(pos.above()) ? 8 : 0;
    }
}
