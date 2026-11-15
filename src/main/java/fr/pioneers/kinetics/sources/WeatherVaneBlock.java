package fr.pioneers.kinetics.sources;

import fr.pioneers.core.RenderClock;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.kinetics.KineticAngle;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.registries.PiKinetics;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Girouette-anemometre : la fleche suit la direction du vent, les coupelles tournent a sa vitesse. Clic droit : vent ici (m/s) et
 * puissance qu'en tirerait un moulin de 5 blocs ; comparateur : 1 niveau par m/s.
 */
public class WeatherVaneBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public WeatherVaneBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Tile tile ? Math.min(15, (int) Math.round(tile.wind)) : 0;
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!level.isClientSide) {
            double v = Wind.at(level, pos), rho = Wind.density(level);
            double power = 0.5 * rho * Math.PI * 25 * v * v * v * 0.15;
            player.displayClientMessage(Component.translatable("kinetics.pioneers.vane.reading", String.format(Locale.ROOT, "%.1f", v),
                  String.format(Locale.ROOT, "%.2f", rho), KineticTile.watts(power)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PiKinetics.VANE_TILE.get(), (l, p, s, t) -> t.tick((ServerLevel) l));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends PiBlockEntity {

        public double wind;

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.VANE_TILE.get(), pos, state);
        }

        void tick(ServerLevel level) {
            if (level.getGameTime() % 40 != (worldPosition.asLong() & 31)) {
                return;
            }
            double now = Wind.at(level, worldPosition);
            if (Math.abs(now - wind) > 0.5) {
                int before = (int) Math.round(wind);
                wind = now;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                if (before != (int) Math.round(now)) {
                    level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
                }
            }
        }

        /** Direction du vent (degres) : la meme pour tout le monde, tourne lentement. */
        public static double direction(Level level, float partial) {
            return direction(level.getGameTime() + partial);
        }

        /** 1.0.5-beta (lot 29) : direction du vent a une heure donnee (rendu : heure serveur estimee {@code RenderClock}, continue). */
        public static double direction(double t) {
            return 360 * Math.sin(t / 24000.0) + 25 * Math.sin(t / 233.0) + 8 * Math.sin(t / 31.0);
        }

        /** 1.0.5-beta (lot 29) : client : rotation des coupelles (20 tr/min par m/s), raccordee sans saut quand le vent change. */
        public final KineticAngle cups = new KineticAngle();

        @NotNull
        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            tag.putDouble("wind", wind);
            return tag;
        }

        @Override
        public void handleUpdateTag(CompoundTag tag) {
            wind = tag.getDouble("wind");
            if (level != null && level.isClientSide) {
                double now = RenderClock.now();
                //Avant le lot 29 : angle = heure du monde x vent x 6 : un changement de vent faisait sauter les coupelles n'importe ou
                cups.receive(wind * 6 / KineticTile.DEG_PER_TICK, cups.known() ? cups.angle(now) : 0, now, now, false);
            }
        }

        @Nullable
        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
            if (packet.getTag() != null) {
                handleUpdateTag(packet.getTag());
            }
        }
    }
}
