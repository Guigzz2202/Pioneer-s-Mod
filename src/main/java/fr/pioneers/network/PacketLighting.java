package fr.pioneers.network;

import fr.pioneers.client.lighting.LightLinkPreview;
import fr.pioneers.lighting.LightFixtureBlock;
import fr.pioneers.lighting.LightLinks;
import fr.pioneers.lighting.LightSwitchBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 5) : apercu des liaisons de l'eclairage. Le client demande les liaisons du bloc vise (interrupteur ou lumiere,
 * {@link Ask}) ; le serveur renvoie l'interrupteur, son etat et ses lumieres ({@link Links}) ; il les envoie aussi apres chaque liaison.
 * Ajoute EN FIN de {@link PiNetwork} (VERSION 20 inchangee).
 */
public final class PacketLighting {

    private PacketLighting() {
    }

    /** Distance maximale (blocs) entre le joueur et le bloc dont il demande les liaisons. */
    private static final double REACH = 48;

    /** Envoie au joueur les liaisons de l'interrupteur {@code sw}. */
    public static void send(ServerPlayer player, BlockPos sw, LightLinks data) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Links(sw, data.isOn(sw), data.lightsOf(sw)));
    }

    /** Client -> serveur : liaisons du bloc vise. */
    public record Ask(BlockPos pos) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
        }

        public static Ask decode(FriendlyByteBuf buffer) {
            return new Ask(buffer.readBlockPos());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || !PacketRate.allowKeyed(player, PacketRate.LOOK, pos.asLong() ^ 0x5A5A5A5AL)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) {
                return;
            }
            LightLinks data = LightLinks.get(level);
            BlockState st = level.getBlockState(pos);
            BlockPos sw = st.getBlock() instanceof LightSwitchBlock ? pos : st.getBlock() instanceof LightFixtureBlock ? data.switchOf(pos) : null;
            if (sw == null) {
                sw = LightLinks.memorized(level, player);
            }
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), sw == null ? new Links(null, false, List.of())
                  : new Links(sw, data.isOn(sw), data.lightsOf(sw)));
        }
    }

    /** Serveur -> client : un interrupteur (null = aucun), son etat, ses lumieres. */
    public record Links(@Nullable BlockPos sw, boolean on, List<BlockPos> lights) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(sw != null);
            if (sw != null) {
                buffer.writeBlockPos(sw);
                buffer.writeBoolean(on);
                int n = Math.min(LightLinks.MAX_LIGHTS, lights.size());
                buffer.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buffer.writeBlockPos(lights.get(i));
                }
            }
        }

        public static Links decode(FriendlyByteBuf buffer) {
            if (!buffer.readBoolean()) {
                return new Links(null, false, List.of());
            }
            BlockPos sw = buffer.readBlockPos();
            boolean on = buffer.readBoolean();
            int n = Math.min(LightLinks.MAX_LIGHTS, buffer.readVarInt());
            List<BlockPos> lights = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                lights.add(buffer.readBlockPos());
            }
            return new Links(sw, on, lights);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightLinkPreview.receive(sw, on, lights));
        }
    }
}
